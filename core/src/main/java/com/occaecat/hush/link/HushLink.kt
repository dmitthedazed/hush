package com.occaecat.hush.link

import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
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
) {
    val connected get() = phase == Phase.CONNECTED
    val busy get() = phase == Phase.CONNECTING || phase == Phase.RECONNECTING

    fun toJson(): String = JSONObject().apply {
        put("phase", phase.name)
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
        putOpt("reconnectReason", reconnectReason)
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
        )

        fun fromJson(json: String): Snapshot? = runCatching {
            val o = JSONObject(json)
            fun int(k: String) = if (o.has(k) && !o.isNull(k)) o.getInt(k) else null
            fun bool(k: String) = if (o.has(k) && !o.isNull(k)) o.getBoolean(k) else null
            fun str(k: String) = if (o.has(k) && !o.isNull(k)) o.getString(k) else null
            val presets = o.optJSONArray("eqPresets")
            Snapshot(
                phase = Phase.valueOf(o.getString("phase")),
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
    data class SetAmbientLevel(val level: Int) : Command
    data class SetVoiceFocus(val on: Boolean) : Command
    data class SetSpeakToChat(val on: Boolean) : Command
    data class SetDsee(val auto: Boolean) : Command
    data class SetEqPreset(val id: Int) : Command

    fun encode(): ByteArray = JSONObject().apply {
        when (val c = this@Command) {
            Connect -> put("op", "connect")
            Disconnect -> put("op", "disconnect")
            CycleNoise -> put("op", "cycle")
            Refresh -> put("op", "refresh")
            is SetMode -> put("op", "mode").put("v", c.mode.name)
            is SetAmbientLevel -> put("op", "ambient").put("v", c.level)
            is SetVoiceFocus -> put("op", "focus").put("v", c.on)
            is SetSpeakToChat -> put("op", "stc").put("v", c.on)
            is SetDsee -> put("op", "dsee").put("v", c.auto)
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
                "mode" -> SetMode(AmbientMode.valueOf(o.getString("v")))
                "ambient" -> SetAmbientLevel(o.getInt("v"))
                "focus" -> SetVoiceFocus(o.getBoolean("v"))
                "stc" -> SetSpeakToChat(o.getBoolean("v"))
                "dsee" -> SetDsee(o.getBoolean("v"))
                "eq" -> SetEqPreset(o.getInt("v"))
                else -> null
            }
        }.getOrNull()
    }
}

/** Runs a [Command] against a local session (the phone for relayed commands, the watch when standalone). */
fun com.occaecat.hush.HeadphonesController.apply(c: Command) {
    when (c) {
        Command.Connect -> connectSaved()
        Command.Disconnect -> disconnect()
        Command.CycleNoise -> cycleNoiseMode()
        Command.Refresh -> refreshBattery()
        is Command.SetMode -> setAmbientMode(c.mode)
        is Command.SetAmbientLevel -> setAmbientLevel(c.level, dragging = false)
        is Command.SetVoiceFocus -> setVoiceFocus(c.on)
        is Command.SetSpeakToChat -> setSpeakToChat(c.on)
        is Command.SetDsee -> setDsee(c.auto)
        is Command.SetEqPreset -> selectEqPreset(c.id)
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
