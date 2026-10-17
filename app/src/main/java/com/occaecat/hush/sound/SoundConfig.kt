package com.occaecat.hush.sound

import android.content.Context
import com.occaecat.hush.mdr.AmbientMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** What a trigger does to the headset. Null fields are left alone. */
data class SoundAction(
    val mode: AmbientMode? = null,
    val ambientLevel: Int = 10,
    val voiceFocus: Boolean = false,
    val eq: EqChoice? = null,
) {
    val isEmpty get() = mode == null && eq == null
}

sealed interface EqChoice {
    /** A preset stored in the headset (factory, Manual or Custom 1–5). */
    data class Preset(val id: Int) : EqChoice
    /** A curve from the app's own EQ library, written into Manual. */
    data class Library(val id: String) : EqChoice
}

/** A named EQ curve kept on the phone; the headset itself only has Manual and two Custom slots. */
data class SavedEq(val id: String, val name: String, val levels: List<Int>, val source: String? = null)

/** Sound Connect's Adaptive Sound Control activities. */
enum class Activity(val label: String) {
    STILL("Staying"), WALKING("Walking"), RUNNING("Running"), VEHICLE("Transport"),
}

/** A My Places entry: a circle around a spot and what to do inside it. */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radius: Float,
    val action: SoundAction,
)

/**
 * A one-tap scene ("Commute", "Office", "Sleep"): noise control and EQ like any trigger, plus Speak-to-Chat and the
 * headset volume. Null means "leave as it is".
 */
data class Scene(
    val id: String,
    val name: String,
    /** Icon key: commute, office, sleep, focus, call, walk, star. */
    val icon: String,
    val action: SoundAction,
    val speakToChat: Boolean? = null,
    val volume: Int? = null,
)

/** Per-app profile: applied while [pkg] is the app playing media. */
data class AppRule(val pkg: String, val label: String, val action: SoundAction)

data class SoundConfig(
    val ascEnabled: Boolean = false,
    val activityActions: Map<Activity, SoundAction> = defaultActivityActions,
    val places: List<Place> = emptyList(),
    val appsEnabled: Boolean = false,
    val appRules: List<AppRule> = emptyList(),
    val library: List<SavedEq> = emptyList(),
    val scenes: List<Scene> = defaultScenes,
) {
    companion object {
        /** Sound Connect's defaults: noise canceling while still or riding, ambient sound while moving on foot. */
        val defaultActivityActions = mapOf(
            Activity.STILL to SoundAction(AmbientMode.NOISE_CANCELLING),
            Activity.WALKING to SoundAction(AmbientMode.AMBIENT, ambientLevel = 10),
            Activity.RUNNING to SoundAction(AmbientMode.AMBIENT, ambientLevel = 15),
            Activity.VEHICLE to SoundAction(AmbientMode.NOISE_CANCELLING),
        )
        val defaultScenes = listOf(
            Scene("commute", "Commute", "commute", SoundAction(AmbientMode.NOISE_CANCELLING), speakToChat = false),
            Scene("office", "Office", "office", SoundAction(AmbientMode.AMBIENT, ambientLevel = 10, voiceFocus = true), speakToChat = true),
            Scene("walk", "Walk", "walk", SoundAction(AmbientMode.AMBIENT, ambientLevel = 17), speakToChat = false),
            Scene("sleep", "Sleep", "sleep", SoundAction(AmbientMode.NOISE_CANCELLING), speakToChat = false, volume = 8),
        )
    }
}

/** The single JSON file behind [SoundConfig]; every change is written straight back. */
object SoundStore {
    private val _config = MutableStateFlow(SoundConfig())
    val config: StateFlow<SoundConfig> = _config
    private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.filesDir, "sound.json")
        file = f
        if (f.exists()) runCatching { _config.value = decode(JSONObject(f.readText())) }
    }

    fun update(change: (SoundConfig) -> SoundConfig) {
        _config.update(change)
        file?.let { f -> runCatching { f.writeText(encode(_config.value).toString()) } }
    }

    // ---- library ----

    fun saveEq(name: String, levels: List<Int>, source: String? = null): SavedEq {
        val eq = SavedEq(UUID.randomUUID().toString(), name.trim().ifEmpty { "My EQ" }, levels, source)
        update { it.copy(library = it.library + eq) }
        return eq
    }

    fun renameEq(id: String, name: String) =
        update { c -> c.copy(library = c.library.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }) }

    fun deleteEq(id: String) = update { c ->
        // Triggers that pointed at it fall back to "don't change the EQ".
        fun SoundAction.drop() = if ((eq as? EqChoice.Library)?.id == id) copy(eq = null) else this
        c.copy(
            library = c.library.filterNot { it.id == id },
            activityActions = c.activityActions.mapValues { it.value.drop() },
            places = c.places.map { it.copy(action = it.action.drop()) },
            appRules = c.appRules.map { it.copy(action = it.action.drop()) },
        )
    }

    // ---- whole config, for backup ----

    fun exportJson(): JSONObject = encode(_config.value)

    fun importJson(o: JSONObject) = update { decode(o) }

    // ---- share codes ----

    private const val CODE_PREFIX = "HUSHEQ1:"

    /** "HUSHEQ1:-2,1,2,5,2,2|Name": dB per band, Clear Bass first, then the name. */
    fun shareCode(eq: SavedEq) = CODE_PREFIX + eq.levels.joinToString(",") { "${it - 10}" } + "|" + eq.name

    fun parseCode(text: String, bandCount: Int): Pair<String, List<Int>>? {
        val body = text.trim().substringAfter(CODE_PREFIX, missingDelimiterValue = "").ifEmpty { return null }
        val db = body.substringBefore('|').split(',').map { it.trim().toIntOrNull() ?: return null }
        if (db.size != bandCount || db.any { it !in -10..10 }) return null
        return body.substringAfter('|', "Imported EQ") to db.map { it + 10 }
    }

    // ---- JSON ----

    private fun encode(c: SoundConfig) = JSONObject().apply {
        put("ascEnabled", c.ascEnabled)
        put("activities", JSONObject().apply { c.activityActions.forEach { (k, v) -> put(k.name, encode(v)) } })
        put("places", JSONArray(c.places.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("lat", p.lat).put("lng", p.lng)
                .put("radius", p.radius.toDouble()).put("action", encode(p.action))
        }))
        put("appsEnabled", c.appsEnabled)
        put("apps", JSONArray(c.appRules.map { JSONObject().put("pkg", it.pkg).put("label", it.label).put("action", encode(it.action)) }))
        put("library", JSONArray(c.library.map { e ->
            JSONObject().put("id", e.id).put("name", e.name).put("levels", JSONArray(e.levels)).put("source", e.source ?: JSONObject.NULL)
        }))
        put("scenes", JSONArray(c.scenes.map { s ->
            JSONObject().put("id", s.id).put("name", s.name).put("icon", s.icon).put("action", encode(s.action))
                .put("stc", s.speakToChat ?: JSONObject.NULL).put("volume", s.volume ?: JSONObject.NULL)
        }))
    }

    private fun encode(a: SoundAction) = JSONObject().apply {
        put("mode", a.mode?.name ?: JSONObject.NULL)
        put("level", a.ambientLevel)
        put("voice", a.voiceFocus)
        when (val e = a.eq) {
            is EqChoice.Preset -> put("eqPreset", e.id)
            is EqChoice.Library -> put("eqLibrary", e.id)
            null -> {}
        }
    }

    private fun decode(o: JSONObject): SoundConfig {
        val acts = o.optJSONObject("activities")
        return SoundConfig(
            ascEnabled = o.optBoolean("ascEnabled"),
            activityActions = Activity.entries.associateWith { a ->
                acts?.optJSONObject(a.name)?.let(::decodeAction) ?: SoundConfig.defaultActivityActions.getValue(a)
            },
            places = o.optJSONArray("places").objects().map { p ->
                Place(p.getString("id"), p.getString("name"), p.getDouble("lat"), p.getDouble("lng"),
                    p.optDouble("radius", 150.0).toFloat(), decodeAction(p.optJSONObject("action") ?: JSONObject()))
            },
            appsEnabled = o.optBoolean("appsEnabled"),
            appRules = o.optJSONArray("apps").objects().map {
                AppRule(it.getString("pkg"), it.optString("label", it.getString("pkg")), decodeAction(it.optJSONObject("action") ?: JSONObject()))
            },
            library = o.optJSONArray("library").objects().map { e ->
                val lv = e.getJSONArray("levels")
                SavedEq(e.getString("id"), e.getString("name"), List(lv.length()) { lv.getInt(it) },
                    e.optString("source").takeIf { it.isNotEmpty() && it != "null" })
            },
            scenes = if (!o.has("scenes")) SoundConfig.defaultScenes else o.optJSONArray("scenes").objects().map { s ->
                Scene(s.getString("id"), s.getString("name"), s.optString("icon", "star"), decodeAction(s.optJSONObject("action") ?: JSONObject()),
                    if (s.isNull("stc") || !s.has("stc")) null else s.getBoolean("stc"),
                    if (s.isNull("volume") || !s.has("volume")) null else s.getInt("volume"))
            },
        )
    }

    private fun decodeAction(o: JSONObject) = SoundAction(
        mode = o.optString("mode").takeIf { it.isNotEmpty() && it != "null" }?.let { runCatching { AmbientMode.valueOf(it) }.getOrNull() },
        ambientLevel = o.optInt("level", 10),
        voiceFocus = o.optBoolean("voice"),
        eq = when {
            o.has("eqPreset") -> EqChoice.Preset(o.getInt("eqPreset"))
            o.has("eqLibrary") -> EqChoice.Library(o.getString("eqLibrary"))
            else -> null
        },
    )

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
}
