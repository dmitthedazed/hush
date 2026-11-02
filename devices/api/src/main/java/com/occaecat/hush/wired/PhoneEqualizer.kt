package com.occaecat.hush.wired

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.occaecat.hush.autoeq.AutoEq
import com.occaecat.hush.devices.AutoEqTarget
import com.occaecat.hush.devices.Equalizer
import com.occaecat.hush.devices.Preset
import com.occaecat.hush.devices.PresetGroup
import com.occaecat.hush.devices.QuickControls
import com.occaecat.hush.devices.QuickMode
import com.occaecat.hush.devices.QuickToggle
import com.occaecat.hush.devices.Source
import com.occaecat.hush.ui.EqUi
import com.occaecat.hush.ui.ItemWidth
import com.occaecat.hush.ui.SegmentedGroup
import com.occaecat.hush.ui.Space
import com.occaecat.hush.ui.SwitchRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.ln

/**
 * One device's phone-side equalizer ([PhoneEq]) as the shared Sound page sees it: its own saved settings, applied
 * while [active] (the device is plugged in), with presets, AutoEq taking the curve as is, hold-to-compare, a widget
 * switch and the "Apply AutoEq correction" row. Wired EarPods and passive wired headphones share it.
 */
class PhoneEqualizer(
    context: Context,
    /** Owner of the effect while it runs: the device key. */
    private val key: String,
    /** Notification title while it runs ("EarPods", "MDR-MV1"). */
    private val name: String,
    /** Preferences file for the settings. */
    private val file: String,
    private val active: StateFlow<Boolean>,
    /** AutoEq's search for this model ("Apple EarPods", "Sony MDR-MV1"). */
    private val autoEqName: String,
) : Equalizer {
    override val providedBy get() = Source.PHONE
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _settings = MutableStateFlow(PhoneEq.load(app, file))
    val settings: StateFlow<PhoneEq.Settings> = _settings.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        active.onEach { sync() }.launchIn(scope)
    }

    private fun sync() {
        _error.value = PhoneEq.apply(key, name, _settings.value, active.value)
        PhoneEqService.sync(app)
    }

    private fun edit(change: (PhoneEq.Settings) -> PhoneEq.Settings) {
        _settings.update(change)
        PhoneEq.save(app, file, _settings.value)
        sync()
    }

    fun setEnabled(on: Boolean) = edit { it.copy(enabled = on) }
    fun setGains(gains: List<Int>) = edit { it.copy(gains = gains) }
    fun setCorrectionOn(on: Boolean) = edit { it.copy(correctionOn = on) }
    fun setCorrection(entry: AutoEq.Entry, curve: List<AutoEq.Point>) =
        edit { it.copy(correction = PhoneEq.Correction(entry.name, entry.detail, curve), correctionOn = true) }

    /** "EQ Bass boost" for subtitles and chips; null while off. */
    val label: String? get() = _settings.value.takeIf { it.enabled }?.let { "EQ ${it.presetName}" }

    // --- Equalizer -------------------------------------------------------------------------------------------------

    override val state: StateFlow<EqUi?> = settings.map { eq ->
        EqUi(
            preset = if (!eq.enabled) "Off"
                else listOfNotNull(eq.presetName, eq.correction?.takeIf { eq.correctionOn }?.let { "AutoEq" }).joinToString(" + "),
            levels = eq.gains,
            labels = PhoneEq.labels,
            range = -PhoneEq.MAX_DB..PhoneEq.MAX_DB,
            enabled = eq.enabled,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val presets: StateFlow<List<PresetGroup>> = settings.map { s ->
        listOf(PresetGroup("Presets", PhoneEq.presets.map { Preset(it.name, it.name, s.gains == it.gains) }))
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val layout = "phone-10"
    override fun applyLevels(levels: List<Int>) = setGains(levels)
    override fun setBand(index: Int, level: Int) = edit { s ->
        s.copy(gains = s.gains.toMutableList().also { it[index] = level.coerceIn(-PhoneEq.MAX_DB, PhoneEq.MAX_DB) })
    }
    override fun selectPreset(id: String) { PhoneEq.presets.firstOrNull { it.name == id }?.let { setGains(it.gains) } }
    override fun reset() = setGains(List(PhoneEq.bands.size) { 0 })
    override val toggle: (Boolean) -> Unit = ::setEnabled

    /** Hold-to-compare: bypasses the effect without touching the saved settings. */
    override fun compare(pressed: Boolean) {
        val s = _settings.value
        PhoneEq.apply(key, name, s.copy(enabled = !pressed && s.enabled), active.value)
    }

    @Composable
    override fun HeroExtra() {
        val s by settings.collectAsState()
        val e by error.collectAsState()
        e?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s))
        }
        Spacer(Modifier.height(Space.m))
        ResponseCurve(s, Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Space.l))
    }

    override val autoEqApplied: StateFlow<String?> = settings.map { s ->
        s.correction?.takeIf { s.correctionOn }?.let { "${it.name} · ${it.detail}" }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** A phone-side EQ takes the curve as it is, so the "fit" is exact. */
    override val autoEq = object : AutoEqTarget {
        override val label = "the phone's equalizer, under your bands"
        override val exactName = autoEqName

        override fun fit(curve: List<AutoEq.Point>): AutoEq.Fit {
            val pts = curve.filter { it.hz in 20.0..16_000.0 }
            val mean = pts.map { it.db }.average()
            val shape = pts.map { AutoEq.Point(it.hz, it.db - mean) }
            return AutoEq.Fit(emptyList(), shape, shape, 0.0)
        }

        override fun apply(entry: AutoEq.Entry, curve: List<AutoEq.Point>, fit: AutoEq.Fit) = setCorrection(entry, curve)
    }

    // --- Shared pieces for the device's controller -----------------------------------------------------------------

    /** Sound page: the AutoEq correction's switch, once there is one. */
    fun soundSections(scope: LazyStaggeredGridScope) {
        scope.item {
            val s by settings.collectAsState()
            if (s.correction != null) SegmentedGroup(ItemWidth.padding(top = Space.s)) {
                SwitchRow(0, 1, Icons.Outlined.GraphicEq, "Apply AutoEq correction",
                    s.correction?.let { "${it.name} · ${it.detail}" }, s.correctionOn,
                    enabled = s.enabled, onCheckedChange = ::setCorrectionOn)
            }
        }
    }

    /** Widgets: the equalizer as a switch. */
    val quick = object : QuickControls {
        override val modes = MutableStateFlow(emptyList<QuickMode>())
        override fun setMode(id: String) {}
        override fun cycleMode() = setEnabled(!_settings.value.enabled)
        override val toggles = settings.map { s ->
            listOf(QuickToggle("eq", "Equalizer · ${s.presetName}", s.enabled, true))
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
        override fun toggle(id: String) = setEnabled(!_settings.value.enabled)
    }
}

/** What the phone will actually do: both layers, drawn over 20 Hz–20 kHz on a ±15 dB scale. */
@Composable
fun ResponseCurve(eq: PhoneEq.Settings, modifier: Modifier) {
    val line = if (eq.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val axis = MaterialTheme.colorScheme.outlineVariant
    val points = remember(eq) { PhoneEq.response(eq) }
    Canvas(modifier) {
        fun x(hz: Double) = (ln(hz / 20.0) / ln(1000.0)).toFloat() * size.width
        fun y(db: Double) = size.height / 2 - (db.coerceIn(-15.0, 15.0) / 15.0).toFloat() * size.height / 2
        drawLine(axis, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.hz), y(p.db)) else path.lineTo(x(p.hz), y(p.db)) }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
    }
}
