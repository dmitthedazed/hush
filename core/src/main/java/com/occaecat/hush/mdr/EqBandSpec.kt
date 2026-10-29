package com.occaecat.hush.mdr

import kotlin.math.roundToInt

/** UI and storage describe levels in the device's exact units; only the backend encodes them. */
data class EqBandSpec(
    val label: String,
    val frequencyHz: Double?,
    val min: Int,
    val max: Int,
    val neutral: Int,
    val unitsPerDb: Double = 1.0,
    val uiStep: Int = 1,
) {
    init {
        require(min <= neutral && neutral <= max)
        require(unitsPerDb.isFinite() && unitsPerDb > 0 && uiStep > 0)
    }

    fun toDb(raw: Int): Double = (raw - neutral) / unitsPerDb
    fun fromDb(db: Double): Int =
        (neutral + (db * unitsPerDb / uiStep).roundToInt() * uiStep).coerceIn(min, max)

    companion object {
        fun sony(count: Int, steps: Int): List<EqBandSpec> {
            val labels = listOf("Clear Bass", "400 Hz", "1 kHz", "2.5 kHz", "6.3 kHz", "16 kHz")
            val frequencies = listOf(null, 400.0, 1000.0, 2500.0, 6300.0, 16000.0)
            return List(count.coerceAtLeast(0)) { i ->
                EqBandSpec(
                    if (count == 6) labels[i] else "Band ${i + 1}",
                    if (count == 6) frequencies[i] else null,
                    0, (steps - 1).coerceAtLeast(0), ((steps - 1) / 2).coerceAtLeast(0),
                )
            }
        }
    }
}
