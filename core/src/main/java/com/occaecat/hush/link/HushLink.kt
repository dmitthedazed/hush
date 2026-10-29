package com.occaecat.hush.link

import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.BatteryComponent
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.EqBandSpec
import org.json.JSONArray
import org.json.JSONObject

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
    val mode: AmbientMode? = null,
    val ambientLevel: Int = 20,
    val voiceFocus: Boolean = false,
    val speakToChat: Boolean? = null,
    val dseeAuto: Boolean? = null,
    val codec: String? = null,
    val eqPreset: Int? = null,
    val eqPresets: List<Int> = emptyList(),
    val hasAddress: Boolean = false,
    val reconnectReason: String? = null,
    val brand: String = "SONY",
    val noiseModes: Set<AmbientMode> = AmbientMode.entries.toSet(),
    val ambientMin: Int = 1,
    val ambientMax: Int = 20,
    val ancLevel: Int? = null,
    val ancMax: Int = 0,
    val supportsVoiceFocus: Boolean = true,
    val batteries: List<BatteryComponent> = emptyList(),
    val eqNamespace: String = "sony-mdr",
    val eqPresetNames: List<String> = emptyList(),
    val eqBands: List<Int> = emptyList(),
    val eqBandSpecs: List<EqBandSpec> = emptyList(),
    val eqLayoutId: String? = null,
    val eqEnabled: Boolean? = null,
    val supportsEqEnable: Boolean = false,
    val error: String? = null,
) {
    val connected get() = phase == Phase.CONNECTED
    val busy get() = phase == Phase.CONNECTING || phase == Phase.RECONNECTING || phase == Phase.UPDATING

    fun toJson(): String = JSONObject().apply {
        // Older watch versions must still decode the snapshot and disable their controls.
        put("phase", if (phase == Phase.UPDATING) Phase.RECONNECTING.name else phase.name)
        if (phase == Phase.UPDATING) put("firmwareUpdating", true)
        putOpt("name", name)
        putOpt("battery", battery)
        put("charging", charging)
        putOpt("mode", mode?.name)
        put("ambientLevel", ambientLevel)
        put("voiceFocus", voiceFocus)
        putOpt("speakToChat", speakToChat)
        putOpt("dseeAuto", dseeAuto)
        putOpt("codec", codec)
        putOpt("eqPreset", eqPreset)
        put("eqPresets", JSONArray(eqPresets))
        put("hasAddress", hasAddress)
        putOpt("reconnectReason", if (phase == Phase.UPDATING) "Firmware update in progress" else reconnectReason)
        put("brand", brand)
        put("noiseModes", JSONArray(noiseModes.map { it.name }))
        put("ambientMin", ambientMin)
        put("ambientMax", ambientMax)
        putOpt("ancLevel", ancLevel)
        put("ancMax", ancMax)
        put("supportsVoiceFocus", supportsVoiceFocus)
        put("batteries", JSONArray(batteries.map { b -> JSONObject().apply {
            put("id", b.id); put("label", b.label); putOpt("level", b.level); putOpt("charging", b.charging)
        } }))
        put("eqNamespace", eqNamespace)
        put("eqPresetNames", JSONArray(eqPresetNames))
        put("eqBands", JSONArray(eqBands))
        putOpt("eqLayoutId", eqLayoutId)
        putOpt("eqEnabled", eqEnabled)
        put("supportsEqEnable", supportsEqEnable)
        putOpt("error", error)
        put("eqBandSpecs", JSONArray(eqBandSpecs.map { b -> JSONObject().apply {
            put("label", b.label); putOpt("frequency", b.frequencyHz); put("min", b.min); put("max", b.max)
            put("neutral", b.neutral); put("unitsPerDb", b.unitsPerDb); put("step", b.uiStep)
        } }))
    }.toString()

    companion object {
        fun from(s: HeadphonesState) = Snapshot(
            phase = s.phase,
            name = s.displayName,
            battery = s.battery,
            charging = s.charging,
            mode = s.ncAsm?.mode,
            ambientLevel = s.ncAsm?.ambientLevel ?: 20,
            voiceFocus = s.ncAsm?.voiceFocus ?: false,
            speakToChat = s.speakToChat,
            dseeAuto = s.dseeAuto,
            codec = s.codec?.label,
            eqPreset = s.eq?.presetId,
            eqPresets = s.eqCaps?.presets?.map { it.id }.orEmpty(),
            hasAddress = s.address != null,
            reconnectReason = s.reconnectReason,
            brand = s.brand.name,
            noiseModes = s.capabilities.noiseModes,
            ambientMin = s.capabilities.ambientMin,
            ambientMax = s.capabilities.ambientMax,
            ancLevel = s.ancLevel,
            ancMax = s.capabilities.ancMax,
            supportsVoiceFocus = s.capabilities.voiceFocus,
            batteries = s.batteries,
            eqNamespace = s.eqCaps?.namespace ?: "sony-mdr",
            eqPresetNames = s.eqCaps?.presets?.map { it.name }.orEmpty(),
            eqBands = s.eq?.bands.orEmpty(),
            eqBandSpecs = s.eqCaps?.bandSpecs.orEmpty(),
            eqLayoutId = s.eqCaps?.layoutId,
            eqEnabled = s.eq?.enabled,
            supportsEqEnable = s.eqCaps?.supportsEnable == true,
            error = s.error,
        )

        fun fromJson(json: String): Snapshot? = runCatching {
            val o = JSONObject(json)
            fun int(k: String) = if (o.has(k) && !o.isNull(k)) o.getInt(k) else null
            fun bool(k: String) = if (o.has(k) && !o.isNull(k)) o.getBoolean(k) else null
            fun str(k: String) = if (o.has(k) && !o.isNull(k)) o.getString(k) else null
            val presets = o.optJSONArray("eqPresets")
            Snapshot(
                phase = if (o.optBoolean("firmwareUpdating")) Phase.UPDATING else Phase.valueOf(o.getString("phase")),
                name = str("name"),
                battery = int("battery"),
                charging = o.optBoolean("charging"),
                mode = str("mode")?.let(AmbientMode::valueOf),
                ambientLevel = o.optInt("ambientLevel", 20),
                voiceFocus = o.optBoolean("voiceFocus"),
                speakToChat = bool("speakToChat"),
                dseeAuto = bool("dseeAuto"),
                codec = str("codec"),
                eqPreset = int("eqPreset"),
                eqPresets = presets?.let { a -> List(a.length()) { a.getInt(it) } }.orEmpty(),
                hasAddress = o.optBoolean("hasAddress"),
                reconnectReason = str("reconnectReason"),
                brand = str("brand") ?: "SONY",
                noiseModes = o.optJSONArray("noiseModes")?.let { a ->
                    List(a.length()) { AmbientMode.valueOf(a.getString(it)) }.toSet()
                } ?: AmbientMode.entries.toSet(),
                ambientMin = o.optInt("ambientMin", 1),
                ambientMax = o.optInt("ambientMax", 20),
                ancLevel = int("ancLevel"),
                ancMax = o.optInt("ancMax", 0),
                supportsVoiceFocus = o.optBoolean("supportsVoiceFocus", true),
                batteries = o.optJSONArray("batteries")?.let { a -> List(a.length()) { i ->
                    val b = a.getJSONObject(i)
                    BatteryComponent(b.getString("id"), b.getString("label"),
                        if (b.has("level")) b.getInt("level") else null,
                        if (b.has("charging")) b.getBoolean("charging") else null)
                } }.orEmpty(),
                eqNamespace = str("eqNamespace") ?: "sony-mdr",
                eqPresetNames = o.optJSONArray("eqPresetNames")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty(),
                eqBands = o.optJSONArray("eqBands")?.let { a -> List(a.length()) { a.getInt(it) } }.orEmpty(),
                eqLayoutId = str("eqLayoutId"),
                eqEnabled = bool("eqEnabled"),
                supportsEqEnable = o.optBoolean("supportsEqEnable", false),
                error = str("error"),
                eqBandSpecs = o.optJSONArray("eqBandSpecs")?.let { a -> List(a.length()) { i ->
                    val b = a.getJSONObject(i)
                    EqBandSpec(b.getString("label"), if (b.has("frequency")) b.getDouble("frequency") else null,
                        b.getInt("min"), b.getInt("max"), b.getInt("neutral"), b.getDouble("unitsPerDb"), b.getInt("step"))
                } }.orEmpty(),
            )
        }.getOrNull()
    }
}

/** What the watch asks the phone to do; [apply] is also how the watch drives its own standalone session. */
sealed interface Command {
    data object Connect : Command
    data object Disconnect : Command
    data object CycleNoise : Command
    data object Refresh : Command
    data class SetMode(val mode: AmbientMode) : Command
    data class SetAmbientLevel(val level: Int, val namespace: String = "sony-mdr", val min: Int = 1, val max: Int = 20) : Command
    data class SetAncLevel(val level: Int, val namespace: String = "sony-mdr", val max: Int = 0) : Command
    data class SetVoiceFocus(val on: Boolean) : Command
    data class SetSpeakToChat(val on: Boolean) : Command
    data class SetDsee(val auto: Boolean) : Command
    data class SetEqPreset(val id: Int, val namespace: String = "sony-mdr") : Command
    data class SetEqBands(val levels: List<Int>, val layoutId: String) : Command
    data class SetEqEnabled(val enabled: Boolean, val namespace: String = "sony-mdr", val layoutId: String? = null) : Command

    fun encode(): ByteArray = JSONObject().apply {
        when (val c = this@Command) {
            Connect -> put("op", "connect")
            Disconnect -> put("op", "disconnect")
            CycleNoise -> put("op", "cycle")
            Refresh -> put("op", "refresh")
            is SetMode -> put("op", "mode").put("v", c.mode.name)
            is SetAmbientLevel -> put("op", "ambient").put("v", c.level).put("namespace", c.namespace).put("min", c.min).put("max", c.max)
            is SetAncLevel -> put("op", "anc").put("v", c.level).put("namespace", c.namespace).put("max", c.max)
            is SetVoiceFocus -> put("op", "focus").put("v", c.on)
            is SetSpeakToChat -> put("op", "stc").put("v", c.on)
            is SetDsee -> put("op", "dsee").put("v", c.auto)
            is SetEqPreset -> put("op", "eq").put("v", c.id).put("namespace", c.namespace)
            is SetEqBands -> put("op", "eqBands").put("v", JSONArray(c.levels)).put("layout", c.layoutId)
            is SetEqEnabled -> put("op", "eqEnabled").put("v", c.enabled).put("namespace", c.namespace).putOpt("layout", c.layoutId)
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
                "mode" -> SetMode(AmbientMode.valueOf(o.getString("v")))
                "ambient" -> SetAmbientLevel(o.getInt("v"), o.optString("namespace", "sony-mdr"), o.optInt("min", 1), o.optInt("max", 20))
                "anc" -> SetAncLevel(o.getInt("v"), o.optString("namespace", "sony-mdr"), o.optInt("max", 0))
                "focus" -> SetVoiceFocus(o.getBoolean("v"))
                "stc" -> SetSpeakToChat(o.getBoolean("v"))
                "dsee" -> SetDsee(o.getBoolean("v"))
                "eq" -> SetEqPreset(o.getInt("v"), o.optString("namespace", "sony-mdr"))
                "eqBands" -> o.getJSONArray("v").let { a -> SetEqBands(List(a.length()) { a.getInt(it) }, o.getString("layout")) }
                "eqEnabled" -> SetEqEnabled(o.getBoolean("v"), o.optString("namespace", "sony-mdr"), if (o.has("layout")) o.getString("layout") else null)
                else -> null
            }
        }.getOrNull()
    }
}

/** Runs a [Command] against a local session (the phone for relayed commands, the watch when standalone). */
fun com.occaecat.hush.HeadphonesController.apply(c: Command) {
    val snapshot = Snapshot.from(state.value)
    if (!snapshot.accepts(c)) return
    when (c) {
        Command.Connect -> connectSaved()
        Command.Disconnect -> disconnect()
        Command.CycleNoise -> cycleNoiseMode()
        Command.Refresh -> refreshBattery()
        is Command.SetMode -> setAmbientMode(c.mode)
        is Command.SetAmbientLevel -> setAmbientLevel(c.level, dragging = false)
        is Command.SetAncLevel -> setAncLevel(c.level)
        is Command.SetVoiceFocus -> setVoiceFocus(c.on)
        is Command.SetSpeakToChat -> setSpeakToChat(c.on)
        is Command.SetDsee -> setDsee(c.auto)
        is Command.SetEqPreset -> selectEqPreset(c.id, c.namespace)
        is Command.SetEqBands -> applyEqBands(c.levels, c.layoutId)
        is Command.SetEqEnabled -> setEqEnabled(c.enabled)
    }
}

/** Prevent commands queued for an earlier device or older Sony-only app from reusing a different scale. */
internal fun Snapshot.accepts(c: Command): Boolean {
    val namespace = if (brand == "JABRA") "jabra-gnp" else if (brand == "SONY") "sony-mdr" else "unknown"
    return when (c) {
        is Command.SetAmbientLevel -> c.namespace == namespace && c.min == ambientMin && c.max == ambientMax && c.level in ambientMin..ambientMax
        is Command.SetAncLevel -> c.namespace == namespace && c.max == ancMax && ancMax > 0 && c.level in 0..ancMax
        is Command.SetEqEnabled -> c.namespace == namespace && c.namespace == eqNamespace &&
            supportsEqEnable &&
            (c.layoutId == eqLayoutId || (c.layoutId == null && brand == "SONY"))
        is Command.SetEqBands -> c.layoutId == eqLayoutId && c.levels.size == eqBandSpecs.size &&
            c.levels.indices.all { c.levels[it] in eqBandSpecs[it].min..eqBandSpecs[it].max }
        is Command.SetEqPreset -> c.namespace == eqNamespace && c.id in eqPresets
        is Command.SetMode -> c.mode in noiseModes
        is Command.SetVoiceFocus -> supportsVoiceFocus
        is Command.SetSpeakToChat -> speakToChat != null
        is Command.SetDsee -> dseeAuto != null
        else -> true
    }
}

/** EQ preset names, following Sound Connect's strings. Shared so the watch shows the same wording. */
object EqNames {
    private val names = mapOf(
        0x00 to "Off", 0x01 to "Rock", 0x02 to "Pop", 0x03 to "Jazz", 0x04 to "Dance", 0x05 to "EDM",
        0x06 to "R&B/Hip-Hop", 0x07 to "Acoustic", 0x10 to "Bright", 0x11 to "Excited", 0x12 to "Mellow",
        0x13 to "Relaxed", 0x14 to "Vocal", 0x15 to "Treble Boost", 0x16 to "Bass Boost", 0x17 to "Speech",
        0xA0 to "Custom", 0xA1 to "User 1", 0xA2 to "User 2", 0xA3 to "User 3", 0xA4 to "User 4", 0xA5 to "User 5",
    )

    fun of(id: Int) = names[id] ?: "Preset %02x".format(id)
}
