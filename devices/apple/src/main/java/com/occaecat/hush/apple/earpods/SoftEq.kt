package com.occaecat.hush.apple.earpods

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.util.Log
import com.occaecat.hush.autoeq.AutoEq
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The EarPods have no DSP of their own (their feature units only do mute and volume), so their equalizer runs on the
 * phone: one [DynamicsProcessing] effect on the global output mix (session 0), active only while the EarPods are
 * plugged in. The response is the user's ten graphic bands plus an optional AutoEq correction, sampled onto 31
 * third-octave bands, with the input lowered by the largest boost and the limiter on, so boosts never clip.
 */
object SoftEq {
    private const val TAG = "SoftEq"

    /** Centres of the user's bands, in Hz. */
    val bands = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val labels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    const val MAX_DB = 12

    data class Preset(val name: String, val gains: List<Int>)

    val presets = listOf(
        Preset("Flat", List(10) { 0 }),
        Preset("Bass boost", listOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0)),
        Preset("Bass reducer", listOf(-5, -4, -3, -1, 0, 0, 0, 0, 0, 0)),
        Preset("Vocal", listOf(-2, -2, -1, 0, 2, 3, 3, 2, 0, -1)),
        Preset("Treble boost", listOf(0, 0, 0, 0, 0, 1, 2, 4, 5, 6)),
        Preset("Loudness", listOf(5, 4, 2, 0, -1, 0, 0, 1, 3, 4)),
    )

    data class Correction(val name: String, val detail: String, val curve: List<AutoEq.Point>)

    data class Settings(
        val enabled: Boolean = true,
        val gains: List<Int> = List(10) { 0 },
        val correction: Correction? = null,
        val correctionOn: Boolean = true,
    ) {
        val presetName get() = presets.firstOrNull { it.gains == gains }?.name ?: "Custom"
    }

    // --- Persistence ---------------------------------------------------------------------------------------------

    private fun prefs(context: Context) = context.getSharedPreferences("earpods_eq", Context.MODE_PRIVATE)

    fun load(context: Context): Settings = prefs(context).let { p ->
        val gains = p.getString("gains", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 10 }
        val curve = p.getString("curve", null)?.split(';')?.mapNotNull { pair ->
            val (f, g) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            AutoEq.Point(f.toDoubleOrNull() ?: return@mapNotNull null, g.toDoubleOrNull() ?: return@mapNotNull null)
        }
        val name = p.getString("correction", null)
        Settings(
            enabled = p.getBoolean("enabled", true),
            gains = gains ?: List(10) { 0 },
            correction = if (name != null && !curve.isNullOrEmpty()) Correction(name, p.getString("detail", "").orEmpty(), curve) else null,
            correctionOn = p.getBoolean("correction_on", true),
        )
    }

    fun save(context: Context, s: Settings) {
        prefs(context).edit()
            .putBoolean("enabled", s.enabled)
            .putString("gains", s.gains.joinToString(","))
            .putBoolean("correction_on", s.correctionOn)
            .apply {
                val c = s.correction
                if (c == null) remove("correction").remove("detail").remove("curve")
                else putString("correction", c.name).putString("detail", c.detail)
                    .putString("curve", c.curve.joinToString(";") { "${it.hz}:${it.db}" })
            }
            .apply()
    }

    // --- Response --------------------------------------------------------------------------------------------------

    /** Third-octave centres 20 Hz..20 kHz: the bands the effect actually runs. */
    private val engine = (0 until 31).map { 20.0 * 2.0.pow(it / 3.0) }

    /** Linear interpolation over log frequency, flat beyond the ends. */
    private fun interpolate(points: List<AutoEq.Point>, hz: Double): Double {
        if (points.isEmpty()) return 0.0
        if (hz <= points.first().hz) return points.first().db
        if (hz >= points.last().hz) return points.last().db
        val i = points.indexOfFirst { it.hz >= hz }
        val a = points[i - 1]; val b = points[i]
        val t = (ln(hz) - ln(a.hz)) / (ln(b.hz) - ln(a.hz))
        return a.db + (b.db - a.db) * t
    }

    /** The total gain the effect applies at [hz], in dB, before the anti-clipping preamp. */
    fun gainAt(s: Settings, hz: Double): Double {
        val user = interpolate(bands.zip(s.gains) { f, g -> AutoEq.Point(f.toDouble(), g.toDouble()) }, hz)
        val correction = s.correction?.takeIf { s.correctionOn }?.let { interpolate(it.curve, hz) } ?: 0.0
        return user + correction
    }

    /** Curve for drawing: [count] log-spaced points over 20 Hz..20 kHz. */
    fun response(s: Settings, count: Int = 96): List<AutoEq.Point> =
        (0 until count).map { i -> 20.0 * 1000.0.pow(i / (count - 1.0)) }.map { AutoEq.Point(it, gainAt(s, it)) }

    // --- Effect ----------------------------------------------------------------------------------------------------

    private var effect: DynamicsProcessing? = null

    /** Applies [s] when [active], releases the effect otherwise. Returns an error message when the effect can't run. */
    fun apply(s: Settings, active: Boolean): String? {
        if (!active || !s.enabled) {
            release()
            return null
        }
        val gains = engine.map { gainAt(s, it) }
        val preamp = -(gains.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
        return try {
            val fx = effect ?: create().also { effect = it }
            for (ch in 0 until CHANNELS) {
                fx.setInputGainbyChannel(ch, preamp.toFloat())
                val eq = fx.getPreEqByChannelIndex(ch)
                engine.forEachIndexed { i, hz ->
                    val band = eq.getBand(i)
                    band.gain = gains[i].toFloat()
                    eq.setBand(i, band)
                }
                fx.setPreEqByChannelIndex(ch, eq)
            }
            fx.enabled = true
            null
        } catch (e: RuntimeException) {
            Log.w(TAG, "equalizer unavailable", e)
            release()
            "This phone doesn’t allow a system-wide equalizer (${e.javaClass.simpleName})."
        }
    }

    private fun create(): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, CHANNELS,
            true, engine.size, false, 0, false, 0, true,
        ).setPreferredFrameDuration(10f).build()
        // Upper edge of each band: halfway (in octaves) to the next centre; the last one covers the rest.
        val pre = DynamicsProcessing.Eq(true, true, engine.size)
        engine.forEachIndexed { i, hz ->
            val edge = if (i == engine.lastIndex) 24_000.0 else sqrt(hz * engine[i + 1])
            pre.setBand(i, DynamicsProcessing.EqBand(true, edge.toFloat(), 0f))
        }
        config.setPreEqAllChannelsTo(pre)
        // Catches what the preamp can't: inter-band overlap and hot masters.
        config.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
        return DynamicsProcessing(Int.MAX_VALUE, 0, config)
    }

    fun release() {
        effect?.let { runCatching { it.enabled = false; it.release() } }
        effect = null
    }

    private const val CHANNELS = 2
}
