package com.occaecat.hush.devices

/**
 * Who provides a capability: the device itself, or the phone standing in for hardware the device lacks (an EQ on
 * the output mix, Android's own battery reading). The UI marks PHONE capabilities; it never asks which brand it is.
 */
enum class Source { DEVICE, PHONE }

/** Something a device can do. [providedBy] PHONE = emulated on the phone. */
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

/**
 * The capability model of a controller, read from what it exposes. Scenes, the mic test and insights are the
 * phone's by nature; the sleep timer and volume fall back to the phone's music stream.
 */
val DeviceController.capabilities: Capabilities get() {
    val s = state.value
    return Capabilities(buildMap {
        noise?.let { put(Feature.NOISE, it.providedBy) }
        equalizer?.let { put(Feature.EQUALIZER, it.providedBy); put(Feature.AUTO_EQ, it.providedBy) }
        s.batteries.firstOrNull()?.let { put(Feature.BATTERY, it.providedBy) }
        put(Feature.VOLUME, if (s.volumeSteps > 0) Source.DEVICE else Source.PHONE)
        put(Feature.SLEEP_TIMER, sleepTarget?.providedBy ?: Source.PHONE)
        firmware?.let { put(Feature.FIRMWARE, it.providedBy) }
        microphone?.let { put(Feature.MICROPHONE, Source.PHONE) }
        if (noise != null || equalizer != null) put(Feature.SCENES, Source.PHONE)
        insights?.let { put(Feature.INSIGHTS, Source.PHONE) }
        quick?.let { put(Feature.WIDGETS, Source.PHONE) }
    })
}

/** One line for a capability the phone emulates, for the place that shows it. */
fun Feature.phoneNote(): String = when (this) {
    Feature.EQUALIZER, Feature.AUTO_EQ -> "Runs on this phone, on everything it plays"
    Feature.BATTERY -> "As reported to Android"
    Feature.SLEEP_TIMER, Feature.VOLUME -> "Uses this phone's volume"
    else -> "On this phone"
}
