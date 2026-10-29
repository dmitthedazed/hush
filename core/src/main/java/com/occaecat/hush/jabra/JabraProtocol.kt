package com.occaecat.hush.jabra

import com.occaecat.hush.BatteryComponent
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.EqBandSpec
import com.occaecat.hush.mdr.EqCapability
import java.io.IOException

internal enum class JabraModel(val productId: Int, val label: String) {
    ELITE_10(11846, "Jabra Elite 10"),
    ELITE_85T(9421, "Jabra Elite 85t");

    val pauseTopic: Int get() = if (this == ELITE_10) 0x138e else 0x1383
    val pauseRead: ByteArray get() = if (this == ELITE_10) byteArrayOf(0) else byteArrayOf()
    fun pauseWrite(enabled: Boolean): ByteArray = if (this == ELITE_10)
        byteArrayOf(0, if (enabled) 1 else 0) else byteArrayOf(if (enabled) 0 else 1)

    fun parsePause(payload: ByteArray): Boolean? = if (this == ELITE_10) {
        if (payload.size == 2 && payload[0] == 0.toByte()) payload[1].asBoolean() else null
    } else payload.singleOrNull()?.asBoolean()?.not()
}

internal object JabraProtocol {
    const val PRODUCT_ID = 0x0211
    const val FIRMWARE = 0x0203
    const val SERIAL = 0x0201
    const val BATTERY = 0x1202
    const val AMBIENCE = 0x13be
    const val EQ = 0x137d
    const val EQ_ENABLED = 0x137e
    const val SLEEP = 0x1390
    const val ANNOUNCEMENTS = 0x133a
    const val EVENTS = 0x0d4c
    val sleepMinutes = listOf(0, 15, 30, 60, 120)

    /** Device-reported event masks are 32-bit big-endian blocks, with no count prefix. */
    fun eventSubscription(supported: ByteArray): ByteArray? {
        if (supported.size !in 4..56 || supported.size % 4 != 0) return null
        // Voice, on-head, configuration, earbud-link, and ANC/HearThrough events.
        // Products reject unsupported bits; the device response takes precedence over model XML.
        val wanted = byteArrayOf(0, 0, 2, 0x96.toByte())
        return ByteArray(supported.size) { i ->
            if (i < wanted.size) (supported[i].u() and wanted[i].u()).toByte() else 0
        }
    }

    fun model(payload: ByteArray): JabraModel {
        if (payload.size != 2) throw IOException("Invalid Jabra product identity")
        val pid = payload[0].u() or (payload[1].u() shl 8)
        return JabraModel.entries.firstOrNull { it.productId == pid }
            ?: throw IOException("Jabra product $pid is not supported; choose an original Elite 10 or Elite 85t")
    }

    fun text(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val length = payload[0].u()
        if (length == 0 || length > payload.size - 1) return null
        return payload.copyOfRange(1, 1 + length).toString(Charsets.UTF_8).trimEnd('\u0000').takeIf { it.isNotBlank() }
    }

    fun mode(value: Int): AmbientMode? = when (value) {
        1 -> AmbientMode.OFF
        2 -> AmbientMode.AMBIENT
        4 -> AmbientMode.NOISE_CANCELLING
        else -> null
    }

    fun modeValue(mode: AmbientMode): Int = when (mode) {
        AmbientMode.OFF -> 1
        AmbientMode.AMBIENT -> 2
        AmbientMode.NOISE_CANCELLING -> 4
    }

    /** Firmware can echo the requested selector; Elite 10 captures also show compact scalar replies. */
    fun indexedScalar(payload: ByteArray, index: Int): Int? = when {
        payload.size == 1 -> payload[0].u()
        payload.size == 2 && payload[0].u() == index -> payload[1].u()
        else -> null
    }

    data class AmbienceCapabilities(val modes: Set<AmbientMode>, val ambientMax: Int, val ancMax: Int)

    fun ambienceCapabilities(payload: ByteArray): AmbienceCapabilities? {
        val data = if (payload.size >= 4 && payload[0] == 0.toByte()) payload.drop(1) else payload.toList()
        if (data.size < 3) return null
        val modes = listOf(1, 2, 4).filter { data[0].u() and it != 0 }.mapNotNull(::mode).toSet()
        if (modes.isEmpty()) return null
        return AmbienceCapabilities(modes, data[1].u(), data[2].u())
    }

    data class Battery(val components: List<BatteryComponent>) {
        val aggregate: Int? get() = components.filter { it.id != "case" }.mapNotNull { it.level }.minOrNull()
        val charging: Boolean get() = components.filter { it.id != "case" }.let { earbuds ->
            earbuds.isNotEmpty() && earbuds.all { it.charging == true }
        }
    }

    fun battery(payload: ByteArray): Battery? {
        if (payload.size < 2) return null
        val flags = payload[0].u()
        val hasExtra = flags and 0x80 != 0
        if (hasExtra && payload.size < 5) return null
        val count = if (hasExtra) payload[4].u() else 0
        if (hasExtra && payload.size != 5 + count * 3) return null
        val components = mutableListOf<BatteryComponent>()
        val entries = (0 until count).map { 5 + it * 3 }
        val hasOtherEarbud = entries.any { payload[it].u() in listOf(4, 5) }
        val charging = flags and 1 != 0
        components += BatteryComponent("primary", if (hasOtherEarbud) "Earbud 1" else "Earbuds",
            payload[1].u().takeIf { it <= 100 }, charging)
        entries.forEach { at ->
            val id = payload[at].u()
            val level = payload[at + 2].u().takeIf { it <= 100 }
            when (id) {
                4, 5 -> components += BatteryComponent("secondary", "Earbud 2", level)
                0x15 -> components += BatteryComponent("case", "Case", level)
            }
        }
        return Battery(components.distinctBy { it.id })
    }
}

/** Keeps the unedited acoustic parameters intact; only gains are changed by Hush. */
internal data class JabraEqualizer(val masterGain: Int, val bands: List<Band>) {
    data class Band(val maximumQuarterDb: Int, val frequency: Int, val gain: Int, val q: Int) {
        val maxGain: Int get() = maximumQuarterDb * 15
    }

    fun capability(supportsEnable: Boolean = true) = EqCapability(
        bandCount = bands.size,
        levelSteps = bands.maxOf { it.maxGain } * 2 + 1,
        presets = emptyList(),
        namespace = "jabra-gnp",
        bandSpecs = bands.map {
            val hz = it.frequency / 3.0
            EqBandSpec(if (hz >= 1000) "${formatNumber(hz / 1000)}k" else formatNumber(hz),
                hz, -it.maxGain, it.maxGain, 0, 60.0, 30)
        },
        supportsEnable = supportsEnable,
    )

    fun write(gains: List<Int>): ByteArray {
        require(gains.size == bands.size) { "The equalizer does not match these headphones" }
        require(gains.zip(bands).all { (gain, band) -> gain in -band.maxGain..band.maxGain }) {
            "Equalizer gain is outside the headphone's range"
        }
        val result = ArrayList<Byte>()
        result += bands.size.toByte()
        result += 0
        result.addWord(masterGain)
        bands.zip(gains).forEach { (band, gain) ->
            result.addWord(band.frequency)
            result.addWord(gain)
            result.addWord(band.q)
        }
        return result.toByteArray()
    }

    companion object {
        fun parse(payload: ByteArray): JabraEqualizer? {
            if (payload.size < 3) return null
            val count = payload[0].u()
            if (count !in 1..5 || payload.size != 3 + 7 * count) return null
            val bands = (0 until count).map { i ->
                val at = 3 + 7 * i
                Band(payload[at].u(), payload.word(at + 1), payload.word(at + 3).toShort().toInt(), payload.word(at + 5))
            }
            if (bands.any { it.maximumQuarterDb == 0 || it.frequency !in 60..60000 || it.q !in 1024..32768 ||
                    it.gain !in -it.maxGain..it.maxGain }) return null
            return JabraEqualizer(payload.word(1).toShort().toInt(), bands)
        }

        private fun formatNumber(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString()
            else String.format(java.util.Locale.ROOT, "%.1f", value)
    }
}

private fun ByteArray.word(at: Int) = (this[at].u() shl 8) or this[at + 1].u()
private fun MutableList<Byte>.addWord(value: Int) { add((value shr 8).toByte()); add(value.toByte()) }
internal fun Byte.asBoolean(): Boolean? = when (u()) { 0 -> false; 1 -> true; else -> null }
