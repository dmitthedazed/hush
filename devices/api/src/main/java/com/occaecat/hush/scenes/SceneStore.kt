package com.occaecat.hush.scenes

import android.content.Context
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.EqLibrary
import com.occaecat.hush.devices.NoiseKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What a scene or a trigger does to a device. Null (or absent) parts are left as they are. */
data class SceneAction(
    /** A [com.occaecat.hush.devices.NoiseMode.id]. */
    val noise: String? = null,
    val level: Int? = null,
    val voiceFocus: Boolean? = null,
    val eq: EqPick? = null,
    val volume: Int? = null,
    /** [com.occaecat.hush.devices.SceneSwitch] id → value. */
    val switches: Map<String, Boolean> = emptyMap(),
) {
    val isEmpty get() = noise == null && eq == null && volume == null && switches.isEmpty()
}

sealed interface EqPick {
    /** One of the equalizer's presets, by the device's own id. */
    data class Preset(val id: String) : EqPick
    /** A curve from the shared EQ library. */
    data class Library(val id: String) : EqPick
    /** A phone-side EQ switched off. */
    data object Off : EqPick
}

/** A one-tap scene ("Commute", "Office", "Sleep"): its own noise control, EQ, volume and switches. */
data class Scene(val id: String, val name: String, /** commute, office, walk, sleep, focus, call, star… */ val icon: String, val action: SceneAction)

/** Sound Connect's Adaptive Sound Control activities. */
enum class Activity(val label: String) { STILL("Staying"), WALKING("Walking"), RUNNING("Running"), VEHICLE("Transport") }

/** A place, shared by every device: a circle around a spot. What happens there is per device. */
data class Place(val id: String, val name: String, val lat: Double, val lng: Double, val radius: Float)

/** Per-app profile: applied while [pkg] is the app playing media. */
data class AppRule(val pkg: String, val label: String, val action: SceneAction)

/** One device's scenes and automation. */
data class DeviceScenes(
    val scenes: List<Scene>,
    val ascEnabled: Boolean = false,
    val activities: Map<Activity, SceneAction> = emptyMap(),
    /** [Place.id] → what to do there. */
    val places: Map<String, SceneAction> = emptyMap(),
    val appsEnabled: Boolean = false,
    val apps: List<AppRule> = emptyList(),
)

data class SceneConfig(
    val places: List<Place> = emptyList(),
    /** By [com.occaecat.hush.devices.Device.key]. */
    val devices: Map<String, DeviceScenes> = emptyMap(),
    /** Hush 0.17's Sony-only settings, waiting for the first Sony headset to take them. */
    val legacySony: DeviceScenes? = null,
)

/**
 * Scenes, Adaptive Sound Control and app profiles for every device, in one JSON file. A device without saved settings
 * gets defaults made from what it can do ([defaultsFor]); they are stored the first time something changes.
 */
object SceneStore {
    private val _config = MutableStateFlow(SceneConfig())
    val config: StateFlow<SceneConfig> = _config
    private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        val dir = context.applicationContext.filesDir
        val f = File(dir, "scenes.json")
        file = f
        if (f.exists()) runCatching { _config.value = decode(JSONObject(f.readText())) }
        else File(dir, "sound.json").takeIf { it.exists() }?.let { old ->
            runCatching { importLegacy(JSONObject(old.readText())) }
        }
        // Triggers that pointed at a deleted curve fall back to "don't change the EQ".
        EqLibrary.onDelete = { id ->
            fun SceneAction.drop() = if ((eq as? EqPick.Library)?.id == id) copy(eq = null) else this
            fun DeviceScenes.drop() = copy(
                scenes = scenes.map { it.copy(action = it.action.drop()) },
                activities = activities.mapValues { it.value.drop() },
                places = places.mapValues { it.value.drop() },
                apps = apps.map { it.copy(action = it.action.drop()) },
            )
            update { c -> c.copy(devices = c.devices.mapValues { it.value.drop() }, legacySony = c.legacySony?.drop()) }
        }
    }

    fun update(change: (SceneConfig) -> SceneConfig) {
        _config.update(change)
        save()
    }

    private fun save() {
        file?.let { f -> runCatching { f.writeText(encode(_config.value).toString()) } }
    }

    /** [key]'s settings: saved ones, Hush 0.17's for the first Sony headset, or defaults from what [c] can do. */
    fun device(config: SceneConfig, key: String, c: DeviceController): DeviceScenes =
        config.devices[key] ?: config.legacySony?.takeIf { key.startsWith("sony:") } ?: defaultsFor(c)

    fun updateDevice(key: String, c: DeviceController, change: (DeviceScenes) -> DeviceScenes) = update { cfg ->
        val adopted = cfg.devices[key] == null && cfg.legacySony != null && key.startsWith("sony:")
        cfg.copy(
            devices = cfg.devices + (key to change(device(cfg, key, c))),
            legacySony = if (adopted) null else cfg.legacySony,
        )
    }

    /** Sound Connect's defaults where the device has the modes: noise canceling when still or riding, ambient on foot. */
    fun defaultsFor(c: DeviceController): DeviceScenes {
        val modes = c.noise?.modes.orEmpty()
        val nc = modes.firstOrNull { it.kind == NoiseKind.CANCELLING }
        val amb = modes.firstOrNull { it.kind == NoiseKind.AMBIENT }
        val voice = c.sceneSwitches?.list?.firstOrNull()
        fun ambient(level: Int, focus: Boolean? = null) =
            amb?.let { SceneAction(it.id, level = it.levelRange?.let { r -> level.coerceIn(r) }, voiceFocus = focus) }
        val quiet = c.state.value.volumeSteps.takeIf { it > 0 }?.let { (it * 0.27).toInt().coerceAtLeast(1) }
        val scenes = buildList {
            nc?.let { add(Scene("commute", "Commute", "commute", SceneAction(it.id, switches = voice?.let { v -> mapOf(v.id to false) }.orEmpty()))) }
            ambient(10, true)?.let { add(Scene("office", "Office", "office", it.copy(switches = voice?.let { v -> mapOf(v.id to true) }.orEmpty()))) }
            ambient(17)?.let { add(Scene("walk", "Walk", "walk", it)) }
            if (nc != null || quiet != null) add(Scene("sleep", "Sleep", "sleep", SceneAction(nc?.id, volume = quiet)))
        }
        val activities = buildMap {
            nc?.let { put(Activity.STILL, SceneAction(it.id)); put(Activity.VEHICLE, SceneAction(it.id)) }
            ambient(10)?.let { put(Activity.WALKING, it) }
            ambient(15)?.let { put(Activity.RUNNING, it) }
        }
        return DeviceScenes(scenes, activities = activities)
    }

    // ---- backup ----

    fun exportJson(): JSONObject = encode(_config.value)

    fun importJson(o: JSONObject) = update { decode(o) }

    /** Hush 0.17's `sound.json` (or a backup's "sound"): Sony's settings, kept until a Sony headset takes them. */
    fun importLegacy(o: JSONObject) {
        // That file also held the EQ library, in raw levels 0..20; it lives in the shared EqLibrary now.
        o.optJSONArray("library").objects().forEach { e ->
            if (EqLibrary.items.value.none { it.id == e.getString("id") }) {
                val lv = e.getJSONArray("levels")
                EqLibrary.save(e.getString("name"), "sony-6", List(lv.length()) { lv.getInt(it) - 10 },
                    e.optString("source").takeIf { it.isNotEmpty() && it != "null" }, id = e.getString("id"))
            }
        }
        fun action(a: JSONObject?, stc: Boolean? = null, volume: Int? = null): SceneAction {
            val mode = a?.optString("mode")?.takeIf { it.isNotEmpty() && it != "null" }
            return SceneAction(
                noise = mode,
                level = if (mode == "AMBIENT") a.optInt("level", 10) else null,
                voiceFocus = if (mode == "AMBIENT") a.optBoolean("voice") else null,
                eq = when {
                    a == null -> null
                    a.has("eqPreset") -> EqPick.Preset(a.getInt("eqPreset").toString())
                    a.has("eqLibrary") -> EqPick.Library(a.getString("eqLibrary"))
                    else -> null
                },
                volume = volume,
                switches = stc?.let { mapOf("speakToChat" to it) }.orEmpty(),
            )
        }
        val places = o.optJSONArray("places").objects().map {
            Place(it.getString("id"), it.getString("name"), it.getDouble("lat"), it.getDouble("lng"), it.optDouble("radius", 150.0).toFloat()) to
                action(it.optJSONObject("action"))
        }
        val acts = o.optJSONObject("activities")
        val legacy = DeviceScenes(
            scenes = if (!o.has("scenes")) legacyDefaultScenes else o.optJSONArray("scenes").objects().map { s ->
                Scene(s.getString("id"), s.getString("name"), s.optString("icon", "star"), action(s.optJSONObject("action"),
                    s.optNullableBoolean("stc"), if (s.isNull("volume") || !s.has("volume")) null else s.getInt("volume")))
            },
            ascEnabled = o.optBoolean("ascEnabled"),
            activities = Activity.entries.mapNotNull { a -> acts?.optJSONObject(a.name)?.let { a to action(it) } }.toMap(),
            places = places.associate { (p, a) -> p.id to a },
            appsEnabled = o.optBoolean("appsEnabled"),
            apps = o.optJSONArray("apps").objects().map {
                AppRule(it.getString("pkg"), it.optString("label", it.getString("pkg")), action(it.optJSONObject("action")))
            },
        )
        update { c ->
            val sony = c.devices.keys.firstOrNull { it.startsWith("sony:") }
            c.copy(
                places = c.places + places.map { it.first }.filter { p -> c.places.none { it.id == p.id } },
                devices = if (sony != null) c.devices + (sony to legacy) else c.devices,
                legacySony = if (sony != null) null else legacy,
            )
        }
    }

    /** Hush 0.17's built-in scenes, for a sound.json that never changed them. */
    private val legacyDefaultScenes = listOf(
        Scene("commute", "Commute", "commute", SceneAction("NOISE_CANCELLING", switches = mapOf("speakToChat" to false))),
        Scene("office", "Office", "office", SceneAction("AMBIENT", 10, true, switches = mapOf("speakToChat" to true))),
        Scene("walk", "Walk", "walk", SceneAction("AMBIENT", 17, false, switches = mapOf("speakToChat" to false))),
        Scene("sleep", "Sleep", "sleep", SceneAction("NOISE_CANCELLING", volume = 8, switches = mapOf("speakToChat" to false))),
    )

    // ---- JSON ----

    private fun encode(c: SceneConfig) = JSONObject().apply {
        put("places", JSONArray(c.places.map {
            JSONObject().put("id", it.id).put("name", it.name).put("lat", it.lat).put("lng", it.lng).put("radius", it.radius.toDouble())
        }))
        put("devices", JSONObject().apply { c.devices.forEach { (k, v) -> put(k, encode(v)) } })
        c.legacySony?.let { put("legacySony", encode(it)) }
    }

    private fun encode(d: DeviceScenes) = JSONObject().apply {
        put("scenes", JSONArray(d.scenes.map { JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("action", encode(it.action)) }))
        put("asc", d.ascEnabled)
        put("activities", JSONObject().apply { d.activities.forEach { (k, v) -> put(k.name, encode(v)) } })
        put("places", JSONObject().apply { d.places.forEach { (k, v) -> put(k, encode(v)) } })
        put("apps", d.appsEnabled)
        put("appRules", JSONArray(d.apps.map { JSONObject().put("pkg", it.pkg).put("label", it.label).put("action", encode(it.action)) }))
    }

    private fun encode(a: SceneAction) = JSONObject().apply {
        a.noise?.let { put("noise", it) }
        a.level?.let { put("level", it) }
        a.voiceFocus?.let { put("voice", it) }
        when (val e = a.eq) {
            is EqPick.Preset -> put("eqPreset", e.id)
            is EqPick.Library -> put("eqLibrary", e.id)
            EqPick.Off -> put("eqOff", true)
            null -> {}
        }
        a.volume?.let { put("volume", it) }
        if (a.switches.isNotEmpty()) put("switches", JSONObject().apply { a.switches.forEach { (k, v) -> put(k, v) } })
    }

    private fun decode(o: JSONObject) = SceneConfig(
        places = o.optJSONArray("places").objects().map {
            Place(it.getString("id"), it.getString("name"), it.getDouble("lat"), it.getDouble("lng"), it.optDouble("radius", 150.0).toFloat())
        },
        devices = o.optJSONObject("devices")?.let { d -> d.keys().asSequence().associateWith { decodeDevice(d.getJSONObject(it)) } }.orEmpty(),
        legacySony = o.optJSONObject("legacySony")?.let(::decodeDevice),
    )

    private fun decodeDevice(o: JSONObject): DeviceScenes {
        val acts = o.optJSONObject("activities")
        val places = o.optJSONObject("places")
        return DeviceScenes(
            scenes = o.optJSONArray("scenes").objects().map {
                Scene(it.getString("id"), it.getString("name"), it.optString("icon", "star"), decodeAction(it.optJSONObject("action")))
            },
            ascEnabled = o.optBoolean("asc"),
            activities = Activity.entries.mapNotNull { a -> acts?.optJSONObject(a.name)?.let { a to decodeAction(it) } }.toMap(),
            places = places?.keys()?.asSequence()?.associateWith { decodeAction(places.getJSONObject(it)) }.orEmpty(),
            appsEnabled = o.optBoolean("apps"),
            apps = o.optJSONArray("appRules").objects().map { AppRule(it.getString("pkg"), it.optString("label"), decodeAction(it.optJSONObject("action"))) },
        )
    }

    private fun decodeAction(o: JSONObject?): SceneAction {
        if (o == null) return SceneAction()
        val sw = o.optJSONObject("switches")
        return SceneAction(
            noise = o.optString("noise").takeIf { it.isNotEmpty() },
            level = if (o.has("level")) o.getInt("level") else null,
            voiceFocus = o.optNullableBoolean("voice"),
            eq = when {
                o.has("eqPreset") -> EqPick.Preset(o.getString("eqPreset"))
                o.has("eqLibrary") -> EqPick.Library(o.getString("eqLibrary"))
                o.optBoolean("eqOff") -> EqPick.Off
                else -> null
            },
            volume = if (o.has("volume")) o.getInt("volume") else null,
            switches = sw?.keys()?.asSequence()?.associateWith { sw.getBoolean(it) }.orEmpty(),
        )
    }

    private fun JSONObject.optNullableBoolean(k: String): Boolean? = if (!has(k) || isNull(k)) null else getBoolean(k)

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
}
