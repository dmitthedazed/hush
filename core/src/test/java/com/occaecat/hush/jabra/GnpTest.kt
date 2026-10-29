package com.occaecat.hush.jabra

import org.junit.Assert.*
import org.junit.Test

class GnpTest {
    private val modeReply = bytes(9, 4, 7, 0xc7, 0x13, 0xbe, 4)
    private val batteryReply = bytes(9, 4, 0, 0x11, 0x12, 2, 0x80, 90, 0, 0, 2, 0x15, 0, 80, 5, 0, 85)

    @Test fun packsKindAndLengthInTheSameByte() {
        assertArrayEquals(bytes(4, 9, 7, 0x88, 0x13, 0xbe, 1, 4),
            GnpFrame.request(7, GnpFrame.WRITE, 0x13be, bytes(1, 4)).encode())
        assertArrayEquals(bytes(4, 9, 8, 0x46, 2, 0x11),
            GnpFrame.request(8, GnpFrame.READ, 0x0211).encode())
    }

    @Test fun parsesEveryPossibleReadBoundary() {
        val stream = modeReply + batteryReply + modeReply
        for (split in 0..stream.size) {
            val parser = GnpParser()
            val frames = parser.feed(stream.copyOfRange(0, split)) + parser.feed(stream.copyOfRange(split, stream.size))
            assertEquals("split=$split", 3, frames.size)
            assertArrayEquals(modeReply, frames[0].encode())
            assertArrayEquals(batteryReply, frames[1].encode())
            assertArrayEquals(modeReply, frames[2].encode())
        }
    }

    @Test fun handlesSingleByteReadsAndPayloadThatLooksLikeAHeader() {
        val incoming = GnpFrame(9, 4, 11, GnpFrame.RESPONSE, 2, 0, bytes(9, 4, 0, 0xc7, 0x13, 0xbe)).encode()
        val parser = GnpParser()
        val frames = incoming.flatMap { parser.feed(byteArrayOf(it)) }
        assertEquals(1, frames.size)
        assertArrayEquals(incoming, frames.single().encode())
    }

    @Test fun skipsNoiseAndInvalidLengthsWithoutInventingAResponse() {
        val parser = GnpParser()
        assertTrue(parser.feed(bytes(0, 1, 2, 9, 4, 0, 0xc5)).isEmpty())
        assertArrayEquals(modeReply, parser.feed(modeReply).single().encode())
        assertTrue(GnpParser().feed(modeReply.copyOf(3)).isEmpty())
        assertTrue(GnpParser().feed(modeReply.copyOf(6)).isEmpty())
    }

    @Test fun rejectsOversizeOutgoingPackets() {
        assertEquals(63, GnpFrame.request(1, GnpFrame.WRITE, 0x137d, ByteArray(57)).encode().size)
        assertThrows(IllegalArgumentException::class.java) {
            GnpFrame.request(1, GnpFrame.WRITE, 0x137d, ByteArray(58)).encode()
        }
    }

    @Test fun correlatesAckAndNakUsingEchoedHeader() {
        val request = GnpFrame.request(42, GnpFrame.WRITE, 0x13be, bytes(1, 4))
        val header = request.encode().copyOf(5)
        val ack = GnpFrame(9, 4, 99, GnpFrame.RESPONSE, GnpFrame.ACK, header[0].u(), header.copyOfRange(1, 5))
        val nak = GnpFrame(9, 4, 100, GnpFrame.RESPONSE, GnpFrame.NAK, 0xfa, header)
        assertTrue(ack.acknowledges(request))
        assertTrue(nak.acknowledges(request))
        assertFalse(ack.acknowledges(request.copy(sequence = 43)))
        assertFalse(nak.acknowledges(request.copy(command = 2)))
        assertFalse(GnpFrame(9, 4, 42, GnpFrame.EVENT, 0x13, 0xbe, bytes(4)).acknowledges(request))
    }

    @Test fun correlatesReadReplyBySequenceAndTopic() {
        val request = GnpFrame.request(7, GnpFrame.READ, 0x13be, bytes(1))
        val reply = GnpParser().feed(modeReply).single()
        assertTrue(reply.acknowledges(request))
        assertFalse(reply.acknowledges(request.copy(sequence = 8)))
        assertFalse(reply.acknowledges(request.copy(subcommand = 0x7d)))
        assertFalse(reply.copy(source = 5).acknowledges(request))
        assertFalse(reply.copy(destination = 4).acknowledges(request))
    }

    @Test fun observedElite10EqualizerStatusDenialCorrelatesToTheEmptyRead() {
        val request = GnpFrame.request(10, GnpFrame.READ, 0x137e)
        // Live Elite 10 firmware 4.6.0: FE FD = ACCESS_DENIED, followed by the request header.
        val denied = GnpParser().feed(bytes(9, 4, 10, 0xcb, 0xfe, 0xfd, 4, 9, 10, 0x46, 0x13)).single()
        assertTrue(denied.acknowledges(request))
        assertEquals(0xfd, denied.subcommand)
        assertFalse(denied.acknowledges(request.copy(payload = bytes(0))))
    }
}

internal fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()
