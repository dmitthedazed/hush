package com.occaecat.hush.wear

import android.app.Application
import com.occaecat.hush.wear.hub.HushHub

class HushWearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HushHub.get(this)
    }
}
