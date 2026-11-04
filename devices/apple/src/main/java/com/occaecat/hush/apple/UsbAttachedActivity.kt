package com.occaecat.hush.apple

import android.app.Activity
import android.os.Bundle
import com.occaecat.hush.devices.appIntent

/**
 * Target of the EarPods' USB_DEVICE_ATTACHED filter. Choosing "Always open Hush" in that system dialog also grants
 * USB access (needed for the serial number and the authenticity check); this just hands over to the app.
 */
class UsbAttachedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(appIntent(this))
        finish()
    }
}
