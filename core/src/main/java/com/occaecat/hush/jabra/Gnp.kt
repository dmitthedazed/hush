package com.occaecat.hush.jabra

/** GNP packs the packet kind and its total length into the fourth byte. */
internal data class GnpFrame(
    val destination: Int,
    val source: Int,
    val sequence: Int,
    val kind: Int,
    val command: Int,
    val subcommand: Int,
    val payload: ByteArray = byteArrayOf(),
) {
    val topic: Int get() = (command shl 8) or subcommand

    fun encode(): ByteArray {
        require(kind in 0..3)
        require(listOf(destination, source, sequence, command, subcommand).all { it in 0..255 })
        require(payload.size <= 57) { "GNP frames cannot exceed 63 bytes" }
        return byteArrayOf(destination.toByte(), source.toByte(), sequence.toByte(),
            ((kind shl 6) or (6 + payload.size)).toByte(), command.toByte(), subcommand.toByte()) + payload
    }

    /** ACK/NAK use their own sequence and echo the original request's header. */
    fun acknowledges(request: GnpFrame): Boolean {
        if (kind != RESPONSE) return false
        if (destination != request.source || source != request.destination) return false
        val echo = when (command) {
            ACK -> byteArrayOf(subcommand.toByte()) + payload
            NAK -> payload
            else -> return sequence == request.sequence && topic == request.topic
        }
        return echo.size >= 5 && echo[0].u() == request.destination && echo[1].u() == request.source &&
            echo[2].u() == request.sequence && echo[3].u() == request.encode()[3].u() &&
            echo[4].u() == request.command
    }

    companion object {
        const val EVENT = 0
        const val READ = 1
        const val WRITE = 2
        const val RESPONSE = 3
        const val ACK = 0xff
        const val NAK = 0xfe

        fun request(sequence: Int, kind: Int, topic: Int, payload: ByteArray = byteArrayOf()) =
            GnpFrame(4, 9, sequence, kind, topic ushr 8, topic and 255, payload)
    }
}

/** Session-local stream decoder. A partial packet occupies at most 62 bytes. */
internal class GnpParser {
    private var partial = byteArrayOf()

    fun feed(bytes: ByteArray, count: Int = bytes.size): List<GnpFrame> {
        require(count in 0..bytes.size)
        val data = partial + bytes.copyOf(count)
        val result = mutableListOf<GnpFrame>()
        var at = 0
        while (at < data.size) {
            // Incoming packets address application endpoint 9. This also bounds resynchronization.
            if (data[at].u() != 9) { at++; continue }
            if (data.size - at < 4) break
            val length = data[at + 3].u() and 63
            if (length !in 6..63) { at++; continue }
            if (data.size - at < length) break
            result += GnpFrame(data[at].u(), data[at + 1].u(), data[at + 2].u(),
                data[at + 3].u() ushr 6, data[at + 4].u(), data[at + 5].u(),
                data.copyOfRange(at + 6, at + length))
            at += length
        }
        partial = data.copyOfRange(at, data.size)
        return result
    }
}

internal fun Byte.u(): Int = toInt() and 255
