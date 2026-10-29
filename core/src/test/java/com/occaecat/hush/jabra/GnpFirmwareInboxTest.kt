package com.occaecat.hush.jabra

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GnpFirmwareInboxTest {
    @Test fun preservesEveryEventInOrderAndReportsOverflow() = runBlocking {
        val inbox = GnpFirmwareInbox(2)
        assertTrue(inbox.offer(frame(1)))
        assertTrue(inbox.offer(frame(2)))
        assertFalse(inbox.offer(frame(3)))
        assertEquals(1, inbox.receive(100).sequence)
        assertEquals(2, inbox.receive(100).sequence)
        inbox.requireEmpty()
        inbox.cancel()
    }

    @Test fun unexpectedQueuedEventPreventsNextStage() {
        val inbox = GnpFirmwareInbox()
        inbox.offer(frame(1))
        assertTrue(runCatching { inbox.requireEmpty() }.exceptionOrNull() is IOException)
        inbox.cancel()
    }

    @Test fun closingTheConnectionUnblocksAWaitingUpdate() = runBlocking {
        val inbox = GnpFirmwareInbox()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { runCatching { inbox.receive(5_000) } }
        inbox.close(IOException("Connection lost"))
        assertEquals("Connection lost", pending.await().exceptionOrNull()?.message)
    }

    @Test fun cancellationClosesBlockedWriteBeforeWaitingForWriterToReturn() = runBlocking {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val task = async(start = CoroutineStart.UNDISPATCHED) {
            cancellableGnpWrite(this, { closed.set(true); release.countDown() }) {
                started.complete(Unit)
                check(release.await(3, TimeUnit.SECONDS))
            }
        }
        started.await()
        withTimeout(1_000) { task.cancelAndJoin() }
        assertTrue(closed.get())
    }

    @Test fun commandDeadlineCoversBlockedWriteItself() = runBlocking {
        val release = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val completed = withTimeoutOrNull(25) {
            cancellableGnpWrite(this, { closed.set(true); release.countDown() }) {
                check(release.await(3, TimeUnit.SECONDS))
            }
            true
        }
        assertNull(completed)
        assertTrue(closed.get())
    }

    private fun frame(sequence: Int) = GnpFrame(9, 4, sequence, GnpFrame.EVENT, 15, 27, ByteArray(4))
}
