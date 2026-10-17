package com.occaecat.hush.wear.hub

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.PairedDevice
import com.occaecat.hush.Phase
import com.occaecat.hush.link.Command
import com.occaecat.hush.link.HushLink
import com.occaecat.hush.link.Snapshot
import com.occaecat.hush.link.apply
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.wear.complication.BatteryComplicationService
import com.occaecat.hush.wear.complication.NoiseComplicationService
import com.occaecat.hush.wear.tile.HushTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Which side holds the headset's control channel. */
enum class Route { WATCH, PHONE }

/** The user's choice; [AUTO] picks per situation. */
enum class RoutePref { AUTO, WATCH, PHONE }

data class HubState(
    /** What every surface shows: the active route's view of the headset. */
    val snapshot: Snapshot = Snapshot(),
    val route: Route = Route.PHONE,
    val pref: RoutePref = RoutePref.AUTO,
    val phoneReachable: Boolean = false,
    val phoneName: String? = null,
    /** Headsets bonded to the watch itself, for the standalone route. */
    val watchPaired: List<PairedDevice> = emptyList(),
    val watchHeadset: String? = null,
    val watchError: String? = null,
) {
    /** Nothing to connect to on either side yet. */
    val needsSetup get() = !phoneReachable && watchHeadset == null
}

/**
 * The watch's single source of truth. It merges two routes to the headset and decides which one to use:
 *
 * - **Companion** — the phone runs Hush and owns the RFCOMM session; the watch mirrors its state from the Data
 *   Layer and sends commands as messages. Cheapest on the watch battery.
 * - **Standalone** — the watch opens the Sony MDR channel itself with the shared [HeadphonesController], for when
 *   the phone is away, off, or the headset is paired only with the watch.
 *
 * In [RoutePref.AUTO] a live watch session wins, then a reachable phone, then the watch's own saved headset.
 */
class HushHub private constructor(private val app: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = app.getSharedPreferences("hush_wear", Context.MODE_PRIVATE)
    val local: HeadphonesController = HeadphonesController.get(app)

    private val pref = MutableStateFlow(
        runCatching { RoutePref.valueOf(prefs.getString(PREF_ROUTE, null) ?: "") }.getOrDefault(RoutePref.AUTO)
    )
    private val phoneSnapshot = MutableStateFlow<Snapshot?>(null)
    private val phoneNode = MutableStateFlow<Node?>(null)

    /** Debug builds only (DemoReceiver): stands in for a phone-relayed headset to exercise every surface. */
    private val demo = MutableStateFlow(prefs.getString(PREF_DEMO, null)?.let(Snapshot::fromJson))

    val state: StateFlow<HubState> =
        combine(local.state, phoneSnapshot, phoneNode, pref, demo) { l, p, node, pr, d ->
            val merged = merge(l, p, node, pr)
            if (d == null) merged else merged.copy(snapshot = d, route = Route.PHONE, phoneReachable = true, phoneName = "Demo phone")
        }
            .stateIn(scope, SharingStarted.Eagerly, HubState())

    private fun merge(l: HeadphonesState, phone: Snapshot?, node: Node?, pr: RoutePref): HubState {
        val localLive = l.phase == Phase.CONNECTING || l.phase == Phase.CONNECTED || l.phase == Phase.RECONNECTING
        val reachable = node != null
        val route = when (pr) {
            RoutePref.WATCH -> Route.WATCH
            RoutePref.PHONE -> Route.PHONE
            RoutePref.AUTO -> when {
                localLive -> Route.WATCH
                reachable -> Route.PHONE
                l.address != null -> Route.WATCH
                else -> Route.PHONE
            }
        }
        val snapshot = when (route) {
            Route.WATCH -> Snapshot.from(l)
            // The last synced item is stale once the phone is gone; keep the name, drop the live bits.
            Route.PHONE -> if (reachable) phone ?: Snapshot() else Snapshot(name = phone?.name, hasAddress = phone?.hasAddress == true)
        }
        return HubState(
            snapshot = snapshot,
            route = route,
            pref = pr,
            phoneReachable = reachable,
            phoneName = node?.displayName,
            watchPaired = l.paired,
            watchHeadset = l.address,
            watchError = l.error.takeIf { l.phase == Phase.FAILED },
        )
    }

    init {
        // Never let the watch grab the headset behind the phone's back.
        scope.launch {
            state.map { it.pref == RoutePref.WATCH || (it.pref == RoutePref.AUTO && !it.phoneReachable) }
                .distinctUntilChanged().collect { local.autoReconnect = it }
        }
        // A standalone session keeps the process alive and shows as an Ongoing Activity.
        scope.launch {
            local.state.map { it.phase != Phase.IDLE && it.phase != Phase.FAILED }.distinctUntilChanged().collect { live ->
                if (live) SessionService.start(app) else SessionService.stop(app)
            }
        }
        scope.launch { refreshSurfacesOnChange() }

        val dataClient = Wearable.getDataClient(app)
        dataClient.addListener { events -> onDataEvents(events) }
        Wearable.getCapabilityClient(app).addListener({ onCapability(it) }, HushLink.PHONE_CAPABILITY)
        scope.launch { refreshPhone() }
        refreshPaired()
    }

    @OptIn(FlowPreview::class)
    private suspend fun refreshSurfacesOnChange() {
        state.map { it.snapshot to it.route }.distinctUntilChanged().debounce(300).collect {
            TileService.getUpdater(app).requestUpdate(HushTileService::class.java)
            for (cls in listOf(BatteryComplicationService::class.java, NoiseComplicationService::class.java)) {
                ComplicationDataSourceUpdateRequester.create(app, ComponentName(app, cls)).requestUpdateAll()
            }
        }
    }

    // ---- phone link ----

    /** Re-reads the reachable phone and its last published state; cheap, safe to call from any surface. */
    suspend fun refreshPhone() {
        runCatching {
            val info = Wearable.getCapabilityClient(app)
                .getCapability(HushLink.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
            onCapability(info)
            val items = Wearable.getDataClient(app).dataItems.await()
            try {
                items.firstOrNull { it.uri.path == HushLink.STATE_PATH }?.let(::onPhoneItem)
            } finally {
                items.release()
            }
        }.onFailure { Log.w(TAG, "phone refresh failed", it) }
    }

    fun onCapability(info: CapabilityInfo) {
        phoneNode.value = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
    }

    fun onDataEvents(events: DataEventBuffer) {
        events.forEach { e -> if (e.dataItem.uri.path == HushLink.STATE_PATH) onPhoneItem(e.dataItem) }
    }

    private fun onPhoneItem(item: DataItem) {
        val json = DataMapItem.fromDataItem(item).dataMap.getString(HushLink.KEY_SNAPSHOT) ?: return
        Snapshot.fromJson(json)?.let { phoneSnapshot.value = it }
    }

    private fun sendToPhone(c: Command) {
        val node = phoneNode.value ?: return
        // Answer instantly; the phone's next sync confirms or corrects it.
        phoneSnapshot.update { it?.optimistic(c) }
        scope.launch {
            runCatching { Wearable.getMessageClient(app).sendMessage(node.id, HushLink.COMMAND_PATH, c.encode()).await() }
                .onFailure { Log.w(TAG, "send $c failed", it) }
        }
    }

    // ---- actions (UI, tiles, complications) ----

    fun perform(c: Command) {
        if (demo.value != null) {
            demo.update { it?.optimistic(c) }
            prefs.edit().putString(PREF_DEMO, demo.value?.toJson()).apply()
            return
        }
        val s = state.value
        when (s.route) {
            Route.WATCH -> {
                if (c == Command.Connect && !hasBluetoothPermission()) return
                local.apply(c)
            }
            Route.PHONE -> {
                // Auto: with the phone away, fall back to the watch's own headset for a connect.
                if (!s.phoneReachable && s.pref == RoutePref.AUTO && c == Command.Connect && s.watchHeadset != null) {
                    if (hasBluetoothPermission()) local.connectSaved()
                } else sendToPhone(c)
            }
        }
    }

    /** Survives process death so a demo session stays up while testing by hand. */
    fun setDemo(s: Snapshot?) {
        prefs.edit().putString(PREF_DEMO, s?.toJson()).apply()
        demo.value = s
    }

    fun setPref(p: RoutePref) {
        prefs.edit().putString(PREF_ROUTE, p.name).apply()
        // Hand the headset over cleanly: one side at a time holds the control channel.
        if (p == RoutePref.PHONE) local.disconnect()
        pref.value = p
    }

    /** The user picked a headset bonded to the watch: go standalone with it. */
    fun pickWatchHeadset(address: String) {
        if (!hasBluetoothPermission()) return
        if (pref.value == RoutePref.PHONE) setPref(RoutePref.AUTO)
        local.pick(address)
    }

    fun forgetWatchHeadset() = local.forget()

    fun refreshPaired() {
        if (hasBluetoothPermission()) local.refreshPaired()
    }

    fun hasBluetoothPermission() =
        app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "HushHub"
        private const val PREF_ROUTE = "route"
        private const val PREF_DEMO = "demo"

        @Volatile private var instance: HushHub? = null

        fun get(context: Context): HushHub =
            instance ?: synchronized(this) {
                instance ?: HushHub(context.applicationContext as Application).also { instance = it }
            }
    }
}

/** The phone's answer before it arrives, so taps on a relayed route feel local. */
private fun Snapshot.optimistic(c: Command): Snapshot = when (c) {
    is Command.SetMode -> copy(mode = c.mode)
    is Command.SetAmbientLevel -> copy(mode = AmbientMode.AMBIENT, ambientLevel = c.level)
    is Command.SetVoiceFocus -> copy(voiceFocus = c.on)
    is Command.SetSpeakToChat -> copy(speakToChat = c.on)
    is Command.SetDsee -> copy(dseeAuto = c.auto)
    is Command.SetEqPreset -> copy(eqPreset = c.id)
    Command.CycleNoise -> copy(
        mode = when (mode) {
            AmbientMode.NOISE_CANCELLING -> AmbientMode.AMBIENT
            AmbientMode.AMBIENT -> AmbientMode.OFF
            else -> AmbientMode.NOISE_CANCELLING
        }
    )
    Command.Connect -> if (connected) this else copy(phase = Phase.CONNECTING)
    Command.Disconnect -> copy(phase = Phase.IDLE)
    Command.Refresh -> this
}
