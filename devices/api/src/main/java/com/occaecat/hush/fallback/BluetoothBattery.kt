package com.occaecat.hush.fallback

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAssignedNumbers
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The battery Android itself knows for a Bluetooth headset, for devices whose own protocol doesn't report one (or
 * that Hush has no protocol for). There is no public getter: `BluetoothDevice.getBatteryLevel()` and its broadcast
 * are hidden API, so they are tried by name and may be gone on a later release; the public signal is the HFP
 * vendor event (Apple's `+IPHONEACCEV`, Plantronics' `+XEVENT=BATTERY`), which only headsets that send it raise.
 */
object BluetoothBattery {
    private const val ACTION_LEVEL = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    private val levels = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var started = false

    /** Levels by Bluetooth address, 0–100; an address is missing while unknown. */
    fun levels(context: Context): StateFlow<Map<String, Int>> {
        start(context.applicationContext)
        return levels.asStateFlow()
    }

    /** Reads the hidden getter again (the hero was tapped, the device connected). */
    @SuppressLint("MissingPermission")
    fun refresh(context: Context, address: String) {
        val device = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)
        }.getOrNull() ?: return
        val level = runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int }.getOrNull()
        set(address, level)
    }

    private fun set(address: String, level: Int?) = levels.update {
        if (level != null && level in 0..100) it + (address to level) else it - address
    }

    private fun start(app: Context) {
        if (started) return
        started = true
        val filter = IntentFilter(ACTION_LEVEL).apply {
            addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)
            addCategory("${BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY}.${BluetoothAssignedNumbers.APPLE}")
            addCategory("${BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY}.${BluetoothAssignedNumbers.PLANTRONICS}")
        }
        // The Bluetooth stack runs as its own app, so the receiver has to be exported. Without BLUETOOTH_CONNECT
        // nothing arrives and the levels stay empty.
        runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val address = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.address ?: return
            when (intent.action) {
                ACTION_LEVEL -> set(address, intent.getIntExtra(EXTRA_LEVEL, -1))
                BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT -> {
                    val cmd = intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD)
                    @Suppress("DEPRECATION")
                    val args = intent.extras?.get(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS) as? Array<*>
                    HfpBattery.level(cmd, args?.map { it.toString() } ?: return)?.let { set(address, it) }
                }
            }
        }
    }
}

/** The battery in an HFP vendor event, as the headset sends it. Plain Kotlin, so it is tested on the JVM. */
object HfpBattery {
    /** 0–100; null when the event says nothing about the battery. */
    fun level(cmd: String?, args: List<String>): Int? = when (cmd) {
        // +IPHONEACCEV=<count>,<key>,<value>,…: key 1 is the battery, 0–9.
        "+IPHONEACCEV" -> args.drop(1).chunked(2).firstOrNull { it.size == 2 && it[0].trim() == "1" }
            ?.get(1)?.trim()?.toIntOrNull()?.takeIf { it in 0..9 }?.let { (it + 1) * 10 }
        // +XEVENT=BATTERY,<level>,<max>,…
        "+XEVENT" -> if (args.firstOrNull()?.trim() == "BATTERY" && args.size >= 3) {
            val level = args[1].trim().toIntOrNull()
            val max = args[2].trim().toIntOrNull()
            if (level != null && max != null && max > 0 && level in 0..max) level * 100 / max else null
        } else null
        else -> null
    }
}
