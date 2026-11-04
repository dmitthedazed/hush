package com.occaecat.hush.mdr

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Tracks which devices have A2DP connected. Sound Connect only opens the MDR channel once a headset's
 * A2DP profile reports CONNECTED (platform/connection/broadcastreceiver/i.java). Probing RFCOMM while the
 * headset is restarting its links can hang the socket and wedge the phone's RFCOMM port.
 */
class A2dpWatcher(private val context: Context) {
    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)) {
                BluetoothProfile.STATE_CONNECTED -> _connected.update { it + device.address }
                BluetoothProfile.STATE_DISCONNECTED -> _connected.update { it - device.address }
            }
        }
    }

    private var proxy: BluetoothProfile? = null
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    fun start() {
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
                proxy = p
                val now = runCatching { p.connectedDevices.map { it.address }.toSet() }.getOrDefault(emptySet())
                _connected.update { it + now }
            }

            override fun onServiceDisconnected(profile: Int) {
                proxy = null
            }
        }, BluetoothProfile.A2DP)
    }

    /**
     * Live state from the A2DP service, not the broadcast cache: right after a headset drops its link the
     * DISCONNECTED broadcast can arrive after we notice the dead socket, and a cached "connected" would make
     * us call connect(), which pages the headset while it is still restarting.
     */
    @SuppressLint("MissingPermission")
    fun isConnected(address: String): Boolean {
        val p = proxy as? BluetoothA2dp ?: return address in _connected.value
        return runCatching { p.connectedDevices.any { it.address == address } }.getOrDefault(false)
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        proxy?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
    }
}
