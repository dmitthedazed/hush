package com.occaecat.hush.jabra

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.occaecat.hush.devices.Candidate
import com.occaecat.hush.devices.Connection
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceModule
import com.occaecat.hush.devices.DeviceRegistry
import com.occaecat.hush.devices.Model
import com.occaecat.hush.devices.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Original Elite 10 and Elite 85t over Bluetooth GNP. Product ID is checked after connection. */
object JabraModule : DeviceModule {
    override val id = "jabra"
    override val brand = "Jabra"
    override val notice = "Hush is independent and is not made, endorsed or supported by Jabra. " +
        "Jabra, Elite 10, Elite 85t and Sound+ are trademarks of GN Audio A/S. " +
        "Firmware information comes from Jabra's public manifest."

    override val models = listOf(
        Model("elite-10", "Elite 10", Transport.BLUETOOTH,
            "Pair the original Elite 10 in Android Bluetooth settings, then choose it here. Close Jabra Sound+ before opening controls.",
            permission = Manifest.permission.BLUETOOTH_CONNECT, imageUrl = ELITE_10_ART,
            note = "Experimental", group = "Bluetooth"),
        Model("elite-85t", "Elite 85t", Transport.BLUETOOTH,
            "Pair the Elite 85t in Android Bluetooth settings, then choose it here. Close Jabra Sound+ before opening controls.",
            permission = Manifest.permission.BLUETOOTH_CONNECT, imageUrl = ELITE_85T_ART,
            note = "Experimental", group = "Bluetooth"),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val found = MutableStateFlow<List<Candidate>>(emptyList())
    override val candidates: StateFlow<List<Candidate>> = found
    private val live = MutableStateFlow<List<Device>>(emptyList())
    override val devices: StateFlow<List<Device>> = live
    private val controllers = mutableMapOf<String, JabraController>()
    private var selected: String? = null
    private lateinit var app: Context

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scan(context)
            if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                val address = IntentCompat.getParcelableExtra(
                    intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
                )?.address
                if (address != null && address == selected) controllers[address]?.reconnect()
            }
        }
    }

    override fun init(context: Context) {
        app = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(app, bluetoothReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        scan(app)
    }

    private fun modelFor(name: String): String? = when {
        name.contains("Elite 85t", ignoreCase = true) -> "elite-85t"
        name.contains("Elite 10", ignoreCase = true) && !name.contains("Gen 2", ignoreCase = true) -> "elite-10"
        else -> null
    }

    @SuppressLint("MissingPermission")
    override fun scan(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val remembered = DeviceRegistry.items.value.filter { it.moduleId == id && it.address != null }
        val saved = remembered.associateBy { it.address }
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter ?: return
        found.value = adapter.bondedDevices.mapNotNull { device ->
            val name = device.name ?: saved[device.address]?.name.orEmpty()
            val model = modelFor(name) ?: saved[device.address]?.modelId ?: return@mapNotNull null
            if (model !in setOf("elite-10", "elite-85t")) return@mapNotNull null
            Candidate(id, model, name, Transport.BLUETOOTH, device.address)
        }.sortedBy { it.name }
    }

    override fun remembered(context: Context): List<Candidate> {
        val preferences = context.getSharedPreferences("hush", Context.MODE_PRIVATE)
        val address = preferences.getString("address", null) ?: return emptyList()
        if (preferences.getString("brand:$address", null) != "JABRA") return emptyList()
        val savedModel = preferences.getString("model:$address", null)
        val candidate = found.value.firstOrNull { it.address == address }
        val model = savedModel?.let(::modelFor) ?: candidate?.modelId ?: return emptyList()
        return listOf(Candidate(id, model, candidate?.name ?: savedModel ?: "Jabra earbuds", Transport.BLUETOOTH, address))
    }

    @SuppressLint("MissingPermission")
    override fun onOpen(device: Device) {
        val address = device.address ?: return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        if (selected != address) {
            selected?.let { controllers[it]?.disconnect() }
            selected = address
        }
        val controller = controller(device) as JabraController
        live.value = listOf(device.copy(active = controller.state.value.connection == Connection.CONNECTED))
        controller.reconnect()
    }

    override fun onRemove(device: Device) {
        val address = device.address ?: return
        controllers.remove(address)?.dispose()
        if (selected == address) { selected = null; live.value = emptyList() }
    }

    override fun onDeactivated() {
        selected?.let { controllers[it]?.disconnect() }
        selected = null
        live.value = emptyList()
    }

    override fun controller(device: Device): DeviceController {
        val address = device.address ?: return JabraController(app, device, null)
        return controllers.getOrPut(address) {
            val bluetooth = app.getSystemService(BluetoothManager::class.java).adapter
            val controller = JabraController(app, device, bluetooth?.getRemoteDevice(address))
            scope.launch {
                controller.state.collectLatest { state ->
                    if (selected == address) live.value = listOf(device.copy(active = state.connection == Connection.CONNECTED))
                }
            }
            controller
        }
    }

    internal const val ELITE_10_ART = "https://assets2.jabra.com/7/7/0/1/7701381317c83cf54e47fcaaf95fabff58363c12_0_Elite_10_TitaniumBlack.png"
    internal const val ELITE_85T_ART = "https://assets2.jabra.com/a/8/5/9/a8598c278583d541a7e8f57ab3f71a73b740fa73_Jabra_elite85t_black_01a.png"
}
