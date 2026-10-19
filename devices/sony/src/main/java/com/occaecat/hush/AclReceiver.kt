package com.occaecat.hush

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ACL_CONNECTED is one of the few Bluetooth broadcasts still delivered to manifest receivers. Waking up here
 * creates the controller, whose A2DP watcher opens the session once the saved headset's audio link is up.
 */
class AclReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            HeadphonesController.get(context)
            return
        }
        val saved = context.getSharedPreferences("hush", Context.MODE_PRIVATE).getString("address", null) ?: return
        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
        if (device?.address == saved) HeadphonesController.get(context)
    }
}
