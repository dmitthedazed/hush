package com.occaecat.hush.link

import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.mdr.AmbientMode

// Sony's side of the watch link: its session state as a [Snapshot], and [Command]s run against its controller.

/** What the watch shows about a Sony headset, from the phone's session or the watch's own. */
fun HeadphonesState.toSnapshot() = Snapshot(
    phase = phase,
    name = displayName,
    battery = battery,
    charging = charging,
    mode = ncAsm?.mode?.toLink(),
    ambientLevel = ncAsm?.ambientLevel ?: 20,
    voiceFocus = ncAsm?.voiceFocus,
    toggles = listOfNotNull(
        dseeAuto?.let { Snapshot.Toggle(TOGGLE_DSEE, "DSEE Extreme", it) },
        speakToChat?.let { Snapshot.Toggle(TOGGLE_STC, "Speak-to-Chat", it) },
    ),
    eqPreset = eq?.presetId?.toString(),
    eqPresets = eqCaps?.presets?.map { Snapshot.Preset("${it.id}", EqNames.of(it.id)) }.orEmpty(),
    hasAddress = address != null,
    reconnectReason = reconnectReason,
)

/** The phone's Sony controller names its widget toggles the same, so a watch command means the same on either route. */
const val TOGGLE_DSEE = "dsee"
const val TOGGLE_STC = "stc"

fun AmbientMode.toLink() = NoiseMode.valueOf(name)

fun NoiseMode.toSony() = AmbientMode.valueOf(name)

/** Runs a [Command] against a local session (the phone for relayed commands, the watch when standalone). */
fun HeadphonesController.apply(c: Command) {
    when (c) {
        Command.Connect -> connectSaved()
        Command.Disconnect -> disconnect()
        Command.CycleNoise -> cycleNoiseMode()
        Command.Refresh -> refreshBattery()
        is Command.SetMode -> setAmbientMode(c.mode.toSony())
        is Command.SetAmbientLevel -> setAmbientLevel(c.level, dragging = false)
        is Command.SetVoiceFocus -> setVoiceFocus(c.on)
        is Command.SetToggle -> when (c.id) {
            TOGGLE_DSEE -> setDsee(c.on)
            TOGGLE_STC -> setSpeakToChat(c.on)
        }
        is Command.SetEqPreset -> c.id.toIntOrNull()?.let(::selectEqPreset)
    }
}

/** EQ preset names, following Sound Connect's strings; the watch's standalone session names them the same. */
object EqNames {
    private val names = mapOf(
        0x00 to "Off", 0x01 to "Rock", 0x02 to "Pop", 0x03 to "Jazz", 0x04 to "Dance", 0x05 to "EDM",
        0x06 to "R&B/Hip-Hop", 0x07 to "Acoustic", 0x10 to "Bright", 0x11 to "Excited", 0x12 to "Mellow",
        0x13 to "Relaxed", 0x14 to "Vocal", 0x15 to "Treble Boost", 0x16 to "Bass Boost", 0x17 to "Speech",
        0xA0 to "Custom", 0xA1 to "User 1", 0xA2 to "User 2", 0xA3 to "User 3", 0xA4 to "User 4", 0xA5 to "User 5",
    )

    fun of(id: Int) = names[id] ?: "Preset %02x".format(id)
}
