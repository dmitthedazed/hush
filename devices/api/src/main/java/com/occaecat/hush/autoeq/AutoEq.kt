package com.occaecat.hush.autoeq

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AutoEq (github.com/jaakkopasanen/AutoEq) integration: the profile index, a profile's correction curve, and a
 * least-squares fit of that curve onto the headset's fixed EQ (Clear Bass + five graphic bands, ±10 steps).
 */
object AutoEq {
    private const val BASE = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/"
    private const val INDEX_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** One measured profile: `path` is URL-encoded and relative to results/, as INDEX.md lists it. */
    data class Entry(val name: String, val source: String, val rig: String?, val path: String) {
        val detail get() = listOfNotNull(source, rig).joinToString(" · ")
    }

    /** A point of the AutoEq correction curve: frequency in Hz, gain in dB. */
    data class Point(val hz: Double, val db: Double)

    data class Fit(
        /** Headset levels 0..20 (10 = flat), in band order: Clear Bass, 400, 1k, 2.5k, 6.3k, 16k. */
        val levels: List<Int>,
        /** The AutoEq curve with its overall level removed, for drawing. */
        val target: List<Point>,
        /** What the headset will actually do with [levels], on the same frequencies. */
        val result: List<Point>,
        /** RMS distance between the two over 20 Hz..16 kHz, in dB. */
        val rmsError: Double,
    )

    private val line = Regex("""^- \[(.+)]\(\./(.+)\) by (.+?)(?: on (.+))?$""")

    /** The full profile index, from a week-old cache at most; falls back to a stale cache when offline. */
    suspend fun index(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "autoeq-index.md")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < INDEX_TTL_MS
        val text = if (fresh) cache.readText() else try {
            download(BASE + "INDEX.md").also { cache.writeText(it) }
        } catch (e: IOException) {
            if (cache.exists()) cache.readText() else throw e
        }
        text.lineSequence().mapNotNull { l ->
            line.matchEntire(l.trim())?.destructured?.let { (name, path, source, rig) ->
                Entry(name, source, rig.ifBlank { null }, path)
            }
        }.toList()
    }

    /** AutoEq's own preference between measurement sources; others follow alphabetically. */
    private val sourceRank = listOf("oratory1990", "crinacle", "Innerfidelity", "Rtings", "Headphone.com Legacy")

    /** Ranks [entries] for [query]: every word must appear; over-ear first, then shorter names, then trusted sources. */
    fun search(entries: List<Entry>, query: String): List<Entry> {
        val words = query.lowercase().split(' ', '-', '_').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries.filter { e ->
            val hay = e.name.lowercase().replace('-', ' ')
            words.all { it in hay }
        }.sortedWith(
            compareBy<Entry>({ "over-ear" !in it.path }, { it.name.length }, { it.name })
                .thenBy { sourceRank.indexOf(it.source).let { r -> if (r < 0) sourceRank.size else r } }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.source },
        )
    }

    suspend fun curve(entry: Entry): List<Point> = withContext(Dispatchers.IO) {
        val folder = entry.path.substringAfterLast('/')
        val text = download("$BASE${entry.path}/$folder%20GraphicEQ.txt")
        text.substringAfter("GraphicEQ:").split(';').mapNotNull { pair ->
            val (f, g) = pair.trim().split(' ').takeIf { it.size == 2 } ?: return@mapNotNull null
            Point(f.toDoubleOrNull() ?: return@mapNotNull null, g.toDoubleOrNull() ?: return@mapNotNull null)
        }.also { if (it.size < 10) throw IOException("No GraphicEQ curve for ${entry.name}") }
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // --- Fitting -----------------------------------------------------------------------------------------------

    /**
     * The headset's EQ as filters: Clear Bass as a low shelf, the graphic bands as peaking filters about
     * 1⅓ octaves wide. Sony doesn't publish the exact shapes, so the fit is a close approximation.
     */
    private sealed interface Band { val hz: Double }
    private data class Shelf(override val hz: Double, val q: Double) : Band
    private data class Peak(override val hz: Double, val q: Double) : Band

    private val sixBands = listOf(
        Shelf(100.0, 0.7), Peak(400.0, 1.05), Peak(1000.0, 1.05), Peak(2500.0, 1.05), Peak(6300.0, 1.05), Peak(16000.0, 1.05),
    )
    private const val FS = 48_000.0
    private const val MAX_DB = 10

    fun fit(curve: List<Point>, bandCount: Int): Fit? {
        if (bandCount != sixBands.size) return null
        val pts = curve.filter { it.hz in 20.0..16_000.0 }
        if (pts.size < 10) return null
        val n = sixBands.size
        // Per-band response for +6 dB divided by 6: close to linear in gain over ±10 dB.
        val basis = sixBands.map { b -> pts.map { response(b, 6.0, it.hz) / 6.0 } }

        val fixed = arrayOfNulls<Double>(n)
        var gains = DoubleArray(n)
        repeat(4) {
            gains = solve(pts.map { it.db }, basis, fixed)
            var clamped = false
            for (i in 0 until n) if (fixed[i] == null && abs(gains[i]) > MAX_DB) {
                fixed[i] = gains[i].coerceIn(-MAX_DB.toDouble(), MAX_DB.toDouble()); clamped = true
            }
            if (!clamped) return@repeat
        }
        val levels = gains.map { (it.roundToInt().coerceIn(-MAX_DB, MAX_DB)) + MAX_DB }

        // Compare shapes, not levels: remove each curve's mean before drawing and scoring.
        val result = pts.map { p -> Point(p.hz, sixBands.indices.sumOf { response(sixBands[it], levels[it] - 10.0, p.hz) }) }
        val tMean = pts.map { it.db }.average()
        val rMean = result.map { it.db }.average()
        val target = pts.map { Point(it.hz, it.db - tMean) }
        val res = result.map { Point(it.hz, it.db - rMean) }
        val rms = sqrt(target.indices.sumOf { (target[it].db - res[it].db).pow(2) } / target.size)
        return Fit(levels, target, res, rms)
    }

    /** Ridge-regularised least squares with a free level offset; [fixed] gains are held where they are. */
    private fun solve(target: List<Double>, basis: List<List<Double>>, fixed: Array<Double?>): DoubleArray {
        val free = basis.indices.filter { fixed[it] == null }
        val y = target.indices.map { k ->
            target[k] - basis.indices.sumOf { j -> (fixed[j] ?: 0.0) * basis[j][k] }
        }
        val cols = free.map { basis[it] } + listOf(List(target.size) { 1.0 }) // last column: offset
        val m = cols.size
        val a = Array(m) { DoubleArray(m + 1) }
        for (i in 0 until m) {
            for (j in 0 until m) a[i][j] = cols[i].indices.sumOf { cols[i][it] * cols[j][it] }
            a[i][m] = cols[i].indices.sumOf { cols[i][it] * y[it] }
            if (i < m - 1) a[i][i] += 0.02 * target.size // keep neighbouring bands from fighting each other
        }
        // Gaussian elimination with partial pivoting.
        for (c in 0 until m) {
            val p = (c until m).maxBy { abs(a[it][c]) }
            a[c] = a[p].also { a[p] = a[c] }
            for (r in 0 until m) if (r != c) {
                val f = a[r][c] / a[c][c]
                for (k in c..m) a[r][k] -= f * a[c][k]
            }
        }
        val out = DoubleArray(basis.size) { fixed[it] ?: 0.0 }
        free.forEachIndexed { i, j -> out[j] = a[i][m] / a[i][i] }
        return out
    }

    /** RBJ biquad magnitude in dB at [hz] for a band at [gainDb]. */
    private fun response(band: Band, gainDb: Double, hz: Double): Double {
        if (gainDb == 0.0) return 0.0
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * PI * band.hz.coerceAtMost(FS / 2.2) / FS
        val cw = cos(w0)
        val (b, den) = when (band) {
            is Peak -> {
                val alpha = sin(w0) / (2 * band.q)
                doubleArrayOf(1 + alpha * a, -2 * cw, 1 - alpha * a) to doubleArrayOf(1 + alpha / a, -2 * cw, 1 - alpha / a)
            }
            is Shelf -> {
                val alpha = sin(w0) / (2 * band.q)
                val s = 2 * sqrt(a) * alpha
                doubleArrayOf(a * ((a + 1) - (a - 1) * cw + s), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - s)) to
                    doubleArrayOf((a + 1) + (a - 1) * cw + s, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - s)
            }
        }
        val w = 2 * PI * hz / FS
        fun mag(c: DoubleArray): Double {
            val re = c[0] + c[1] * cos(w) + c[2] * cos(2 * w)
            val im = -c[1] * sin(w) - c[2] * sin(2 * w)
            return hypot(re, im)
        }
        return 20 * log10(mag(b) / mag(den))
    }

    // --- Last applied profile ------------------------------------------------------------------------------------

    /** The profile last written, and the preset slot it went into. */
    data class Applied(val name: String, val detail: String, val presetId: Int)

    private fun prefs(context: Context) = context.getSharedPreferences("autoeq", Context.MODE_PRIVATE)

    fun lastApplied(context: Context): Applied? = prefs(context).let { p ->
        p.getString("name", null)?.let { Applied(it, p.getString("detail", "").orEmpty(), p.getInt("preset", 0xA0)) }
    }

    fun rememberApplied(context: Context, entry: Entry, presetId: Int) = prefs(context).edit()
        .putString("name", entry.name).putString("detail", entry.detail).putInt("preset", presetId).apply()

    fun forgetApplied(context: Context) = prefs(context).edit().clear().apply()
}
