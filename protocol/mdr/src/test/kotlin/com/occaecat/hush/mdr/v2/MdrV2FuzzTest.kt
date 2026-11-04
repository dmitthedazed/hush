package com.occaecat.hush.mdr.v2

import com.occaecat.hush.mdr.Table
import com.occaecat.hush.sim.Profiles
import org.junit.Test
import kotlin.random.Random

/** Random, truncated and over-long v2 payloads for every opcode never throw out of the parser. */
class MdrV2FuzzTest {
    private val random = Random(0x5EED2)

    private fun parseAll(table: Table, payloads: Sequence<ByteArray>) {
        for (p in payloads) {
            try {
                MdrV2.parse(table, p)
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
        for (r in Profiles.wf1000xm5()) {
            val p = r.payload
            parseAll(r.table, (1 until p.size).asSequence().map { p.copyOf(it) })
            parseAll(r.table, sequenceOf(p + random.nextBytes(1), p + random.nextBytes(40), p + ByteArray(300) { 0xFF.toByte() }))
            parseAll(r.table, (1 until p.size).asSequence().flatMap { i ->
                sequenceOf(0x00, 0x01, 0x7F, 0x80, 0xFF).map { v -> p.copyOf().also { it[i] = v.toByte() } }
            })
        }
    }
}
