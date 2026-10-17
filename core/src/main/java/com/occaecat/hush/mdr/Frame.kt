package com.occaecat.hush.mdr

import java.io.ByteArrayOutputStream

/**
 * Sony "Tandem" link-layer framing, as used by Sound Connect (qm0.a / qm0.b / cp0.b):
 *
 *   0x3E | escape( type | seq | len(4, BE) | payload | sum8 ) | 0x3C
 *
 * The checksum is the low byte of the sum of every unescaped byte before it.
 * Bytes 0x3C/0x3D/0x3E inside the body are sent as 0x3D followed by (byte & ~0x10).
 */
object Frame {
    const val START: Byte = 0x3E
    const val END: Byte = 0x3C
    const val ESC: Byte = 0x3D

    object Type {
        const val DATA: Byte = 0x00
        const val ACK: Byte = 0x01
        const val DATA_MDR: Byte = 0x0C
        const val DATA_COMMON: Byte = 0x0D
        const val DATA_MDR_NO2: Byte = 0x0E
    }

    fun encode(type: Byte, seq: Byte, payload: ByteArray): ByteArray {
        val body = ByteArrayOutputStream(payload.size + 7).apply {
            write(type.toInt())
            write(seq.toInt())
            write(payload.size ushr 24)
            write(payload.size ushr 16)
            write(payload.size ushr 8)
            write(payload.size)
            write(payload)
        }.toByteArray()
        val withSum = body + checksum(body, body.size)
        val out = ByteArrayOutputStream(withSum.size + 8)
        out.write(START.toInt())
        out.write(escape(withSum))
        out.write(END.toInt())
        return out.toByteArray()
    }

    fun ack(seq: Byte): ByteArray = encode(Type.ACK, seq, ByteArray(0))

    fun checksum(bytes: ByteArray, length: Int): Byte {
        var sum = 0
        for (i in 0 until length) sum += bytes[i].toInt() and 0xFF
        return sum.toByte()
    }

    fun escape(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size + 4)
        for (b in bytes) {
            when (b) {
                END, ESC, START -> {
                    out.write(ESC.toInt())
                    out.write(b.toInt() and 0xEF)
                }
                else -> out.write(b.toInt())
            }
        }
        return out.toByteArray()
    }

    fun unescape(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i]
            if (b == ESC) {
                i++
                require(i < bytes.size) { "ESC byte at end of frame" }
                out.write(bytes[i].toInt() or 0x10)
            } else {
                out.write(b.toInt())
            }
            i++
        }
        return out.toByteArray()
    }
}

data class ReceivedFrame(val type: Byte, val seq: Byte, val payload: ByteArray)

/** Incremental parser: feed raw socket bytes, get whole, checksum-verified frames back. */
class FrameParser {
    private val buffer = ByteArrayOutputStream()
    private var inFrame = false

    fun feed(data: ByteArray, length: Int = data.size): List<ReceivedFrame> {
        val frames = mutableListOf<ReceivedFrame>()
        for (i in 0 until length) {
            val b = data[i]
            if (!inFrame) {
                if (b == Frame.START) {
                    inFrame = true
                    buffer.reset()
                }
                continue
            }
            if (b == Frame.END) {
                decode(buffer.toByteArray())?.let(frames::add)
                buffer.reset()
                inFrame = false
            } else {
                buffer.write(b.toInt())
            }
        }
        return frames
    }

    private fun decode(escaped: ByteArray): ReceivedFrame? {
        val raw = runCatching { Frame.unescape(escaped) }.getOrNull() ?: return null
        if (raw.size < 7) return null
        if (raw.last() != Frame.checksum(raw, raw.size - 1)) return null
        val len = ((raw[2].toInt() and 0xFF) shl 24) or ((raw[3].toInt() and 0xFF) shl 16) or
            ((raw[4].toInt() and 0xFF) shl 8) or (raw[5].toInt() and 0xFF)
        if (len < 0 || 6 + len > raw.size - 1) return null
        return ReceivedFrame(raw[0], raw[1], raw.copyOfRange(6, 6 + len))
    }
}
