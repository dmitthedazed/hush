package com.occaecat.hush.apple.earpods

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.occaecat.hush.autoeq.AutoEq
import com.occaecat.hush.apple.usb.EarPodsCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Apple EarPods (USB-C): a plain USB Audio headset, so there is no control channel, only what Android and the
 * USB descriptors tell. This watches for them, reads their descriptors once USB access is granted (for the
 * authenticity check and the details), and drives the phone-side equalizer while they're plugged in.
 */
object EarPods {
    data class Audio(val rates: List<Int>, val encodings: List<String>, val channels: List<Int>)

    data class State(
        val attached: Boolean = false,
        val hasAccess: Boolean = false,
        val manufacturer: String? = null,
        val product: String? = null,
        val serial: String? = null,
        val firmware: String? = null,
        val report: EarPodsCheck.Report? = null,
        val output: Audio? = null,
        val mic: Audio? = null,
        val eq: SoftEq.Settings = SoftEq.Settings(),
        val eqError: String? = null,
    )

    private const val ACTION_PERMISSION = "com.occaecat.hush.earpods.USB_PERMISSION"

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var app: Context
    internal val appContext get() = app
    private val usb get() = app.getSystemService(UsbManager::class.java)

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        _state.update { it.copy(eq = SoftEq.load(app)) }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_PERMISSION)
        }
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = refresh()
        }, filter, Context.RECEIVER_NOT_EXPORTED)
        // USB attach/detach broadcasts aren't sent to every receiver; audio routing changes always are.
        app.getSystemService(AudioManager::class.java).registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
        }, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun device(): UsbDevice? =
        usb.deviceList.values.firstOrNull { EarPodsCheck.isEarPods(it.vendorId, it.productId) }

    fun refresh() {
        val dev = device()
        if (dev == null) {
            _state.update { State(eq = it.eq) }
            applyEq()
            EarPodsService.sync(app, false)
            return
        }
        val access = usb.hasPermission(dev)
        var raw: ByteArray? = null
        var bos: ByteArray? = null
        if (access) usb.openDevice(dev)?.let { conn ->
            try {
                raw = conn.rawDescriptors
                // BOS: read the 5-byte header for wTotalLength, then the whole thing.
                val head = ByteArray(5)
                if (conn.controlTransfer(0x80, 6, 0x0F00, 0, head, head.size, 1000) == 5) {
                    val total = (head[2].toInt() and 0xFF) or ((head[3].toInt() and 0xFF) shl 8)
                    val full = ByteArray(total)
                    val n = conn.controlTransfer(0x80, 6, 0x0F00, 0, full, total, 1000)
                    if (n > 0) bos = full.copyOf(n)
                }
            } finally {
                conn.close()
            }
        }
        val serial = if (access) runCatching { dev.serialNumber }.getOrNull() else null
        val report = EarPodsCheck.check(
            EarPodsCheck.Input(dev.vendorId, dev.productId, dev.manufacturerName, dev.productName, serial, raw, bos),
        )
        val audio = app.getSystemService(AudioManager::class.java)
        fun describe(flag: Int) = audio.getDevices(flag)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            ?.let { d ->
                Audio(d.sampleRates.toList().sorted(), d.encodings.toList().distinct().map(::encodingName), d.channelCounts.toList().distinct().sorted())
            }
        _state.update {
            it.copy(
                attached = true, hasAccess = access, manufacturer = dev.manufacturerName, product = dev.productName,
                serial = serial, firmware = dev.version, report = report,
                output = describe(AudioManager.GET_DEVICES_OUTPUTS), mic = describe(AudioManager.GET_DEVICES_INPUTS),
            )
        }
        applyEq()
        EarPodsService.sync(app, _state.value.eq.enabled)
    }

    /** Asks for USB access (a system dialog); the answer arrives as a broadcast that refreshes the state. */
    fun requestAccess() {
        val dev = device() ?: return
        val pi = PendingIntent.getBroadcast(
            app, 0, Intent(ACTION_PERMISSION).setPackage(app.packageName),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        usb.requestPermission(dev, pi)
    }

    // --- Equalizer -------------------------------------------------------------------------------------------------

    private fun editEq(change: (SoftEq.Settings) -> SoftEq.Settings) {
        _state.update { it.copy(eq = change(it.eq)) }
        SoftEq.save(app, _state.value.eq)
        applyEq()
        EarPodsService.sync(app, _state.value.attached && _state.value.eq.enabled)
    }

    private fun applyEq() {
        val s = _state.value
        val error = SoftEq.apply(s.eq, s.attached)
        _state.update { it.copy(eqError = error) }
    }

    fun setEqEnabled(on: Boolean) = editEq { it.copy(enabled = on) }
    fun setBand(index: Int, db: Int) = editEq { s ->
        s.copy(gains = s.gains.toMutableList().also { it[index] = db.coerceIn(-SoftEq.MAX_DB, SoftEq.MAX_DB) })
    }
    fun setGains(gains: List<Int>) = editEq { it.copy(gains = gains) }
    fun setCorrection(entry: AutoEq.Entry, curve: List<AutoEq.Point>) =
        editEq { it.copy(correction = SoftEq.Correction(entry.name, entry.detail, curve), correctionOn = true) }
    fun setCorrectionOn(on: Boolean) = editEq { it.copy(correctionOn = on) }
    fun clearCorrection() = editEq { it.copy(correction = null) }

    /** Hold-to-compare: bypasses the effect without touching the saved settings. */
    fun bypass(on: Boolean) {
        val s = _state.value
        SoftEq.apply(s.eq.copy(enabled = !on && s.eq.enabled), s.attached)
    }

    private fun encodingName(e: Int) = when (e) {
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "float"
        AudioFormat.ENCODING_PCM_8BIT -> "8-bit"
        else -> "#$e"
    }
}
