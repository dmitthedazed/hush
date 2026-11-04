package com.occaecat.hush.wired

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The phone's wired outputs: the headphone jack, or a USB-C adapter / USB headset. Passive headphones say nothing
 * about themselves, so all Hush can know is that "something wired" is playing. Accessories that do identify
 * themselves (EarPods over USB) [claim] their output; what's left over is [free], the output a passive wired
 * headphone the user registered is taken to be on.
 */
object WiredAudio {
    data class Output(
        val type: Int,
        /** What Android calls it: the adapter's USB product name, or the jack's generic name. */
        val product: String,
        val usb: Boolean,
        /** It has a microphone (a headset on the jack, a USB headset or an adapter with a mic line). */
        val mic: Boolean,
        val rates: List<Int>,
    ) {
        val kind get() = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headphone jack"
            else -> "USB-C adapter"
        }
    }

    private val outTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
    )
    private val micTypes = setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)

    private val _outputs = MutableStateFlow<List<Output>>(emptyList())
    val outputs: StateFlow<List<Output>> = _outputs.asStateFlow()
    private val _free = MutableStateFlow<Output?>(null)
    val free: StateFlow<Output?> = _free.asStateFlow()

    private val claims = mutableMapOf<String, (Output) -> Boolean>()
    private var audio: AudioManager? = null

    fun init(context: Context) {
        if (audio != null) return
        val am = context.applicationContext.getSystemService(AudioManager::class.java)
        audio = am
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
        }, Handler(Looper.getMainLooper()))
        refresh()
    }

    /** [owner]'s accessory is on the outputs [isMine] picks; call [refresh] when that changes. */
    fun claim(owner: String, isMine: (Output) -> Boolean) {
        claims[owner] = isMine
        refresh()
    }

    fun refresh() {
        val am = audio ?: return
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.type }.toSet()
        val list = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in outTypes }.map { d ->
            val usb = d.type == AudioDeviceInfo.TYPE_USB_HEADSET || d.type == AudioDeviceInfo.TYPE_USB_DEVICE
            Output(
                type = d.type, product = d.productName?.toString().orEmpty(), usb = usb,
                mic = if (usb) inputs.any { it in micTypes && it != AudioDeviceInfo.TYPE_WIRED_HEADSET }
                    else AudioDeviceInfo.TYPE_WIRED_HEADSET in inputs,
                rates = d.sampleRates.toList().sorted(),
            )
        }
        _outputs.value = list
        _free.value = list.firstOrNull { o -> claims.values.none { it(o) } }
    }

    /**
     * Insights samples for a wired device: the phone's music volume is what reaches the ears, sampled every half
     * minute while [connected].
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun volumeSamples(context: Context, connected: kotlinx.coroutines.flow.Flow<Boolean>) =
        connected.flatMapLatest { up ->
            val am = context.applicationContext.getSystemService(AudioManager::class.java)
            kotlinx.coroutines.flow.flow {
                while (true) {
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    emit(com.occaecat.hush.features.Insights.Sample(
                        connected = up, volume = am.getStreamVolume(AudioManager.STREAM_MUSIC), volumeSteps = max + 1,
                    ))
                    kotlinx.coroutines.delay(30_000)
                }
            }
        }
}
