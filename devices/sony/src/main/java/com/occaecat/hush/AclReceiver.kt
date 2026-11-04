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
        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE) ?: return
        // Any Sony headset the user added, not just one: the registry is loaded in Application.onCreate.
        val added = com.occaecat.hush.devices.DeviceRegistry.items.value
            .any { it.moduleId == "sony" && it.address.equals(device.address, ignoreCase = true) }
        if (added) HeadphonesController.get(context)
    }
}
