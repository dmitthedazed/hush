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
import java.util.Base64

/** What a trigger does to the headset. Null fields are left alone. */
data class SoundAction(
    val mode: AmbientMode? = null,
    val ambientLevel: Int = 10,
    val voiceFocus: Boolean = false,
    val eq: EqChoice? = null,
    /** Numeric ambient levels are meaningful only within the vendor's scale. */
    val noiseNamespace: String = "sony-mdr",
    val ambientMin: Int? = null,
    val ambientMax: Int? = null,
) {
    val isEmpty get() = mode == null && eq == null
}

sealed interface EqChoice {
    /** A preset stored in the headset (factory, Manual or Custom 1–5). */
    data class Preset(val id: Int, val namespace: String = "sony-mdr") : EqChoice
    /** A curve from the app's own EQ library, written into Manual. */
    data class Library(val id: String) : EqChoice
}

/** A named EQ curve kept on the phone; the headset itself only has Manual and two Custom slots. */
data class SavedEq(
    val id: String,
    val name: String,
    val levels: List<Int>,
    val source: String? = null,
    /** Unversioned libraries were exclusively Sony, with neutral 10 and 21 possible levels. */
    val layoutId: String = legacySonyLayout(levels.size),
)

fun legacySonyLayout(bandCount: Int) = "sony:legacy:$bandCount"

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
    val volumeSteps: Int = 31,
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

    fun saveEq(name: String, levels: List<Int>, source: String? = null, layoutId: String = legacySonyLayout(levels.size)): SavedEq {
        val eq = SavedEq(UUID.randomUUID().toString(), name.trim().ifEmpty { "My EQ" }, levels, source, layoutId)
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
            scenes = c.scenes.map { it.copy(action = it.action.drop()) },
        )
    }

    // ---- whole config, for backup ----

    fun exportJson(): JSONObject = encode(_config.value)

    fun importJson(o: JSONObject) = update { decode(o) }

    // ---- share codes ----

    private const val CODE_PREFIX = "HUSHEQ2:"

    /** The layout travels with raw levels: equal band counts do not imply compatible equalizers. */
    fun shareCode(eq: SavedEq): String {
        val json = JSONObject().put("name", eq.name).put("layout", eq.layoutId).put("levels", JSONArray(eq.levels))
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
    }

    fun parseCode(text: String): SavedEq? = runCatching {
        val code = text.trim()
        require(code.length <= 16_384)
        if (code.startsWith("HUSHEQ1:")) {
            val body = code.removePrefix("HUSHEQ1:")
            val db = body.substringBefore('|').split(',').map { it.trim().toInt() }
            require(db.isNotEmpty() && db.size <= 32 && db.all { it in -10..10 })
            SavedEq(UUID.randomUUID().toString(), body.substringAfter('|', "Imported EQ"), db.map { it + 10 }, "Imported")
        } else {
            require(code.startsWith(CODE_PREFIX))
            val o = JSONObject(String(Base64.getUrlDecoder().decode(code.removePrefix(CODE_PREFIX)), Charsets.UTF_8))
            val levels = o.getJSONArray("levels")
            require(levels.length() in 1..32)
            val layout = o.getString("layout")
            require(layout.isNotBlank())
            val raw = List(levels.length()) {
                val value = levels.get(it)
                require(value is Number)
                val number = value.toDouble()
                require(number.isFinite() && number >= Int.MIN_VALUE && number <= Int.MAX_VALUE && number == number.toInt().toDouble())
                number.toInt()
            }
            SavedEq(UUID.randomUUID().toString(), o.optString("name", "Imported EQ"), raw, "Imported", layout)
        }
    }.getOrNull()

    // ---- JSON ----

    private fun encode(c: SoundConfig) = JSONObject().apply {
        put("version", 2)
        put("ascEnabled", c.ascEnabled)
        put("activities", JSONObject().apply { c.activityActions.forEach { (k, v) -> put(k.name, encode(v)) } })
        put("places", JSONArray(c.places.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("lat", p.lat).put("lng", p.lng)
                .put("radius", p.radius.toDouble()).put("action", encode(p.action))
        }))
        put("appsEnabled", c.appsEnabled)
        put("apps", JSONArray(c.appRules.map { JSONObject().put("pkg", it.pkg).put("label", it.label).put("action", encode(it.action)) }))
        put("library", JSONArray(c.library.map { e ->
            JSONObject().put("id", e.id).put("name", e.name).put("levels", JSONArray(e.levels))
                .put("layout", e.layoutId).put("source", e.source ?: JSONObject.NULL)
        }))
        put("scenes", JSONArray(c.scenes.map { s ->
            JSONObject().put("id", s.id).put("name", s.name).put("icon", s.icon).put("action", encode(s.action))
                .put("stc", s.speakToChat ?: JSONObject.NULL).put("volume", s.volume ?: JSONObject.NULL).put("volumeSteps", s.volumeSteps)
        }))
    }

    private fun encode(a: SoundAction) = JSONObject().apply {
        put("mode", a.mode?.name ?: JSONObject.NULL)
        put("level", a.ambientLevel)
        put("voice", a.voiceFocus)
        put("noiseNamespace", a.noiseNamespace)
        put("ambientMin", a.ambientMin ?: JSONObject.NULL)
        put("ambientMax", a.ambientMax ?: JSONObject.NULL)
        when (val e = a.eq) {
            is EqChoice.Preset -> { put("eqPreset", e.id); put("eqNamespace", e.namespace) }
            is EqChoice.Library -> put("eqLibrary", e.id)
            null -> {}
        }
    }

    private fun decode(o: JSONObject): SoundConfig {
        require(o.optInt("version", 1) in 1..2) { "Unsupported sound settings version" }
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
                    e.optString("source").takeIf { it.isNotEmpty() && it != "null" },
                    e.optString("layout").takeIf { it.isNotBlank() && it != "null" } ?: legacySonyLayout(lv.length()))
            },
            scenes = if (!o.has("scenes")) SoundConfig.defaultScenes else o.optJSONArray("scenes").objects().map { s ->
                Scene(s.getString("id"), s.getString("name"), s.optString("icon", "star"), decodeAction(s.optJSONObject("action") ?: JSONObject()),
                    if (s.isNull("stc") || !s.has("stc")) null else s.getBoolean("stc"),
                    if (s.isNull("volume") || !s.has("volume")) null else s.getInt("volume"), s.optInt("volumeSteps", 31).coerceAtLeast(2))
            },
        )
    }

    private fun decodeAction(o: JSONObject) = SoundAction(
        mode = o.optString("mode").takeIf { it.isNotEmpty() && it != "null" }?.let { runCatching { AmbientMode.valueOf(it) }.getOrNull() },
        ambientLevel = o.optInt("level", 10),
        voiceFocus = o.optBoolean("voice"),
        noiseNamespace = o.optString("noiseNamespace", "sony-mdr"),
        ambientMin = if (o.has("ambientMin") && !o.isNull("ambientMin")) o.getInt("ambientMin") else null,
        ambientMax = if (o.has("ambientMax") && !o.isNull("ambientMax")) o.getInt("ambientMax") else null,
        eq = when {
            o.has("eqPreset") -> EqChoice.Preset(o.getInt("eqPreset"), o.optString("eqNamespace", "sony-mdr"))
            o.has("eqLibrary") -> EqChoice.Library(o.getString("eqLibrary"))
            else -> null
        },
    )

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { getJSONObject(it) }
}
