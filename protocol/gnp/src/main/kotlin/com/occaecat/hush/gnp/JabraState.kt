package com.occaecat.hush.gnp

/** Values confirmed by the current GNP session. Null means the device did not report the setting. */
data class JabraState(
    val model: JabraModel? = null,
    val firmware: String? = null,
    val serial: String? = null,
    val batteries: List<BatteryComponent> = emptyList(),
    val noise: JabraNoiseState? = null,
    val noiseCapabilities: JabraNoiseCapabilities = JabraNoiseCapabilities(),
    val ancLevel: Int? = null,
    val eq: JabraEqState? = null,
    val eqCaps: EqCapability? = null,
    val pauseWhenRemoved: Boolean? = null,
    val sleepMinutes: Int? = null,
    val announcementMode: Int? = null,
    val buttonsState: JabraButtonState? = null,
    val leftOnHead: Boolean? = null,
    val rightOnHead: Boolean? = null,
    val error: String? = null,
) {
    val battery: Int? get() = batteries.filter { it.id != "case" }.mapNotNull { it.level }.minOrNull()
    val charging: Boolean get() = batteries.filter { it.id != "case" }.let { it.isNotEmpty() && it.all { part -> part.charging == true } }
}

data class JabraNoiseState(
    val mode: AmbientMode,
    val ambientLevel: Int = 0,
    val ancLevel: Int? = null,
)

data class JabraNoiseCapabilities(
    val modes: Set<AmbientMode> = emptySet(),
    val ambientMax: Int = 0,
    val ancMax: Int = 0,
)

data class JabraEqState(val levels: List<Int>, val enabled: Boolean)
