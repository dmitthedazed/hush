package com.occaecat.hush.diag

import com.occaecat.hush.mdr.FrameTap
import com.occaecat.hush.mdr.Table

/**
 * The data payloads of the current headset's sessions, oldest first, with the time since [clear]. Bounded: on a long
 * session the oldest entries go, and the self test re-sends what it needs.
 */
class SessionLog(private val limit: Int = 20_000) : FrameTap {
    class Entry(
        /** Increases by one per entry; [since] takes it. */
        val seq: Long,
        val atMs: Long,
        val out: Boolean,
        val table: Table,
        val payload: ByteArray,
        /** A marker ("session opened") instead of a payload. */
        val note: String? = null,
    ) {
        val code get() = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
        val param get() = payload.getOrNull(1)?.toInt()?.and(0xFF) ?: -1
    }

    private val entries = ArrayDeque<Entry>()
    private var next = 0L
    @Volatile private var startedAt = System.currentTimeMillis()

    /** The [Entry.seq] of the newest entry; -1 when empty. */
    val lastSeq: Long get() = synchronized(this) { next - 1 }

    fun clear() = synchronized(this) {
        entries.clear()
        startedAt = System.currentTimeMillis()
    }

    override fun frame(out: Boolean, table: Table, payload: ByteArray) = add(out, table, payload.copyOf(), null)

    fun mark(note: String) = add(false, Table.ONE, ByteArray(0), note)

    private fun add(out: Boolean, table: Table, payload: ByteArray, note: String?) = synchronized(this) {
        entries.addLast(Entry(next++, System.currentTimeMillis() - startedAt, out, table, payload, note))
        while (entries.size > limit) entries.removeFirst()
    }

    fun snapshot(): List<Entry> = synchronized(this) { entries.toList() }

    /** Entries newer than [seq]. */
    fun since(seq: Long): List<Entry> = synchronized(this) { entries.filter { it.seq > seq } }

    val isEmpty: Boolean get() = synchronized(this) { entries.none { it.note == null } }
}
