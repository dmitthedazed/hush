package com.occaecat.hush.jabra.firmware

import java.security.MessageDigest
import java.util.Collections

/** Values carried in the staged-partition version record, not a vendor app version. */
data class JabraFirmwareVersion(val major: Int, val minor: Int, val patch: Int, val languageId: Int) {
    init {
        require(listOf(major, minor, patch).all { it in 0..255 }) { "Invalid firmware version" }
        require(languageId in 0..65535) { "Invalid firmware language" }
    }
    val display: String get() = "$major.$minor.$patch"

    internal fun encode() = byteArrayOf(major.toByte(), minor.toByte(), patch.toByte(),
        languageId.toByte(), (languageId ushr 8).toByte(), 0, 0, 0)

    companion object {
        fun parse(version: String, languageId: Int): JabraFirmwareVersion {
            require(Regex("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}").matches(version)) { "Invalid firmware version" }
            val components = version.split('.').map(String::toInt)
            return JabraFirmwareVersion(components[0], components[1], components[2], languageId)
        }
    }
}

/** Independent implementation of the firmware checksum; this is not signature validation. */
object JabraFirmwareCrc {
    private val table = LongArray(256) { value ->
        var remainder = value.toLong() shl 24
        repeat(8) {
            remainder = ((remainder shl 1) xor if (remainder and 0x80000000L != 0L) 0xdb710641L else 0L) and 0xffffffffL
        }
        remainder
    }

    fun calculate(bytes: ByteArray, offset: Int = 0): Long {
        require(offset >= 0 && offset < bytes.size && (bytes.size - offset) % 2 == 0) {
            "Firmware checksum requires a nonempty, even-length payload"
        }
        var crc = 0L
        fun accept(value: Int) {
            crc = ((crc shl 8) xor table[((crc ushr 24) and 255).toInt()] xor value.toLong()) and 0xffffffffL
        }
        var end = bytes.size
        while (end > offset) {
            accept(bytes[end - 2].toInt() and 255)
            accept(bytes[end - 1].toInt() and 255)
            end -= 2
        }
        repeat(4) { accept(255) }
        return crc
    }
}

/** Owns a defensive copy of checked bytes. No mutable array is exposed to callers. */
class JabraFirmwareFile private constructor(
    val partition: Int,
    val version: JabraFirmwareVersion,
    val crc: Long,
    private val payload: ByteArray,
    val crcOffset: Int,
) {
    val size: Int get() = payload.size
    val sha256: String = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    internal val chunkCount: Int get() = (size + 51) / 52
    internal fun chunk(index: Int): ByteArray {
        require(index in 0 until chunkCount)
        val start = index * 52
        val count = minOf(52, size - start)
        return byteArrayOf(index.toByte(), (index ushr 8).toByte(), count.toByte(), 0) +
            payload.copyOfRange(start, start + count)
    }

    companion object {
        fun verified(partition: Int, version: JabraFirmwareVersion, crc: Long, bytes: ByteArray, crcOffset: Int = 0): JabraFirmwareFile {
            require(partition in 0..255) { "Invalid firmware partition" }
            require(crc in 0..0xffffffffL) { "Invalid firmware checksum" }
            require(crcOffset == 0 || crcOffset == 52) { "Unsupported signed firmware header" }
            require(bytes.size in 1..(65535 * 52)) { "Firmware partition is too large" }
            val owned = bytes.copyOf()
            require(JabraFirmwareCrc.calculate(owned, crcOffset) == crc) { "Firmware checksum does not match the manifest" }
            return JabraFirmwareFile(partition, version, crc, owned, crcOffset)
        }
    }
}

/** Validated, immutable applicability and ordered files for one product and language. */
class JabraFirmwarePackage private constructor(
    val productId: Int,
    val version: String,
    val languageId: Int,
    val files: List<JabraFirmwareFile>,
    val preloadCount: Int,
    val partialUploadAllowed: Boolean,
    val packageId: String,
) {
    val totalBytes: Long get() = files.sumOf { it.size.toLong() }

    companion object {
        fun verified(productId: Int, version: String, languageId: Int, files: List<JabraFirmwareFile>,
                     preloadCount: Int, partialUploadAllowed: Boolean, packageId: String): JabraFirmwarePackage {
            val expected = when (productId) {
                11846 -> setOf(0)
                9421 -> setOf(0, 1, 4, 5, 6, 7, 9, 254)
                else -> error("Unsupported Jabra firmware product")
            }
            require(languageId in setOf(0x0407, 0x0409, 0x040c, 0x0411, 0x0412, 0x0804)) { "Unsupported firmware language" }
            val target = JabraFirmwareVersion.parse(version, languageId)
            require(target.major != 0 || target.minor != 0 || target.patch != 0) { "Invalid target firmware version" }
            require(files.size == expected.size && files.map { it.partition }.toSet() == expected) { "Incomplete or unsupported firmware partition layout" }
            require(files.all { it.version.display == target.display && it.version.languageId in setOf(0, languageId) }) {
                "Firmware files do not match the selected version and language"
            }
            require(preloadCount in 1..100) { "Unsupported firmware preload count" }
            require(packageId.isNotBlank() && packageId.length <= 512) { "Missing firmware package identity" }
            return JabraFirmwarePackage(productId, target.display, languageId,
                Collections.unmodifiableList(ArrayList(files)), preloadCount, partialUploadAllowed, packageId)
        }
    }
}

data class JabraFirmwareReadiness(
    val productId: Int,
    val currentVersion: String,
    val languageId: Int?,
    val protocols: Set<Int>,
    val locked: Boolean,
    val blockers: List<String>,
    val batteryLevels: Map<String, Int?> = emptyMap(),
    val startConditionFlags: Int? = null,
) {
    val ready: Boolean get() = blockers.isEmpty()
}

enum class JabraFirmwareStage { PREPARING, ERASING, TRANSFERRING, VERIFYING, COMMITTING, AWAITING_CASE }
data class JabraFirmwareProgress(val stage: JabraFirmwareStage, val percent: Int,
                                 val partition: Int? = null, val message: String? = null)
enum class JabraFirmwareResult { AWAITING_CASE }
