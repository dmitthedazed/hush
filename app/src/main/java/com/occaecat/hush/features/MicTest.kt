package com.occaecat.hush.features

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.occaecat.hush.devices.Microphone
import com.occaecat.hush.devices.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records a few seconds from a device's microphone (or the phone's, to compare) and plays them back, so you hear
 * yourself as others do on calls. Any device with a [Microphone] gets it:
 *  - Bluetooth: the mic only exists on the call profile, so the headset becomes the communication device for the
 *    recording (music pauses) and the route is dropped before playback, which then goes over the music profile.
 *  - USB: the input is picked directly; playback goes to the same USB device.
 */
class MicTest(context: Context, private val mic: Microphone) {

    enum class Phase { IDLE, RECORDING, PLAYING }
    enum class Source { DEVICE, PHONE }

    data class Result(val from: String, val sampleRate: Int, val peakDb: Double, val floorDb: Double, val clipped: Boolean)

    data class State(
        val phase: Phase = Phase.IDLE,
        /** 0..1 for the meter: the live input level while recording, the playback level while playing. */
        val level: Float = 0f,
        /** 0..1 through the recording or the playback. */
        val progress: Float = 0f,
        val result: Result? = null,
        val error: String? = null,
    )

    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var job: Job? = null

    private val bluetooth get() = mic.transport == Transport.BLUETOOTH
    private val btTypes = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
    private val usbTypes = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)
    /** A passive headset: on the jack, or on a USB-C adapter. */
    private val wiredTypes = usbTypes + setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES)

    private fun matches(d: AudioDeviceInfo) = when (mic.transport) {
        Transport.BLUETOOTH -> d.type in btTypes && (mic.address == null || d.address.equals(mic.address, ignoreCase = true))
        Transport.USB -> d.type in usbTypes
        Transport.WIRED -> d.type in wiredTypes
    }

    /** The device's input as Android sees it right now (for Bluetooth, only once the call route is up). */
    private fun input(): AudioDeviceInfo? = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull(::matches)

    fun record(source: Source, seconds: Int = 5) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = State(Phase.RECORDING)
            val viaCall = source == Source.DEVICE && bluetooth
            try {
                if (viaCall && !openCallRoute()) {
                    _state.value = State(error = "The headset's microphone isn't available. Check that “Calls” (HFP) is on for it in Bluetooth settings.")
                    return@launch
                }
                val pcm = withContext(Dispatchers.IO) { capture(source, seconds) } ?: return@launch
                if (viaCall) closeCallRoute()
                _state.value = _state.value.copy(phase = Phase.PLAYING, level = 0f, progress = 0f)
                if (viaCall) delay(700) // the music profile takes a moment to come back after the call route
                withContext(Dispatchers.IO) { play(pcm.first, pcm.second, source) }
                _state.value = _state.value.copy(phase = Phase.IDLE, level = 0f, progress = 0f)
            } finally {
                if (viaCall) closeCallRoute()
                if (_state.value.phase != Phase.IDLE) _state.value = _state.value.copy(phase = Phase.IDLE, level = 0f, progress = 0f)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun close() {
        scope.cancel()
        if (bluetooth) closeCallRoute()
    }

    private suspend fun openCallRoute(): Boolean {
        val target = audio.availableCommunicationDevices.firstOrNull(::matches) ?: return false
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        if (!audio.setCommunicationDevice(target)) return false
        // The SCO link comes up asynchronously; wait until the input appears.
        repeat(30) { if (input() != null) return true; delay(100) }
        return input() != null
    }

    private fun closeCallRoute() {
        runCatching { audio.clearCommunicationDevice() }
        audio.mode = AudioManager.MODE_NORMAL
    }

    /** Mono 16-bit PCM and its sample rate, or null when recording failed (the error is in [state]). */
    @SuppressLint("MissingPermission")
    private suspend fun capture(source: Source, seconds: Int): Pair<ShortArray, Int>? {
        val device = if (source == Source.DEVICE) input() else
            audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        if (device == null) {
            _state.value = State(error = "Hush can't see the microphone right now. Reconnect the device and try again.")
            return null
        }
        // Bluetooth calls run at 16 kHz (8 kHz on old headsets); asking for more only upsamples.
        val rate = if (source == Source.DEVICE && bluetooth) 16_000 else 48_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord.Builder()
            .setAudioSource(if (source == Source.DEVICE && bluetooth) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(max(min, rate / 5 * 2))
            .build()
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            _state.value = State(error = "The microphone couldn't be opened; another app may be using it.")
            return null
        }
        record.preferredDevice = device
        val out = ShortArray(rate * seconds)
        val chunk = ShortArray(rate / 20) // 50 ms
        var n = 0
        val chunkRms = ArrayList<Double>()
        try {
            record.startRecording()
            while (n < out.size && kotlinx.coroutines.currentCoroutineContext().isActive) {
                val r = record.read(chunk, 0, minOf(chunk.size, out.size - n))
                if (r <= 0) break
                chunk.copyInto(out, n, 0, r)
                n += r
                val rms = rms(chunk, r)
                chunkRms += rms
                _state.value = _state.value.copy(level = meter(rms), progress = n.toFloat() / out.size)
            }
            val routed = record.routedDevice ?: device
            val from = routed.productName?.toString()?.takeIf { it.isNotBlank() && routed.type != AudioDeviceInfo.TYPE_BUILTIN_MIC }
                ?: if (routed.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) "Phone microphone" else "Microphone"
            // The first 300 ms are skipped: a Bluetooth call link starts with digital silence and a click.
            val skip = minOf(rate * 3 / 10, n)
            var peak = 0
            for (i in skip until n) peak = max(peak, kotlin.math.abs(out[i].toInt()))
            // Background: the quietest tenth of the 50 ms chunks that carry any signal.
            val sorted = chunkRms.drop(6).filter { it > 0 }.sorted()
            val floor = if (sorted.isEmpty()) 0.0 else sorted.take(max(1, sorted.size / 10)).average()
            _state.value = _state.value.copy(result = Result(from, rate, db(peak / 32768.0), db(floor), peak >= 32700))
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        return if (n == 0) null.also { _state.value = State(error = "Nothing came from the microphone.") } else out.copyOf(n) to rate
    }

    private suspend fun play(pcm: ShortArray, rate: Int, source: Source) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        // Played back on the device being tested (a USB headset isn't always the media route).
        if (mic.transport != Transport.BLUETOOTH) audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull(::matches)?.let { track.preferredDevice = it }
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val window = rate / 20
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val pos = track.playbackHeadPosition
                if (pos >= pcm.size) break
                val from = pos.coerceAtMost(pcm.size - 1)
                val len = minOf(window, pcm.size - from)
                _state.value = _state.value.copy(level = meter(rms(pcm, len, from)), progress = pos.toFloat() / pcm.size)
                delay(50)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun rms(a: ShortArray, len: Int, from: Int = 0): Double {
        if (len <= 0) return 0.0
        var sum = 0.0
        for (i in from until from + len) { val v = a[i] / 32768.0; sum += v * v }
        return sqrt(sum / len)
    }

    private fun db(v: Double) = if (v <= 0) -96.0 else 20 * log10(v)

    /** −60 dBFS … 0 dBFS onto 0 … 1. */
    private fun meter(rms: Double) = ((db(rms) + 60) / 60).toFloat().coerceIn(0f, 1f)
}
