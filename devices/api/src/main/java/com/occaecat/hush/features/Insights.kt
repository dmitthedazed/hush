package com.occaecat.hush.features

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.occaecat.hush.sound.NowPlaying
import kotlinx.coroutines.flow.Flow
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
 * What Hush learns while a device is connected, kept on the phone only and per device ([Profile.key]):
 *  - battery samples, grouped into *sessions* (one unbroken connection). Drain is only measured inside a session,
 *    so hours the headphones spent with another device (laptop, without multipoint) never count as "idle".
 *    With multipoint the phone stays connected and sees the drain from the laptop too.
 *  - listening minutes and an estimated sound dose per day (volume → dB, WHO 80 dB × 40 h a week = 100 %), kept per
 *    device but read merged: the dose is the listener's, whatever they listened on.
 *  - where and when the device was last seen (the disconnect), for "Find my devices" (all of them on one page).
 * Device modules feed it with [track]; devices without a battery simply have no battery samples.
 */
object Insights {

    /** A device as Insights knows it: rated battery life (null = no battery) and how its volume maps to dB. */
    data class Profile(
        val key: String,
        /** "headphones", "EarPods" — for the pages. */
        val noun: String,
        val ratedNcPerHour: Double? = null,
        val ratedOffPerHour: Double? = null,
        /** Level at the top volume step and the span of the whole scale, in dB(A). */
        val topDb: Double = 100.0,
        val spanDb: Double = 45.0,
    ) {
        val hasBattery get() = ratedOffPerHour != null
    }

    /** What the device is doing right now. */
    data class Sample(
        val connected: Boolean,
        val battery: Int? = null,
        val charging: Boolean = false,
        val nc: Boolean = false,
        val volume: Int? = null,
        val volumeSteps: Int = 31,
        /** What the device itself says about playback; the phone's media session wins when readable. */
        val playing: Boolean = false,
    )

    data class BatterySample(val t: Long, val level: Int, val charging: Boolean, val nc: Boolean, val session: Int)

    data class Day(val date: LocalDate, val minutes: Int, val dose: Double, val volumeSum: Long)

    data class LastSeen(val t: Long, val connected: Boolean, val lat: Double?, val lng: Double?, val accuracy: Float?)

    data class Snapshot(
        val battery: List<BatterySample> = emptyList(),
        val days: List<Day> = emptyList(),
        val lastSeen: LastSeen? = null,
    )

    private val _all = MutableStateFlow<Map<String, Snapshot>>(emptyMap())
    val all: StateFlow<Map<String, Snapshot>> = _all
    private val profiles = mutableMapOf<String, Profile>()

    fun profile(key: String) = profiles[key]
    fun snapshot(key: String) = _all.value[key] ?: Snapshot()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var file: File? = null
    private val sessions = mutableMapOf<String, Int>()
    private val last = mutableMapOf<String, Sample>()
    private val upAt = mutableMapOf<String, Long>()
    private var ticking = false
    private const val KEEP_DAYS = 30L

    private fun open(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, "insights.json")
        load()
    }

    /** Starts recording [profile]'s device from [samples] (call once per device, from the module's init). */
    fun track(context: Context, profile: Profile, samples: Flow<Sample>) {
        open(context)
        if (profiles.put(profile.key, profile) != null) return
        val app = context.applicationContext
        val key = profile.key
        sessions[key] = (snapshot(key).battery.maxOfOrNull { it.session } ?: 0) + 1

        scope.launch {
            var wasUp = false
            samples.map { it.connected }.distinctUntilChanged().collect { up ->
                if (up && !wasUp) {
                    upAt[key] = System.currentTimeMillis()
                    sessions[key] = (sessions[key] ?: 0) + 1
                    seen(app, key, connected = true)
                }
                if (!up && wasUp) seen(app, key, connected = false)
                wasUp = up
            }
        }
        scope.launch {
            samples.collect { s ->
                last[key] = s
                val level = s.battery ?: return@collect
                if (!s.connected) return@collect
                val session = sessions[key] ?: 0
                val prev = snapshot(key).battery.lastOrNull()
                if (prev != null && prev.session == session && prev.level == level && prev.charging == s.charging && prev.nc == s.nc) return@collect
                update(key) { snap ->
                    snap.copy(battery = (snap.battery + BatterySample(System.currentTimeMillis(), level, s.charging, s.nc, session)).trimDays { it.t })
                }
            }
        }
        if (ticking) return
        ticking = true
        scope.launch {
            while (true) {
                delay(60_000)
                tick()
            }
        }
    }

    /**
     * One tick a minute for all devices: the ears hear one device at a time, so a minute of listening goes to the one
     * playing — the device that says it plays, else the one connected last (Android routes media to the newest
     * output). Two connected devices never count the same minute twice, so the merged dose stays honest.
     */
    private fun tick() {
        val mediaPlaying = NowPlaying.media.value?.playing
        val up = last.filter { (_, s) -> s.connected && s.volume != null && (mediaPlaying ?: s.playing) }
        val key = up.keys.maxWithOrNull(compareBy<String>({ up.getValue(it).playing }, { upAt[it] ?: 0L })) ?: return
        val profile = profiles[key] ?: return
        val s = up.getValue(key)
        val v = s.volume ?: return
        val today = LocalDate.now()
        update(key) { snap ->
            val days = snap.days.toMutableList()
            val i = days.indexOfFirst { it.date == today }
            val add = doseForMinute(profile, v, s.volumeSteps)
            if (i >= 0) days[i] = days[i].let { it.copy(minutes = it.minutes + 1, dose = it.dose + add, volumeSum = it.volumeSum + v) }
            else days += Day(today, 1, add, v.toLong())
            snap.copy(days = days.filter { it.date.isAfter(today.minusDays(KEEP_DAYS)) })
        }
    }

    // ---- battery ----


    data class Drain(val percentPerHour: Double, val hoursMeasured: Double, val measured: Boolean)

    /** Drain measured inside sessions only (see the class doc), over the last two weeks; the rating until 2 h are known. */
    fun drain(profile: Profile, nc: Boolean, samples: List<BatterySample> = snapshot(profile.key).battery): Drain {
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
        val rated = (if (nc) profile.ratedNcPerHour else profile.ratedOffPerHour) ?: profile.ratedOffPerHour ?: 5.0
        return if (hours >= 2 && drop > 0) Drain(drop / hours, hours, true) else Drain(rated, hours, false)
    }

    // ---- hearing ----

    /**
     * Rough level at the ear: the volume scale spans [Profile.spanDb] (about 45 dB on Sony's 31 steps), the top step
     * taken as [Profile.topDb]. An estimate, not a measurement.
     */
    fun estimatedDb(profile: Profile, volume: Int, steps: Int = 31): Double {
        val top = (steps - 1).coerceAtLeast(1)
        return profile.topDb - (top - volume.coerceIn(0, top)) * profile.spanDb / top
    }

    /** Fraction of the WHO weekly allowance (80 dB for 40 h; +3 dB halves the time) used by one minute. */
    fun doseForMinute(profile: Profile, volume: Int, steps: Int): Double {
        if (volume <= 0) return 0.0
        return 2.0.pow((estimatedDb(profile, volume, steps) - 80.0) / 3.0) / (40 * 60)
    }

    fun weeklyDose(days: List<Day>): Double {
        val from = LocalDate.now().minusDays(6)
        return days.filter { !it.date.isBefore(from) }.sumOf { it.dose }
    }

    /** The ears' week: every device's dose added up (a minute is only ever counted on one device, see [tick]). */
    fun weeklyDose(all: Map<String, Snapshot>): Double = all.values.sumOf { weeklyDose(it.days) }

    // ---- last seen ----

    @SuppressLint("MissingPermission")
    private fun seen(context: Context, key: String, connected: Boolean) = scope.launch {
        val now = System.currentTimeMillis()
        update(key) { it.copy(lastSeen = LastSeen(now, connected, it.lastSeen?.lat, it.lastSeen?.lng, it.lastSeen?.accuracy)) }
        if (connected || !hasLocation(context)) return@launch
        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc = runCatching { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() }.getOrNull()
            ?: runCatching { client.lastLocation.await() }.getOrNull()
        if (loc != null) update(key) { it.copy(lastSeen = LastSeen(now, false, loc.latitude, loc.longitude, loc.accuracy)) }
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

    private fun update(key: String, change: (Snapshot) -> Snapshot) {
        _all.value = _all.value + (key to change(snapshot(key)))
        save()
    }

    private fun save() {
        val all = _all.value
        val f = file ?: return
        scope.launch(Dispatchers.IO) { runCatching { f.writeText(encodeAll(all).toString()) } }
    }

    fun exportJson(): JSONObject = encodeAll(_all.value)

    fun importJson(o: JSONObject) {
        _all.value = decodeAll(o)
        save()
    }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        runCatching { _all.value = decodeAll(JSONObject(f.readText())) }
    }

    private fun encodeAll(all: Map<String, Snapshot>) =
        JSONObject().put("devices", JSONObject().apply { all.forEach { (k, v) -> put(k, encode(v)) } })

    /** Hush 0.17 and older kept one snapshot for the XM4 at the root. */
    private fun decodeAll(o: JSONObject): Map<String, Snapshot> {
        val d = o.optJSONObject("devices") ?: return mapOf("sony" to decode(o))
        return d.keys().asSequence().associateWith { decode(d.getJSONObject(it)) }
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
