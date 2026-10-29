package com.occaecat.hush

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.occaecat.hush.mdr.A2dpWatcher
import com.occaecat.hush.mdr.AlertType
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.Codec
import com.occaecat.hush.mdr.EqCapability
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.mdr.PeerDevice
import com.occaecat.hush.mdr.GeneralSetting
import com.occaecat.hush.mdr.OptimizerResult
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.Mdr
import com.occaecat.hush.backend.HeadphoneBackend
import com.occaecat.hush.backend.SonyBackend
import com.occaecat.hush.jabra.JabraBackend
import com.occaecat.hush.jabra.JabraButtonContext
import com.occaecat.hush.jabra.JabraButtonSlot
import com.occaecat.hush.jabra.JabraButtonState
import com.occaecat.hush.jabra.JabraConnection
import com.occaecat.hush.jabra.JabraProtocol
import com.occaecat.hush.jabra.firmware.*
import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.NcAsmState
import android.bluetooth.BluetoothDevice
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
import kotlinx.coroutines.CancellationException

data class PairedDevice(
    val name: String, val address: String, val looksSony: Boolean,
    val brand: HeadphoneBrand = if (looksSony) HeadphoneBrand.SONY else HeadphoneBrand.UNKNOWN,
)

enum class Phase { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED, UPDATING }

data class HeadphonesState(
    val brand: HeadphoneBrand = HeadphoneBrand.SONY,
    val capabilities: DeviceCapabilities = DeviceCapabilities.SONY,
    val batteries: List<BatteryComponent> = emptyList(),
    val ancLevel: Int? = null,
    val sleepMinutes: Int? = null,
    val announcementMode: Int? = null,
    val jabraProductId: Int? = null,
    val jabraButtons: JabraButtonState? = null,
    val jabraFirmwareReadiness: JabraFirmwareReadiness? = null,
    val firmwareUpdate: JabraFirmwareUpdateState? = null,
    val phase: Phase = Phase.IDLE,
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
    /** Firmware-update parameters by UpdateInquiredType (see [Mdr.getUpdateParam]); for the device details page. */
    val updateInfo: Map<Int, String> = emptyMap(),
    /** CONNECT_RET_PROTOCOL_INFO version, e.g. 0x7000. */
    val protocolVersion: Int? = null,
    val firmware: String? = null,
    val battery: Int? = null,
    val charging: Boolean = false,
    val codec: Codec? = null,
    val dseeAuto: Boolean? = null,
    val dseeActive: Boolean = false,
    val ncAsm: NcAsmState? = null,
    val eqCaps: EqCapability? = null,
    val eq: EqState? = null,
    val speakToChat: Boolean? = null,
    val speakToChatDetail: SpeakToChatDetail? = null,
    val pauseWhenRemoved: Boolean? = null,
    val pauseWhenRemovedAvailable: Boolean = true,
    val autoPowerOffOptions: List<Int> = emptyList(),
    val autoPowerOff: Pair<Int, Int>? = null,
    val customButton: Int? = null,
    val soundQualityPreferConnection: Boolean? = null,
    val generalSettings: Map<Int, GeneralSetting> = emptyMap(),
    val generalValues: Map<Int, Boolean> = emptyMap(),
    val generalEnabled: Map<Int, Boolean> = emptyMap(),
    /** An alert the headset raised that the user has not pre-approved: (AlertMessageType, actionType). */
    val alert: Pair<Int, Int>? = null,
    val optimizerRunning: Boolean = false,
    val optimizerResult: OptimizerResult? = null,
    val voiceGuidance: Boolean? = null,
    val voiceGuidanceLanguage: Int? = null,
    /**
     * Set while another multipoint source holds the headset's single control channel and Hush stepped back
     * (phase FAILED): the other device's name as the headset reported it, or "another device".
     */
    val elsewhere: String? = null,
    /** FunctionType codes the headset reported (CONNECT_RET_SUPPORT_FUNCTION); empty until it answers. */
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
    val isSony: Boolean get() = brand == HeadphoneBrand.SONY
    val isJabra: Boolean get() = brand == HeadphoneBrand.JABRA
    /** What to call the headset in the UI: the phone's own name for it first, then the model. */
    val displayName: String get() = deviceName ?: modelName ?: "Headphones"
}

/**
 * Owns the headset session for the whole process. On the phone the app UI, the foreground service, the quick
 * settings tile and the ColorOS device card all drive this one connection; on the watch it is the standalone
 * route. Platform side effects subscribe to [state] from outside (PhoneIntegrations, the wear hub).
 */
class HeadphonesController private constructor(private val app: Application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val prefs = app.getSharedPreferences("hush", Context.MODE_PRIVATE)
    // An unfinished installation belongs to this phone, never to an Android cloud backup.
    private val firmwareJournal = android.util.AtomicFile(java.io.File(app.noBackupFilesDir, "jabra-firmware-operation.json"))
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val audio = app.getSystemService(android.media.AudioManager::class.java)

    private val _state = MutableStateFlow(savedState())
    val state: StateFlow<HeadphonesState> = _state

    private fun savedState(): HeadphonesState {
        val address = prefs.getString("address", null)
        val brand = address?.let { resolveBrand(it) }?.takeIf { it != HeadphoneBrand.UNKNOWN } ?: HeadphoneBrand.SONY
        val installation = restoreFirmwareUpdate()?.takeIf { it.address.equals(address, ignoreCase = true) }
        return HeadphonesState(address = address, brand = brand,
            firmwareUpdate = installation,
            phase = if (installation?.active == true) Phase.UPDATING else Phase.IDLE,
            capabilities = if (brand == HeadphoneBrand.SONY) DeviceCapabilities.SONY else DeviceCapabilities(),
            modelName = address?.let { prefs.getString("model:$it", null) })
    }

    private var backend: HeadphoneBackend? = null
    private val connection: SonyBackend? get() = backend as? SonyBackend
    private val jabra: JabraBackend? get() = backend as? JabraBackend
    private var sessionJob: Job? = null
    private var firmwareJob: Job? = null
    private val commandJobs = java.util.Collections.synchronizedSet(mutableSetOf<Job>())

    /** Last non-zero ambient level, restored when switching back to Ambient. */
    private var ambientLevel = 20

    /** Conflated outboxes so slider drags never queue up stale values. */
    private val ncAsmOutbox = Channel<Triple<SonyBackend, NcAsmState, Boolean>>(Channel.CONFLATED)
    private val eqOutbox = Channel<Pair<SonyBackend, ByteArray>>(Channel.CONFLATED)

    private val a2dp = A2dpWatcher(app).also { it.start() }

    /** Reopen the saved headset when its audio link comes back. The watch turns this off while the phone drives it. */
    @Volatile var autoReconnect = true

    init {
        if (_state.value.firmwareUpdate?.status == FirmwareUpdateStatus.AWAITING_CASE) {
            firmwareJob = scope.launch { awaitFirmwareCaseCycle() }
        }
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
        scope.launch { for ((owner, s, dragging) in ncAsmOutbox) if (backend === owner) sendQuietly(Mdr.setNcAsm(s, dragging)) }
        scope.launch { for ((owner, p) in eqOutbox) if (backend === owner) sendQuietly(p) }
    }

    @SuppressLint("MissingPermission")
    fun refreshPaired() {
        val devices = runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        _state.update { s ->
            s.copy(paired = devices.filterNot {
                it.type == BluetoothDevice.DEVICE_TYPE_LE && resolveBrand(it.address, it.name, it.alias) == HeadphoneBrand.JABRA
            }.map {
                val name = it.alias ?: it.name ?: it.address
                val brand = resolveBrand(it.address, it.name, it.alias)
                PairedDevice(name, it.address, brand == HeadphoneBrand.SONY, brand)
            }.sortedByDescending { it.brand != HeadphoneBrand.UNKNOWN })
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
        if (_state.value.firmwareUpdate?.active == true) return
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
        val modes = listOf(AmbientMode.NOISE_CANCELLING, AmbientMode.AMBIENT, AmbientMode.OFF)
            .filter { it in _state.value.capabilities.noiseModes }
        if (modes.isNotEmpty()) setAmbientMode(modes[(modes.indexOf(_state.value.ncAsm?.mode) + 1) % modes.size])
    }

    private fun hasBluetoothPermission() =
        app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (_state.value.firmwareUpdate?.active == true) return
        val installation = _state.value.firmwareUpdate?.takeIf { it.address.equals(address, ignoreCase = true) }
        disconnect()
        if (!hasBluetoothPermission()) {
            _state.update { it.copy(phase = Phase.FAILED, error = "Nearby devices permission is required") }
            return
        }
        val device = adapter?.getRemoteDevice(address) ?: return
        val detected = resolveBrand(address, device.name, device.alias)
        if (detected == HeadphoneBrand.JABRA && device.type == BluetoothDevice.DEVICE_TYPE_LE) {
            _state.update { it.copy(phase = Phase.FAILED, error = "Choose the Bluetooth audio entry for these Jabra headphones, not the LE entry") }
            return
        }
        // Preserve the existing manual Sony connection path for names we cannot recognize.
        val brand = if (detected == HeadphoneBrand.UNKNOWN) HeadphoneBrand.SONY else detected
        prefs.edit().putString("address", address).apply()
        if (brand == HeadphoneBrand.SONY) prefs.edit().putString("brand:$address", brand.name).apply()
        _state.value = HeadphonesState(
            brand = brand, capabilities = if (brand == HeadphoneBrand.SONY) DeviceCapabilities.SONY else DeviceCapabilities(),
            phase = Phase.CONNECTING, paired = _state.value.paired, address = address, deviceName = device.alias ?: device.name,
            firmwareUpdate = installation,
        )
        sessionJob = scope.launch { if (brand == HeadphoneBrand.JABRA) runJabraSession(device) else runSession(device) }
    }

    private fun resolveBrand(address: String, vararg names: String?): HeadphoneBrand {
        val detected = headphoneBrand(*names)
        if (detected != HeadphoneBrand.UNKNOWN) return detected
        return runCatching { HeadphoneBrand.valueOf(prefs.getString("brand:$address", "UNKNOWN")!!) }
            .getOrDefault(HeadphoneBrand.UNKNOWN)
    }

    private suspend fun runJabraSession(device: BluetoothDevice) = coroutineScope {
        var reconnect = false
        while (isActive) {
            if (reconnect) {
                _state.update { it.copy(phase = Phase.RECONNECTING, reconnectReason = "Connection lost") }
                if (!waitForA2dp(device.address, System.currentTimeMillis() + UNPLANNED_RECONNECT_MS)) {
                    _state.update { it.copy(phase = Phase.FAILED, reconnectReason = null, error = "Headphones did not come back") }
                    return@coroutineScope
                }
                delay(A2DP_SETTLE_MS)
            }
            val conn = JabraBackend(device, this,
                debug = app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            backend = conn
            val collector = launch {
                conn.state.collect { next ->
                    if (backend === conn) _state.update { current ->
                        next.copy(
                            phase = current.phase, paired = current.paired, address = current.address,
                            deviceName = current.deviceName, reconnectReason = current.reconnectReason,
                            volume = current.volume, volumeSteps = current.volumeSteps, playing = current.playing,
                            firmwareUpdate = current.firmwareUpdate,
                        )
                    }
                }
            }
            var mediaJob: Job? = null
            try {
                conn.open()
                conn.initialize()
                if (backend !== conn) return@coroutineScope
                prefs.edit().putString("brand:${device.address}", HeadphoneBrand.JABRA.name)
                    .putString("model:${device.address}", conn.state.value.modelName).apply()
                _state.update { it.copy(phase = Phase.CONNECTED, error = null, reconnectReason = null) }
                mediaJob = launch {
                    while (isActive) {
                        refreshLocalPlayback()
                        delay(1_000)
                    }
                }
                conn.closed.await()
                if (_state.value.firmwareUpdate?.active == true) return@coroutineScope
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (backend === conn && _state.value.firmwareUpdate?.active != true) _state.update {
                    it.copy(phase = Phase.FAILED, error = e.message ?: "Could not connect to Jabra", reconnectReason = null)
                }
                return@coroutineScope
            } finally {
                mediaJob?.cancel()
                collector.cancel()
                conn.close()
                if (backend === conn) backend = null
            }
            reconnect = true
        }
    }

    /**
     * Keeps a control session alive. Some settings (CUSTOM button, sound quality mode, multipoint) make
     * the headset drop and re-establish the Bluetooth link; Sound Connect waits up to 30 s for it to come
     * back (connectionmode/h.java), so we do the same and then re-read everything.
     */
    private suspend fun runSession(device: BluetoothDevice) = coroutineScope {
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
            val conn = openWithRetry(device, window)
            if (conn == null) {
                _state.update {
                    it.copy(
                        phase = Phase.FAILED, reconnectReason = null,
                        error = if (firstAttempt) "Could not open the headphone channel" else "Headphones did not come back",
                    )
                }
                return@coroutineScope
            }
            backend = conn
            val openedAt = System.currentTimeMillis()
            expectReconnectUntil = 0L
            _state.update { it.copy(phase = Phase.CONNECTED, error = null) }
            val collector = launch { conn.events.collect(::onEvent) }
            val init = launch {
                queryEverything()
                if (!firstAttempt) settled()
            }
            conn.closed.await()
            collector.cancel()
            init.cancel()
            if (backend === conn) backend = null
            conn.close()
            firstAttempt = false
            val planned = System.currentTimeMillis() < expectReconnectUntil
            val shortLived = System.currentTimeMillis() - openedAt < KICK_WINDOW_MS
            kicks = if (!planned && shortLived && a2dp.isConnected(device.address)) kicks + 1 else 0
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
    private suspend fun openWithRetry(device: BluetoothDevice, windowMs: Long): SonyBackend? {
        val deadline = System.currentTimeMillis() + windowMs
        if (windowMs > 0) delay(DROP_GRACE_MS) // let the headset finish tearing its links down
        while (true) {
            if (windowMs > 0) {
                // Never connect() unless A2DP is up right now: connect() would page a restarting headset and
                // grab the MDR channel before it is ready, which wedges it until a power cycle.
                if (!waitForA2dp(device.address, deadline)) return null
                delay(A2DP_SETTLE_MS)
                if (!a2dp.isConnected(device.address)) continue
            }
            val conn = SonyBackend(device, scope)
            backend = conn
            try { conn.open(); return conn }
            catch (e: CancellationException) { conn.close(); throw e }
            catch (_: Exception) { conn.close() }
            if (backend === conn) backend = null
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

    private suspend fun queryEverything() { connection?.initialize() }

    private fun musicActive() = runCatching { audio.isMusicActive }.getOrDefault(false)

    /** Do not change the speaker or a different headset's stream while controlling Jabra over multipoint. */
    private fun isLocalAudioRoute(): Boolean = runCatching {
        val address = _state.value.address ?: return false
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            audio.getAudioDevicesForAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()).any {
                it.address.equals(address, ignoreCase = true) &&
                    it.type in listOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        } else {
            @Suppress("DEPRECATION")
            val route = app.getSystemService(android.media.MediaRouter::class.java)
                .getSelectedRoute(android.media.MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
            val devices = audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            route.deviceType == android.media.MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH &&
                devices.singleOrNull()?.address.equals(address, ignoreCase = true)
        }
    }.getOrDefault(false)

    private fun refreshLocalPlayback() {
        if (!_state.value.isJabra || _state.value.phase != Phase.CONNECTED) return
        if (!isLocalAudioRoute()) {
            _state.update { it.copy(volume = null, playing = null) }
            return
        }
        val stream = android.media.AudioManager.STREAM_MUSIC
        _state.update { it.copy(volume = audio.getStreamVolume(stream), volumeSteps = audio.getStreamMaxVolume(stream) + 1,
            playing = musicActive()) }
    }

    private fun localMediaKey(code: Int) {
        if (_state.value.phase != Phase.CONNECTED || !isLocalAudioRoute()) return
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code))
        refreshLocalPlayback()
    }

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
        if (_state.value.firmwareUpdate?.active == true) return
        sessionJob?.cancel()
        synchronized(commandJobs) { commandJobs.toList() }.forEach { it.cancel() }
        backend?.close()
        backend = null
        playbackJob?.cancel()
        settleJob?.cancel()
        expectReconnectUntil = 0L
        approvedAlerts.clear()
        while (ncAsmOutbox.tryReceive().isSuccess) { /* Drop commands from the old session. */ }
        while (eqOutbox.tryReceive().isSuccess) { }
        _state.update { it.copy(phase = Phase.IDLE, audioSettling = false, reconnectReason = null) }
    }

    fun forget() {
        if (_state.value.firmwareUpdate?.active == true) return
        disconnect()
        prefs.edit().remove("address").apply()
        _state.update { HeadphonesState(paired = it.paired) }
    }

    private suspend fun sendQuietly(payload: ByteArray, table2: Boolean = false, owner: SonyBackend? = connection) {
        val conn = owner ?: return
        if (backend !== conn) return
        try { conn.send(payload, table2) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w("Hush", "send failed", e) }
    }

    /** Capture ownership before launching: a delayed UI action must not target a replacement session. */
    private fun jabraAction(action: suspend JabraBackend.() -> Unit) {
        val owner = jabra ?: return
        if (_state.value.phase != Phase.CONNECTED) return
        scope.launch {
            if (backend !== owner || _state.value.phase != Phase.CONNECTED) return@launch
            try { owner.action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (backend === owner) _state.update { it.copy(error = e.message ?: "The headphones did not confirm the change") }
            }
        }.also { job ->
            commandJobs.add(job)
            job.invokeOnCompletion { commandJobs.remove(job) }
        }
    }

    fun refreshJabraButtons() = jabraAction { refreshJabraButtons() }
    fun setJabraButton(slot: JabraButtonSlot, action: Int) = jabraAction { setJabraButton(slot, action) }
    fun resetJabraButtons(context: JabraButtonContext) = jabraAction { resetJabraButtons(context) }

    suspend fun readJabraFirmwareReadiness(): JabraFirmwareReadiness {
        val owner = jabra ?: error("Connect the Jabra headphones first")
        check(_state.value.phase == Phase.CONNECTED) { "The headphones are busy" }
        val result = owner.readFirmwareReadiness()
        check(backend === owner && _state.value.phase == Phase.CONNECTED) { "The selected headphones changed" }
        return result
    }

    /** Only the reviewed firmware screen calls this; generic commands cannot start an installation. */
    fun startJabraFirmwareUpdate(firmware: JabraFirmwarePackage): Boolean {
        val before = _state.value
        val owner = jabra ?: return false
        val address = before.address ?: return false
        if (before.phase != Phase.CONNECTED || before.firmwareUpdate?.active == true ||
            before.jabraProductId != firmware.productId ||
            !JabraFirmwareUpdateState.isNewer(firmware.version, before.firmware ?: "")) return false
        val operation = JabraFirmwareUpdateState(firmware.productId, firmware.version, address,
            FirmwareUpdateStatus.TRANSFERRING)
        // A synchronous journal commit must succeed before any erase/write is sent to the device.
        if (!publishFirmwareUpdate(operation)) return false
        synchronized(commandJobs) { commandJobs.toList() }.forEach { it.cancel() }
        _state.update { it.copy(phase = Phase.UPDATING, error = null, jabraFirmwareReadiness = null) }
        firmwareJob = scope.launch {
            try {
                owner.installFirmware(firmware) { progress ->
                    val current = checkNotNull(_state.value.firmwareUpdate)
                    check(current.matches(address, firmware.productId)) { "The update device changed" }
                    val next = current.copy(progress = progress)
                    if (current.progress?.stage != progress.stage || current.progress?.partition != progress.partition) {
                        check(publishFirmwareUpdate(next)) { "Could not save firmware progress" }
                    } else _state.update { it.copy(firmwareUpdate = next) }
                }
                check(publishFirmwareUpdate(operation.copy(status = FirmwareUpdateStatus.AWAITING_CASE,
                    progress = JabraFirmwareProgress(JabraFirmwareStage.AWAITING_CASE, 100)))) {
                    "The transfer completed, but its status could not be saved. Check the installed version."
                }
                closeFirmwareSession(owner)
                awaitFirmwareCaseCycle()
            } catch (e: CancellationException) {
                if (_state.value.firmwareUpdate?.status == FirmwareUpdateStatus.TRANSFERRING) {
                    publishFirmwareUpdate(checkNotNull(_state.value.firmwareUpdate).copy(
                        status = FirmwareUpdateStatus.INTERRUPTED, error = "Transfer interrupted. Check the installed version before trying again."))
                    _state.update { it.copy(phase = Phase.FAILED) }
                }
                closeFirmwareSession(owner)
                throw e
            } catch (e: Exception) {
                publishFirmwareUpdate(checkNotNull(_state.value.firmwareUpdate).copy(
                    status = FirmwareUpdateStatus.FAILED, error = e.message ?: "Firmware installation failed"))
                _state.update { it.copy(phase = Phase.FAILED) }
                closeFirmwareSession(owner)
            }
        }
        return true
    }

    fun cancelJabraFirmwareUpdate() {
        if (_state.value.firmwareUpdate?.canCancel != true) return
        firmwareJob?.cancel()
    }

    fun dismissJabraFirmwareUpdate() {
        if (_state.value.firmwareUpdate?.active == true) return
        firmwareJournal.delete()
        if (firmwareJournal.baseFile.exists()) {
            _state.update { it.copy(firmwareUpdate = it.firmwareUpdate?.journalFailure()) }
            return
        }
        _state.update { it.copy(firmwareUpdate = null) }
    }

    private fun closeFirmwareSession(owner: JabraBackend?) {
        owner?.close()
        if (owner != null && backend === owner) {
            sessionJob?.cancel()
            backend = null
        }
    }

    /** Post-commit work is read-only. Never send GO, erase, or data again on reconnect. */
    private suspend fun awaitFirmwareCaseCycle() {
        val address = _state.value.firmwareUpdate?.address ?: return
        var sawDisconnect = !a2dp.isConnected(address)
        val deadline = android.os.SystemClock.elapsedRealtime() + 30 * 60_000L
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                _state.value.firmwareUpdate?.let { publishFirmwareUpdate(it.copy(status = FirmwareUpdateStatus.INTERRUPTED,
                    error = "Installation has not been confirmed. Wait for the case light to finish, reconnect the earbuds, and check the installed version.")) }
                _state.update { it.copy(phase = Phase.FAILED) }
                return
            }
            if (!a2dp.isConnected(address)) sawDisconnect = true
            if (sawDisconnect && a2dp.isConnected(address)) {
                delay(A2DP_SETTLE_MS)
                if (a2dp.isConnected(address) && verifyInstalledFirmware()) return
                delay(25_000)
            }
            delay(5_000)
        }
    }

    /** Explicit retry after the case LED has finished, including recovery after process death. */
    fun verifyJabraFirmwareUpdate() {
        val current = _state.value.firmwareUpdate ?: return
        if (current.status == FirmwareUpdateStatus.TRANSFERRING || current.status == FirmwareUpdateStatus.SUCCEEDED) return
        firmwareJob?.cancel()
        sessionJob?.cancel()
        backend?.close()
        backend = null
        firmwareJob = scope.launch { verifyInstalledFirmware(finalAttempt = true) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun verifyInstalledFirmware(finalAttempt: Boolean = false): Boolean {
        val operation = _state.value.firmwareUpdate ?: return false
        if (!hasBluetoothPermission() || !a2dp.isConnected(operation.address)) {
            publishFirmwareUpdate(operation.copy(status = if (finalAttempt) FirmwareUpdateStatus.INTERRUPTED else FirmwareUpdateStatus.AWAITING_CASE,
                error = "Wait for the case LED to stop flashing, then reconnect the earbuds to check the installed version."))
            if (finalAttempt) _state.update { it.copy(phase = Phase.FAILED) }
            return false
        }
        _state.update { it.copy(phase = Phase.UPDATING) }
        publishFirmwareUpdate(operation.copy(status = FirmwareUpdateStatus.VERIFYING, error = null))
        val conn = JabraConnection(adapter!!.getRemoteDevice(operation.address), scope)
        var confirmed = false
        try {
            conn.open()
            val identity = JabraProtocol.model(conn.read(JabraProtocol.PRODUCT_ID))
            val version = checkNotNull(JabraProtocol.text(conn.read(JabraProtocol.FIRMWARE))) { "The installed firmware version is unknown" }
            check(operation.matches(_state.value.address ?: "", identity.productId)) { "The update device changed" }
            confirmed = operation.confirms(operation.address, identity.productId, version)
            if (confirmed) {
                publishFirmwareUpdate(operation.copy(status = FirmwareUpdateStatus.SUCCEEDED, error = null))
                _state.update { it.copy(phase = Phase.IDLE, firmware = version) }
            } else {
                publishFirmwareUpdate(operation.copy(status = if (finalAttempt) FirmwareUpdateStatus.FAILED else FirmwareUpdateStatus.AWAITING_CASE,
                    error = "The headphones still report $version. Finish the case installation before checking again."))
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            publishFirmwareUpdate(operation.copy(status = if (finalAttempt) FirmwareUpdateStatus.INTERRUPTED else FirmwareUpdateStatus.AWAITING_CASE,
                error = e.message ?: "Could not read the installed firmware"))
        } finally { conn.close() }
        if (!confirmed && finalAttempt) _state.update { it.copy(phase = Phase.FAILED) }
        if (confirmed) connect(operation.address)
        return confirmed
    }

    private fun publishFirmwareUpdate(value: JabraFirmwareUpdateState): Boolean {
        val json = org.json.JSONObject().put("productId", value.productId).put("targetVersion", value.targetVersion)
            .put("address", value.address).put("status", value.status.name).put("error", value.error)
        val saved = runCatching {
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            val stream = firmwareJournal.startWrite()
            try {
                stream.write(bytes)
                stream.fd.sync()
                firmwareJournal.finishWrite(stream)
            } catch (e: Throwable) { firmwareJournal.failWrite(stream); throw e }
            check(firmwareJournal.readFully().contentEquals(bytes)) { "Firmware journal was not committed" }
        }.isSuccess
        // Failure to journal cannot leave controls permanently trapped in an active state. The
        // caller aborts writes; read-only version checking and explicit dismissal remain possible.
        _state.update { it.copy(firmwareUpdate = if (saved) value else value.journalFailure()) }
        return saved
    }

    private fun restoreFirmwareUpdate(): JabraFirmwareUpdateState? = runCatching {
        val json = org.json.JSONObject(firmwareJournal.readFully().toString(Charsets.UTF_8))
        JabraFirmwareUpdateState(json.getInt("productId"), json.getString("targetVersion"), json.getString("address"),
            FirmwareUpdateStatus.valueOf(json.getString("status")), error = json.optString("error").takeIf { it.isNotEmpty() })
            .afterRestart()
    }.getOrNull()

    /** Side effects run once here; the [reduce] lambda may be retried by StateFlow.update and must stay pure. */
    private fun onEvent(e: MdrEvent) {
        val before = _state.value
        when (e) {
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
                scope.launch { sendQuietly(Mdr.getOptimizerResult()) }
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
            is MdrEvent.AudioCodec -> s.copy(codec = e.codec)
            is MdrEvent.UpscalingStatus -> s.copy(dseeActive = e.active)
            is MdrEvent.UpscalingSetting -> s.copy(dseeAuto = e.auto)
            is MdrEvent.NcAsm ->
                s.copy(ncAsm = e.state.copy(ambientLevel = if (e.state.ambientLevel > 0) e.state.ambientLevel else ambientLevel))
            is MdrEvent.EqCaps -> s.copy(eqCaps = e.caps)
            is MdrEvent.Eq -> s.copy(eq = e.state)
            is MdrEvent.SpeakToChat -> s.copy(speakToChat = e.enabled)
            is MdrEvent.SpeakToChatDetails -> s.copy(speakToChatDetail = e.detail)
            is MdrEvent.WearingStatus -> s.copy(pauseWhenRemovedAvailable = e.available)
            is MdrEvent.PauseWhenRemoved -> s.copy(pauseWhenRemoved = e.on)
            is MdrEvent.AutoPowerOffOptions -> s.copy(autoPowerOffOptions = e.ids)
            is MdrEvent.AutoPowerOff -> s.copy(autoPowerOff = e.activeId to e.selectTimeId)
            is MdrEvent.CustomButton -> s.copy(customButton = e.presets.firstOrNull())
            is MdrEvent.SoundQualityMode -> s.copy(soundQualityPreferConnection = e.preferConnection)
            is MdrEvent.GsCapability -> s.copy(generalSettings = s.generalSettings + (e.setting.id to e.setting))
            is MdrEvent.GsValue -> s.copy(generalValues = s.generalValues + (e.id to e.on))
            is MdrEvent.Alert -> s
            is MdrEvent.GsStatus -> s.copy(generalEnabled = s.generalEnabled + (e.id to e.enabled))
            is MdrEvent.OptimizerState -> s.copy(optimizerRunning = e.running)
            is MdrEvent.Optimizer -> s.copy(optimizerResult = e.result)
            is MdrEvent.VoiceGuidance -> s.copy(voiceGuidance = e.on)
            is MdrEvent.SupportedFunctions -> s.copy(functions = e.codes)
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
            is MdrEvent.UpdateParam -> s.copy(updateInfo = s.updateInfo + (e.type to e.value))
            is MdrEvent.ProtocolInfo -> s.copy(protocolVersion = e.version)
            is MdrEvent.Unknown -> s
        }

    // ---- user actions ----

    private fun pushNcAsm(next: NcAsmState, dragging: Boolean = false) {
        val owner = connection ?: return
        if (!sonyReady()) return
        _state.update { it.copy(ncAsm = next) }
        ncAsmOutbox.trySend(Triple(owner, next, dragging))
    }

    fun setAmbientMode(mode: AmbientMode) {
        if (mode !in _state.value.capabilities.noiseModes) return
        if (_state.value.isJabra) { jabraAction { setMode(mode) }; return }
        val cur = _state.value.ncAsm ?: NcAsmState(AmbientMode.OFF, ambientLevel, false)
        pushNcAsm(cur.copy(mode = mode, ambientLevel = ambientLevel))
    }

    fun setAmbientLevel(level: Int, dragging: Boolean) {
        val caps = _state.value.capabilities
        if (!caps.ambientLevels) return
        if (_state.value.isJabra) {
            jabraAction { setAmbientLevel(level.coerceIn(caps.ambientMin, caps.ambientMax)) }
            return
        }
        val cur = _state.value.ncAsm ?: return
        ambientLevel = level.coerceAtLeast(1)
        pushNcAsm(cur.copy(mode = AmbientMode.AMBIENT, ambientLevel = ambientLevel), dragging)
    }

    /** Mode, ambient level and voice focus in one command (automation profiles). */
    fun setNoiseControl(mode: AmbientMode, level: Int? = null, voiceFocus: Boolean? = null) {
        if (mode !in _state.value.capabilities.noiseModes) return
        if (_state.value.isJabra) {
            val caps = _state.value.capabilities
            jabraAction {
                setMode(mode)
                if (level != null && caps.ambientLevels) setAmbientLevel(level.coerceIn(caps.ambientMin, caps.ambientMax))
            }
            return
        }
        val cur = _state.value.ncAsm ?: NcAsmState(AmbientMode.OFF, ambientLevel, false)
        level?.let { ambientLevel = it.coerceIn(1, 20) }
        pushNcAsm(cur.copy(mode = mode, ambientLevel = ambientLevel, voiceFocus = voiceFocus ?: cur.voiceFocus))
    }

    fun setVoiceFocus(on: Boolean) {
        if (!_state.value.capabilities.voiceFocus) return
        val cur = _state.value.ncAsm ?: return
        pushNcAsm(cur.copy(voiceFocus = on))
    }

    fun selectEqPreset(id: Int, namespace: String = "sony-mdr") {
        val owner = connection ?: return
        if (!sonyReady() || namespace != "sony-mdr") return
        val caps = _state.value.eqCaps ?: return
        if (id !in caps.presets.map { it.id } && id != 0) return
        _state.update { it.copy(eq = it.eq?.copy(presetId = id) ?: EqState(id, emptyList())) }
        eqOutbox.trySend(owner to Mdr.setEqPreset(id))
        // The headset answers with an EQ notify carrying that preset's band levels.
    }

    /**
     * Edits one band. Like Sound Connect, a change lands in the active preset when that is a writable slot
     * (Manual or Custom 1–5); on a factory preset it becomes Manual, starting from that preset's levels.
     */
    fun setEqBand(index: Int, level: Int) {
        val eq = _state.value.eq ?: return
        val caps = _state.value.eqCaps ?: return
        if (index !in eq.bands.indices) return
        val spec = caps.bandSpecs.getOrNull(index) ?: return
        if (_state.value.isJabra) {
            jabraAction { setEqBand(index, level.coerceIn(spec.min, spec.max)) }
            return
        }
        val bands = eq.bands.toMutableList().also { it[index] = level.coerceIn(spec.min, spec.max) }
        applyEqBands(bands, caps.layoutId)
    }

    /** Writes every band at once into the active writable slot (AutoEq results, imported curves). */
    fun applyEqBands(levels: List<Int>, layoutId: String? = null): Boolean {
        val st = _state.value
        val caps = st.eqCaps ?: return false
        val eq = st.eq ?: return false
        if (st.phase != Phase.CONNECTED || !caps.supports(levels)) return false
        if (layoutId != caps.layoutId && !(layoutId == null && st.isSony)) return false
        if (st.isJabra) {
            if (jabra == null) return false
            jabraAction { setEqBands(levels.toList(), enabled = true) }
            return true
        }
        return writeEqPreset(editTarget(eq.presetId), levels, caps.layoutId)
    }

    /** Selects [presetId] and stores [levels] in it; only Manual and Custom 1–5 keep what is written. */
    fun writeEqPreset(presetId: Int, levels: List<Int>, layoutId: String? = null): Boolean {
        val owner = connection ?: return false
        val caps = _state.value.eqCaps ?: return false
        if (!sonyReady() || presetId !in WRITABLE_PRESETS || !caps.supports(levels)) return false
        if (layoutId != null && layoutId != caps.layoutId) return false
        val bands = levels.toList()
        _state.update { it.copy(eq = EqState(presetId, bands)) }
        eqOutbox.trySend(owner to Mdr.setEqBands(presetId, bands))
        return true
    }

    fun restoreEq(eq: EqState, layoutId: String, address: String? = null): Boolean {
        val st = _state.value
        if (address != null && address != st.address) return false
        val caps = st.eqCaps ?: return false
        if (layoutId != caps.layoutId || st.phase != Phase.CONNECTED) return false
        if (st.isJabra) {
            if (!caps.supports(eq.bands) || jabra == null) return false
            jabraAction { setEqBands(eq.bands, enabled = eq.enabled) }
            return true
        }
        if (eq.presetId in WRITABLE_PRESETS) return writeEqPreset(eq.presetId, eq.bands, layoutId)
        if (eq.presetId !in caps.presets.map { it.id } && eq.presetId != 0) return false
        selectEqPreset(eq.presetId)
        return true
    }

    fun setEqEnabled(enabled: Boolean) {
        if (_state.value.isJabra && _state.value.eqCaps?.supportsEnable == true) jabraAction { setEqEnabled(enabled) }
        else if (_state.value.isSony) {
            if (!enabled) selectEqPreset(0)
            else selectEqPreset(CUSTOM_PRESET)
        }
    }

    private fun editTarget(presetId: Int) = if (presetId in WRITABLE_PRESETS) presetId else CUSTOM_PRESET

    /** Sound Connect's Reset: every band of the active writable slot back to 0. */
    fun setEqFlat() {
        val caps = _state.value.eqCaps ?: return
        applyEqBands(caps.bandSpecs.map { it.neutral }, caps.layoutId)
    }

    private fun sonyReady() = _state.value.isSony && _state.value.phase == Phase.CONNECTED && connection != null

    fun setAncLevel(level: Int) {
        val max = _state.value.capabilities.ancMax
        if (max > 0 && _state.value.isJabra) jabraAction { setAncLevel(level.coerceIn(0, max)) }
    }

    fun setSleepMinutes(minutes: Int) {
        if (_state.value.sleepMinutes != null && minutes in listOf(0, 15, 30, 60, 120))
            jabraAction { setSleepMinutes(minutes) }
    }

    fun setAnnouncementMode(mode: Int) {
        if (_state.value.announcementMode != null && mode in 0..2) jabraAction { setAnnouncementMode(mode) }
    }

    fun setDsee(auto: Boolean) {
        if (!sonyReady()) return
        _state.update { it.copy(dseeAuto = auto) }
        fire(Mdr.setUpscaling(auto))
    }

    fun setSpeakToChat(on: Boolean) {
        if (!sonyReady()) return
        _state.update { it.copy(speakToChat = on) }
        fire(Mdr.setSpeakToChat(on))
    }

    private fun fire(payload: ByteArray, table2: Boolean = false) {
        val owner = connection ?: return
        if (!sonyReady()) return
        scope.launch { if (backend === owner) sendQuietly(payload, table2) }
    }

    fun setSpeakToChatDetail(d: SpeakToChatDetail) {
        if (!sonyReady()) return
        _state.update { it.copy(speakToChatDetail = d) }
        fire(Mdr.setSpeakToChatDetail(d))
    }

    fun setPauseWhenRemoved(on: Boolean) {
        if (_state.value.isJabra) {
            if (_state.value.pauseWhenRemoved != null) jabraAction { setPauseWhenRemoved(on) }
            return
        }
        if (!sonyReady()) return
        _state.update { it.copy(pauseWhenRemoved = on) }
        fire(Mdr.setPauseWhenRemoved(on))
    }

    /** Sony keeps the last chosen timer as "selectTime" even while a non-timer option is active. */
    fun setAutoPowerOff(id: Int) {
        if (!sonyReady()) return
        val prevSelect = _state.value.autoPowerOff?.second ?: id
        val select = if (id < 0x10) id else prevSelect
        _state.update { it.copy(autoPowerOff = id to select) }
        fire(Mdr.setAutoPowerOff(id, select))
    }

    /** Alert types the user already confirmed in our own dialog; the headset's prompt is answered for them. */
    private val approvedAlerts = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    fun answerAlert(type: Int, positive: Boolean) {
        if (!sonyReady()) return
        val owner = connection ?: return
        _state.update { it.copy(alert = null) }
        if (positive && type in RECONNECTING_ALERTS) expectReconnect(ALERT_REASON[type] ?: "Applying the change")
        fire(Mdr.answerAlert(type, positive))
        // A declined change leaves the headset as it was; re-read so the UI drops its optimistic value.
        if (!positive) scope.launch {
            sendQuietly(Mdr.getCustomButton(), owner = owner)
            sendQuietly(Mdr.getSoundQualityMode(), owner = owner)
            if (backend === owner) multipointId()?.let { sendQuietly(Mdr.getGs(it), owner = owner) }
        }
    }

    fun setCustomButton(preset: Int) {
        if (!sonyReady()) return
        _state.update { it.copy(customButton = preset) }
        approvedAlerts += AlertType.KEY_ASSIGN_CHANGE
        fire(Mdr.setCustomButton(preset))
    }

    fun setSoundQualityMode(preferConnection: Boolean) {
        if (!sonyReady()) return
        _state.update { it.copy(soundQualityPreferConnection = preferConnection) }
        approvedAlerts += AlertType.CONNECTION_MODE_CHANGE
        fire(Mdr.setSoundQualityMode(preferConnection))
    }

    fun setGeneralSetting(id: Int, on: Boolean) {
        if (!sonyReady()) return
        if (id == multipointId()) approvedAlerts += if (on) AlertType.MULTIPOINT_ON else AlertType.MULTIPOINT_OFF
        _state.update { it.copy(generalValues = it.generalValues + (id to on)) }
        fire(Mdr.setGs(id, on))
    }

    fun multipointId(): Int? =
        _state.value.generalSettings.values.firstOrNull { it.title == "MULTIPOINT_SETTING" }?.id

    fun startOptimizer() {
        if (!sonyReady()) return
        _state.update { it.copy(optimizerRunning = true) }
        fire(Mdr.startOptimizer())
    }

    fun cancelOptimizer() {
        if (!sonyReady()) return
        _state.update { it.copy(optimizerRunning = false) }
        fire(Mdr.cancelOptimizer())
    }

    // ---- playback controller ----

    fun setVolume(v: Int) {
        if (_state.value.isJabra) {
            if (_state.value.phase != Phase.CONNECTED || !isLocalAudioRoute()) return
            val stream = android.media.AudioManager.STREAM_MUSIC
            audio.setStreamVolume(stream, v.coerceIn(audio.getStreamMinVolume(stream), audio.getStreamMaxVolume(stream)), 0)
            refreshLocalPlayback()
            return
        }
        if (!sonyReady()) return
        val c = v.coerceIn(0, _state.value.volumeSteps - 1)
        _state.update { it.copy(volume = c) }
        fire(Mdr.setVolume(c))
    }
    fun playPause() {
        if (_state.value.isJabra) { localMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); return }
        if (!sonyReady()) return
        val playing = _state.value.playing == true
        _state.update { it.copy(playing = !playing) }
        fire(Mdr.playControl(if (playing) 0x01 else 0x07))
    }
    fun nextTrack() {
        if (_state.value.isJabra) localMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) else fire(Mdr.playControl(0x02))
    }
    fun previousTrack() {
        if (_state.value.isJabra) localMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) else fire(Mdr.playControl(0x03))
    }

    // ---- multipoint / pairing (table 2 peripheral) ----

    fun refreshPeers() = fire(Mdr.getPeers(), table2 = true)
    /**
     * The XM4 only enters pairing mode while multipoint is on; otherwise it ignores the command. So the state
     * follows the headset's notify (re-read shortly after) instead of being set optimistically.
     */
    fun setPairingMode(on: Boolean) {
        if (!sonyReady()) return
        val owner = connection ?: return
        scope.launch {
            if (backend !== owner) return@launch
            sendQuietly(Mdr.setPairingMode(on), table2 = true)
            delay(1_500)
            if (backend !== owner) return@launch
            sendQuietly(Mdr.getPeerStatus(), table2 = true)
        }
    }
    fun connectPeer(address: String) = peer(1, address)
    fun disconnectPeer(address: String) = peer(0, address)
    fun unpairPeer(address: String) = peer(2, address)
    fun clearPeerResult() = _state.update { it.copy(peerResult = null) }

    private fun peer(action: Int, address: String) {
        if (!sonyReady()) return
        val owner = connection ?: return
        // Dropping the phone's own link is a reconnect, not a failure.
        if (action != 1 && address.equals(_state.value.address, ignoreCase = true)) expectReconnectUntil = System.currentTimeMillis() + 15_000
        fire(Mdr.peerAction(action, address), table2 = true)
        scope.launch { delay(1_500); if (backend === owner) sendQuietly(Mdr.getPeers(), table2 = true) }
    }

    fun setVoiceGuidance(on: Boolean) {
        if (!sonyReady()) return
        _state.update { it.copy(voiceGuidance = on) }
        fire(Mdr.setVoiceGuidance(on), table2 = true)
    }

    private var playbackJob: Job? = null

    /** Re-reads track, album, artist and play state; bursts of notifies collapse into one read. */
    private var emptyTitleRetries = 0

    private fun refreshPlayback(delayMs: Long = 150) {
        val owner = connection ?: return
        playbackJob?.cancel()
        playbackJob = scope.launch {
            delay(delayMs)
            if (backend !== owner) return@launch
            for (t in listOf(0x00, 0x02, 0x01)) sendQuietly(Mdr.getPlayParam(t), owner = owner)
            // The playing source may have moved to another multipoint device.
            if (_state.value.peers != null) sendQuietly(Mdr.getPeers(), table2 = true, owner = owner)
        }
    }

    fun refreshBattery() {
        if (_state.value.isJabra) jabraAction { refreshBattery() }
        else fire(Mdr.getBattery())
    }

    fun powerOff() { if (_state.value.capabilities.powerOff) fire(Mdr.powerOff()) }

    companion object {
        @Volatile private var instance: HeadphonesController? = null

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
        private val RECONNECTING_ALERTS = setOf(
            AlertType.CONNECTION_MODE_CHANGE, AlertType.KEY_ASSIGN_CHANGE, AlertType.MULTIPOINT_ON, AlertType.MULTIPOINT_OFF,
        )
        private val ALERT_REASON = mapOf(
            AlertType.CONNECTION_MODE_CHANGE to "Applying Sound quality mode",
            AlertType.KEY_ASSIGN_CHANGE to "Applying CUSTOM button",
            AlertType.MULTIPOINT_ON to "Turning on Connect to 2 devices",
            AlertType.MULTIPOINT_OFF to "Turning off Connect to 2 devices",
        )
    }
}
