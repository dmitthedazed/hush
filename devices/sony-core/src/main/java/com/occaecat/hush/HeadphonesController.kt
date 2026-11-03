package com.occaecat.hush

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.occaecat.hush.mdr.A2dpWatcher
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.AssignableKey
import com.occaecat.hush.mdr.Charge
import com.occaecat.hush.mdr.Codec
import com.occaecat.hush.mdr.Ebb
import com.occaecat.hush.mdr.EqBand
import com.occaecat.hush.mdr.EqCapability
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.mdr.GeneralSetting
import com.occaecat.hush.mdr.BluetoothMdrLink
import com.occaecat.hush.mdr.MdrConnection
import com.occaecat.hush.mdr.MdrLink
import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.NcAsmCapability
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.OptimizerCapability
import com.occaecat.hush.mdr.OptimizerResult
import com.occaecat.hush.mdr.PeerDevice
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.ListeningMode
import com.occaecat.hush.mdr.ListeningModes
import com.occaecat.hush.mdr.VoiceGuidanceCapability
import com.occaecat.hush.mdr.VptPreset
import com.occaecat.hush.mdr.MdrProtocol
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.v1.Alert
import com.occaecat.hush.mdr.v1.FunctionType
import com.occaecat.hush.mdr.v1.Init
import com.occaecat.hush.mdr.v1.V1Protocol
import com.occaecat.hush.mdr.v2.V2Functions
import com.occaecat.hush.mdr.v2.V2Protocol
import com.occaecat.hush.mdr.v2.Init as V2Init
import com.occaecat.hush.mdr.v2.Connect as V2Connect
import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PairedDevice(val name: String, val address: String, val looksSony: Boolean)

data class HeadphonesState(
    val phase: Phase = Phase.IDLE,
    /** A demo headset ([HeadphonesController.demo]): nothing is sent anywhere. */
    val demo: Boolean = false,
    val error: String? = null,
    /** Why we are in [Phase.RECONNECTING], e.g. "Applying Sound quality mode". */
    val reconnectReason: String? = null,
    /** A setting that re-establishes the audio link was sent and the codec has not settled yet. */
    val audioSettling: Boolean = false,
    val paired: List<PairedDevice> = emptyList(),
    val address: String? = null,
    /** The phone's name for the headset: its Bluetooth alias, which Fast Pair sets (e.g. "Dmytrii's WH-1000XM4"). */
    val deviceName: String? = null,
    val modelName: String? = null,
    /** Sony ModelColor the headset reports (picks the official render and header colour). */
    val modelColor: Int? = null,
    /** Sony ModelSeries (0x30 PREMIUM …). */
    val modelSeries: Int? = null,
    /** GuidanceCategory codes of the instruction guide; null until the headset answers. */
    val guideCategories: List<Int>? = null,
    val serial: String? = null,
    /** Firmware-update parameters by UpdateInquiredType (see [com.occaecat.hush.mdr.v1.Update.param]); for the device details page. */
    val updateInfo: Map<Int, String> = emptyMap(),
    /** CONNECT_RET_PROTOCOL_INFO version, e.g. 0x7000. */
    val protocolVersion: Int? = null,
    /** The session speaks Sony's protocol v2 (WH-1000XM5, WF-1000XM5, LinkBuds and later). */
    val protocolV2: Boolean = false,
    /** v2 only: the FunctionType codes of both tables as the headset sent them ([functions] holds v1 equivalents). */
    val v2Functions: V2Functions? = null,
    val firmware: String? = null,
    val battery: Int? = null,
    val charging: Boolean = false,
    /** Earbuds (LEFT_RIGHT_BATTERY_LEVEL) and their case (CRADLE_BATTERY_LEVEL) on true wireless models. */
    val leftBattery: Charge? = null,
    val rightBattery: Charge? = null,
    val caseBattery: Charge? = null,
    /** LEFT_RIGHT_CONNECTION_STATUS: which earbuds are linked right now. */
    val earbudsConnected: Pair<Boolean, Boolean>? = null,
    val codec: Codec? = null,
    val dseeAuto: Boolean? = null,
    val dseeActive: Boolean = false,
    /** UpscalingType of the upscaling switch: 0 DSEE HX, 1 DSEE, 2 DSEE Extreme (HX AI). */
    val upscalingType: Int? = null,
    val ncAsmCaps: NcAsmCapability? = null,
    val ncAsm: NcAsmState? = null,
    /** SENSE AUTO_NC_ASM: the headset can adapt NC/ASM on its own. */
    val senseAvailable: Boolean = false,
    val eqCaps: EqCapability? = null,
    /** PRESET_EQ_NONCUSTOMIZABLE models let presets be picked but not the bands edited. */
    val eqCustomizable: Boolean = true,
    /** Band centre frequencies (EQEBB_RET_EXTENDED_INFO); empty when the headset does not say. */
    val eqBands: List<EqBand> = emptyList(),
    val eq: EqState? = null,
    val ebb: Ebb? = null,
    /** Surround (VPT) presets and the active one; empty when the model has none. */
    val vptPresets: List<VptPreset> = emptyList(),
    val vpt: Int? = null,
    /** SoundPositionType (1) when the model has Sound Position Control, and its SoundPositionPresetId. */
    val soundPositionType: Int? = null,
    val soundPosition: Int? = null,
    val vibrator: Boolean? = null,
    val powerSaving: Boolean? = null,
    val trainingMode: Boolean? = null,
    val speakToChat: Boolean? = null,
    val speakToChatDetail: SpeakToChatDetail? = null,
    val pauseWhenRemoved: Boolean? = null,
    val pauseWhenRemovedAvailable: Boolean = true,
    val autoPowerOffOptions: List<Int> = emptyList(),
    val autoPowerOff: Pair<Int, Int>? = null,
    /** Keys the user can reassign (CUSTOM button, or left and right earbud) and each key's current preset. */
    val assignableKeys: List<AssignableKey> = emptyList(),
    val assignable: List<Int> = emptyList(),
    /** The first assignable key's preset: the CUSTOM button on over-ear models. */
    val customButton: Int? = null,
    val soundQualityPreferConnection: Boolean? = null,
    /** v2: the LE Audio (low latency) priority is on instead of either of the above. */
    val soundQualityLowLatency: Boolean = false,
    val generalSettings: Map<Int, GeneralSetting> = emptyMap(),
    val generalValues: Map<Int, Boolean> = emptyMap(),
    /** LIST_TYPE general settings: the index of the chosen option. */
    val generalListValues: Map<Int, Int> = emptyMap(),
    val generalEnabled: Map<Int, Boolean> = emptyMap(),
    /** An alert the headset raised that the user has not pre-approved: (AlertMessageType, actionType). */
    val alert: Pair<Int, Int>? = null,
    val optimizerCaps: OptimizerCapability? = null,
    val optimizerRunning: Boolean = false,
    val optimizerResult: OptimizerResult? = null,
    val voiceGuidanceCaps: VoiceGuidanceCapability? = null,
    val voiceGuidance: Boolean? = null,
    val voiceGuidanceLanguage: Int? = null,
    /** v2 listening modes; null when the headset has none. */
    val listening: ListeningModes? = null,
    /** Max Volume Control: on/off and the limit 0..4; null when the headset has none. */
    val maxVolumeOn: Boolean? = null,
    val maxVolumeLevel: Int? = null,
    /** −2..+2, null when the headset has no prompt volume. */
    val voiceGuidanceVolume: Int? = null,
    /**
     * Set while another multipoint source holds the headset's single control channel and Hush stepped back
     * (phase FAILED): the other device's name as the headset reported it, or "another device".
     */
    val elsewhere: String? = null,
    /**
     * FunctionType codes the headset reported (CONNECT_RET_SUPPORT_FUNCTION); empty until it answers. On v2
     * these are the v1 codes of what it declared ([V2Functions.v1Equivalents]), so `has()` reads the same.
     */
    val functions: Set<Int> = emptySet(),
    /** The headset's own paired-source list (multipoint management); null when unsupported or not read yet. */
    val peers: List<PeerDevice>? = null,
    val peerPlayback: Int = 0,
    val pairingMode: Boolean = false,
    /** PLAYBACK_CONTROLLER: the headset's volume (0 until [volumeSteps]-1) and what the source is playing. */
    val volume: Int? = null,
    val volumeSteps: Int = 31,
    val playing: Boolean? = null,
    val track: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** Last connect/disconnect/unpair outcome, for a snackbar: (ResultType, address). */
    val peerResult: Pair<Int, String>? = null,
) {
    /** What to call the headset in the UI: the phone's own name for it first, then the model. */
    val displayName: String get() = deviceName ?: modelName ?: "Headphones"

    fun has(f: FunctionType) = f.code in functions

    /** Modes the headset's noise control has, in the order of its own NC/AMB button. */
    fun noiseModes(): List<AmbientMode> = when (ncAsm?.kind ?: ncAsmCaps?.kind) {
        NcAsmKind.NC -> listOf(AmbientMode.NOISE_CANCELLING, AmbientMode.OFF)
        NcAsmKind.ASM -> listOf(AmbientMode.AMBIENT, AmbientMode.OFF)
        else -> listOf(AmbientMode.NOISE_CANCELLING, AmbientMode.AMBIENT, AmbientMode.OFF)
    }

    /** The level of a flat EQ band: 10 of 0..20 on 6-band models, 6 of 0..12 on 10-band v2 ones (WF-LC900). */
    val eqFlat: Int get() = eqCaps?.levelSteps?.takeIf { it > 1 }?.let { (it - 1) / 2 } ?: HeadphonesController.EQ_FLAT

    /** True wireless: the headset reports earbuds rather than one battery. */
    val trueWireless get() = has(FunctionType.LEFT_RIGHT_BATTERY_LEVEL)
}

/**
 * Owns the headset session for the whole process. On the phone the app UI, the foreground service, the quick
 * settings tile and the ColorOS device card all drive this one connection; on the watch it is the standalone
 * route. Platform side effects subscribe to [state] from outside (PhoneIntegrations, the wear hub).
 */
class HeadphonesController internal constructor(
    private val app: Application,
    /** A demo: starts from this state, never opens Bluetooth or touches the saved headset; changes stay local. */
    private val demo: HeadphonesState? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val prefs = app.getSharedPreferences("hush", Context.MODE_PRIVATE)
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(demo ?: HeadphonesState(address = prefs.getString("address", null)))
    val state: StateFlow<HeadphonesState> = _state

    private var connection: MdrConnection? = null

    /** The current session's command encoding; v2 sessions get theirs once the functions are known. */
    @Volatile private var proto: MdrProtocol = V1Protocol

    /** Set when the session start finds a headset Hush cannot drive; the session is then not retried. */
    @Volatile private var unsupported = false

    /** Every payload of the current headset's sessions, for the self test ([com.occaecat.hush.diag.HeadsetProbe]). */
    val sessionLog = com.occaecat.hush.diag.SessionLog()
    private var sessionJob: Job? = null

    /** Last non-zero ambient level, restored when switching back to Ambient. */
    private var ambientLevel = 20

    /** Conflated outboxes so slider drags never queue up stale values. */
    private val ncAsmOutbox = Channel<Pair<NcAsmState, Boolean>>(Channel.CONFLATED)
    private val eqOutbox = Channel<Request?>(Channel.CONFLATED)

    private val a2dp = A2dpWatcher(app).also { if (demo == null) it.start() }

    /** Reopen the saved headset when its audio link comes back. The watch turns this off while the phone drives it. */
    @Volatile var autoReconnect = true

    init {
        if (demo == null) watchSystem()
        scope.launch { for ((s, dragging) in ncAsmOutbox) sendQuietly(proto.ncAsm(s, dragging)) }
        scope.launch { for (p in eqOutbox) sendQuietly(p) }
    }

    private fun watchSystem() {
        // Like Sound Connect's background receiver: when the headset's audio link comes back, reconnect.
        scope.launch {
            a2dp.connected.collect { up ->
                val st = _state.value
                val idle = st.phase == Phase.FAILED || st.phase == Phase.IDLE
                if (autoReconnect && idle && st.address != null && st.address in up && hasBluetoothPermission()) connect(st.address)
            }
        }
        // Stepped back for another source: take the controls back once music plays on this phone.
        runCatching {
            audio.registerAudioPlaybackCallback(object : android.media.AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) {
                    val st = _state.value
                    if (autoReconnect && st.elsewhere != null && st.phase == Phase.FAILED && musicActive()) takeControl()
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        // Fast Pair (or the user in Bluetooth settings) renames the headset on the phone; follow it.
        runCatching {
            val receiver = object : android.content.BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                override fun onReceive(c: Context, i: android.content.Intent) {
                    @Suppress("DEPRECATION") val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    if (d.address.equals(_state.value.address, ignoreCase = true)) {
                        _state.update { it.copy(deviceName = runCatching { d.alias ?: d.name }.getOrNull() ?: it.deviceName) }
                    }
                    refreshPaired()
                }
            }
            val filter = android.content.IntentFilter(BluetoothDevice.ACTION_ALIAS_CHANGED).apply { addAction(BluetoothDevice.ACTION_NAME_CHANGED) }
            if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else app.registerReceiver(receiver, filter)
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPaired() {
        if (demo != null) return
        val devices = runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        _state.update { s ->
            s.copy(paired = devices.map {
                val name = it.alias ?: it.name ?: it.address
                // The alias can be renamed ("Dmytrii's WH-1000XM4"); the device's own name is the reliable one.
                val sony = listOfNotNull(it.name, it.alias).any { n -> SONY_NAMES.any { p -> n.contains(p, ignoreCase = true) } }
                PairedDevice(name, it.address, sony)
            }.sortedByDescending { it.looksSony })
        }
    }

    fun autoConnect() {
        val addr = _state.value.address ?: return
        if (_state.value.phase == Phase.IDLE) connect(addr)
    }

    /** Platform hooks run when the user explicitly picks a headset (the phone brings back its My Devices card). */
    var onPick: (String) -> Unit = {}

    /** The user chose a headset in the app. */
    fun pick(address: String) {
        onPick(address)
        connect(address)
    }

    /** Used by the tile and the device card:(re)open the saved headset unless a session is already running. */
    fun connectSaved(): Boolean {
        val st = _state.value
        val addr = st.address ?: return false
        if (!hasBluetoothPermission()) return false
        if (st.phase == Phase.IDLE || st.phase == Phase.FAILED) connect(addr)
        return true
    }

    /** NC → Ambient → Off → NC, the order the headset's own NC/AMB button uses. */
    fun cycleNoiseMode() {
        val modes = noiseModes()
        val i = modes.indexOf(_state.value.ncAsm?.mode)
        setAmbientMode(modes[(i + 1) % modes.size])
    }

    private fun hasBluetoothPermission() =
        app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (demo != null) return
        disconnect()
        val device = adapter?.getRemoteDevice(address) ?: return
        start(address, device.alias ?: device.name, BluetoothMdrLink(device, scope))
    }

    /** Starts a session over [link]; tests pass a simulated headset. */
    internal fun start(address: String, name: String?, link: MdrLink) {
        prefs.edit().putString("address", address).apply()
        sessionLog.clear()
        _state.value = HeadphonesState(phase = Phase.CONNECTING, paired = _state.value.paired, address = address, deviceName = name)
        sessionJob = scope.launch { runSession(address, link) }
    }

    /**
     * Keeps a control session alive. Some settings (CUSTOM button, sound quality mode, multipoint) make
     * the headset drop and re-establish the Bluetooth link; Sound Connect waits up to 30 s for it to come
     * back (connectionmode/h.java), so we do the same and then re-read everything.
     */
    private suspend fun runSession(address: String, link: MdrLink) = coroutineScope {
        var firstAttempt = true
        // Consecutive sessions the headset closed soon after opening while its audio link stayed up: another
        // multipoint source (e.g. a laptop running its own MDR client) took the single control channel.
        var kicks = 0
        while (isActive) {
            val window = when {
                firstAttempt -> 0L
                System.currentTimeMillis() < expectReconnectUntil -> PLANNED_RECONNECT_MS
                else -> UNPLANNED_RECONNECT_MS
            }
            val conn = openWithRetry(address, link, window)
            if (conn == null) {
                _state.update {
                    it.copy(
                        phase = Phase.FAILED, reconnectReason = null,
                        error = if (firstAttempt) "Could not open the headphone channel" else "Headphones did not come back",
                    )
                }
                return@coroutineScope
            }
            connection = conn
            sessionLog.mark("session opened")
            val openedAt = System.currentTimeMillis()
            expectReconnectUntil = 0L
            _state.update { it.copy(phase = Phase.CONNECTED, error = null) }
            val collector = launch { conn.events.collect(::onEvent) }
            val init = launch {
                if (!initialize(conn)) {
                    conn.close()
                    return@launch
                }
                if (!firstAttempt) settled()
            }
            conn.closed.await()
            sessionLog.mark("session closed")
            collector.cancel()
            init.cancel()
            if (connection === conn) connection = null
            conn.close()
            if (unsupported) return@coroutineScope
            firstAttempt = false
            val planned = System.currentTimeMillis() < expectReconnectUntil
            val shortLived = System.currentTimeMillis() - openedAt < KICK_WINDOW_MS
            kicks = if (!planned && shortLived && a2dp.isConnected(address)) kicks + 1 else 0
            // The device that is playing keeps the controls: step back unless music plays here, and stop
            // fighting altogether if the other side never yields.
            if (kicks >= 2 && (!musicActive() || kicks >= MAX_KICKS)) {
                val other = otherSource()
                Log.i("Hush", "control channel taken by ${other ?: "another device"}; stepping back")
                _state.update {
                    it.copy(phase = Phase.FAILED, reconnectReason = null, audioSettling = false,
                        elsewhere = other ?: "another device", error = "Controls are in use on ${other ?: "another device"}")
                }
                return@coroutineScope
            }
            _state.update {
                it.copy(phase = Phase.RECONNECTING, reconnectReason = if (planned) it.reconnectReason else "Connection lost")
            }
        }
    }

    /**
     * Sound Connect waits for A2DP CONNECTED before touching RFCOMM. On reconnects we do the same: wait for
     * the audio link (the XM4 drops it completely for e.g. a CUSTOM button change), let it settle, then open.
     */
    private suspend fun openWithRetry(address: String, link: MdrLink, windowMs: Long): MdrConnection? {
        val deadline = System.currentTimeMillis() + windowMs
        if (windowMs > 0) delay(DROP_GRACE_MS) // let the headset finish tearing its links down
        while (true) {
            if (windowMs > 0) {
                // Never connect() unless A2DP is up right now: connect() would page a restarting headset and
                // grab the MDR channel before it is ready, which wedges it until a power cycle.
                if (!waitForA2dp(address, deadline)) return null
                delay(A2DP_SETTLE_MS)
                if (!a2dp.isConnected(address)) continue
            }
            val conn = MdrConnection(link, scope, tap = sessionLog)
            if (runCatching { conn.open() }.isSuccess) return conn
            conn.close()
            if (System.currentTimeMillis() >= deadline) return null
            delay(RETRY_DELAY_MS)
        }
    }

    private suspend fun waitForA2dp(address: String, deadline: Long): Boolean {
        while (System.currentTimeMillis() < deadline) {
            if (a2dp.isConnected(address)) return true
            delay(500)
        }
        return false
    }

    /** Replies the session start waits for; completed from [onEvent]. */
    @Volatile private var protocolReply: CompletableDeferred<MdrEvent.ProtocolInfo>? = null
    @Volatile private var functionsReply: CompletableDeferred<Set<Int>>? = null
    @Volatile private var functions2Reply: CompletableDeferred<Set<Int>>? = null

    private suspend fun initialize(conn: MdrConnection): Boolean {
        unsupported = false
        return if (conn.v2) initializeV2(conn, mayFallBack = true) else initializeV1(conn, mayFallBack = true)
    }

    private fun giveUp(error: String) {
        unsupported = true
        _state.update { it.copy(phase = Phase.FAILED, error = error) }
    }

    /**
     * Sound Connect's v1 start (see [Init]): handshake, then only what the headset declared. A headset that
     * answers the v1 service in the v2 layout ([mayFallBack]) is switched to the v2 parser and started over.
     */
    private suspend fun initializeV1(conn: MdrConnection, mayFallBack: Boolean): Boolean {
        proto = V1Protocol
        val protocol = CompletableDeferred<MdrEvent.ProtocolInfo>().also { protocolReply = it }
        val functions = CompletableDeferred<Set<Int>>().also { functionsReply = it }
        for (req in Init.handshake()) sendQuietly(req)
        val info = withTimeoutOrNull(REPLY_TIMEOUT_MS) { protocol.await() }
        if (info?.v2 == true) {
            if (mayFallBack) {
                Log.i("Hush", "v2 reply on the v1 service; switching to v2")
                conn.v2 = true
                return initializeV2(conn, mayFallBack = false)
            }
            giveUp("These headphones answer in a protocol Hush could not settle on")
            return false
        }
        val version = info?.version ?: 0
        if (info != null && version !in Init.SUPPORTED_VERSIONS) Log.w("Hush", "unknown protocol version 0x%04x, trying anyway".format(version))
        // A headset that never answers SUPPORT_FUNCTION is read as the WH-1000XM4 Hush was built on.
        val codes = withTimeoutOrNull(REPLY_TIMEOUT_MS) { functions.await() } ?: XM4_FUNCTIONS.also {
            _state.update { s -> s.copy(functions = it) }
        }
        for (req in Init.plan(version, codes)) sendQuietly(req)
        return true
    }

    /**
     * The v2 start (see [V2Init]): protocol info, the device info and both support-function lists, then the
     * requests for what was declared. Untested on v2 hardware (Hush was built on a v1 WH-1000XM4); docs/MDR_V2.md.
     */
    private suspend fun initializeV2(conn: MdrConnection, mayFallBack: Boolean): Boolean {
        _state.update { it.copy(protocolV2 = true) }
        val protocol = CompletableDeferred<MdrEvent.ProtocolInfo>().also { protocolReply = it }
        val table1 = CompletableDeferred<Set<Int>>().also { functionsReply = it }
        val table2 = CompletableDeferred<Set<Int>>().also { functions2Reply = it }
        sendQuietly(V2Connect.protocolInfo())
        val info = withTimeoutOrNull(REPLY_TIMEOUT_MS) { protocol.await() }
        if (info != null && !info.v2) {
            if (mayFallBack) {
                Log.i("Hush", "v1 reply on the v2 service; switching to v1")
                conn.v2 = false
                _state.update { it.copy(protocolV2 = false) }
                return initializeV1(conn, mayFallBack = false)
            }
            giveUp("These headphones answer in a protocol Hush could not settle on")
            return false
        }
        if (info == null) {
            giveUp("The headphones did not answer")
            return false
        }
        if (!info.table1) {
            giveUp("These headphones don't offer Sony's main command table")
            return false
        }
        if (info.version !in V2Init.SUPPORTED_VERSIONS) Log.w("Hush", "unknown v2 protocol version 0x%08x, trying anyway".format(info.version))
        for (req in V2Init.handshake(info.version, info.table1, info.table2)) sendQuietly(req)
        val t1 = withTimeoutOrNull(REPLY_TIMEOUT_MS) { table1.await() } ?: emptySet()
        val t2 = if (info.table2) withTimeoutOrNull(REPLY_TIMEOUT_MS) { table2.await() } ?: emptySet() else emptySet()
        val functions = V2Functions(t1, t2)
        proto = V2Protocol(functions)
        _state.update { it.copy(functions = functions.v1Equivalents(), v2Functions = functions) }
        for (req in V2Init.plan(functions)) sendQuietly(req)
        return true
    }

    private val audio = app.getSystemService(android.media.AudioManager::class.java)
    private fun musicActive() = runCatching { audio.isMusicActive }.getOrDefault(false)

    /** The other connected source from the headset's list (this phone is matched by its Bluetooth name). */
    @SuppressLint("MissingPermission")
    private fun otherSource(): String? {
        val me = runCatching { adapter?.name }.getOrNull()
        return _state.value.peers?.firstOrNull { it.connected && it.name != me }?.name
    }

    /** Take the control channel back from another source (the "Take control" button). */
    fun takeControl() {
        _state.value.address?.let(::connect)
    }

    /** Timestamp until which a link drop is expected (a reconnecting setting was just sent). */
    @Volatile private var expectReconnectUntil = 0L

    @Volatile private var sawUnsettledCodec = false
    private var settleJob: Job? = null

    /**
     * On the XM4 these settings keep the MDR session up but restart the A2DP link: the headset notifies
     * codec UNSETTLED, then the new codec a few seconds later. If no UNSETTLED shows up quickly the change
     * did not touch the audio link. A full session drop is handled by [runSession] as a fallback.
     */
    private fun expectReconnect(reason: String) {
        expectReconnectUntil = System.currentTimeMillis() + PLANNED_RECONNECT_MS
        sawUnsettledCodec = false
        _state.update { it.copy(reconnectReason = reason, audioSettling = true) }
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(SETTLE_GRACE_MS)
            if (!sawUnsettledCodec) return@launch settled()
            delay(PLANNED_RECONNECT_MS - SETTLE_GRACE_MS)
            settled()
        }
    }

    /** The headset finished applying a reconnecting setting. */
    private fun settled() {
        settleJob?.cancel()
        expectReconnectUntil = 0L
        _state.update { it.copy(audioSettling = false, reconnectReason = null) }
    }

    fun disconnect() {
        sessionJob?.cancel()
        connection?.close()
        connection = null
        _state.update { it.copy(phase = Phase.IDLE) }
    }

    fun forget() {
        if (demo != null) return
        disconnect()
        prefs.edit().remove("address").apply()
        _state.update { HeadphonesState(paired = it.paired) }
    }

    /** One request as it is (the self test); false when there is no session or the headset never ACKed it. */
    internal suspend fun sendRaw(request: Request): Boolean {
        val conn = connection ?: return false
        return runCatching { conn.send(request) }.isSuccess
    }

    private suspend fun sendQuietly(request: Request?) {
        if (request == null) return
        val conn = connection ?: return
        runCatching { conn.send(request) }.onFailure { Log.w("Hush", "send failed", it) }
    }

    /** Side effects run once here; the [reduce] lambda may be retried by StateFlow.update and must stay pure. */
    private fun onEvent(e: MdrEvent) {
        val before = _state.value
        when (e) {
            is MdrEvent.ProtocolInfo -> protocolReply?.complete(e)
            is MdrEvent.SupportedFunctions -> (if (e.table == Table.TWO) functions2Reply else functionsReply)?.complete(e.codes)
            is MdrEvent.Alert -> {
                if (approvedAlerts.remove(e.type)) answerAlert(e.type, true)
                else _state.update { it.copy(alert = e.type to e.actionType) }
                return
            }
            is MdrEvent.AudioCodec -> if (before.audioSettling) {
                if (e.codec == Codec.UNSETTLED) sawUnsettledCodec = true
                else if (sawUnsettledCodec) settled()
            }
            is MdrEvent.OptimizerState -> if (before.optimizerRunning && !e.running) {
                scope.launch { sendQuietly(proto.optimizerResult()) }
            }
            is MdrEvent.NcAsm -> if (e.state.ambientLevel > 0) ambientLevel = e.state.ambientLevel
            // Like Sound Connect (c40/b): the headset only says "track changed" without the text, and a
            // play/pause can come with a new track, so re-read the names. A text-less notify must not blank them.
            is MdrEvent.PlayName -> if (e.text == null) {
                if (e.notify) { refreshPlayback(); return }
                // Right after a skip the headset may not have the new metadata yet: keep the old title and ask again.
                if (e.type == 0x00 && emptyTitleRetries++ < 3) { refreshPlayback(500); return }
            } else if (e.type == 0x00) emptyTitleRetries = 0
            is MdrEvent.PlayStatus -> if (before.playing != (e.status == 1)) refreshPlayback()
            else -> {}
        }
        _state.update { reduce(it, e) }
    }

    private fun reduce(s: HeadphonesState, e: MdrEvent): HeadphonesState =
        when (e) {
            is MdrEvent.ModelName -> s.copy(modelName = e.name)
            is MdrEvent.FirmwareVersion -> s.copy(firmware = e.version)
            is MdrEvent.SeriesAndColor -> s.copy(modelColor = e.color, modelSeries = e.series)
            is MdrEvent.GuideCategories -> s.copy(guideCategories = e.codes)
            // The headset pads to 16 digits; the label on the box has none of the leading zeros.
            is MdrEvent.SerialNumber -> s.copy(serial = e.serial.trimStart('0').ifEmpty { null })
            is MdrEvent.Battery -> s.copy(battery = e.level, charging = e.charging)
            is MdrEvent.EarbudBatteries -> s.copy(leftBattery = e.left, rightBattery = e.right)
            is MdrEvent.CaseBattery -> s.copy(caseBattery = e.charge)
            is MdrEvent.EarbudsConnected -> s.copy(earbudsConnected = e.left to e.right)
            is MdrEvent.AudioCodec -> s.copy(codec = e.codec)
            is MdrEvent.UpscalingStatus -> s.copy(dseeActive = e.active)
            is MdrEvent.UpscalingSetting -> s.copy(dseeAuto = e.auto)
            is MdrEvent.UpscalingCaps -> s.copy(upscalingType = e.type)
            is MdrEvent.NcAsmCaps -> s.copy(ncAsmCaps = e.caps)
            is MdrEvent.NcAsm ->
                s.copy(ncAsm = e.state.copy(ambientLevel = if (e.state.ambientLevel > 0) e.state.ambientLevel else ambientLevel))
            is MdrEvent.SenseCaps -> s.copy(senseAvailable = true)
            is MdrEvent.EqCaps -> s.copy(eqCaps = e.caps, eqCustomizable = e.customizable)
            is MdrEvent.EqBands -> s.copy(eqBands = e.bands)
            is MdrEvent.Eq -> s.copy(eq = e.state)
            is MdrEvent.EbbCaps -> s.copy(ebb = Ebb(s.ebb?.level?.coerceIn(e.range) ?: e.range.first.coerceAtLeast(0), e.range))
            is MdrEvent.EbbLevel -> s.copy(ebb = s.ebb?.copy(level = e.level) ?: Ebb(e.level, -10..10))
            is MdrEvent.VptCaps -> s.copy(vptPresets = e.presets)
            is MdrEvent.Vpt -> s.copy(vpt = e.presetId)
            is MdrEvent.SoundPositionCaps -> s.copy(soundPositionType = e.type)
            is MdrEvent.SoundPosition -> s.copy(soundPosition = e.presetId)
            is MdrEvent.Vibrator -> s.copy(vibrator = e.on)
            is MdrEvent.PowerSaving -> s.copy(powerSaving = e.on)
            is MdrEvent.TrainingModeCaps -> s
            is MdrEvent.TrainingMode -> s.copy(trainingMode = e.on)
            is MdrEvent.SpeakToChat -> s.copy(speakToChat = e.enabled)
            is MdrEvent.SpeakToChatDetails -> s.copy(speakToChatDetail = e.detail)
            is MdrEvent.WearingStatus -> s.copy(pauseWhenRemovedAvailable = e.available)
            is MdrEvent.PauseWhenRemoved -> s.copy(pauseWhenRemoved = e.on)
            is MdrEvent.AutoPowerOffOptions -> s.copy(autoPowerOffOptions = e.ids)
            is MdrEvent.AutoPowerOff -> s.copy(autoPowerOff = e.activeId to e.selectTimeId)
            is MdrEvent.AssignableCaps -> s.copy(assignableKeys = e.keys)
            is MdrEvent.CustomButton -> s.copy(assignable = e.presets, customButton = e.presets.firstOrNull())
            is MdrEvent.SoundQualityMode -> s.copy(soundQualityPreferConnection = e.preferConnection, soundQualityLowLatency = e.lowLatency)
            is MdrEvent.GsCapability -> s.copy(generalSettings = s.generalSettings + (e.setting.id to e.setting))
            is MdrEvent.GsValue -> s.copy(generalValues = s.generalValues + (e.id to e.on))
            is MdrEvent.GsListValue -> s.copy(generalListValues = s.generalListValues + (e.id to e.index))
            is MdrEvent.Alert -> s
            is MdrEvent.GsStatus -> s.copy(generalEnabled = s.generalEnabled + (e.id to e.enabled))
            is MdrEvent.OptimizerState -> s.copy(optimizerRunning = e.running)
            is MdrEvent.OptimizerCaps -> s.copy(optimizerCaps = e.caps)
            is MdrEvent.Optimizer -> s.copy(optimizerResult = e.result)
            is MdrEvent.VoiceGuidanceCaps -> s.copy(voiceGuidanceCaps = e.caps)
            is MdrEvent.VoiceGuidance -> s.copy(voiceGuidance = e.on)
            // v2 lists are merged by the session start; on v1 the one list is the set.
            is MdrEvent.SupportedFunctions -> if (s.protocolV2 || e.table == Table.TWO) s else s.copy(functions = e.codes)
            is MdrEvent.PlayVolume -> s.copy(volume = e.volume)
            is MdrEvent.PlayCapability -> if (e.volumeSteps > 1) s.copy(volumeSteps = e.volumeSteps) else s
            is MdrEvent.PlayStatus -> s.copy(playing = e.status == 1)
            is MdrEvent.PlayName -> when (e.type) {
                0x00 -> s.copy(track = e.text)
                0x01 -> s.copy(album = e.text)
                else -> s.copy(artist = e.text)
            }
            is MdrEvent.Peers -> s.copy(peers = e.devices, peerPlayback = e.playbackOrder)
            is MdrEvent.PairingMode -> s.copy(pairingMode = e.on)
            is MdrEvent.PeerResult -> s.copy(peerResult = e.result to e.address)
            is MdrEvent.VoiceGuidanceLanguage -> s.copy(voiceGuidanceLanguage = e.language)
            is MdrEvent.VoiceGuidanceVolume -> s.copy(voiceGuidanceVolume = e.volume)
            is MdrEvent.MaxVolumeOn -> s.copy(maxVolumeOn = e.on)
            is MdrEvent.MaxVolumeLevel -> s.copy(maxVolumeLevel = e.level)
            is MdrEvent.Listening -> s.copy(listening = e.update(s.listening ?: ListeningModes()))
            is MdrEvent.UpdateParam -> s.copy(updateInfo = s.updateInfo + (e.type to e.value))
            is MdrEvent.ProtocolInfo -> when {
                !e.v2 -> s.copy(protocolVersion = e.version)
                e.version != 0 -> s.copy(protocolVersion = e.version, protocolV2 = true)
                else -> s
            }
            is MdrEvent.PeerCaps, is MdrEvent.CapabilityInfo, is MdrEvent.BluetoothInfo, is MdrEvent.Ignored, is MdrEvent.Unknown -> s
            is MdrEvent.Several -> e.events.fold(s, ::reduce)
        }

    // ---- user actions ----

    private fun pushNcAsm(next: NcAsmState, dragging: Boolean = false) {
        _state.update { it.copy(ncAsm = next) }
        ncAsmOutbox.trySend(next to dragging)
    }

    /** The noise state to start from before the headset reported one, shaped by its capability. */
    private fun currentNcAsm(): NcAsmState {
        val s = _state.value
        s.ncAsm?.let { return it }
        val caps = s.ncAsmCaps
        return NcAsmState(
            AmbientMode.OFF, ambientLevel, false,
            ncSettingType = caps?.ncSettingType ?: 0x02,
            asmSettingType = caps?.asmSettingType ?: 0x01,
            kind = caps?.kind ?: NcAsmKind.NC_ASM,
        )
    }

    private fun noiseModes() = _state.value.noiseModes()

    private val maxAmbientLevel get() = _state.value.ncAsmCaps?.asmSteps?.takeIf { it > 1 } ?: 20

    fun setAmbientMode(mode: AmbientMode) {
        if (mode !in noiseModes()) return
        pushNcAsm(currentNcAsm().copy(mode = mode, ambientLevel = ambientLevel))
    }

    fun setAmbientLevel(level: Int, dragging: Boolean) {
        val cur = _state.value.ncAsm ?: return
        if (AmbientMode.AMBIENT !in noiseModes()) return
        ambientLevel = level.coerceIn(1, maxAmbientLevel)
        pushNcAsm(cur.copy(mode = AmbientMode.AMBIENT, ambientLevel = ambientLevel), dragging)
    }

    /** Mode, ambient level and voice focus in one command (automation profiles). */
    fun setNoiseControl(mode: AmbientMode, level: Int? = null, voiceFocus: Boolean? = null) {
        if (mode !in noiseModes()) return
        val cur = currentNcAsm()
        level?.let { ambientLevel = it.coerceIn(1, maxAmbientLevel) }
        pushNcAsm(cur.copy(mode = mode, ambientLevel = ambientLevel, voiceFocus = voiceFocus ?: cur.voiceFocus))
    }

    /** NC with one microphone pair instead of two (NcDualSingleValue SINGLE): Sound Connect's Wind Noise Reduction. */
    fun setWindReduction(on: Boolean) {
        if (_state.value.ncAsmCaps?.windReduction != true) return
        pushNcAsm(currentNcAsm().copy(mode = AmbientMode.NOISE_CANCELLING, ncOnValue = if (on) 0x01 else 0x02))
    }

    /** Asks the headset to adapt NC/ASM to what it senses (SENSE AUTO_NC_ASM). */
    fun startHeadsetAdaptiveSound() = fire(proto.senseStart())

    fun setVoiceFocus(on: Boolean) {
        val cur = _state.value.ncAsm ?: return
        pushNcAsm(cur.copy(voiceFocus = on))
    }

    fun selectEqPreset(id: Int) {
        _state.update { it.copy(eq = it.eq?.copy(presetId = id) ?: EqState(id, emptyList())) }
        eqOutbox.trySend(proto.eqPreset(id, _state.value.eqCustomizable))
        // The headset answers with an EQ notify carrying that preset's band levels.
    }

    /**
     * Edits one band. Like Sound Connect, a change lands in the active preset when that is a writable slot
     * (Manual or Custom 1–5); on a factory preset it becomes Manual, starting from that preset's levels.
     */
    fun setEqBand(index: Int, level: Int) {
        val eq = _state.value.eq ?: return
        if (index !in eq.bands.indices) return
        val bands = eq.bands.toMutableList().also { it[index] = level }
        writeEqPreset(editTarget(eq.presetId), bands)
    }

    /** Writes every band at once into the active writable slot (AutoEq results, imported curves). */
    fun applyEqBands(levels: List<Int>) {
        val eq = _state.value.eq ?: return
        writeEqPreset(editTarget(eq.presetId), levels)
    }

    /** Selects [presetId] and stores [levels] in it; only Manual and Custom 1–5 keep what is written. */
    fun writeEqPreset(presetId: Int, levels: List<Int>) {
        if (!_state.value.eqCustomizable) return
        val n = _state.value.eq?.bands?.size?.takeIf { it > 0 } ?: return
        if (levels.size != n) return
        val bands = levels.map { it.coerceIn(0, 2 * _state.value.eqFlat) }
        _state.update { it.copy(eq = EqState(presetId, bands)) }
        eqOutbox.trySend(proto.eqBands(presetId, bands))
    }

    private fun editTarget(presetId: Int) = if (presetId in WRITABLE_PRESETS) presetId else CUSTOM_PRESET

    /** Sound Connect's Reset: every band of the active writable slot back to 0. */
    fun setEqFlat() {
        val eq = _state.value.eq ?: return
        if (eq.bands.isEmpty()) return
        writeEqPreset(editTarget(eq.presetId), List(eq.bands.size) { _state.value.eqFlat })
    }

    fun setListeningMode(mode: ListeningMode, roomSize: Int? = null) {
        val from = _state.value.listening ?: return
        val to = from.with(mode).let { if (roomSize != null) it.copy(roomSize = roomSize) else it }
        _state.update { it.copy(listening = to) }
        val writes = proto.listeningModes(from, to)
        scope.launch { writes.forEach { sendQuietly(it) } }
    }

    fun setDsee(auto: Boolean) {
        _state.update { it.copy(dseeAuto = auto) }
        fire(proto.upscaling(auto))
    }

    fun setSpeakToChat(on: Boolean) {
        _state.update { it.copy(speakToChat = on) }
        fire(proto.speakToChat(on))
    }

    private fun fire(request: Request?) {
        scope.launch { sendQuietly(request) }
    }

    fun setSpeakToChatDetail(d: SpeakToChatDetail) {
        _state.update { it.copy(speakToChatDetail = d) }
        fire(proto.speakToChatDetail(d))
    }

    fun setPauseWhenRemoved(on: Boolean) {
        _state.update { it.copy(pauseWhenRemoved = on) }
        fire(proto.pauseWhenRemoved(on))
    }

    /** Sony keeps the last chosen timer as "selectTime" even while a non-timer option is active. */
    fun setAutoPowerOff(id: Int) {
        val prevSelect = _state.value.autoPowerOff?.second ?: id
        val select = if (id < 0x10) id else prevSelect
        _state.update { it.copy(autoPowerOff = id to select) }
        fire(proto.autoPowerOff(id, select))
    }

    /** Alert types the user already confirmed in our own dialog; the headset's prompt is answered for them. */
    private val approvedAlerts = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    fun answerAlert(type: Int, positive: Boolean) {
        _state.update { it.copy(alert = null) }
        if (positive && type in RECONNECTING_ALERTS) expectReconnect(ALERT_REASON[type] ?: "Applying the change")
        fire(proto.alertAnswer(type, positive))
        // A declined change leaves the headset as it was; re-read so the UI drops its optimistic value.
        if (!positive) scope.launch {
            sendQuietly(proto.readAssignable()); sendQuietly(proto.readSoundQualityMode())
            multipointId()?.let { sendQuietly(proto.readGeneral(it)) }
        }
    }

    fun setCustomButton(preset: Int) = setAssignable(0, preset)

    /** Reassigns key [index] of the headset's assignable keys; the others keep their presets. */
    fun setAssignable(index: Int, preset: Int) {
        val s = _state.value
        val current = s.assignable.ifEmpty { List(maxOf(s.assignableKeys.size, 1)) { s.customButton ?: preset } }
        if (index !in current.indices) return
        val next = current.toMutableList().also { it[index] = preset }
        _state.update { it.copy(assignable = next, customButton = next.first()) }
        approvedAlerts += Alert.KEY_ASSIGN_CHANGE
        fire(proto.assignable(next))
    }

    val canUseLowLatencyMode get() = proto.lowLatencyMode() != null

    fun setLowLatencyMode() {
        _state.update { it.copy(soundQualityPreferConnection = false, soundQualityLowLatency = true) }
        fire(proto.lowLatencyMode())
    }

    fun setSoundQualityMode(preferConnection: Boolean) {
        _state.update { it.copy(soundQualityPreferConnection = preferConnection, soundQualityLowLatency = false) }
        approvedAlerts += Alert.CONNECTION_MODE_CHANGE
        fire(proto.soundQualityMode(preferConnection))
    }

    fun setGeneralSetting(id: Int, on: Boolean) {
        if (id == multipointId()) approvedAlerts += if (on) Alert.MULTIPOINT_ON else Alert.MULTIPOINT_OFF
        _state.update { it.copy(generalValues = it.generalValues + (id to on)) }
        fire(proto.general(id, on))
    }

    /** A LIST_TYPE general setting, e.g. what the WH-1000XM3's NC/AMB button does. */
    fun setGeneralListSetting(id: Int, index: Int) {
        _state.update { it.copy(generalListValues = it.generalListValues + (id to index)) }
        approvedAlerts += Alert.KEY_ASSIGN_CHANGE
        fire(proto.generalIndex(id, index))
    }

    fun multipointId(): Int? =
        _state.value.generalSettings.values.firstOrNull { it.title == "MULTIPOINT_SETTING" }?.id

    fun startOptimizer() {
        _state.update { it.copy(optimizerRunning = true) }
        fire(proto.optimizerStart())
    }

    fun cancelOptimizer() {
        _state.update { it.copy(optimizerRunning = false) }
        fire(proto.optimizerCancel())
    }

    // ---- playback controller ----

    fun setVolume(v: Int) {
        val c = v.coerceIn(0, _state.value.volumeSteps - 1)
        _state.update { it.copy(volume = c) }
        fire(proto.volume(c))
    }
    fun playPause() {
        val playing = _state.value.playing == true
        _state.update { it.copy(playing = !playing) }
        fire(proto.playControl(if (playing) 0x01 else 0x07))
    }
    fun nextTrack() = fire(proto.playControl(0x02))
    fun previousTrack() = fire(proto.playControl(0x03))

    // ---- multipoint / pairing (table 2 peripheral) ----

    fun refreshPeers() = fire(proto.readPeers())
    /**
     * The XM4 only enters pairing mode while multipoint is on; otherwise it ignores the command. So the state
     * follows the headset's notify (re-read shortly after) instead of being set optimistically.
     */
    fun setPairingMode(on: Boolean) {
        scope.launch {
            sendQuietly(proto.pairingMode(on))
            delay(1_500)
            sendQuietly(proto.readPairingMode())
        }
    }
    fun connectPeer(address: String) = peer(1, address)
    fun disconnectPeer(address: String) = peer(0, address)
    fun unpairPeer(address: String) = peer(2, address)
    fun clearPeerResult() = _state.update { it.copy(peerResult = null) }

    private fun peer(action: Int, address: String) {
        // Dropping the phone's own link is a reconnect, not a failure.
        if (action != 1 && address.equals(_state.value.address, ignoreCase = true)) expectReconnectUntil = System.currentTimeMillis() + 15_000
        fire(proto.peerAction(action, address))
        scope.launch { delay(1_500); sendQuietly(proto.readPeers()) }
    }

    fun setVoiceGuidance(on: Boolean) {
        _state.update { it.copy(voiceGuidance = on) }
        fire(proto.voiceGuidance(on, _state.value.voiceGuidanceLanguage))
    }

    /** Whether the prompt language can be picked here (a v2 language-switch headset). */
    val canSetVoiceGuidanceLanguage get() = proto.voiceGuidanceLanguage(true, 1) != null

    fun setVoiceGuidanceLanguage(language: Int) {
        val on = _state.value.voiceGuidance ?: true
        _state.update { it.copy(voiceGuidanceLanguage = language) }
        fire(proto.voiceGuidanceLanguage(on, language))
    }

    fun setMaxVolumeOn(on: Boolean) {
        _state.update { it.copy(maxVolumeOn = on) }
        fire(proto.maxVolumeOn(on))
    }

    fun setMaxVolumeLevel(level: Int) {
        _state.update { it.copy(maxVolumeLevel = level) }
        fire(proto.maxVolumeLevel(level))
    }

    fun setVoiceGuidanceVolume(volume: Int) {
        _state.update { it.copy(voiceGuidanceVolume = volume) }
        fire(proto.voiceGuidanceVolume(volume, sample = true))
    }

    private var playbackJob: Job? = null

    /** Re-reads track, album, artist and play state; bursts of notifies collapse into one read. */
    private var emptyTitleRetries = 0

    private fun refreshPlayback(delayMs: Long = 150) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            delay(delayMs)
            for (r in proto.readPlayback()) sendQuietly(r)
            // The playing source may have moved to another multipoint device.
            if (_state.value.peers != null) sendQuietly(proto.readPeers())
        }
    }

    fun refreshBattery() = scope.launch {
        for (r in proto.readBattery(_state.value.functions)) sendQuietly(r)
    }

    fun powerOff() = scope.launch { sendQuietly(proto.powerOff()) }

    // ---- sound and system settings found on other v1 models ----

    fun setEbb(level: Int) {
        val ebb = _state.value.ebb ?: return
        val l = level.coerceIn(ebb.range)
        _state.update { it.copy(ebb = ebb.copy(level = l)) }
        eqOutbox.trySend(proto.ebb(l))
    }

    fun setVpt(presetId: Int) {
        _state.update { it.copy(vpt = presetId) }
        fire(proto.vpt(presetId))
    }

    fun setSoundPosition(presetId: Int) {
        _state.update { it.copy(soundPosition = presetId) }
        fire(proto.soundPosition(presetId))
    }

    fun setVibrator(on: Boolean) {
        _state.update { it.copy(vibrator = on) }
        fire(proto.vibrator(on))
    }

    fun setPowerSaving(on: Boolean) {
        _state.update { it.copy(powerSaving = on) }
        fire(proto.powerSaving(on))
    }

    fun setTrainingMode(on: Boolean) {
        _state.update { it.copy(trainingMode = on) }
        fire(proto.trainingMode(on))
    }

    companion object {
        @Volatile private var instance: HeadphonesController? = null

        /** A headset that isn't there, starting from [state] (see the constructor's `demo`). */
        fun demo(context: Context, state: HeadphonesState) = HeadphonesController(context.applicationContext as Application, state.copy(demo = true))

        fun get(context: Context): HeadphonesController =
            instance ?: synchronized(this) {
                instance ?: HeadphonesController(context.applicationContext as Application).also { instance = it }
            }

        const val CUSTOM_PRESET = 0xA0
        /** Custom 2: where Find Your Equalizer saves its result, as in Sound Connect. */
        const val USER_PRESET_2 = 0xA2
        /** Manual and the user slots Custom 1–5 hold whatever is written to them. */
        val WRITABLE_PRESETS = 0xA0..0xA5
        const val EQ_FLAT = 10
        private const val PLANNED_RECONNECT_MS = 45_000L
        /** A session the headset closes sooner than this (audio still up) counts as taken by another source. */
        private const val KICK_WINDOW_MS = 20_000L
        private const val MAX_KICKS = 5
        private const val UNPLANNED_RECONNECT_MS = 15_000L
        private const val RETRY_DELAY_MS = 2_000L
        private const val A2DP_SETTLE_MS = 3_000L
        private const val DROP_GRACE_MS = 2_000L
        private const val SETTLE_GRACE_MS = 3_000L
        /** Sony's model prefixes: over-ear, earbuds, neckband, and older MDR- models. */
        private val SONY_NAMES = listOf("WH-", "WF-", "WI-", "MDR-", "LinkBuds", "ULT WEAR", "Sony")
        /** How long the handshake waits for PROTOCOL_INFO and SUPPORT_FUNCTION. */
        private const val REPLY_TIMEOUT_MS = 3_000L
        /** What a firmware 3.0.1 WH-1000XM4 reports (docs/NOTES.md); the fallback for a silent SUPPORT_FUNCTION. */
        val XM4_FUNCTIONS = setOf(
            0x11, 0x12, 0x13, 0x14, 0x21, 0x22, 0x30, 0x39, 0x51, 0x62, 0x71, 0x81, 0xA1, 0xC1,
            0xD1, 0xD2, 0xE1, 0xE2, 0xF3, 0xF4, 0xF5, 0xF6,
        )
        private val RECONNECTING_ALERTS = setOf(
            Alert.CONNECTION_MODE_CHANGE, Alert.KEY_ASSIGN_CHANGE, Alert.MULTIPOINT_ON, Alert.MULTIPOINT_OFF,
        )
        private val ALERT_REASON = mapOf(
            Alert.CONNECTION_MODE_CHANGE to "Applying Sound quality mode",
            Alert.KEY_ASSIGN_CHANGE to "Applying CUSTOM button",
            Alert.MULTIPOINT_ON to "Turning on Connect to 2 devices",
            Alert.MULTIPOINT_OFF to "Turning off Connect to 2 devices",
        )
    }
}
