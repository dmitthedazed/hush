package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.FrameParser
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.sim.Profiles
import org.junit.Test
import kotlin.random.Random

/** Random, truncated and over-long payloads for every opcode never throw out of the parser or the framer. */
class MdrV1FuzzTest {
    private val random = Random(0x5EED)

    private fun parseAll(table: Table, payloads: Sequence<ByteArray>) {
        for (p in payloads) {
            try {
                MdrV1.parse(table, p)
            } catch (e: Throwable) {
                throw AssertionError("parse threw on $table ${p.joinToString(" ") { "%02x".format(it) }}", e)
            }
        }
    }

    @Test
    fun randomPayloadsForEveryOpcode() {
        for ((table, codes) in listOf(Table.ONE to Command1.entries.map { it.code }, Table.TWO to Command2.entries.map { it.code })) {
            parseAll(table, codes.asSequence().flatMap { code ->
                generateSequence { byteArrayOf(code.toByte()) + random.nextBytes(random.nextInt(0, 48)) }.take(500)
            })
        }
    }

    @Test
    fun truncatedAndExtendedRealReplies() {
        val replies = Profiles.xm4() + Profiles.xm3() + Profiles.wf1000xm3()
        for (r in replies) {
            val p = r.payload
            parseAll(r.table, (1 until p.size).asSequence().map { p.copyOf(it) })
            parseAll(r.table, sequenceOf(p + random.nextBytes(1), p + random.nextBytes(40), p + ByteArray(300) { 0xFF.toByte() }))
            // Every byte after the opcode set to the extremes: list counts of 0 and 255, lengths past the end.
            parseAll(r.table, (1 until p.size).asSequence().flatMap { i ->
                sequenceOf(0x00, 0x01, 0x7F, 0x80, 0xFF).map { v -> p.copyOf().also { it[i] = v.toByte() } }
            })
        }
    }

    @Test
    fun framerTakesAnyBytes() {
        val parser = FrameParser()
        repeat(2_000) {
            val chunk = random.nextBytes(random.nextInt(1, 256))
            // Plenty of frame delimiters, so half-frames, escapes at the end and bad lengths all come up.
            for (i in chunk.indices) if (random.nextInt(8) == 0) chunk[i] = listOf<Byte>(0x3C, 0x3D, 0x3E)[random.nextInt(3)]
            parser.feed(chunk, chunk.size)
        }
    }
}
