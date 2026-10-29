package com.occaecat.hush.ui

import com.occaecat.hush.HeadphonesState

/** User-facing wording, following Sound Connect's strings where it has them. */
object Labels {
    private fun separatesToPause(state: HeadphonesState) =
        state.isJabra && state.modelName?.contains("85t", ignoreCase = true) == true

    fun autoPauseTitle(state: HeadphonesState) =
        if (separatesToPause(state)) "Auto-pause" else "Pause when taken off"

    fun autoPauseSupporting(state: HeadphonesState) = when {
        !state.pauseWhenRemovedAvailable -> "Not available right now"
        separatesToPause(state) -> "Pause when the earbuds are separated; bring them together to resume."
        state.isJabra -> "Pause when an earbud is removed; put it back to resume."
        else -> "Resume when you put them back on"
    }

    val presetNames = mapOf(
        0x00 to "Off", 0x01 to "Rock", 0x02 to "Pop", 0x03 to "Jazz", 0x04 to "Dance", 0x05 to "EDM",
        0x06 to "R&B/Hip-Hop", 0x07 to "Acoustic", 0x10 to "Bright", 0x11 to "Excited", 0x12 to "Mellow",
        0x13 to "Relaxed", 0x14 to "Vocal", 0x15 to "Treble Boost", 0x16 to "Bass Boost", 0x17 to "Speech",
        // Sound Connect calls the edit-in-place slot "Manual" and the saved user slots "Custom".
        0xA0 to "Manual", 0xA1 to "Custom 1", 0xA2 to "Custom 2", 0xA3 to "Custom 3", 0xA4 to "Custom 4", 0xA5 to "Custom 5",
    )

    val sixBands = listOf("Clear Bass", "400", "1k", "2.5k", "6.3k", "16k")

    val autoPowerOff = mapOf(
        0x00 to "After 5 minutes",
        0x01 to "After 30 minutes",
        0x02 to "After 1 hour",
        0x03 to "After 3 hours",
        0x10 to "When headphones are taken off",
        0x11 to "Do not turn off",
    )

    val customButton = mapOf(
        0x00 to "Ambient Sound Control",
        0x10 to "Volume control",
        0x20 to "Playback control",
        0x30 to "Voice assist function",
        0x31 to "Gemini",
        0x32 to "Amazon Alexa",
        0xFF to "No function",
    )
    val xm4CustomButtonOptions = listOf(0x00, 0x31, 0x32)

    val stcSensitivity = mapOf(0 to "Auto", 1 to "High sensitivity", 2 to "Low sensitivity")
    val stcModeOut = mapOf(
        0 to "Short (about 15 seconds)",
        1 to "Standard (about 30 seconds)",
        2 to "Long (about 1 minute)",
        3 to "Do not end automatically",
    )

    val languages = mapOf(
        1 to "English", 2 to "French", 3 to "German", 4 to "Spanish", 5 to "Italian", 6 to "Portuguese",
        7 to "Dutch", 8 to "Swedish", 9 to "Finnish", 10 to "Russian", 11 to "Japanese", 12 to "Simplified Chinese",
        13 to "Brazilian Portuguese", 14 to "Traditional Chinese", 15 to "Korean", 16 to "Turkish", 0xF0 to "Chinese",
    )

    /** Prompts the headset raises itself (ALERT_NTFY_PARAM). */
    val headsetAlerts = mapOf(
        0x01 to "Reconnects to the audio device. Change?",
        0x02 to "Reconnects to the headphones. Change?",
        0x03 to "The headphones need to disconnect to update the firmware.",
        0x04 to "Gemini is now available on your headphones.",
        0x05 to "A voice assistant can't be assigned to both functions at the same time.",
        0x06 to "Reconnects to the audio device. LDAC cannot be used while Connect to 2 devices is on. Reconnect?",
        0x07 to "The audio device is temporarily disconnected and will reconnect automatically.",
        0x08 to "Battery life is shorter while the equalizer and DSEE Extreme are both on.",
    )

    const val RECONNECT = "The headphones will reconnect and the music that is playing will stop."

    fun gsTitle(raw: String) = when (raw) {
        "TOUCH_PANEL_SETTING", "TOUCH_SENSOR_CONTROL_PANEL" -> "Touch sensor control panel"
        "MULTIPOINT_SETTING" -> "Connect to 2 devices"
        "VOICE_GUIDANCE_SETTING" -> "Voice guidance"
        "SIDETONE_SETTING" -> "Capture voice during calls"
        else -> raw.lowercase().replace('_', ' ').removeSuffix(" setting").replaceFirstChar { it.uppercase() }
    }

    fun gsSupporting(raw: String) = when (raw) {
        "MULTIPOINT_SETTING" -> "Stay connected to two devices. LDAC is not available."
        "TOUCH_PANEL_SETTING", "TOUCH_SENSOR_CONTROL_PANEL" -> "Swipe and tap the right earcup"
        else -> null
    }

    fun hex(v: Int) = "0x%02x".format(v)
}
