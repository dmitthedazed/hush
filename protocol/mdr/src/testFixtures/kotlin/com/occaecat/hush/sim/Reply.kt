package com.occaecat.hush.sim

import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.hex

/** One stored reply: what the headset answers a GET with (or notifies). */
data class Reply(val table: Table, val payload: ByteArray) {
    override fun equals(other: Any?) = other is Reply && other.table == table && other.payload.contentEquals(payload)
    override fun hashCode() = 31 * table.hashCode() + payload.contentHashCode()
    override fun toString() = "${if (table == Table.TWO) "T2 " else ""}${payload.hex()}"
}
