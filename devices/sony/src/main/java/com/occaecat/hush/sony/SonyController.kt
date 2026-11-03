package com.occaecat.hush.sony

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.autoeq.AutoEq
import com.occaecat.hush.devices.AboutRow
import com.occaecat.hush.devices.AutoEqTarget
import com.occaecat.hush.devices.BatteryLevel
import com.occaecat.hush.devices.BatteryPart
import com.occaecat.hush.devices.BlobRole
import com.occaecat.hush.devices.Connection
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceState
import com.occaecat.hush.devices.Equalizer
import com.occaecat.hush.devices.Firmware
import com.occaecat.hush.devices.HeroChip
import com.occaecat.hush.devices.Preset
import com.occaecat.hush.devices.PresetGroup
import com.occaecat.hush.devices.quickModes
import com.occaecat.hush.ui.sonySettings
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.Charge
import com.occaecat.hush.mdr.Codec
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.sound.SoundStore
import com.occaecat.hush.ui.DeepLink
import com.occaecat.hush.ui.DeviceTrack
import com.occaecat.hush.ui.EqUi
import com.occaecat.hush.ui.HeadsetAlertDialog
import com.occaecat.hush.ui.Labels
import com.occaecat.hush.ui.PermissionScreen
import com.occaecat.hush.ui.SonyCardActions
import com.occaecat.hush.ui.SonyDeviceOverlays
import com.occaecat.hush.ui.SonyDeviceUi
import com.occaecat.hush.ui.SonyFirmware
import com.occaecat.hush.ui.SonySoundOverlays
import com.occaecat.hush.ui.SonySoundUi
import com.occaecat.hush.ui.StatusPane
import com.occaecat.hush.ui.TourHost
import com.occaecat.hush.ui.codecLogo
import com.occaecat.hush.ui.presetsFor
import com.occaecat.hush.ui.rememberArt
import com.occaecat.hush.ui.rememberRender
import com.occaecat.hush.ui.sonyAbout
import com.occaecat.hush.ui.sonyDetails
import com.occaecat.hush.ui.sonyDevice
import com.occaecat.hush.ui.sonyListen
import com.occaecat.hush.ui.sonySound
import com.occaecat.hush.ui.splitPresets
import com.occaecat.hush.ui.subtitleFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * The WH-1000XM4 for the shared UI: [HeadphonesController]'s MDR session mapped onto [DeviceState], the headset's
 * six-band EQ, Sony's firmware check, and everything only Sony headphones have as sections.
 */
class SonyController(
    private val app: Context,
    private val vm: HeadphonesController,
    /** The headset state; the debug demo passes a canned one. */
    source: StateFlow<HeadphonesState> = vm.state,
    private val gated: Boolean = true,
) : DeviceController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The latest headset state as Compose state: the sections read it while the grid builds its items. */
    private val hs = mutableStateOf(source.value)
    private val soundUi = SonySoundUi()
    private val deviceUi = SonyDeviceUi()

    init {
        source.onEach { hs.value = it }.launchIn(scope)
    }

    override val state: StateFlow<DeviceState> = source.map(::deviceState).stateIn(scope, SharingStarted.Eagerly, deviceState(source.value))

    private fun deviceState(s: HeadphonesState): DeviceState {
        val connection = when (s.phase) {
            Phase.CONNECTED -> Connection.CONNECTED
            Phase.RECONNECTING -> Connection.RECONNECTING
            Phase.CONNECTING -> Connection.CONNECTING
            else -> Connection.DISCONNECTED
        }
        val connected = s.phase == Phase.CONNECTED || s.phase == Phase.RECONNECTING
        val codec = s.codec?.takeIf { it != Codec.UNSETTLED }
        val me = runCatching { app.getSystemService(android.bluetooth.BluetoothManager::class.java).adapter.name }.getOrNull()
        val src = s.peers?.firstOrNull { s.peerPlayback > 0 && it.order == s.peerPlayback }
        return DeviceState(
            name = s.displayName,
            connection = connection,
            subtitle = subtitleFor(s),
            statusText = subtitleFor(s) ?: "Not connected",
            banner = if (s.phase == Phase.RECONNECTING || s.audioSettling) s.reconnectReason ?: "Connection lost" else null,
            batteries = batteries(s),
            noiseCancelling = s.ncAsm?.mode == AmbientMode.NOISE_CANCELLING,
            blobShape = when (s.ncAsm?.mode?.takeIf { connected }) {
                AmbientMode.NOISE_CANCELLING -> MaterialShapes.Cookie12Sided
                AmbientMode.AMBIENT -> MaterialShapes.Flower
                AmbientMode.OFF -> MaterialShapes.Circle
                null -> MaterialShapes.SoftBurst
            },
            blobRole = when (s.ncAsm?.mode) {
                AmbientMode.AMBIENT -> BlobRole.TERTIARY
                AmbientMode.OFF -> BlobRole.SECONDARY
                else -> BlobRole.PRIMARY
            },
            track = DeviceTrack(s.track, s.artist, s.album, s.playing, vm::previousTrack, vm::playPause, vm::nextTrack),
            volume = s.volume,
            volumeSteps = s.volumeSteps,
            source = if (src == null || src.name == me) Icons.Outlined.Smartphone to "This phone" else Icons.Outlined.Laptop to src.name,
            cardFacts = listOfNotNull(
                s.codec?.label?.takeIf { connected && it != "—" },
                s.firmware?.let { "v$it" },
            ),
            details = sonyDetails(s),
            disconnectedHelp = s.error?.takeIf { s.protocolV2 }
                ?: ("Make sure ${s.deviceName ?: "the headphones"} are on and connected in Bluetooth settings. " +
                    "Only one app can hold the control channel, so close Sound Connect."),
            model = s.modelName,
            heroChips = if (!connected) emptyList() else listOfNotNull(
                codec?.let { c -> codecLogo(c)?.let { HeroChip(c.label, it, tight = it == R.drawable.logo_codec_ldac) } ?: HeroChip(c.label) },
                // Shown while DSEE is switched on; it brightens while it is actually upscaling. The logo follows UpscalingType.
                (s.upscalingType ?: 0x02).let { type ->
                    val logo = when (type) { 0x00 -> R.drawable.logo_dsee_hx; 0x01 -> R.drawable.logo_dsee; else -> R.drawable.logo_dsee_extreme }
                    HeroChip(Labels.upscaling[type] ?: "DSEE", logo, dimmed = !s.dseeActive)
                }.takeIf { s.dseeAuto == true },
            ),
        )
    }

    /** One battery on over-ear and neckband models; earbuds and their case on true wireless ones (LEFT_RIGHT_BATTERY_LEVEL). */
    private fun batteries(s: HeadphonesState): List<BatteryLevel> {
        fun level(c: Charge?, part: BatteryPart) =
            c?.takeIf { it.level in 0..100 }?.let { BatteryLevel(it.level, it.charging, part) }
        if (!s.trueWireless) return listOfNotNull(s.battery?.let { BatteryLevel(it, s.charging) })
        val (left, right) = s.earbudsConnected ?: (true to true)
        // An earbud reports 0 while it is out of reach or docked, not flat (Gadgetbridge and SonyBridge skip it too).
        return listOfNotNull(
            level(s.leftBattery?.takeIf { left && it.level > 0 }, BatteryPart.LEFT),
            level(s.rightBattery?.takeIf { right && it.level > 0 }, BatteryPart.RIGHT),
            level(s.caseBattery, BatteryPart.CASE),
        )
    }

    @Composable
    override fun Render(modifier: Modifier, connected: Boolean) {
        val s by hs
        Image(
            rememberRender(s), contentDescription = null, modifier = modifier,
            contentScale = ContentScale.Fit,
            alpha = if (connected) 1f else 0.5f,
            colorFilter = if (connected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        )
    }

    @Composable
    override fun partImage(part: BatteryPart): androidx.compose.ui.graphics.painter.Painter? {
        val s by hs
        val art = when (part) { BatteryPart.LEFT -> SonyArt.Part.LEFT; BatteryPart.RIGHT -> SonyArt.Part.RIGHT; BatteryPart.CASE -> SonyArt.Part.CASE; else -> return null }
        return if (s.trueWireless) rememberArt(s, art) else null
    }

    override fun onHeroTap() { vm.refreshBattery() }
    override fun setVolume(level: Int) { vm.setVolume(level) }

    @Composable
    override fun Gate(content: @Composable () -> Unit) {
        if (!gated) return content()
        SonyGate(vm) {
            val s by hs
            TourHost(s)
            content()
        }
    }

    override val noise = SonyNoise(vm, source, scope)

    @Composable
    override fun settings() = sonySettings(hs.value, vm, deviceUi)

    override fun listenSections(scope: LazyStaggeredGridScope) = scope.sonyListen(hs.value, vm)
    override fun soundSections(scope: LazyStaggeredGridScope) = scope.sonySound(hs.value, vm, soundUi)
    override fun deviceSections(scope: LazyStaggeredGridScope) = scope.sonyDevice(hs.value, vm, deviceUi)

    @Composable
    override fun androidx.compose.foundation.layout.RowScope.CardActions() {
        val s by hs
        SonyCardActions(s, vm)
    }

    override val sleepTarget = sonySleepTarget(vm)
    override val insights get() = sonyInsights(hs.value.modelName)
    override val sceneSwitches = object : com.occaecat.hush.devices.SceneSwitches {
        override val list get() = if (hs.value.speakToChat != null) listOf(com.occaecat.hush.devices.SceneSwitch("speakToChat", "Speak-to-Chat")) else emptyList()
        override fun get(id: String) = if (id == "speakToChat") hs.value.speakToChat else null
        override fun set(id: String, on: Boolean) { if (id == "speakToChat") vm.setSpeakToChat(on) }
    }
    override val microphone get() = com.occaecat.hush.devices.Microphone(
        com.occaecat.hush.devices.Transport.BLUETOOTH, hs.value.address,
        "Calls use Bluetooth's call profile, narrower than music. Music pauses while the test records.",
    )

    override val aboutRows: List<AboutRow> get() =
        sonyAbout(deviceUi, hs.value, app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)

    @Composable
    override fun Overlays() {
        val s by hs
        SonySoundOverlays(s, vm, soundUi, onEqChanged = { equalizer.refreshApplied() })
        SonyDeviceOverlays(s, deviceUi)
    }

    override val statusPane: (@Composable (Modifier) -> Unit) = { m ->
        val s by hs
        StatusPane(s, vm, m)
    }

    override fun reconnect() { vm.connectSaved() }
    override fun disconnect() { vm.disconnect() }

    override fun renderBitmap(context: Context): android.graphics.Bitmap? = com.occaecat.hush.ui.renderBitmap(context, hs.value)

    /** The ColorOS-style quick panel. */
    override fun panelIntent(context: Context) =
        android.content.Intent(context, com.occaecat.hush.DeviceCardPanelActivity::class.java)

    override val quick = object : com.occaecat.hush.devices.QuickControls {
        override val modes = noise.quickModes(scope)
        override fun setMode(id: String) = noise.setMode(id)
        override fun cycleMode() = vm.cycleNoiseMode()

        override val toggles = source.map { s ->
            listOfNotNull(
                s.dseeAuto?.let { com.occaecat.hush.devices.QuickToggle("dsee", Labels.upscaling[s.upscalingType ?: 0x02] ?: "DSEE", it, true) },
                s.speakToChat?.let { com.occaecat.hush.devices.QuickToggle("stc", "Speak-to-Chat", it, true) },
            )
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

        override fun toggle(id: String) {
            val s = vm.state.value
            when (id) {
                "dsee" -> s.dseeAuto?.let { vm.setDsee(!it) }
                "stc" -> s.speakToChat?.let { vm.setSpeakToChat(!it) }
            }
        }
    }

    override val openPage: StateFlow<String?> get() = DeepLink.target
    override fun consumeOpenPage() = DeepLink.consume()

    override val firmware: Firmware = SonyFirmware(vm)
    override val equalizer = SonyEqualizer(app, vm, source, scope)
}

/**
 * The headset's EQ: raw levels 0..2·flat shown as −flat..+flat (flat is 10 on six-band models, 6 on the 13-step
 * ten-band ones; [HeadphonesState.eqFlat]), Sony's presets, the writable slots, the library.
 */
class SonyEqualizer(
    private val app: Context,
    private val vm: HeadphonesController,
    source: StateFlow<HeadphonesState>,
    scope: CoroutineScope,
) : Equalizer {
    private val bandLabels6 = Labels.sixBands
    private val flat get() = vm.state.value.eqFlat

    override val state: StateFlow<EqUi?> = source.map { s ->
        val eq = s.eq ?: return@map null
        EqUi(
            preset = presetsFor(s).firstOrNull { it.first == eq.presetId }?.second ?: "Custom",
            levels = eq.bands.map { it - s.eqFlat },
            labels = labels(s, eq.bands.size),
            range = -s.eqFlat..s.eqFlat,
            editable = s.eqCustomizable,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val presets: StateFlow<List<PresetGroup>> = source.map { s ->
        val eq = s.eq ?: return@map emptyList()
        val (sony, yours) = splitPresets(presetsFor(s))
        listOf(
            PresetGroup("Sony presets", sony.map { (id, name) -> Preset("$id", name, eq.presetId == id) }),
            PresetGroup("Your presets", yours.map { (id, name) -> Preset("$id", name, eq.presetId == id) }),
        )
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The headset's own band frequencies when it gives them (Clear Bass is a "specific information" band). */
    private fun labels(s: HeadphonesState, n: Int): List<String> = when {
        s.eqBands.size == n -> s.eqBands.mapIndexed { i, b -> b.hz?.let(Labels::bandLabel) ?: if (i == 0) "Clear Bass" else "${i + 1}" }
        n == bandLabels6.size -> bandLabels6
        else -> List(n) { "${it + 1}" }
    }

    /** Library curves are kept per band count: "sony-6" for the common six bands, "sony-5" etc. otherwise. */
    override val layout get() = vm.state.value.eq?.bands?.size?.takeIf { it > 0 && it != 6 }?.let { "sony-$it" } ?: com.occaecat.hush.sound.SONY_LAYOUT

    /** Library curves go into Manual: the headset only keeps Manual and two Custom slots. */
    override fun applyLevels(levels: List<Int>) { vm.writeEqPreset(HeadphonesController.CUSTOM_PRESET, levels.map { it + flat }); forgetAutoEq() }

    override val slots = listOf(0xA1, 0xA2).map { "$it" to (Labels.presetNames[it] ?: "Custom") }
    override fun saveToSlot(slot: String, levels: List<Int>) { slot.toIntOrNull()?.let { vm.writeEqPreset(it, levels.map { l -> l + flat }) } }
    override val libraryNote = "The headphones only store Manual and two Custom slots; a library curve is written into Manual when you apply it."

    private val applied = MutableStateFlow(AutoEq.lastApplied(app))

    /**
     * The last AutoEq profile only describes the sound while the custom preset it was written to is active, on the
     * headset it was written to (entries from before Hush kept the owner were all written to the XM4).
     */
    override val autoEqApplied: StateFlow<String?> = combine(source, applied) { s, a ->
        a?.takeIf { s.eq?.presetId == it.presetId }
            ?.takeIf { it.owner?.equals(s.address, ignoreCase = true) ?: (s.modelName?.contains("WH-1000XM4") != false) }?.name
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** A hand-tuned band, a reset or a new Custom 2 means the curve is no longer the AutoEq one. */
    private fun forgetAutoEq() {
        if (applied.value != null) { AutoEq.forgetApplied(app); applied.value = null }
    }

    fun refreshApplied() {
        applied.value = AutoEq.lastApplied(app)?.takeUnless { it.presetId == HeadphonesController.USER_PRESET_2 }
        if (applied.value == null) AutoEq.forgetApplied(app)
    }

    override fun setBand(index: Int, level: Int) { vm.setEqBand(index, level + flat); forgetAutoEq() }
    override fun reset() { vm.setEqFlat(); forgetAutoEq() }

    override fun selectPreset(id: String) { id.toIntOrNull()?.let(vm::selectEqPreset) }

    /** The headset's own EQ state: a writable slot also gets its bands back (a library curve lands in Manual). */
    override fun snapshot(): Any? = vm.state.value.eq

    override fun restore(snapshot: Any?) {
        val eq = snapshot as? EqState ?: return
        if (eq == vm.state.value.eq) return
        if (eq.presetId in HeadphonesController.WRITABLE_PRESETS && eq.bands.isNotEmpty()) vm.writeEqPreset(eq.presetId, eq.bands)
        else vm.selectEqPreset(eq.presetId)
    }

    private var before: EqState? = null
    private val current get() = vm.state.value.eq

    /** Hold: preset Off; release: writable slots come back with their levels, factory presets by id. */
    override fun compare(pressed: Boolean) {
        val eq = current ?: return
        if (pressed) {
            if (eq.presetId != 0x00) { before = eq; vm.selectEqPreset(0x00) } // 0x00 is already flat
        } else before?.let { b ->
            before = null
            if (b.presetId in HeadphonesController.WRITABLE_PRESETS && b.bands.isNotEmpty()) vm.writeEqPreset(b.presetId, b.bands)
            else vm.selectEqPreset(b.presetId)
        }
    }

    private fun targetSlot() = current?.presetId?.takeIf { it in HeadphonesController.WRITABLE_PRESETS } ?: HeadphonesController.CUSTOM_PRESET

    override val autoEq = object : AutoEqTarget {
        override val label get() = Labels.presetNames[targetSlot()] ?: "Manual"
        override val bandLabels get() = listOf("Bass") + bandLabels6.drop(1)
        override val note = "The headset has six fixed bands, so narrow peaks and dips can't be corrected."

        override fun fit(curve: List<AutoEq.Point>) =
            AutoEq.fit(curve, current?.bands?.size ?: 0)?.let { it.copy(levels = it.levels.map { l -> l - 10 }) }

        override fun apply(entry: AutoEq.Entry, curve: List<AutoEq.Point>, fit: AutoEq.Fit) {
            val slot = targetSlot()
            vm.applyEqBands(fit.levels.map { it + 10 })
            AutoEq.rememberApplied(app, entry, slot, source.value.address)
            applied.value = AutoEq.lastApplied(app)
        }

        override val saveToLibrary: (AutoEq.Entry, AutoEq.Fit) -> Unit = { entry, fit ->
            com.occaecat.hush.devices.EqLibrary.save(entry.name, layout, fit.levels, "AutoEq · ${entry.detail}")
        }
    }
}

/** Bluetooth permission before the shared UI; the headset's own prompts on top. */
@Composable
private fun SonyGate(vm: HeadphonesController, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by androidx.compose.runtime.remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    if (!granted) {
        PermissionScreen(onGrant = { launcher.launch(android.Manifest.permission.BLUETOOTH_CONNECT) })
        return
    }
    SonyStartup(vm)
    val state by vm.state.collectAsState()
    state.alert?.let { (type, actionType) -> HeadsetAlertDialog(type, actionType, vm::answerAlert) }
    // Which headset is the shell's choice now (DeviceRegistry); a disconnected one shows the shared UI, not a picker.
    content()
}

/** The headset's own volume, its playback control and power. */
fun sonySleepTarget(vm: HeadphonesController) = object : com.occaecat.hush.features.SleepTarget {
    override val volume get() = vm.state.value.volume
    override fun setVolume(v: Int) = vm.setVolume(v)
    override fun pause() { if (vm.state.value.playing == true) vm.playPause() }
    override val titles = vm.state.map { it.track }
    override val canPowerOff get() = vm.state.value.phase == Phase.CONNECTED
    override fun powerOff() { vm.powerOff() }
}
