package com.occaecat.hush.sim

import com.occaecat.hush.mdr.Frame
import com.occaecat.hush.mdr.FrameParser
import com.occaecat.hush.mdr.Table
import java.io.File

/** What each simulated headset answers, from real sessions where we have them. */
object Profiles {
    private fun resource(path: String) = File(Profiles::class.java.classLoader!!.getResource(path)!!.toURI())

    /** The phone's own session start with a WH-1000XM4 on firmware 3.0.1 (scrubbed logcat). */
    fun xm4(): List<Reply> = resource("mdr/WH-1000XM4-3.0.1/replies.txt").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split(' ')
            Reply(if (parts[0] == "T2") Table.TWO else Table.ONE, ByteArray(parts.size - 1) { parts[it + 1].toInt(16).toByte() })
        }

    /** mos9527's WH-1000XM3 4.5.2 capture (every frame the headset sent, in order) and what it never asked for. */
    fun xm3(): List<Reply> = xm3Capture() + XM3_EXTRA

    /**
     * Replies to requests mos9527's client never sent, written by hand after the decompile: the surround and
     * sound position lists (tm0/m0 with VptPresetId 0..4), adaptive sound control, voice guidance on/off only.
     */
    val XM3_EXTRA = listOf(
        t1(0x41, 0x01, 0x05, 0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x04, 0x00),
        t1(0x47, 0x01, 0x00),
        t1(0x41, 0x02, 0x01),
        t1(0x47, 0x02, 0x00),
        t1(0x71, 0x01, 0x01),
        t2(0x41, 0x01, 0x01, 0x00),
    )

    /**
     * WF-1000XM3 (protocol 0x5000, functions as Gadgetbridge and the demo list them): earbuds and case batteries,
     * earbud connection, NC on/off + ambient, two assignable keys, no speak-to-chat, no general settings, no peer
     * list. Hand-written; sections every v1 headset shares (EQ, optimizer, updates, playback) come from the XM4.
     */
    fun wf1000xm3(): List<Reply> {
        val own = listOf(
            t1(0x01, 0x00, 0x50, 0x00),
            t1(0x05, 0x01) + str("WF-1000XM3"),
            t1(0x05, 0x02) + str("4.0.2"),
            t1(0x05, 0x03, 0x30, 0x01),
            t1(0x07, 0x00, *FUNCTIONS_WF1000XM3.toIntArray().let { intArrayOf(it.size, *it) }),
            t1(0x11, 0x01, 70, 0x00, 65, 0x00),
            t1(0x11, 0x02, 40, 0x01),
            t1(0x25, 0x01, 0x01, 0x01),
            t1(0xE1, 0x02, 0x00, 0x00),
            // NC_ASM: NcAsmSettingType ON_OFF, ncStep, AsmSettingType LEVEL_ADJUSTMENT, NORMAL 20 steps, VOICE 20 steps
            t1(0x61, 0x02, 0x00, 0x00, 0x01, 0x02, 0x00, 0x14, 0x01, 0x14),
            t1(0x67, 0x02, 0x01, 0x00, 0x00, 0x01, 0x01, 0x0C),
            // Left and right key: TAP, default preset, presets each with one action → function pair
            t1(0xF1, 0x06, 0x02,
                0x00, 0x00, 0x00, 0x03, 0x00, 0x01, 0x01, 0x21, 0x10, 0x01, 0x01, 0x30, 0x20, 0x01, 0x01, 0x40,
                0x01, 0x00, 0x20, 0x03, 0x00, 0x01, 0x01, 0x21, 0x10, 0x01, 0x01, 0x30, 0x20, 0x01, 0x01, 0x40),
            t1(0xF7, 0x06, 0x02, 0x00, 0x20),
            t2(0x41, 0x01, 0x01, 0x00),
            t2(0x47, 0x01, 0x01, 0x01),
        )
        val ownKeys = own.map { it.key() }.toSet()
        val shared = setOf(0x03, 0x05, 0x15, 0x19, 0x37, 0x51, 0x57, 0x5B, 0x81, 0x83, 0x87, 0xA1, 0xA3, 0xA7, 0xE7, 0xF1, 0xF3, 0xF7)
        val fromXm4 = xm4().filter {
            it.table == Table.ONE && it.payload[0].toInt() and 0xFF in shared && it.key() !in ownKeys &&
                it.key() !in setOf(Table.ONE to (0xE7 to 0x01), Table.ONE to (0xF7 to 0x05)) &&
                it.key() != (Table.ONE to (0x05 to 0x01)) && it.key() != (Table.ONE to (0x05 to 0x02)) && it.key() != (Table.ONE to (0x05 to 0x03))
        }
        return own + fromXm4
    }

    val FUNCTIONS_WF1000XM3 = listOf(0x13, 0x15, 0x17, 0x18, 0x21, 0x30, 0x39, 0x51, 0x62, 0x81, 0xA1, 0xE2, 0xF3, 0xF4, 0xF6)

    private fun Reply.key() = table to ((payload[0].toInt() and 0xFF) to (payload[1].toInt() and 0xFF))
    private operator fun Reply.plus(tail: ByteArray) = Reply(table, payload + tail)
    private fun str(s: String) = byteArrayOf(s.length.toByte()) + s.toByteArray()

    private fun t1(vararg b: Int) = Reply(Table.ONE, ByteArray(b.size) { b[it].toByte() })
    private fun t2(vararg b: Int) = Reply(Table.TWO, ByteArray(b.size) { b[it].toByte() })

    /**
     * A protocol v2 WF-1000XM5 (firmware 6.1.0): mos9527's capture of its session start plus what that client never
     * asked for, written by hand in the layouts Sound Connect's v2 classes accept (SonyReferenceV2Test): the NC/ASM
     * levels (NORMAL and VOICE 0..20), the EQ presets, the auto power off choices and the volume steps.
     */
    fun wf1000xm5(): List<Reply> = capture("mdr/WF-1000XM5-6.1.0") + WF1000XM5_EXTRA

    val WF1000XM5_EXTRA = listOf(
        t1(0x61, 0x17, 0x02, 0x00, 0x00, 0x14, 0x01, 0x01, 0x00, 0x14, 0x01),
        t1(0x51, 0x00, 0x06, 0x15, 0x03, 0x00) + str("Off") + byteArrayOf(0x10) + str("Bright") + byteArrayOf(0xA0.toByte()) + str("Custom"),
        t1(0x21, 0x05, 0x02, 0x10, 0x11),
        t1(0xA1, 0x01, 0x1E, 0x0F),
    )

    private fun xm3Capture(): List<Reply> = capture("mdr/WH-1000XM3-4.5.2")

    private fun capture(path: String): List<Reply> {
        val parser = FrameParser()
        return resource(path).listFiles { f -> f.name.endsWith(".bin") }!!.sortedBy { it.name }.flatMap { f ->
            val bytes = f.readBytes()
            parser.feed(bytes, bytes.size).map { Reply(if (it.type == Frame.Type.DATA_MDR_NO2) Table.TWO else Table.ONE, it.payload) }
        }
    }
}
