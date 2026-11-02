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
import com.occaecat.hush.apple.usb.EarPodsCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.occaecat.hush.wired.PhoneEqualizer

/**
 * Apple EarPods (USB-C): a plain USB Audio headset, so there is no control channel, only what Android and the
 * USB descriptors tell. This watches for them, reads their descriptors once USB access is granted (for the
 * authenticity check and the details), and runs the phone-side equalizer while they're plugged in.
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
    )

    private const val ACTION_PERMISSION = "com.occaecat.hush.earpods.USB_PERMISSION"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var app: Context
    internal val appContext get() = app
    private val usb get() = app.getSystemService(UsbManager::class.java)

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        eq = PhoneEqualizer(
            app, "apple:earpods-usb-c", "EarPods", "earpods_eq",
            state.map { it.attached }.stateIn(scope, SharingStarted.Eagerly, false), "Apple EarPods",
        )
        com.occaecat.hush.wired.WiredAudio.init(app)
        // While they're plugged in, the USB output is theirs, not a passive headphone's on an adapter.
        com.occaecat.hush.wired.WiredAudio.claim("apple:earpods") { it.usb && _state.value.attached }
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
            _state.value = State()
            com.occaecat.hush.wired.WiredAudio.refresh()
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
        com.occaecat.hush.wired.WiredAudio.refresh()
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

    /** The phone-side equalizer, on while they're plugged in. */
    lateinit var eq: PhoneEqualizer
        private set

    private fun encodingName(e: Int) = when (e) {
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "float"
        AudioFormat.ENCODING_PCM_8BIT -> "8-bit"
        else -> "#$e"
    }
}
