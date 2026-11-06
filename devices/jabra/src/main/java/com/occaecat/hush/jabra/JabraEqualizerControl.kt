package com.occaecat.hush.jabra

import com.occaecat.hush.autoeq.AutoEq
import com.occaecat.hush.devices.AutoEqTarget
import com.occaecat.hush.devices.EqLibrary
import com.occaecat.hush.devices.Equalizer
import com.occaecat.hush.devices.PresetGroup
import com.occaecat.hush.gnp.JabraState
import com.occaecat.hush.ui.EqUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.ln
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Device-reported graphic bands. GNP stores gain in units of 1/60 dB. */
internal class JabraEqualizerControl(
    private val raw: StateFlow<JabraState>,
    scope: CoroutineScope,
    private val command: (suspend (JabraBackend) -> Unit) -> Unit,
) : Equalizer {
    override val layout: String get() {
        val caps = raw.value.eqCaps ?: return "jabra-unavailable"
        val model = raw.value.model?.productId ?: 0
        return "jabra-$model-${caps.bandSpecs.joinToString("-") { it.frequencyHz.roundToInt().toString() }}"
    }

    override val state: StateFlow<EqUi?> = raw.map { s ->
        val eq = s.eq ?: return@map null
        val specs = s.eqCaps?.bandSpecs ?: return@map null
        if (eq.levels.size != specs.size) return@map null
        val range = (specs.maxOfOrNull { it.max } ?: 0) / 60
        val ranges = specs.map { ceil(it.min / 60.0).toInt()..floor(it.max / 60.0).toInt() }
        EqUi(if (eq.enabled) "Custom" else "Off", eq.levels.mapIndexed { i, gain ->
            (gain / 60.0).roundToInt().coerceIn(ranges[i])
        },
            specs.map { it.label }, -range..range, eq.enabled,
            bandRanges = ranges)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override val presets: StateFlow<List<PresetGroup>> = MutableStateFlow(emptyList())
    private val applied = MutableStateFlow<String?>(null)
    override val autoEqApplied: StateFlow<String?> = applied
    override val libraryNote = "Curves are saved in Hush and applied to the earbuds' hardware equalizer."

    private fun rawLevels(levels: List<Int>): List<Int>? {
        val specs = raw.value.eqCaps?.bandSpecs ?: return null
        if (levels.size != specs.size) return null
        return levels.mapIndexed { index, value ->
            (value * 60).takeIf { it in specs[index].min..specs[index].max } ?: return null
        }
    }

    override fun setBand(index: Int, level: Int) {
        val specs = raw.value.eqCaps?.bandSpecs ?: return
        if (index !in specs.indices || level * 60 !in specs[index].min..specs[index].max) return
        command { it.setEqBand(index, level * 60) }
    }

    override fun selectPreset(id: String) { if (id == "flat") reset() }

    override fun applyLevels(levels: List<Int>) {
        val gains = rawLevels(levels) ?: throw IllegalArgumentException("This curve does not match the Jabra equalizer")
        command { it.setEqBands(gains, enabled = true) }
    }

    override fun reset() {
        val count = raw.value.eqCaps?.bandCount ?: return
        command { it.setEqBands(List(count) { 0 }, enabled = true) }
    }

    override val toggle: ((Boolean) -> Unit)? get() = if (raw.value.eqCaps?.supportsEnable == true)
        ({ enabled -> command { it.setEqEnabled(enabled) } }) else null

    private var beforeCompare: List<Int>? = null
    fun clearPending() { beforeCompare = null; applied.value = null }
    override fun compare(pressed: Boolean) {
        if (pressed) {
            if (beforeCompare != null) return
            val levels = raw.value.eq?.levels ?: return
            beforeCompare = levels
            command { it.setEqBands(List(levels.size) { 0 }) }
        } else {
            val levels = beforeCompare ?: return
            beforeCompare = null
            command { it.setEqBands(levels) }
        }
    }

    override val autoEq = object : AutoEqTarget {
        override val label = "Jabra hardware EQ"
        override val bandLabels: List<String> get() = raw.value.eqCaps?.bandSpecs?.map { it.label }.orEmpty()
        override val note = "A nearby fit to the device's reported bands and limits; the exact acoustic response is unknown."

        override fun fit(curve: List<AutoEq.Point>): AutoEq.Fit? {
            val specs = raw.value.eqCaps?.bandSpecs ?: return null
            val points = curve.filter { it.hz in 20.0..16_000.0 }.sortedBy { it.hz }
            if (points.size < 10 || specs.isEmpty()) return null
            val mean = points.map { it.db }.average()
            val levels = specs.map { spec ->
                val target = interpolate(points, spec.frequencyHz) - mean
                target.roundToInt().coerceIn(ceil(spec.min / 60.0).toInt(), floor(spec.max / 60.0).toInt())
            }
            val result = points.map { p -> AutoEq.Point(p.hz,
                interpolate(specs.mapIndexed { index, spec -> AutoEq.Point(spec.frequencyHz, levels[index].toDouble()) }
                    .sortedBy { it.hz }, p.hz)) }
            val target = points.map { AutoEq.Point(it.hz, it.db - mean) }
            val rms = sqrt(target.indices.sumOf { (target[it].db - result[it].db).let { d -> d * d } } / target.size)
            return AutoEq.Fit(levels, target, result, rms)
        }

        override fun apply(entry: AutoEq.Entry, curve: List<AutoEq.Point>, fit: AutoEq.Fit) {
            applyLevels(fit.levels)
            applied.value = entry.name
        }

        override val saveToLibrary: (AutoEq.Entry, AutoEq.Fit) -> Unit = { entry, fit ->
            EqLibrary.save(entry.name, layout, fit.levels, "AutoEq · ${entry.detail}")
        }
    }

    private fun interpolate(points: List<AutoEq.Point>, hz: Double): Double {
        if (hz <= points.first().hz) return points.first().db
        if (hz >= points.last().hz) return points.last().db
        val upper = points.indexOfFirst { it.hz >= hz }
        val lo = points[upper - 1]
        val hi = points[upper]
        val t = (ln(hz) - ln(lo.hz)) / (ln(hi.hz) - ln(lo.hz))
        return lo.db + (hi.db - lo.db) * t
    }
}
