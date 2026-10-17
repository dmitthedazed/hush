package com.occaecat.hush

import android.app.Application

class HushApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PhoneIntegrations.install(this)
        com.occaecat.hush.sound.SoundStore.init(this)
        com.occaecat.hush.features.Insights.start(this)
        com.occaecat.hush.features.FirmwareWatch.start(this)
    }
}
