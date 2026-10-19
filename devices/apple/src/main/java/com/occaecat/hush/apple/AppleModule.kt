package com.occaecat.hush.apple

import android.content.Context
import com.occaecat.hush.apple.earpods.EarPods
import com.occaecat.hush.apple.earpods.EarPodsController
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceModule
import com.occaecat.hush.devices.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Apple accessories. Today: wired EarPods (USB-C), listed only while plugged in. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object AppleModule : DeviceModule {
    override val id = "apple"
    override val brand = "Apple"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val earPods = Device("$id:earpods-usb-c", id, "EarPods (USB-C)", Transport.USB, active = true)

    override val devices: StateFlow<List<Device>> = EarPods.state
        .map { if (it.attached) listOf(earPods) else emptyList() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun init(context: Context) {
        EarPods.init(context)
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        // Wired: the phone's music volume is what reaches the ears; sampled every half minute while plugged in.
        com.occaecat.hush.features.Insights.track(context, EarPodsController.insightsProfile, EarPods.state.flatMapLatest { s ->
            kotlinx.coroutines.flow.flow {
                while (true) {
                    val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    emit(com.occaecat.hush.features.Insights.Sample(
                        connected = s.attached, volume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC),
                        volumeSteps = max + 1,
                    ))
                    kotlinx.coroutines.delay(30_000)
                }
            }
        })
    }

    override fun controller(device: Device): DeviceController = EarPodsController
}
