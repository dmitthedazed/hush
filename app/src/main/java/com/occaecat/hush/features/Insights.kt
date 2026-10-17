package com.occaecat.hush.features

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.sound.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.math.pow

/**
 * What Hush learns while the headset is connected, kept on the phone only:
 *  - battery samples, grouped into *sessions* (one unbroken connection). Drain is only measured inside a session,
 *    so hours the headphones spent with another device (laptop, without multipoint) never count as "idle".
 *    With multipoint the phone stays connected and sees the drain from the laptop too.
 *  - listening minutes and an estimated sound dose per day (headset volume → dB, WHO 80 dB × 40 h a week = 100 %).
 *  - where and when the headset was last seen (the disconnect), for "Find my headphones".
 */
object Insights {

    data class BatterySample(val t: Long, val level: Int, val charging: Boolean, val nc: Boolean, val session: Int)

    data class Day(val date: LocalDate, val minutes: Int, val dose: Double, val volumeSum: Long)

    data class LastSeen(val t: Long, val connected: Boolean, val lat: Double?, val lng: Double?, val accuracy: Float?)

    data class Snapshot(
        val battery: List<BatterySample> = emptyList(),
        val days: List<Day> = emptyList(),
        val lastSeen: LastSeen? = null,
    )

    private val _data = MutableStateFlow(Snapshot())
    val data: StateFlow<Snapshot> = _data

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var file: File
    private var session = 0
    private const val KEEP_DAYS = 30L

    fun start(context: Context) {
        if (::file.isInitialized) return
        val app = context.applicationContext
        file = File(app.filesDir, "insights.json")
        load()
        session = (_data.value.battery.maxOfOrNull { it.session } ?: 0) + 1
        val controller = HeadphonesController.get(app)
        val state = controller.state

        scope.launch {
            var previous = Phase.IDLE
            state.map { it.phase }.distinctUntilChanged().collect { phase ->
                val up = phase == Phase.CONNECTED || phase == Phase.RECONNECTING
                val wasUp = previous == Phase.CONNECTED || previous == Phase.RECONNECTING
                if (phase == Phase.CONNECTED && !wasUp) {
                    session++
                    seen(app, connected = true)
                }
                if (!up && wasUp) seen(app, connected = false)
                previous = phase
            }
        }
        scope.launch {
            state.map { Triple(it.battery, it.charging, it.ncAsm?.mode) to it.phase }.distinctUntilChanged().collect { (b, phase) ->
                val (level, charging, mode) = b
                if (level == null || phase != Phase.CONNECTED) return@collect
                val last = _data.value.battery.lastOrNull()
                if (last != null && last.session == session && last.level == level && last.charging == charging &&
                    last.nc == (mode == AmbientMode.NOISE_CANCELLING)) return@collect
                update { s ->
                    s.copy(battery = (s.battery + BatterySample(System.currentTimeMillis(), level, charging,
                        mode == AmbientMode.NOISE_CANCELLING, session)).trimDays { it.t })
                }
            }
        }
        scope.launch {
            // One tick a minute: counts a listening minute when something plays through the connected headset.
            while (true) {
                delay(60_000)
                val s = state.value
                val playing = NowPlaying.media.value?.playing ?: (s.playing == true)
                if (s.phase != Phase.CONNECTED || !playing) continue
                val v = s.volume ?: continue
                val today = LocalDate.now()
                update { snap ->
                    val days = snap.days.toMutableList()
                    val i = days.indexOfFirst { it.date == today }
                    val add = doseForMinute(v, s.volumeSteps)
                    if (i >= 0) days[i] = days[i].let { it.copy(minutes = it.minutes + 1, dose = it.dose + add, volumeSum = it.volumeSum + v) }
                    else days += Day(today, 1, add, v.toLong())
                    snap.copy(days = days.filter { it.date.isAfter(today.minusDays(KEEP_DAYS)) })
                }
            }
        }
    }

    // ---- battery ----

    /** Sony's rated battery life for the XM4: 30 h with noise canceling, 38 h without. */
    private const val RATED_NC_PER_HOUR = 100.0 / 30
    private const val RATED_OFF_PER_HOUR = 100.0 / 38

    data class Drain(val percentPerHour: Double, val hoursMeasured: Double, val measured: Boolean)

    /** Drain measured inside sessions only (see the class doc), over the last two weeks; Sony's rating until 2 h are known. */
    fun drain(nc: Boolean, samples: List<BatterySample> = _data.value.battery): Drain {
        val since = System.currentTimeMillis() - 14 * 86_400_000L
        var drop = 0.0
        var hours = 0.0
        samples.filter { it.t > since }.zipWithNext().forEach { (a, b) ->
            if (a.session != b.session || a.charging || b.charging || a.nc != nc) return@forEach
            val d = a.level - b.level
            val h = (b.t - a.t) / 3_600_000.0
            if (d < 0 || h <= 0 || h > 12) return@forEach
            drop += d; hours += h
        }
        val rated = if (nc) RATED_NC_PER_HOUR else RATED_OFF_PER_HOUR
        return if (hours >= 2 && drop > 0) Drain(drop / hours, hours, true) else Drain(rated, hours, false)
    }

    // ---- hearing ----

    /**
     * Rough level at the ear: Sony's 31 steps span about 45 dB, the top step taken as 100 dB(A). Each step is ~1.5 dB,
     * which matches how Android maps absolute volume. An estimate, not a measurement.
     */
    fun estimatedDb(volume: Int, steps: Int = 31): Double {
        val top = (steps - 1).coerceAtLeast(1)
        return 100.0 - (top - volume.coerceIn(0, top)) * 45.0 / top
    }

    /** Fraction of the WHO weekly allowance (80 dB for 40 h; +3 dB halves the time) used by one minute. */
    fun doseForMinute(volume: Int, steps: Int): Double {
        if (volume <= 0) return 0.0
        return 2.0.pow((estimatedDb(volume, steps) - 80.0) / 3.0) / (40 * 60)
    }

    fun weeklyDose(days: List<Day> = _data.value.days): Double {
        val from = LocalDate.now().minusDays(6)
        return days.filter { !it.date.isBefore(from) }.sumOf { it.dose }
    }

    // ---- last seen ----

    @SuppressLint("MissingPermission")
    private fun seen(context: Context, connected: Boolean) = scope.launch {
        val now = System.currentTimeMillis()
        update { it.copy(lastSeen = LastSeen(now, connected, it.lastSeen?.lat, it.lastSeen?.lng, it.lastSeen?.accuracy)) }
        if (connected || !hasLocation(context)) return@launch
        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc = runCatching { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() }.getOrNull()
            ?: runCatching { client.lastLocation.await() }.getOrNull()
        if (loc != null) update { it.copy(lastSeen = LastSeen(now, false, loc.latitude, loc.longitude, loc.accuracy)) }
    }

    fun hasLocation(c: Context) =
        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(c: Context) =
        c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ---- storage ----

    private fun <T> List<T>.trimDays(time: (T) -> Long): List<T> {
        val cut = System.currentTimeMillis() - KEEP_DAYS * 86_400_000L
        return filter { time(it) > cut }
    }

    private fun update(change: (Snapshot) -> Snapshot) {
        _data.value = change(_data.value)
        val snap = _data.value
        scope.launch(Dispatchers.IO) { runCatching { file.writeText(encode(snap).toString()) } }
    }

    fun exportJson(): JSONObject = encode(_data.value)

    fun importJson(o: JSONObject) {
        _data.value = decode(o)
        update { it }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching { _data.value = decode(JSONObject(file.readText())) }
    }

    private fun encode(s: Snapshot) = JSONObject()
        .put("battery", JSONArray(s.battery.map { JSONArray().put(it.t).put(it.level).put(it.charging).put(it.nc).put(it.session) }))
        .put("days", JSONArray(s.days.map { JSONArray().put(it.date.toString()).put(it.minutes).put(it.dose).put(it.volumeSum) }))
        .put("lastSeen", s.lastSeen?.let {
            JSONObject().put("t", it.t).put("connected", it.connected).put("lat", it.lat ?: JSONObject.NULL)
                .put("lng", it.lng ?: JSONObject.NULL).put("acc", it.accuracy?.toDouble() ?: JSONObject.NULL)
        } ?: JSONObject.NULL)

    private fun decode(o: JSONObject): Snapshot {
        val b = o.optJSONArray("battery") ?: JSONArray()
        val d = o.optJSONArray("days") ?: JSONArray()
        val ls = o.optJSONObject("lastSeen")
        return Snapshot(
            battery = List(b.length()) { b.getJSONArray(it) }.map { BatterySample(it.getLong(0), it.getInt(1), it.getBoolean(2), it.getBoolean(3), it.getInt(4)) },
            days = List(d.length()) { d.getJSONArray(it) }.map { Day(LocalDate.parse(it.getString(0)), it.getInt(1), it.getDouble(2), it.getLong(3)) },
            lastSeen = ls?.let {
                LastSeen(it.getLong("t"), it.optBoolean("connected"),
                    if (it.isNull("lat")) null else it.getDouble("lat"), if (it.isNull("lng")) null else it.getDouble("lng"),
                    if (it.isNull("acc")) null else it.getDouble("acc").toFloat())
            },
        )
    }
}
