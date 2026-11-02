package com.occaecat.hush.mdr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun ackMatchesSoundConnectConstants() {
        // qm0.b static ACK frames.
        assertArrayEquals(b(0x3e, 0x01, 0x00, 0, 0, 0, 0, 0x01, 0x3c), Frame.ack(0))
        assertArrayEquals(b(0x3e, 0x01, 0x01, 0, 0, 0, 0, 0x02, 0x3c), Frame.ack(1))
    }

    @Test
    fun escapesReservedBytes() {
        assertArrayEquals(b(0x3d, 0x2c, 0x3d, 0x2d, 0x3d, 0x2e, 0x41), Frame.escape(b(0x3c, 0x3d, 0x3e, 0x41)))
        assertArrayEquals(b(0x3c, 0x3d, 0x3e, 0x41), Frame.unescape(b(0x3d, 0x2c, 0x3d, 0x2d, 0x3d, 0x2e, 0x41)))
    }

    @Test
    fun roundTripsThroughParser() {
        val payload = b(0x68, 0x02, 0x11, 0x02, 0x00, 0x01, 0x00, 0x3e)
        val wire = Frame.encode(Frame.Type.DATA_MDR, 1, payload)
        // Feed in two chunks to exercise buffering, with noise before the start byte.
        val parser = FrameParser()
        val noisy = b(0x00, 0x7f) + wire
        val first = parser.feed(noisy.copyOfRange(0, 6))
        val second = parser.feed(noisy.copyOfRange(6, noisy.size))
        assertEquals(0, first.size)
        assertEquals(1, second.size)
        assertEquals(Frame.Type.DATA_MDR, second[0].type)
        assertEquals(1.toByte(), second[0].seq)
        assertArrayEquals(payload, second[0].payload)
    }

    @Test
    fun rejectsBadChecksum() {
        val wire = Frame.encode(Frame.Type.DATA_MDR, 0, b(0x10, 0x00))
        wire[wire.size - 2] = (wire[wire.size - 2] + 1).toByte()
        assertEquals(0, FrameParser().feed(wire).size)
    }

    @Test
    fun recoversFromCutOffFrame() {
        val wire = Frame.encode(Frame.Type.DATA_MDR, 0, b(0x10, 0x00))
        // A frame that lost its tail, then a whole one: the second must survive.
        val frames = FrameParser().feed(wire.copyOfRange(0, 4) + wire)
        assertEquals(1, frames.size)
        assertArrayEquals(b(0x10, 0x00), frames[0].payload)
    }
}
