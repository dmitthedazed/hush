package com.occaecat.hush.mdr

/** The command table a payload belongs to; it picks the frame's data type (DATA_MDR or DATA_MDR_NO2). */
enum class Table { ONE, TWO }

/** One payload to send, already encoded, with its table. */
class Request(val table: Table, val payload: ByteArray) {
    override fun equals(other: Any?) = other is Request && other.table == table && other.payload.contentEquals(payload)
    override fun hashCode() = 31 * table.hashCode() + payload.contentHashCode()
    override fun toString() = "${if (table == Table.TWO) "T2 " else ""}${payload.hex()}"
}

/** Reads a payload front to back; out-of-range reads throw, and the parser turns that into [MdrEvent.Unknown]. */
class Reader(private val p: ByteArray, var pos: Int = 0) {
    val remaining get() = p.size - pos
    fun u8(): Int = p[pos++].toInt() and 0xFF
    fun s8(): Int = p[pos++].toInt()
    fun u16(): Int = (u8() shl 8) or u8()
    fun bool(): Boolean = u8() == 0x01
    fun bytes(n: Int): ByteArray = p.copyOfRange(pos, pos + n).also { pos += n }
    /** MDRPrefixedString: a length byte, then UTF-8 (clamped to what is left, as Sound Connect's reader does). */
    fun str(): String = String(bytes(minOf(u8(), remaining)), Charsets.UTF_8)
    fun ascii(n: Int): String = String(bytes(n), Charsets.US_ASCII)
    fun <T> list(item: Reader.() -> T): List<T> = List(u8()) { item() }
}

internal fun bytesOf(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

fun ByteArray.hex() = joinToString(" ") { "%02x".format(it) }
