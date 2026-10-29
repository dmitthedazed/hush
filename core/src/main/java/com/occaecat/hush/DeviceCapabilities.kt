package com.occaecat.hush

import com.occaecat.hush.mdr.AmbientMode

enum class HeadphoneBrand { SONY, JABRA, UNKNOWN }

data class BatteryComponent(
    val id: String,
    val label: String,
    val level: Int?,
    val charging: Boolean? = null,
)

data class DeviceCapabilities(
    val noiseModes: Set<AmbientMode> = emptySet(),
    val ambientMin: Int = 0,
    val ambientMax: Int = 0,
    val ancMax: Int = 0,
    val voiceFocus: Boolean = false,
    val powerOff: Boolean = false,
    val optimizer: Boolean = false,
    val vendorGuide: Boolean = false,
) {
    val ambientLevels: Boolean get() = ambientMax > ambientMin
    companion object {
        val SONY = DeviceCapabilities(
            noiseModes = AmbientMode.entries.toSet(), ambientMin = 1, ambientMax = 20,
            voiceFocus = true, powerOff = true, optimizer = true, vendorGuide = true,
        )
    }
}

/** Names aid routing; a Jabra backend verifies the product id before enabling writes. */
fun headphoneBrand(vararg names: String?): HeadphoneBrand {
    if (names.any { it?.contains("Jabra", ignoreCase = true) == true }) return HeadphoneBrand.JABRA
    // Sound+ and Fast Pair can replace the manufacturer prefix while retaining the model name.
    if (names.any { it?.contains(Regex("(?i)(?:^|\\s)Elite\\s*(?:10|85t)(?:\\s|$)")) == true })
        return HeadphoneBrand.JABRA
    if (names.any { it?.contains("Sony", ignoreCase = true) == true ||
            it?.contains(Regex("(?i)(?:^|\\s)(WH|WF|WI|MDR)-")) == true }) return HeadphoneBrand.SONY
    return HeadphoneBrand.UNKNOWN
}
