package com.occaecat.hush.sim

import com.occaecat.hush.mdr.Frame
import com.occaecat.hush.mdr.FrameParser
import com.occaecat.hush.mdr.MdrLink
import com.occaecat.hush.mdr.MdrStreams
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.hex
import com.occaecat.hush.mdr.v1.Command1
import com.occaecat.hush.mdr.v1.Command2
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A headset at the far end of an [MdrLink], speaking the Tandem framing over in-memory pipes.
 *
 * GETs are answered from [replies]: the stored reply whose opcode is the request's + 1 and whose bytes after the
 * opcode share the longest prefix with the request (the inquired type at least); no match, no reply. SETs (the
 * opcodes Sony names *_SET_*) are echoed as their NTFY (opcode + 1) and replace the stored RET (opcode − 1) when it
 * has the same layout. [onSet] can override that per request; [alerts] holds a SET until the app answers its alert.
 */
class SimHeadset(
    replies: List<Reply>,
    @Volatile var quirks: Quirks = Quirks(),
    /** Speak MDR v2: the channel is the v2 service and SETs are recognised by v2's command names. */
    val v2: Boolean = false,
) : MdrLink {

    data class Quirks(
        /** Answer a GET before ACKing it, as the WH-1000XM3 and XM2 do. */
        val replyBeforeAck: Boolean = false,
        /** Requests (by payload prefix) that are ACKed and never answered. */
        val silent: List<Reply> = emptyList(),
        /** Every Nth outgoing frame is preceded by a cut-off one (START, half a frame, no END); 0 is off. */
        val cutOffEvery: Int = 0,
        /** Write frames one byte at a time. */
        val trickle: Boolean = false,
        /** The headset ignores this many of each DATA frame before ACKing (exercises retries). */
        val dropFirstCopies: Int = 0,
    )

    /** Stored replies, in profile order; SETs update them. */
    val replies: MutableList<Reply> = CopyOnWriteArrayList(replies)

    /** Every distinct request the app sent, in order. */
    val received: MutableList<Request> = CopyOnWriteArrayList()

    /** Payload prefix of a SET → the alert (ALERT_NTFY_PARAM payload) it raises; the SET waits for the answer. */
    val alerts: MutableMap<Reply, ByteArray> = mutableMapOf()

    /** Runs before the default SET handling; a non-null result is what the headset notifies instead. */
    var onSet: (Request) -> List<Reply>? = { null }

    /** Called for every request after it is handled; tests push notifies from here (mid-init and the like). */
    var onRequest: (Request) -> Unit = {}

    /** Requests that are now refused: `open()` throws while this is true. */
    @Volatile var refuseOpen = false

    @Volatile var opens = 0
        private set

    private var session: Session? = null

    override suspend fun open(): MdrStreams {
        if (refuseOpen) throw IOException("simulated headset refuses the channel")
        session?.close()
        opens++
        return Session().also { session = it }.streams
    }

    /** Sends a headset-initiated frame (a notify). */
    fun push(reply: Reply) {
        session?.send(reply) ?: error("no open session")
    }

    fun push(table: Table, vararg bytes: Int) = push(Reply(table, ByteArray(bytes.size) { bytes[it].toByte() }))

    /** The headset drops the control channel. */
    fun drop() {
        session?.close()
        session = null
    }

    /** Waits until the app has sent [request]; returns false on timeout. */
    fun awaitRequest(request: Request, timeoutMs: Long = 5_000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (request in received) return true
            Thread.sleep(10)
        }
        return false
    }

    fun answerFor(request: Request): Reply? {
        val p = request.payload
        return replies
            .filter { it.table == request.table && it.payload.size >= 2 && it.payload[0].u() == p[0].u() + 1 }
            .filter { p.size < 2 || it.payload[1] == p[1] }
            .maxByOrNull { commonPrefix(it.payload, p) }
    }

    private fun handle(request: Request): List<Reply> {
        val p = request.payload
        if (quirks.silent.any { it.table == request.table && p.startsWith(it.payload) }) return emptyList()
        if (!isSet(request, v2)) return listOfNotNull(answerFor(request))
        alerts.entries.firstOrNull { it.key.table == request.table && p.startsWith(it.key.payload) }?.let { (_, alert) ->
            heldSets += request
            return listOf(Reply(Table.ONE, alert))
        }
        if (request.table == Table.ONE && p[0].u() == Command1.ALERT_SET_PARAM.code) {
            // [cmd, 1, type, positive]: a positive answer applies every SET the alert held.
            val held = heldSets.toList().also { heldSets.clear() }
            return if (p.size >= 4 && p[3].u() == 1) held.flatMap(::applySet) else emptyList()
        }
        return applySet(request)
    }

    private val heldSets = CopyOnWriteArrayList<Request>()

    private fun applySet(request: Request): List<Reply> {
        onSet(request)?.let { return it }
        val p = request.payload
        eqPreset(request)?.let { return it }
        val ntfy = p.copyOf().also { it[0] = (p[0] + 1).toByte() }
        if (p.size >= 2) {
            val ret = replies.indexOfFirst {
                it.table == request.table && it.payload.size == p.size && it.payload[0].u() == p[0].u() - 1 && it.payload[1] == p[1]
            }
            if (ret >= 0) replies[ret] = Reply(request.table, p.copyOf().also { it[0] = (p[0] - 1).toByte() })
        }
        val named = commandName(request.table, ntfy[0].u(), v2)
        return if (named?.contains("_NTFY_") == true) listOf(Reply(request.table, ntfy)) else emptyList()
    }

    /**
     * EQEBB_SET_PARAM selecting a preset ([cmd, 1|3, id, 0]): the headset answers with that preset's levels. The
     * simulator has only the levels it last reported, so it keeps those under the new id.
     */
    private fun eqPreset(request: Request): List<Reply>? {
        val p = request.payload
        val ebb = if (v2) 0x01 else 0x02
        if (request.table != Table.ONE || p[0].u() != Command1.EQEBB_SET_PARAM.code || p.size != 4 || p[1].u() == ebb || p[3].u() != 0) return null
        val i = replies.indexOfFirst { it.table == Table.ONE && it.payload.size >= 4 && it.payload[0].u() == p[0].u() - 1 && it.payload[1] == p[1] }
        if (i < 0) return emptyList()
        val ret = replies[i].payload.copyOf().also { it[2] = p[2] }
        replies[i] = Reply(Table.ONE, ret)
        return listOf(Reply(Table.ONE, ret.copyOf().also { it[0] = (p[0] + 1).toByte() }))
    }

    private inner class Session {
        private val toHeadset = BytePipe()
        private val toApp = BytePipe()
        @Volatile private var closed = false
        private val outbox = LinkedBlockingQueue<Pair<Reply, CountDownLatch>>()
        private val acked = LinkedBlockingQueue<Byte>()
        private var sendSeq: Byte = 0
        private var lastRecvSeq: Byte = -1
        private var copies = 0
        private var framesOut = 0
        private val writeLock = Any()

        val streams = MdrStreams(toApp.input, toHeadset.output, v2) { close() }

        private val reader = thread(isDaemon = true, name = "sim-rx") {
            val parser = FrameParser()
            val buf = ByteArray(1024)
            try {
                while (!closed) {
                    val n = toHeadset.input.read(buf)
                    if (n < 0) break
                    for (f in parser.feed(buf, n)) onFrame(f.type, f.seq, f.payload)
                }
            } catch (_: IOException) {
            } catch (_: InterruptedException) {
            }
        }

        private val sender = thread(isDaemon = true, name = "sim-tx") {
            try {
                while (!closed) {
                    val (reply, written) = outbox.take()
                    val type = if (reply.table == Table.TWO) Frame.Type.DATA_MDR_NO2 else Frame.Type.DATA_MDR
                    acked.clear()
                    var ok = false
                    for (attempt in 0..3) {
                        writeFrame(Frame.encode(type, sendSeq, reply.payload))
                        written.countDown()
                        val ack = acked.poll(750, TimeUnit.MILLISECONDS)
                        if (ack != null && ack != sendSeq) { ok = true; break }
                    }
                    if (!ok) return@thread close()
                    sendSeq = (1 - sendSeq).toByte()
                }
            } catch (_: InterruptedException) {
            } catch (_: IOException) {
            }
        }

        private fun onFrame(type: Byte, seq: Byte, payload: ByteArray) {
            when (type) {
                Frame.Type.ACK -> acked.offer(seq)
                Frame.Type.DATA_MDR, Frame.Type.DATA_MDR_NO2 -> {
                    if (seq != lastRecvSeq && copies < quirks.dropFirstCopies) { copies++; return }
                    copies = 0
                    val fresh = seq != lastRecvSeq
                    val request = Request(if (type == Frame.Type.DATA_MDR_NO2) Table.TWO else Table.ONE, payload)
                    val out = if (fresh) {
                        lastRecvSeq = seq
                        received += request
                        handle(request)
                    } else emptyList()
                    if (quirks.replyBeforeAck && out.isNotEmpty()) {
                        // The first reply goes out before the ACK; the rest follow it.
                        send(out.first()).await(2, TimeUnit.SECONDS)
                        writeFrame(Frame.ack((1 - seq).toByte()))
                        out.drop(1).forEach(::send)
                    } else {
                        writeFrame(Frame.ack((1 - seq).toByte()))
                        out.forEach(::send)
                    }
                    if (fresh) onRequest(request)
                }
            }
        }

        fun send(reply: Reply): CountDownLatch = CountDownLatch(1).also { outbox.put(reply to it) }

        private fun writeFrame(frame: ByteArray) = synchronized(writeLock) {
            if (closed) throw IOException("closed")
            if (quirks.cutOffEvery > 0 && ++framesOut % quirks.cutOffEvery == 0) {
                toApp.output.write(frame.copyOfRange(0, frame.size / 2))
            }
            if (quirks.trickle) frame.forEach { toApp.output.write(byteArrayOf(it)) } else toApp.output.write(frame)
        }

        fun close() {
            if (closed) return
            closed = true
            toApp.close()
            toHeadset.close()
            reader.interrupt()
            sender.interrupt()
        }
    }
}

/** A one-way in-memory byte pipe; reads block until data or close (then -1). */
private class BytePipe {
    private val chunks = LinkedBlockingQueue<ByteArray>()
    @Volatile private var closed = false
    private val eof = ByteArray(0)

    val input = object : InputStream() {
        private var current: ByteArray? = null
        private var pos = 0

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            var c = current
            if (c == null || pos >= c.size) {
                c = try { chunks.take() } catch (e: InterruptedException) { throw IOException(e) }
                if (c === eof) { chunks.put(eof); return -1 }
                current = c
                pos = 0
            }
            val n = minOf(len, c.size - pos)
            System.arraycopy(c, pos, b, off, n)
            pos += n
            return n
        }
    }

    val output = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("pipe closed")
            chunks.put(b.copyOfRange(off, off + len))
        }
    }

    fun close() {
        if (closed) return
        closed = true
        chunks.put(eof)
    }
}

private fun Byte.u() = toInt() and 0xFF

private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun commonPrefix(a: ByteArray, b: ByteArray): Int {
    var i = 0
    while (i < a.size - 1 && i < b.size - 1 && a[i + 1] == b[i + 1]) i++
    return i
}

private fun commandName(table: Table, code: Int, v2: Boolean): String? = when {
    v2 && table == Table.ONE -> com.occaecat.hush.mdr.v2.Command1.of(code)?.name
    v2 -> com.occaecat.hush.mdr.v2.Command2.of(code)?.name
    table == Table.ONE -> Command1.of(code)?.name
    else -> Command2.of(code)?.name
}

/** SET opcodes, by Sound Connect's command names. */
private fun isSet(request: Request, v2: Boolean): Boolean =
    commandName(request.table, request.payload[0].u(), v2)?.contains("_SET_") == true
