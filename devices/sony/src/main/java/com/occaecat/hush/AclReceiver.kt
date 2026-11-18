package com.occaecat.hush

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ACL_CONNECTED is delivered to this manifest receiver even when the app process is cold. Starting the app
 * lets the Bluetooth modules see A2DP links that are already up and watch for links that come up next.
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
