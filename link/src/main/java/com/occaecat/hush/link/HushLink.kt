package com.occaecat.hush.link

import com.occaecat.hush.Phase
import org.json.JSONArray
import org.json.JSONObject

/** The three noise-control states every brand's headsets map onto; the names are the wire format. */
enum class NoiseMode { NOISE_CANCELLING, AMBIENT, OFF }

/**
 * The phone ↔ watch contract over the Wearable Data Layer. Both apps share the application id and signing key,
 * which is what lets the Data Layer route between them.
 *
 * - The phone publishes [Snapshot] as a DataItem at [STATE_PATH]; it syncs to the watch whenever it changes.
 * - The watch sends a [Command] as a message to [COMMAND_PATH] on a node advertising [PHONE_CAPABILITY].
 */
object HushLink {
    const val STATE_PATH = "/hush/state"
    const val COMMAND_PATH = "/hush/command"
    const val KEY_SNAPSHOT = "snapshot"

    /** Declared in res/values/wear.xml of each app. */
    const val PHONE_CAPABILITY = "hush_phone"
    const val WATCH_CAPABILITY = "hush_watch"
}

/** Everything the watch surfaces (app, tile, complications) needs about one headset, from either route. */
data class Snapshot(
    val phase: Phase = Phase.IDLE,
    val name: String? = null,
    val battery: Int? = null,
    val charging: Boolean = false,
    val mode: NoiseMode? = null,
    /** The noise modes this headset has, in display order; empty = no noise control. */
    val modes: List<NoiseMode> = NoiseMode.entries,
    val ambientLevel: Int = 20,
    /** The top of the ambient level; null = the ambient mode has no level. */
    val ambientMax: Int? = 20,
    /** Let voices through in ambient mode; null when the headset has no such option. */
    val voiceFocus: Boolean? = null,
    /** The headset's own on/off features (Speak-to-Chat, upscaling…), as the widgets show them. */
    val toggles: List<Toggle> = emptyList(),
    val eqPreset: String? = null,
    val eqPresets: List<Preset> = emptyList(),
    val hasAddress: Boolean = false,
    val reconnectReason: String? = null,
) {
    val connected get() = phase == Phase.CONNECTED
    val busy get() = phase == Phase.CONNECTING || phase == Phase.RECONNECTING

    data class Toggle(val id: String, val label: String, val on: Boolean)
    data class Preset(val id: String, val name: String)

    fun toJson(): String = JSONObject().apply {
        put("phase", phase.name)
        putOpt("name", name)
        putOpt("battery", battery)
        put("charging", charging)
        putOpt("mode", mode?.name)
        put("modes", JSONArray(modes.map { it.name }))
        put("ambientLevel", ambientLevel)
        putOpt("ambientMax", ambientMax)
        putOpt("voiceFocus", voiceFocus)
        put("toggles", JSONArray(toggles.map { JSONObject().put("id", it.id).put("label", it.label).put("on", it.on) }))
        putOpt("eqPreset", eqPreset)
        put("eqPresetList", JSONArray(eqPresets.map { JSONObject().put("id", it.id).put("name", it.name) }))
        put("hasAddress", hasAddress)
        putOpt("reconnectReason", reconnectReason)
    }.toString()

    companion object {
        fun fromJson(json: String): Snapshot? = runCatching {
            val o = JSONObject(json)
            fun int(k: String) = if (o.has(k) && !o.isNull(k)) o.getInt(k) else null
            fun bool(k: String) = if (o.has(k) && !o.isNull(k)) o.getBoolean(k) else null
            fun str(k: String) = if (o.has(k) && !o.isNull(k)) o.getString(k) else null
            fun objects(k: String) = o.optJSONArray(k)?.let { a -> List(a.length()) { a.getJSONObject(it) } }
            // An older phone sends Sony's fields: two fixed switches and bare preset numbers.
            val legacyToggles = listOfNotNull(
                bool("dseeAuto")?.let { Toggle("dsee", "DSEE Extreme", it) },
                bool("speakToChat")?.let { Toggle("stc", "Speak-to-Chat", it) },
            )
            val legacyPresets = o.optJSONArray("eqPresets")?.let { a -> List(a.length()) { "${a.getInt(it)}" } }.orEmpty()
            Snapshot(
                phase = Phase.valueOf(o.getString("phase")),
                name = str("name"),
                battery = int("battery"),
                charging = o.optBoolean("charging"),
                mode = str("mode")?.let(NoiseMode::valueOf),
                modes = o.optJSONArray("modes")?.let { a -> List(a.length()) { NoiseMode.valueOf(a.getString(it)) } }
                    ?: NoiseMode.entries,
                ambientLevel = o.optInt("ambientLevel", 20),
                ambientMax = if (o.has("modes")) int("ambientMax") else 20,
                voiceFocus = bool("voiceFocus"),
                toggles = objects("toggles")?.map { Toggle(it.getString("id"), it.getString("label"), it.getBoolean("on")) }
                    ?: legacyToggles,
                eqPreset = str("eqPreset"),
                eqPresets = objects("eqPresetList")?.map { Preset(it.getString("id"), it.getString("name")) }
                    ?: legacyPresets.map { Preset(it, "Preset $it") },
                hasAddress = o.optBoolean("hasAddress"),
                reconnectReason = str("reconnectReason"),
            )
        }.getOrNull()
    }
}

/** What the watch asks the phone to do; the watch's standalone session runs the same commands itself. */
sealed interface Command {
    data object Connect : Command
    data object Disconnect : Command
    data object CycleNoise : Command
    data object Refresh : Command
    data class SetMode(val mode: NoiseMode) : Command
    data class SetAmbientLevel(val level: Int) : Command
    data class SetVoiceFocus(val on: Boolean) : Command
    data class SetToggle(val id: String, val on: Boolean) : Command
    data class SetEqPreset(val id: String) : Command

    fun encode(): ByteArray = JSONObject().apply {
        when (val c = this@Command) {
            Connect -> put("op", "connect")
            Disconnect -> put("op", "disconnect")
            CycleNoise -> put("op", "cycle")
            Refresh -> put("op", "refresh")
            is SetMode -> put("op", "mode").put("v", c.mode.name)
            is SetAmbientLevel -> put("op", "ambient").put("v", c.level)
            is SetVoiceFocus -> put("op", "focus").put("v", c.on)
            is SetToggle -> put("op", "toggle").put("id", c.id).put("v", c.on)
            is SetEqPreset -> put("op", "eq").put("v", c.id)
        }
    }.toString().toByteArray()

    companion object {
        fun decode(bytes: ByteArray): Command? = runCatching {
            val o = JSONObject(String(bytes))
            when (o.getString("op")) {
                "connect" -> Connect
                "disconnect" -> Disconnect
                "cycle" -> CycleNoise
                "refresh" -> Refresh
                "mode" -> SetMode(NoiseMode.valueOf(o.getString("v")))
                "ambient" -> SetAmbientLevel(o.getInt("v"))
                "focus" -> SetVoiceFocus(o.getBoolean("v"))
                "toggle" -> SetToggle(o.getString("id"), o.getBoolean("v"))
                // An older watch.
                "stc" -> SetToggle("stc", o.getBoolean("v"))
                "dsee" -> SetToggle("dsee", o.getBoolean("v"))
                "eq" -> SetEqPreset(o.getString("v"))
                else -> null
            }
        }.getOrNull()
    }
}
