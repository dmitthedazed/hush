package com.occaecat.hush.apple

import android.content.Context
import com.occaecat.hush.apple.earpods.EarPods
import com.occaecat.hush.apple.earpods.EarPodsController
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.Candidate
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.Model
import com.occaecat.hush.devices.DeviceModule
import com.occaecat.hush.devices.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Apple accessories. Today: wired EarPods (USB-C), found when plugged in; the model is their identity. */
object AppleModule : DeviceModule {
    override val id = "apple"
    override val brand = "Apple"

    override val notice = "Hush is not made, endorsed or supported by Apple. Apple and EarPods are trademarks of Apple Inc."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private const val EARPODS = "earpods-usb-c"
    private val earPods = Device("$id:$EARPODS", id, "EarPods (USB-C)", Transport.USB, active = true, modelId = EARPODS)
    private val earPodsFound = Candidate(id, EARPODS, "EarPods (USB-C)", Transport.USB)

    override val models = listOf(
        Model(
            EARPODS, "EarPods (USB-C)", Transport.USB,
            "Plug them into the phone's USB-C port. Hush adds a phone-side equalizer, checks they're genuine and " +
                "links Apple's documentation. You can add them now and plug them in later.",
            addWithoutFinding = true, note = "Fully supported",
            // Apple's own product shot (MTJY3, EarPods USB-C) from its store image server, downloaded once.
            imageUrl = "https://store.storeimages.cdn-apple.com/1/as-images.apple.com/is/MTJY3?wid=720&hei=720&fmt=png-alpha",
        ),
    )

    override val candidates: StateFlow<List<Candidate>> = EarPods.state
        .map { if (it.attached) listOf(earPodsFound) else emptyList() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun remembered(context: Context) = candidates.value

    override val devices: StateFlow<List<Device>> = EarPods.state
        .map { if (it.attached) listOf(earPods) else emptyList() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun init(context: Context) {
        EarPods.init(context)
        com.occaecat.hush.features.Insights.track(
            context, EarPodsController.insightsProfile,
            com.occaecat.hush.wired.WiredAudio.volumeSamples(context, EarPods.state.map { it.attached }.distinctUntilChanged()),
        )
    }

    override fun controller(device: Device): DeviceController = EarPodsController
}
