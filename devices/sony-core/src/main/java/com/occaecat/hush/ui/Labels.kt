package com.occaecat.hush.ui

/** User-facing wording, following Sound Connect's strings where it has them. */
object Labels {
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
        0x33 to "Tencent Xiaowei",
        0xFF to "No function",
    )
    /** What the WH-1000XM4 offers when it does not list its CUSTOM button's presets. */
    val xm4CustomButtonOptions = listOf(0x00, 0x31, 0x32)

    /** AssignableSettingsKey: what each reassignable key is called. */
    val assignableKey = mapOf(0x00 to "Left earbud", 0x01 to "Right earbud", 0x02 to "CUSTOM button", 0x03 to "C button")

    /** SoundPositionPresetId (Sound Position Control). */
    val soundPosition = mapOf(
        0x00 to "Off", 0x01 to "Front left", 0x02 to "Front right", 0x03 to "Front", 0x11 to "Rear left", 0x12 to "Rear right",
    )

    /** VptPresetId names for when the headset sends none. */
    val surround = mapOf(0x00 to "Off", 0x01 to "Outdoor stage", 0x02 to "Arena", 0x03 to "Concert hall", 0x04 to "Club")

    /** UpscalingType: what the upscaling switch is called on this model. */
    val upscaling = mapOf(0x00 to "DSEE HX", 0x01 to "DSEE", 0x02 to "DSEE Extreme")

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

    /** General setting titles, after Sound Connect's GsTitleTitleResourceMap. */
    fun gsTitle(raw: String) = when (raw) {
        "TOUCH_PANEL_SETTING", "TOUCH_SENSOR_CONTROL_PANEL" -> "Touch sensor control panel"
        "MULTIPOINT_SETTING" -> "Connect to 2 devices"
        "VOICE_GUIDANCE_SETTING" -> "Voice guidance language"
        "SIDETONE_SETTING" -> "Capture voice during calls"
        "ASSIGNABLE_KEY_SETTING" -> "NC/AMBIENT button"
        "ASSIGNABLE_KEY_SETTING_FOR_SEPARATED" -> "Left button"
        "ASSIGNABLE_KEY_SETTING_FOR_SEPARATED_R" -> "Right button"
        "ASSIGNABLE_KEY_SETTING_NC" -> "NC button"
        "ASSIGNABLE_KEY_SETTING_NCAMB" -> "NC/AMB button"
        "ASSIGNABLE_KEY_SETTING_CUSTOM" -> "CUSTOM button"
        "ASSIGNABLE_KEY_SETTING_C" -> "C button"
        "FACETAP_SETTING", "SELECTABLE_FACETAP_SETTING" -> "Wide Area Tap"
        "TWS_ONE_SIDE_USE_NCASM_SETTING" -> "Noise canceling with one earbud"
        "TAP_SENSITIVITY_SETTING" -> "Tap sensitivity"
        else -> pretty(raw).removeSuffix(" setting")
    }

    fun gsSupporting(raw: String) = when (raw) {
        "MULTIPOINT_SETTING" -> "Stay connected to two devices. LDAC is not available."
        "TOUCH_PANEL_SETTING", "TOUCH_SENSOR_CONTROL_PANEL" -> "Swipe and tap the right earcup"
        "FACETAP_SETTING", "SELECTABLE_FACETAP_SETTING" -> "Also detect taps between your cheeks and ears"
        "TWS_ONE_SIDE_USE_NCASM_SETTING" -> "Keep the noise control when you wear only one earbud"
        else -> if (raw.startsWith("ASSIGNABLE_KEY_SETTING")) "What the button does" else null
    }

    /** A LIST_TYPE general setting's choice (GsElementResource). */
    fun gsOption(raw: String) = when (raw) {
        "ASSIGNABLE_KEY_ELEM_NCASM" -> "Ambient Sound Control"
        "ASSIGNABLE_KEY_ELEM_NC" -> "Noise canceling"
        "ASSIGNABLE_KEY_ELEM_GOOGLE_ASSISTANT" -> "Google Assistant"
        "ASSIGNABLE_KEY_ELEM_AMAZON_ALEXA" -> "Amazon Alexa"
        "ASSIGNABLE_KEY_ELEM_TENCENT_XIAOWEI" -> "Tencent Xiaowei"
        "ASSIGNABLE_KEY_ELEM_VR" -> "Voice assist function"
        "ASSIGNABLE_KEY_ELEM_PLAYBACK_CONTROL" -> "Playback control"
        "ASSIGNABLE_KEY_ELEM_NONE" -> "No function"
        else -> pretty(raw.substringAfter("_ELEM_"))
    }

    private fun pretty(raw: String) = raw.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Band label from its centre frequency (EQEBB extended info), to two digits: 400, 1k, 2.5k, 16k (from 15662 Hz). */
    fun bandLabel(hz: Int): String {
        if (hz < 1000) return "$hz"
        val k = hz / 1000f
        val r = if (k >= 10) Math.round(k).toFloat() else Math.round(k * 10) / 10f
        return if (r == r.toInt().toFloat()) "${r.toInt()}k" else "${r}k"
    }

    fun hex(v: Int) = "0x%02x".format(v)
}
