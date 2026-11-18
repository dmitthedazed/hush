package com.occaecat.hush

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Sony's ColorOS My Devices card. Headphone controls and alerts are managed by the app.
 */
object PhoneIntegrations {

    @Volatile private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        val controller = HeadphonesController.get(app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val state = controller.state

        // Picking a headset in the app also brings back a card removed in My Devices.
        controller.onPick = { app.getSharedPreferences("hush", 0).edit().remove(DeviceCardProvider.PREF_HIDDEN).apply() }

        scope.launch {
            state.map(::CardSnapshot).distinctUntilChanged().collect { snapshot ->
                DeviceCard.publish(app, snapshot)
            }
        }
    }
}
