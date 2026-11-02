package com.occaecat.hush

import android.app.Application
import kotlinx.coroutines.launch

class HushApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.occaecat.hush.devices.EqLibrary.init(this)
        com.occaecat.hush.devices.DeviceRegistry.init(this)
        DeviceModules.all.forEach { it.init(this) }
        DeviceModules.init(this)
        // Scenes and automation work on whichever devices are connected.
        com.occaecat.hush.scenes.SceneStore.init(this)
        com.occaecat.hush.scenes.Scenes.targets = DeviceModules.targets
        com.occaecat.hush.sound.SoundAutomation.start(this, DeviceModules.targets)
        // Home screen widgets: a live Glance session recomposes by itself; this covers ended sessions.
        val app = this
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main).launch {
            com.occaecat.hush.widget.widgetState(app).collect { com.occaecat.hush.widget.HushWidgets.updateAll(app) }
        }
    }
}
