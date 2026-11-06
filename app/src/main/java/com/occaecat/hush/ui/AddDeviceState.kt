package com.occaecat.hush.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import com.occaecat.hush.DeviceModules
import com.occaecat.hush.devices.Candidate
import com.occaecat.hush.devices.Connection
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.Model
import com.occaecat.hush.devices.Transport
import com.occaecat.hush.devices.isDemo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Where the phone's Bluetooth stands, for the add screens' status card. */
enum class Radio { CHECKING, NO_PERMISSION, OFF, MISSING, READY }

/** A headset the add screens see over Bluetooth that no module has claimed (yet). [model] is set when one could. */
data class SeenDevice(val address: String, val name: String, val model: Model?, val moduleId: String?) {
    val supported get() = model != null
}

/** What the user started from an add screen: pairing a nearby headset, connecting a found one. */
data class AddStep(val id: String, val phase: Phase, val message: String? = null) {
    enum class Phase { PAIRING, CONNECTING, CONNECTED, FAILED }
}

/**
 * The add screens' Bluetooth side, alive while one is started: the phone's radio and permission, the bonded audio
 * devices no module claims (read off the main thread, shown at once), a discovery that runs only on request (or once,
 * when nothing addable is listed) and stops with the screen, and the pair → connect → add steps. What a module can
 * add still comes from [DeviceModules.found]; this only fills in around it.
 */
@Stable
class AddDeviceState internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onAdd: () -> (Candidate) -> Unit,
) {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    var radio by mutableStateOf(Radio.CHECKING); private set
    var scanning by mutableStateOf(false); private set
    /** Bonded audio devices, nearest the user's mind first (the module decides which of them it can add). */
    var bonded by mutableStateOf<List<SeenDevice>>(emptyList()); private set
    /** Audio devices discovery found that aren't bonded. */
    var nearby by mutableStateOf<List<SeenDevice>>(emptyList()); private set
    var step by mutableStateOf<AddStep?>(null); private set
    /** The user was asked for the permission once: asking again goes to the app's settings. */
    var askedPermission by mutableStateOf(false)

    private var autoScanned = false
    private var stepJob: Job? = null
    private var started = false

    val busy get() = step?.phase == AddStep.Phase.PAIRING || step?.phase == AddStep.Phase.CONNECTING ||
        step?.phase == AddStep.Phase.CONNECTED

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> refresh()
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> scanning = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                BluetoothDevice.ACTION_FOUND -> {
                    val d = intent.device() ?: return
                    if (d.bondState == BluetoothDevice.BOND_BONDED || nearby.any { it.address == d.address }) return
                    val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: d.name ?: return
                    if (!d.isAudio() && match(name) == null) return
                    nearby = nearby + seen(d.address, name)
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val d = intent.device() ?: return
                    val now = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val pairing = step?.takeIf { it.id == d.address && it.phase == AddStep.Phase.PAIRING }
                    when (now) {
                        BluetoothDevice.BOND_BONDED -> {
                            nearby = nearby.filter { it.address != d.address }
                            readBonded()
                            if (pairing != null) continueAfterPairing(d.address)
                        }
                        BluetoothDevice.BOND_NONE -> if (pairing != null) {
                            step = pairing.copy(phase = AddStep.Phase.FAILED, message = "Pairing didn't finish. Put the headphones in pairing mode and try again.")
                        }
                    }
                }
            }
        }
    }

    /** The screen came up (or back from settings / the permission dialog). */
    internal fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    /** The screen went away or to the background: discovery never outlives it. */
    @SuppressLint("MissingPermission")
    internal fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        if (scanning && granted(Manifest.permission.BLUETOOTH_SCAN)) runCatching { adapter?.cancelDiscovery() }
        scanning = false
    }

    /** Re-reads the radio and permission; a ready radio shows its bonded devices, and discovers once if nothing is listed. */
    fun refresh() {
        radio = when {
            adapter == null -> Radio.MISSING
            !granted(Manifest.permission.BLUETOOTH_CONNECT) -> Radio.NO_PERMISSION
            !adapter.isEnabled -> Radio.OFF
            else -> Radio.READY
        }
        if (radio != Radio.READY) { bonded = emptyList(); nearby = emptyList(); scanning = false; return }
        readBonded()
    }

    /** The permission dialog answered: the modules can now see what's paired, too. */
    fun onPermissionResult() {
        askedPermission = true
        refresh()
        if (radio != Radio.READY) return
        scope.launch(Dispatchers.Default) { DeviceModules.scan(context) }
        if (canScan) scan()
    }

    @SuppressLint("MissingPermission")
    private fun readBonded() = scope.launch {
        val list = withContext(Dispatchers.IO) {
            runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
                .filter { it.isAudio() || match(it.name.orEmpty()) != null }
                .map { seen(it.address, it.alias ?: it.name ?: it.address) }
        }
        bonded = list
        if (!autoScanned && started && canScan && DeviceModules.found.value.none { it.transport == Transport.BLUETOOTH }) {
            autoScanned = true
            scan()
        }
    }

    /** Discovery needs its own permission, asked for when the user first looks (older grants had only connect). */
    val canScan get() = granted(Manifest.permission.BLUETOOTH_SCAN)

    /** Discovery: about twelve seconds of looking for headsets in pairing mode. */
    @SuppressLint("MissingPermission")
    fun scan() {
        if (radio != Radio.READY || scanning || busy) return
        if (!granted(Manifest.permission.BLUETOOTH_SCAN)) return
        nearby = emptyList()
        scanning = runCatching { adapter?.startDiscovery() == true }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (granted(Manifest.permission.BLUETOOTH_SCAN)) runCatching { adapter?.cancelDiscovery() }
        scanning = false
    }

    /** A nearby headset: pair it, then (once its module sees it) connect and add it. */
    @SuppressLint("MissingPermission")
    fun pair(d: SeenDevice) {
        if (busy) return
        stopScan()
        val device = runCatching { adapter?.getRemoteDevice(d.address) }.getOrNull()
        step = AddStep(d.address, AddStep.Phase.PAIRING)
        if (device?.createBond() != true) {
            step = AddStep(d.address, AddStep.Phase.FAILED, "Couldn't start pairing. Pair it in Bluetooth settings instead.")
        }
    }

    private fun continueAfterPairing(address: String) {
        stepJob?.cancel()
        stepJob = scope.launch {
            withContext(Dispatchers.Default) { DeviceModules.scan(context) }
            val c = withTimeoutOrNull(5_000) { DeviceModules.found.first { l -> l.any { it.address == address } } }
                ?.first { it.address == address }
            if (c == null) step = AddStep(address, AddStep.Phase.FAILED, "Paired, but Hush can't control this model.")
            else { step = null; add(c) }
        }
    }

    /**
     * Adds [c]. A Bluetooth headset is connected first so the user sees it work (or why it doesn't) on this screen;
     * plugged-in devices and demos are added at once.
     */
    fun add(c: Candidate) {
        if (busy) return
        val device = Device(c.key, c.moduleId, c.name, c.transport, active = false, modelId = c.modelId, address = c.address)
        if (c.transport != Transport.BLUETOOTH || c.address == null || device.isDemo) return onAdd()(c)
        stopScan()
        step = AddStep(c.key, AddStep.Phase.CONNECTING)
        stepJob?.cancel()
        stepJob = scope.launch {
            val module = DeviceModules.module(c.moduleId)
            val controller = DeviceModules.controller(device)
            DeviceModules.open(device)
            var tried = false
            val ok = withTimeoutOrNull(CONNECT_TIMEOUT) {
                combine(module.devices, controller.state) { live, s -> live.any { it.key == c.key && it.active } to s.connection }
                    .first { (active, connection) ->
                        if (connection == Connection.CONNECTING) tried = true
                        active || (tried && connection == Connection.DISCONNECTED)
                    }.first
            } == true
            if (ok) {
                step = AddStep(c.key, AddStep.Phase.CONNECTED)
                delay(700)
                step = null
                onAdd()(c)
            } else step = AddStep(
                c.key, AddStep.Phase.FAILED,
                "Couldn't connect. Check it's on and nearby, and that Sound Connect is closed.",
            )
        }
    }

    /** Registers [c] without waiting for a connection (after one failed). */
    fun addAnyway(c: Candidate) {
        stepJob?.cancel()
        step = null
        onAdd()(c)
    }

    fun dismissStep() { stepJob?.cancel(); step = null }

    private fun seen(address: String, name: String): SeenDevice {
        val (moduleId, model) = match(name) ?: (null to null)
        return SeenDevice(address, name, model, moduleId)
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CONNECT_TIMEOUT = 20_000L

        /** The model a Bluetooth name belongs to: the longest model name it contains ("LinkBuds S" over "LinkBuds"). */
        fun match(name: String): Pair<String, Model>? = matchModel(name, DeviceModules.all.flatMap { m -> m.models.map { m.id to it } })

        @SuppressLint("MissingPermission")
        fun BluetoothDevice.isAudio() = runCatching { bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
            .getOrDefault(false)

        fun Intent.device(): BluetoothDevice? = IntentCompat.getParcelableExtra(
            this, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java
        )
    }
}

/** The longest Bluetooth model name [name] contains, among [models] (module id → model). */
internal fun matchModel(name: String, models: List<Pair<String, Model>>): Pair<String, Model>? =
    models.filter { (_, m) -> m.transport == Transport.BLUETOOTH && name.contains(m.name, ignoreCase = true) }
        .maxByOrNull { it.second.name.length }

/** The add screens' Bluetooth state, started and stopped with the screen's lifecycle. */
@Composable
fun rememberAddDeviceState(onAdd: (Candidate) -> Unit): AddDeviceState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(onAdd)
    val state = remember { AddDeviceState(context, scope) { latest } }
    LifecycleStartEffect(state) {
        state.start()
        onStopOrDispose { state.stop() }
    }
    return state
}
