package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.hex
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Replies the headset can send, made and judged by Sound Connect's own table-1 classes, then fed to [MdrV1.parse].
 *
 * Seeds are found byte by byte: Sony's class writes a payload back in field order, so a value is right once the
 * rewrite agrees up to it. Each seed is then mutated one byte at a time. A payload Sony reads back unchanged must
 * not come out of our parser as [MdrEvent.Unknown] or an exception.
 */
class SonyRepliesTest {

    private fun sony() = SonyReference.instance.also { assumeTrue("tools/sonyref/sound-connect.jar not made", it != null) }!!

    private fun SonyReference.accepts(p: ByteArray) =
        (read(Table.ONE, p) as? SonyReference.Result.Read)?.rewritten?.contentEquals(p) == true

    private fun SonyReference.agreesUpTo(p: ByteArray, i: Int): Boolean {
        val w = (read(Table.ONE, p) as? SonyReference.Result.Read)?.rewritten ?: return false
        return w.size > i && (0..i).all { w[it] == p[it] }
    }

    /**
     * Valid payloads for [op] and inquired type [type], one per length at most. A depth-first search over the bytes,
     * trying 1 before 0 so counts and string lengths come out non-empty, with a few candidates per byte.
     */
    private fun SonyReference.seeds(op: Int, type: Int, maxLen: Int = 40): List<ByteArray> = buildList {
        val order = listOf(1, 2, 0, 3) + (4..255)
        // Sony writes the inquired type right after the command: skip types its enum does not know.
        val known = (2..12).any { len ->
            listOf(0, 1, 2, 3).any { v -> agreesUpTo(ByteArray(len).also { it[0] = op.toByte(); it[1] = type.toByte(); if (len > 2) it[2] = v.toByte() }, 1) }
        }
        if (!known) return@buildList
        for (len in 2..maxLen) {
            val p = ByteArray(len); p[0] = op.toByte(); p[1] = type.toByte()
            var budget = 3000
            fun dfs(i: Int): Boolean {
                if (i == len) return accepts(p)
                var tried = 0
                for (v in order) {
                    if (--budget < 0) return false
                    p[i] = v.toByte()
                    if (!agreesUpTo(p, i)) continue
                    if (dfs(i + 1)) return true
                    if (++tried == 3) return false
                }
                return false
            }
            if (dfs(2)) add(p.copyOf())
        }
    }

    private fun parses(p: ByteArray): String? = try {
        when (val e = MdrV1.parse(Table.ONE, p)) {
            is MdrEvent.Unknown -> "Unknown"
            else -> null.also { e.hashCode() }
        }
    } catch (t: Throwable) {
        t.toString()
    }

    @Test
    fun everyReplySonyAcceptsParses() {
        val sony = sony()
        val replies = Command1.entries.filter { "_RET_" in it.name || "_NTFY_" in it.name }
        val report = StringBuilder()
        var seedsTotal = 0
        var checked = 0
        val misses = sortedMapOf<String, MutableSet<String>>()
        val covered = sortedMapOf<String, MutableList<String>>()
        for (cmd in replies) for (type in 0..255) {
            val seeds = sony.seeds(cmd.code, type)
            if (seeds.isEmpty()) continue
            seedsTotal += seeds.size
            covered.getOrPut(cmd.name) { mutableListOf() }.add("%02x:%s".format(type, seeds.joinToString(",") { it.size.toString() }))
            for (seed in seeds) {
                val variants = buildList {
                    add(seed)
                    for (i in 2 until seed.size) for (v in 0..255) {
                        if (v == seed[i].toInt() and 0xFF) continue
                        add(seed.copyOf().also { it[i] = v.toByte() })
                    }
                }
                for (p in variants) {
                    if (!sony.accepts(p)) continue
                    checked++
                    if (sony.inquiredType(p) in setOf("NO_USE", "OUT_OF_RANGE")) continue
                    parses(p)?.let { why -> misses.getOrPut("${cmd.name} type %02x: %s".format(type, why)) { mutableSetOf() }.add(p.hex()) }
                }
            }
        }
        report.appendLine("seeds $seedsTotal, Sony-valid payloads checked $checked, groups that fail ${misses.size}")
        for ((k, v) in misses) report.appendLine("$k  (${v.size}) e.g. ${v.take(3).joinToString(" | ")}")
        report.appendLine("-- covered (type:seed lengths)")
        for ((k, v) in covered) report.appendLine("$k ${v.joinToString(" ")}")
        report.appendLine("-- no seed found: " + replies.map { it.name }.filter { it !in covered }.joinToString(" "))
        File("build/sony-replies.txt").writeText(report.toString())
        assertTrue("Sony reads these, Hush does not (build/sony-replies.txt):\n" + misses.keys.joinToString("\n"), misses.isEmpty())
    }

    /** What a real WH-1000XM3 sent, as Sony's classes read it. */
    @Test
    fun captureAsSonyReadsIt() {
        val sony = sony()
        val dir = File(javaClass.classLoader!!.getResource("mdr/WH-1000XM3-4.5.2")!!.toURI())
        val parser = com.occaecat.hush.mdr.FrameParser()
        val out = StringBuilder()
        dir.listFiles { f -> f.name.endsWith(".bin") }!!.sortedBy { it.name }.forEach { f ->
            val bytes = f.readBytes()
            for (frame in parser.feed(bytes, bytes.size)) {
                if (frame.type == com.occaecat.hush.mdr.Frame.Type.ACK) continue
                val table = if (frame.type == com.occaecat.hush.mdr.Frame.Type.DATA_MDR_NO2) Table.TWO else Table.ONE
                val p = frame.payload
                val name = sony.commandName(table, p[0].toInt() and 0xFF)
                when (val r = sony.read(table, p)) {
                    is SonyReference.Result.Rejected -> out.appendLine("$name ${p.hex()}: rejected ${r.why}")
                    is SonyReference.Result.Read -> if (r.rewritten != null && !r.rewritten.contentEquals(p))
                        out.appendLine("$name ${p.hex()}: rewrites ${r.rewritten.hex()}")
                }
            }
        }
        File("build/sony-capture.txt").writeText(out.toString())
    }

    /**
     * Bytes Sony stores but our event ignores: for each seed and position, does any Sony-valid change of that byte
     * change our event? A report for review (build/sony-ignored.txt), not an assertion: Hush skips fields on purpose.
     */
    @Test
    fun bytesWeIgnore() {
        val sony = sony()
        val out = StringBuilder()
        for (cmd in Command1.entries.filter { "_RET_" in it.name || "_NTFY_" in it.name }) for (type in 0..255) {
            for (seed in sony.seeds(cmd.code, type)) {
                val base = MdrV1.parse(Table.ONE, seed)
                if (base is MdrEvent.Ignored || base is MdrEvent.Unknown) continue
                val deaf = (2 until seed.size).filter { i ->
                    val tried = (0..255).map { v -> seed.copyOf().also { it[i] = v.toByte() } }.filter { sony.accepts(it) && !it.contentEquals(seed) }
                    tried.isNotEmpty() && tried.all { MdrV1.parse(Table.ONE, it) == base }
                }
                if (deaf.isNotEmpty()) out.appendLine("${cmd.name} ${seed.hex()} -> ${base::class.simpleName}: ignores bytes $deaf")
            }
        }
        File("build/sony-ignored.txt").writeText(out.toString())
    }
}
