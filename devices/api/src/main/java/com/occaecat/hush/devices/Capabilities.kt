package com.occaecat.hush.devices

/**
 * Who provides a capability. The UI marks anything that isn't plain hardware; it never asks which brand it is.
 *
 * - [DEVICE]: the headphones do it themselves (noise cancelling, a hardware EQ).
 * - [PHONE]: the phone stands in for hardware the device lacks (an EQ on the output mix, Android's battery reading).
 * - [HYBRID]: the phone does the thinking and the headphones do the work (AutoEq fitted onto hardware bands, a sleep
 *   timer that fades and then switches the headphones off, scenes switching their noise control).
 */
enum class Source { DEVICE, PHONE, HYBRID }

/** Something a device can do. [providedBy] PHONE = emulated on the phone; HYBRID = phone logic on device hardware. */
interface Capability {
    val providedBy: Source get() = Source.DEVICE
}

/** The universal features (see docs/capabilities.md). Device-only ones are settings rows and sections instead. */
enum class Feature(val title: String) {
    NOISE("Noise control"),
    EQUALIZER("Equalizer"),
    AUTO_EQ("AutoEq"),
    BATTERY("Battery"),
    VOLUME("Volume"),
    SLEEP_TIMER("Sleep timer"),
    FIRMWARE("Firmware"),
    MICROPHONE("Microphone"),
    SCENES("Scenes and automation"),
    INSIGHTS("Insights"),
    WIDGETS("Widgets"),
}

/** What a device can do right now, and who does each part. */
data class Capabilities(val sources: Map<Feature, Source>) {
    operator fun contains(feature: Feature) = feature in sources
    fun source(feature: Feature): Source? = sources[feature]
    fun emulated(feature: Feature) = sources[feature] == Source.PHONE
}

/** Phone logic acting on [on]: hybrid when the device does the work, the phone's alone otherwise. */
private fun driving(on: Source?) = if (on == Source.DEVICE || on == Source.HYBRID) Source.HYBRID else Source.PHONE

/**
 * The capability model of a controller, read from what it exposes. The mic test and insights are the phone's by
 * nature; AutoEq, scenes and widgets are phone logic, hybrid when they drive hardware; the sleep timer and volume
 * fall back to the phone's music stream.
 */
val DeviceController.capabilities: Capabilities get() {
    val s = state.value
    return Capabilities(buildMap {
        val noise = noise?.providedBy
        val eq = equalizer?.providedBy
        noise?.let { put(Feature.NOISE, it) }
        eq?.let { put(Feature.EQUALIZER, it); put(Feature.AUTO_EQ, driving(it)) }
        s.batteries.firstOrNull()?.let { put(Feature.BATTERY, it.providedBy) }
        put(Feature.VOLUME, volumeSource ?: if (s.volumeSteps > 0) Source.DEVICE else Source.PHONE)
        put(Feature.SLEEP_TIMER, sleepTarget?.providedBy ?: Source.PHONE)
        firmware?.let { put(Feature.FIRMWARE, it.providedBy) }
        microphone?.let { put(Feature.MICROPHONE, Source.PHONE) }
        if (noise != null || eq != null) put(Feature.SCENES, driving(noise ?: eq))
        insights?.let { put(Feature.INSIGHTS, Source.PHONE) }
        quick?.let { put(Feature.WIDGETS, driving(noise ?: eq)) }
    })
}

/** One line for a capability that isn't plain hardware, for the place that shows it; null for [Source.DEVICE]. */
fun Feature.note(source: Source): String? = when (source) {
    Source.DEVICE -> null
    Source.PHONE -> phoneNote()
    Source.HYBRID -> when (this) {
        Feature.AUTO_EQ -> "Fitted on this phone, played by your headphones"
        Feature.SLEEP_TIMER -> "Fades on this phone, then switches your headphones off"
        Feature.SCENES -> "This phone decides, your headphones switch"
        Feature.WIDGETS -> "On your home screen, switching your headphones"
        else -> "This phone and your headphones together"
    }
}

/** One line for a capability the phone emulates, for the place that shows it. */
fun Feature.phoneNote(): String = when (this) {
    Feature.EQUALIZER, Feature.AUTO_EQ -> "Runs on this phone, on everything it plays"
    Feature.BATTERY -> "As reported to Android"
    Feature.SLEEP_TIMER, Feature.VOLUME -> "Uses this phone's volume"
    Feature.MICROPHONE -> "Records through the headphones so you hear how you sound"
    Feature.INSIGHTS -> "Battery and listening history, kept on this phone"
    Feature.SCENES -> "Switches this phone's equalizer and volume"
    Feature.WIDGETS -> "On your home screen"
    else -> "On this phone"
}
