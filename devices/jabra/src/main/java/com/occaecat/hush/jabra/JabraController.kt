package com.occaecat.hush.jabra

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.occaecat.hush.devices.*
import com.occaecat.hush.gnp.*
import com.occaecat.hush.ui.BatteryGlyphs
import com.occaecat.hush.wired.RemoteRender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Maps one verified Jabra GNP session to Hush's shared pages and controls. */
class JabraController(
    private val app: Context,
    private val device: Device,
    private val bluetooth: BluetoothDevice?,
) : DeviceController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val raw = MutableStateFlow(JabraState())
    private val phase = MutableStateFlow(Connection.DISCONNECTED)
    private val failure = MutableStateFlow<String?>(null)
    private val audio = app.getSystemService(AudioManager::class.java)
    private val phoneVolume = MutableStateFlow(audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            phoneVolume.value = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }
    private var backend: JabraBackend? = null
    private var session: Job? = null

    init { app.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volumeObserver) }

    override val state: StateFlow<DeviceState> = combine(raw, phase, failure, phoneVolume) { s, connection, error, volume ->
        val identity = s.model
        val connected = connection == Connection.CONNECTED
        DeviceState(
            name = identity?.label ?: device.name,
            connection = connection,
            subtitle = if (connected) listOfNotNull("Connected", s.battery?.let { "$it%" }).joinToString(" · ") else null,
            statusText = if (connected) null else error ?: when (connection) {
                Connection.CONNECTING -> "Connecting to Jabra controls…"
                else -> "Not connected"
            },
            banner = if (connected) s.error ?: error else null,
            batteries = if (connected) s.batteries.mapNotNull { part ->
                part.level?.let { level -> BatteryLevel(level, part.charging == true,
                    when (part.id) {
                        "case" -> BatteryPart.CASE
                        "primary" -> when {
                            s.batteries.none { it.id == "secondary" } -> BatteryPart.MAIN
                            s.model == JabraModel.ELITE_85T -> BatteryPart.RIGHT
                            else -> BatteryPart.EARBUD_1
                        }
                        "secondary" -> if (s.model == JabraModel.ELITE_85T) BatteryPart.LEFT else BatteryPart.EARBUD_2
                        else -> BatteryPart.MAIN
                    }) }
            } else emptyList(),
            noiseCancelling = connected && s.noise?.mode == AmbientMode.NOISE_CANCELLING,
            volume = volume.takeIf { connected },
            volumeSteps = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            cardFacts = listOfNotNull(s.firmware?.let { "Firmware $it" }, identity?.productId?.let { "Product $it" }),
            details = if (connected) details(s) else emptyList(),
            disconnectedHelp = error ?: "Wake the earbuds, connect them in Bluetooth settings, and close Jabra Sound+.",
            model = identity?.label ?: device.name,
        )
    }.stateIn(scope, SharingStarted.Eagerly, DeviceState(device.name, Connection.DISCONNECTED, null))

    private fun details(s: JabraState): List<DetailGroup> = buildList {
        add(DetailGroup("Identity", listOfNotNull(
            s.model?.let { Detail("Model", it.label, Icons.Outlined.Headphones) },
            s.model?.let { Detail("Product ID", it.productId.toString(), Icons.Outlined.Info) },
            s.firmware?.let { Detail("Firmware", it, Icons.Outlined.Info) },
            s.serial?.let { Detail("Serial", it, Icons.Outlined.Info, copy = true, hidden = true) },
            device.address?.let { Detail("Bluetooth address", it, Icons.Outlined.Bluetooth, copy = true, hidden = true) },
        )))
        if (s.batteries.isNotEmpty()) add(DetailGroup("Battery", s.batteries.map { Detail(it.label, it.level?.let { v -> "$v%" } ?: "Unavailable") }))
        add(DetailGroup("Device settings", listOfNotNull(
            s.noise?.let { Detail("Noise mode", modeLabel(it.mode)) },
            s.noise?.takeIf { it.mode == AmbientMode.AMBIENT && s.noiseCapabilities.ambientMax > 0 }
                ?.let { Detail("HearThrough strength", "${it.ambientLevel} / ${s.noiseCapabilities.ambientMax}") },
            s.ancLevel?.let { Detail("ANC strength", "$it / ${s.noiseCapabilities.ancMax}") },
            s.eq?.let { Detail("Equalizer", if (it.enabled) "On" else "Off") },
            s.pauseWhenRemoved?.let { Detail("Automatic pause", if (it) "On" else "Off") },
            s.sleepMinutes?.let { Detail("Sleep timeout", if (it == 0) "Off" else "$it minutes") },
            s.announcementMode?.let { Detail("Announcements", announcementLabel(it)) },
            s.leftOnHead?.let { Detail("Left earbud on-head", if (it) "Worn" else "Removed") },
            s.rightOnHead?.let { Detail("Right earbud on-head", if (it) "Worn" else "Removed") },
        )))
        s.buttonsState?.takeIf { it.slots.isNotEmpty() }?.let { buttons ->
            add(DetailGroup("Button assignments", buttons.slots.map { assignment ->
                Detail("${assignment.slot.context.label} · ${assignment.slot.label}", assignment.actionLabel)
            }))
        }
    }

    private fun modeLabel(mode: AmbientMode) = when (mode) {
        AmbientMode.NOISE_CANCELLING -> "ANC"
        AmbientMode.AMBIENT -> "HearThrough"
        AmbientMode.OFF -> "Off"
    }

    private fun announcementLabel(value: Int) = when (value) { 0 -> "Tones"; 1 -> "Voice"; else -> "Off" }

    override val noise: NoiseControl = object : NoiseControl {
        override val modes: List<NoiseMode> get() {
            val available = raw.value.noiseCapabilities.modes.ifEmpty { AmbientMode.entries.toSet() }
            return listOf(
                NoiseMode(AmbientMode.NOISE_CANCELLING.name, "ANC", NoiseKind.CANCELLING, R.drawable.ic_mode_anc, title = "Noise cancelling",
                    hasOptions = raw.value.noiseCapabilities.ancMax > 0,
                    levelRange = raw.value.noiseCapabilities.ancMax.takeIf { it > 0 }?.let { 0..it }),
                NoiseMode(AmbientMode.AMBIENT.name, "HearThrough", NoiseKind.AMBIENT, R.drawable.ic_mode_hearthrough,
                    hasOptions = raw.value.noiseCapabilities.ambientMax > 0,
                    levelRange = raw.value.noiseCapabilities.ambientMax.takeIf { it > 0 }?.let { 0..it }),
                NoiseMode(AmbientMode.OFF.name, "Off", NoiseKind.OFF, R.drawable.ic_mode_off),
            ).filter { AmbientMode.valueOf(it.id) in available }
        }
        override val state: StateFlow<NoiseUi?> = combine(raw, phase) { s, c ->
            if (c != Connection.CONNECTED) null else s.noise?.let { n ->
                val max = if (n.mode == AmbientMode.AMBIENT) s.noiseCapabilities.ambientMax else s.noiseCapabilities.ancMax
                val level = if (n.mode == AmbientMode.AMBIENT) n.ambientLevel else s.ancLevel
                NoiseUi(n.mode.name, if (max > 0 && level != null) NoiseLevel(
                    if (n.mode == AmbientMode.AMBIENT) "HearThrough strength" else "ANC strength", level.coerceIn(0, max), 0..max) else null)
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)
        override fun setMode(id: String) {
            val mode = runCatching { AmbientMode.valueOf(id) }.getOrNull() ?: return
            if (mode !in raw.value.noiseCapabilities.modes) return
            command { it.setMode(mode) }
        }
        override fun setLevel(level: Int, dragging: Boolean) {
            if (dragging) return
            when (raw.value.noise?.mode) {
                AmbientMode.AMBIENT -> if (level in 0..raw.value.noiseCapabilities.ambientMax) command { it.setAmbientLevel(level) }
                AmbientMode.NOISE_CANCELLING -> if (level in 0..raw.value.noiseCapabilities.ancMax) command { it.setAncLevel(level) }
                else -> Unit
            }
        }
    }

    override val quick: QuickControls = object : QuickControls {
        override val modes = noise.quickModes(scope)
        override fun setMode(id: String) = noise.setMode(id)
        override fun cycleMode() {
            val options = noise.modes
            val at = options.indexOfFirst { it.id == noise.state.value?.active }
            options.getOrNull((at + 1) % options.size)?.let { noise.setMode(it.id) }
        }
        override val toggles: StateFlow<List<QuickToggle>> = MutableStateFlow(emptyList())
        override fun toggle(id: String) = Unit
    }

    override val microphone = Microphone(Transport.BLUETOOTH, device.address)
    /**
     * Capability label only. The value, step range, and writes use Android's STREAM_MUSIC on the
     * active route; no Jabra GNP playback-volume command is sent.
     */
    override val volumeSource = Source.PHONE
    private val eqControl = JabraEqualizerControl(raw, scope, ::command)
    override val equalizer: Equalizer = eqControl
    override val firmware: Firmware = JabraFirmwareCheck(app, raw)

    @Composable
    override fun settings(): List<SettingsGroup> {
        val s by raw.collectAsStateWithLifecycle()
        val connection by phase.collectAsStateWithLifecycle()
        val ready = connection == Connection.CONNECTED
        val deviceSettings = listOfNotNull(
            s.pauseWhenRemoved?.let { value -> Setting.Toggle(Icons.Outlined.Hearing, "Automatic pause",
                "Pause when an earbud is removed", value, enabled = ready,
                onChange = { on -> command { it.setPauseWhenRemoved(on) } }) },
            s.sleepMinutes?.let { value -> Setting.Choice(Icons.Outlined.Timer, "Sleep timeout",
                JabraProtocol.sleepMinutes.map { it to if (it == 0) "Off" else "$it minutes" }, value, enabled = ready,
                onSelect = { minutes -> command { it.setSleepMinutes(minutes) } }) },
            s.announcementMode?.let { value -> Setting.Choice(Icons.Outlined.VolumeUp, "Announcements",
                listOf(0 to "Tones", 1 to "Voice", 2 to "Off"), value, enabled = ready,
                onSelect = { choice -> command { it.setAnnouncementMode(choice) } }) },
        )
        val buttonGroups = s.buttonsState?.supportedContexts?.sortedBy { it.id }?.map { context ->
            val assignments = s.buttonsState!!.slots.filter { it.slot.context == context }
            SettingsGroup("${context.label} buttons", assignments.map { assignment ->
                Setting.Choice(Icons.Outlined.TouchApp, assignment.slot.label,
                    assignment.allowedActions.map { it.id to it.label }, assignment.action,
                    enabled = ready && assignment.lockReason == null && !s.buttonsState!!.loading,
                    value = assignment.lockReason ?: assignment.actionLabel,
                    onSelect = { action -> command { it.setJabraButton(assignment.slot, action) } })
            } + listOf(Setting.Action(Icons.Outlined.Refresh, "Reset ${context.label.lowercase()} buttons", null,
                enabled = ready && s.buttonsState!!.canReset(context),
                onClick = { command { it.resetJabraButtons(context) } })), Tab.DEVICE,
                note = s.buttonsState!!.error, noteIsError = s.buttonsState!!.error != null)
        }.orEmpty()
        return listOf(SettingsGroup("Earbuds", deviceSettings)) + buttonGroups
    }

    override val aboutRows: List<AboutRow> get() = listOfNotNull(
        raw.value.firmware?.let { AboutRow(Icons.Outlined.SystemUpdate, "Installed firmware", it, ::openJabraSupport) },
        AboutRow(Icons.Outlined.Info, "Jabra support", "Official help and firmware instructions", ::openJabraSupport),
    )

    private fun openJabraSupport() {
        val url = if (device.modelId == "elite-85t")
            "https://www.jabra.com/supportpages/jabra-elite-85t" else "https://www.jabra.com/supportpages/jabra-elite-10"
        app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Composable
    override fun Render(modifier: Modifier, connected: Boolean) {
        val context = LocalContext.current
        val key = if (device.modelId == "elite-85t") "jabra-elite-85t" else "jabra-elite-10"
        val url = if (device.modelId == "elite-85t") JabraModule.ELITE_85T_ART else JabraModule.ELITE_10_ART
        val bitmap by produceState(RemoteRender.cached(context, key, trim = true), key) {
            if (value == null) value = RemoteRender.load(context, key, url, trim = true)
        }
        val art = bitmap
        if (art != null) Image(
            art.asImageBitmap(), device.name, modifier, contentScale = ContentScale.Fit,
            alpha = if (connected) 1f else 0.5f,
            colorFilter = if (connected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        ) else Box(modifier, contentAlignment = Alignment.Center) {
            Icon(BatteryGlyphs.earbud, "Earbuds", Modifier.size(96.dp),
                tint = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline)
        }
    }

    override fun renderBitmap(context: Context): android.graphics.Bitmap? =
        RemoteRender.cached(context, if (device.modelId == "elite-85t") "jabra-elite-85t" else "jabra-elite-10", trim = true)

    override fun onHeroTap() { command { it.refreshBattery() } }
    override fun setVolume(level: Int) {
        if (phase.value != Connection.CONNECTED) return
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,
            level.coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0)
        phoneVolume.value = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
    override fun reconnect() {
        val target = bluetooth ?: return
        if (phase.value == Connection.CONNECTED || phase.value == Connection.CONNECTING) return
        session?.cancel()
        backend?.close()
        raw.value = JabraState()
        failure.value = null
        phase.value = Connection.CONNECTING
        val hint = when (device.modelId) {
            "elite-85t" -> JabraModel.ELITE_85T
            "elite-10" -> JabraModel.ELITE_10
            else -> null
        }
        val next = JabraBackend(target, scope, hint)
        backend = next
        session = scope.launch {
            val collector = launch { next.state.collect { if (backend === next) raw.value = it } }
            try {
                withContext(Dispatchers.IO) { next.open(); next.initialize() }
                val expected = device.modelId
                val found = next.state.value.model
                if (expected == "elite-10" && found != JabraModel.ELITE_10 ||
                    expected == "elite-85t" && found != JabraModel.ELITE_85T) throw IOException("This pair is not the selected Jabra model")
                if (backend === next) phase.value = Connection.CONNECTED
                next.closed.await()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (backend === next) failure.value = e.message ?: "Could not connect to Jabra controls"
            } finally {
                collector.cancel()
                next.close()
                if (backend === next) {
                    backend = null
                    eqControl.clearPending()
                    phase.value = Connection.DISCONNECTED
                    raw.value = JabraState()
                }
            }
        }
    }

    override fun disconnect() {
        session?.cancel()
        backend?.close()
        backend = null
        eqControl.clearPending()
        phase.value = Connection.DISCONNECTED
        raw.value = JabraState()
    }

    internal fun dispose() {
        disconnect()
        app.contentResolver.unregisterContentObserver(volumeObserver)
        scope.cancel()
    }

    private fun command(action: suspend (JabraBackend) -> Unit) {
        val current = backend?.takeIf { phase.value == Connection.CONNECTED } ?: return
        scope.launch(Dispatchers.IO) {
            try { action(current); failure.value = null } catch (_: CancellationException) { } catch (e: Exception) {
                failure.value = e.message ?: "The setting could not be confirmed"
            }
        }
    }
}
