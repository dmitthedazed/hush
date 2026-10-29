package com.occaecat.hush.jabra

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class JabraButtonsTest {
    @Test fun bothModelFixturesExposeEighteenContextSpecificAssignments() = runBlocking {
        for (pid in listOf(9421, 11846)) {
            val fake = ButtonsClient(pid)
            val state = JabraButtons(fake).read()
            assertEquals(JabraButtonContext.entries.toSet(), state.supportedContexts)
            assertEquals(18, state.slots.size)
            assertEquals(setOf(0, 1, 2, 3, 4, 5), state.slots.first().allowedActions.map { it.id }.toSet())
            assertEquals(setOf(0, 6, 7, 8, 11, 12), state.slots[6].allowedActions.map { it.id }.toSet())
            assertEquals(setOf(0, 9, 10, 11, 12), state.slots[12].allowedActions.map { it.id }.toSet())
            assertTrue(state.slots.all { it.lockReason == null })
            assertEquals(pid == 11846, fake.reads.any { it.first == 0x13e5 })
            assertEquals(pid == 11846, fake.reads.any { it.first == 0x0d4d })
            assertTrue(fake.writes.isEmpty())
        }
    }

    @Test fun singleSlotWriteUsesFourByteRecordAndConfirmsWithoutChangingOtherSlots() = runBlocking {
        val fake = ButtonsClient()
        val engine = JabraButtons(fake)
        val before = engine.read()
        val slot = slot(JabraButtonContext.MUSIC, JabraButtonSide.LEFT, JabraButtonGesture.SINGLE)
        val state = engine.set(slot, 2)
        assertEquals(1, fake.writes.size)
        assertEquals(0x139a, fake.writes.single().first)
        assertArrayEquals(bytes(0, 0, 0, 2), fake.writes.single().second)
        assertEquals(2, state.slots.single { it.slot == slot }.action)
        assertEquals(before.slots.filterNot { it.slot == slot }, state.slots.filterNot { it.slot == slot })
    }

    @Test fun unsupportedContextIsNeverReadOrWritten() = runBlocking {
        val fake = ButtonsClient().apply { contexts = bytes(1, 2, 0x7f) }
        val engine = JabraButtons(fake)
        val state = engine.read()
        assertEquals(setOf(JabraButtonContext.INCOMING_CALL, JabraButtonContext.ONGOING_CALL), state.supportedContexts)
        assertTrue(fake.reads.filter { it.first == 0x139a }.all { it.second.single().toInt() in 1..2 })
        assertTrue(runCatching { engine.set(slot(), 2) }.exceptionOrNull() is IOException)
        assertTrue(fake.writes.isEmpty())
    }

    @Test fun malformedRecordsDoNotProduceEditableState() = runBlocking {
        for (bad in listOf(
            bytes(), bytes(0, 0, 0), bytes(1, 0, 0, 0), bytes(0, 2, 0, 0), bytes(0, 0, 3, 0),
            bytes(0, 0, 0, 1, 0, 0, 0, 2), ByteArray(28),
        )) {
            val fake = ButtonsClient().apply { current[0] = bad }
            assertTrue(runCatching { JabraButtons(fake).read() }.exceptionOrNull() is IOException)
            assertTrue(fake.writes.isEmpty())
        }
        assertTrue(runCatching { JabraButtonProtocol.contexts(bytes(0, 0)) }.exceptionOrNull() is IOException)
    }

    @Test fun unknownAndReservedAssignmentsArePreservedAndCannotBeChangedOrReset() = runBlocking {
        for (value in listOf(13, 14, 0xf2, 8)) {
            val fake = ButtonsClient().apply { current[0]!![3] = value.toByte() }
            val engine = JabraButtons(fake)
            val state = engine.read()
            assertEquals(value, state.slots.first().action)
            assertNotNull(state.slots.first().lockReason)
            assertTrue(state.slots.first().allowedActions.isEmpty())
            assertFalse(state.canReset(JabraButtonContext.MUSIC))
            assertTrue(runCatching { engine.set(slot(), 1) }.exceptionOrNull() is IOException)
            assertTrue(runCatching { engine.reset(JabraButtonContext.MUSIC) }.exceptionOrNull() is IOException)
            assertTrue(fake.writes.isEmpty())
        }
    }

    @Test fun assignmentsAndLocksAreReadAgainBeforeEveryWrite() = runBlocking {
        val fake = ButtonsClient()
        val engine = JabraButtons(fake)
        assertNull(engine.read().slots.first().lockReason)
        fake.current[0]!![3] = 13
        assertTrue(runCatching { engine.set(slot(), 1) }.exceptionOrNull() is IOException)
        assertTrue(fake.writes.isEmpty())
    }

    @Test fun actionFromAnotherContextIsRejectedBeforeWrite() = runBlocking {
        val fake = ButtonsClient()
        val engine = JabraButtons(fake)
        assertTrue(runCatching { engine.set(slot(), 8) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { engine.set(slot(JabraButtonContext.INCOMING_CALL), 1) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { engine.set(slot(), 256) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(fake.writes.isEmpty())
    }

    @Test fun spotifyKeepsReservedLeftControlsLockedAndOtherControlsEditable() = runBlocking {
        val fake = ButtonsClient(11846).apply { spotify = bytes(1) }
        val engine = JabraButtons(fake)
        val state = engine.read()
        assertTrue(state.slots.filter { it.slot.side == JabraButtonSide.LEFT }.all { it.lockReason != null })
        assertTrue(state.slots.filter { it.slot.side == JabraButtonSide.RIGHT }.all { it.lockReason == null })
        assertTrue(runCatching { engine.set(slot(), 1) }.exceptionOrNull() is IOException)
        engine.set(slot(side = JabraButtonSide.RIGHT), 2)
        assertEquals(1, fake.writes.size)
        assertEquals(0x139a, fake.writes.single().first)
    }

    @Test fun bistoReservesRightSinglePressAndRemovesVoiceAssistantChoice() = runBlocking {
        val fake = ButtonsClient(11846).apply { assistant = bytes(0x11) }
        val engine = JabraButtons(fake)
        val state = engine.read()
        val right = slot(side = JabraButtonSide.RIGHT)
        assertNotNull(state.slots.single { it.slot == right }.lockReason)
        assertTrue(state.slots.filter { it.slot.context == JabraButtonContext.MUSIC }
            .all { it.allowedActions.none { action -> action.id == 4 } })
        assertTrue(runCatching { engine.set(right, 2) }.exceptionOrNull() is IOException)
        assertTrue(runCatching { engine.set(slot(), 4) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(fake.writes.isEmpty())
    }

    @Test fun unavailableOrMalformedLockStateNeverAuthorizesWrites() = runBlocking {
        for (reply in listOf<ByteArray?>(null, bytes(), bytes(2), bytes(0, 1))) {
            val fake = ButtonsClient(11846).apply { spotify = reply }
            val engine = JabraButtons(fake)
            assertEquals(18, engine.read().slots.size)
            assertTrue(engine.read().slots.all { it.lockReason != null })
            assertTrue(runCatching { engine.set(slot(), 1) }.exceptionOrNull() is IOException)
            assertTrue(fake.writes.isEmpty())
        }
        val fake = ButtonsClient(11846).apply { assistant = bytes(0x80) }
        assertTrue(JabraButtons(fake).read().slots.all { it.lockReason != null })
    }

    @Test fun rejectedAndUnconfirmedWritesAreNotReportedAsSuccess() = runBlocking {
        val rejected = ButtonsClient().apply { rejectWrites = true }
        assertTrue(runCatching { JabraButtons(rejected).set(slot(), 2) }.exceptionOrNull() is JabraRejectedException)
        val ignored = ButtonsClient().apply { applyWrites = false }
        val error = runCatching { JabraButtons(ignored).set(slot(), 2) }.exceptionOrNull()
        assertTrue(error is JabraButtonConfirmationException)
        assertEquals(5, (error as JabraButtonConfirmationException).observed.slots.first().action)
        assertEquals(5, JabraButtons(ignored).read().slots.first().action)
    }

    @Test fun resetUsesContextDefaultsAndConfirmsOnlyThatContext() = runBlocking {
        val fake = ButtonsClient().apply { current[0]!![3] = 2; current[1]!![3] = 6 }
        val engine = JabraButtons(fake)
        val state = engine.reset(JabraButtonContext.MUSIC)
        assertArrayEquals(bytes(0), fake.writes.single().second)
        assertEquals(0x139b, fake.writes.single().first)
        assertEquals(5, state.slots.first().action)
        assertEquals(6, state.slots.first { it.slot.context == JabraButtonContext.INCOMING_CALL }.action)
        assertTrue(fake.reads.any { it.first == 0x139b && it.second.contentEquals(bytes(0)) })
        fake.current[0]!![3] = 2
        fake.applyWrites = false
        val error = runCatching { engine.reset(JabraButtonContext.MUSIC) }.exceptionOrNull()
        assertTrue(error is JabraButtonConfirmationException)
        assertEquals(2, (error as JabraButtonConfirmationException).observed.slots.first().action)
    }

    @Test fun incompatibleOrUnknownDefaultsCannotBeReset() = runBlocking {
        for (bad in listOf(bytes(0, 0, 0, 1), music().also { it[3] = 14 })) {
            val fake = ButtonsClient().apply { defaults[0] = bad }
            assertTrue(runCatching { JabraButtons(fake).reset(JabraButtonContext.MUSIC) }.exceptionOrNull() is IOException)
            assertTrue(fake.writes.isEmpty())
        }
    }

    @Test fun wrongProductAndCancellationDoNotContinueToWrite() = runBlocking {
        val unknown = ButtonsClient(11865)
        assertTrue(runCatching { JabraButtons(unknown).set(slot(), 1) }.exceptionOrNull() is IOException)
        assertEquals(listOf(0x0211), unknown.reads.map { it.first })
        val cancelled = ButtonsClient(11846).apply { cancelLockRead = true }
        assertTrue(runCatching { JabraButtons(cancelled).set(slot(), 1) }.exceptionOrNull() is CancellationException)
        assertTrue(cancelled.writes.isEmpty())
    }

    private fun slot(
        context: JabraButtonContext = JabraButtonContext.MUSIC,
        side: JabraButtonSide = JabraButtonSide.LEFT,
        gesture: JabraButtonGesture = JabraButtonGesture.SINGLE,
    ) = JabraButtonSlot(context, side, gesture)

    private class ButtonsClient(private val pid: Int = 9421) : GnpClient {
        override val closed = CompletableDeferred<Throwable?>()
        override val events = MutableSharedFlow<GnpFrame>()
        var contexts = bytes(0, 1, 2)
        var spotify: ByteArray? = bytes(0)
        var assistant: ByteArray? = bytes(0)
        var rejectWrites = false
        var applyWrites = true
        var cancelLockRead = false
        val current = mutableMapOf(0 to music(), 1 to incoming(), 2 to ongoing())
        val defaults = current.mapValues { it.value.copyOf() }.toMutableMap()
        val reads = mutableListOf<Pair<Int, ByteArray>>()
        val writes = mutableListOf<Pair<Int, ByteArray>>()
        override suspend fun open() = Unit
        override fun close() = Unit
        override suspend fun read(topic: Int, payload: ByteArray): ByteArray {
            reads += topic to payload.copyOf()
            return when (topic) {
                0x0211 -> bytes(pid and 255, pid shr 8)
                0x13ad -> contexts.copyOf()
                0x139a -> current.getValue(payload.single().toInt()).copyOf()
                0x139b -> defaults.getValue(payload.single().toInt()).copyOf()
                0x13e5 -> {
                    if (cancelLockRead) throw CancellationException("Session ended")
                    spotify?.copyOf() ?: throw JabraRejectedException(0xfd)
                }
                0x0d4d -> {
                    check(payload.contentEquals(bytes(3)))
                    assistant?.copyOf() ?: throw JabraRejectedException(0xfd)
                }
                else -> error("Unexpected read ${topic.toString(16)}")
            }
        }
        override suspend fun write(topic: Int, payload: ByteArray) {
            writes += topic to payload.copyOf()
            if (rejectWrites) throw JabraRejectedException(0xfd)
            if (!applyWrites) return
            when (topic) {
                0x139a -> {
                    check(payload.size == 4)
                    val data = current.getValue(payload[0].toInt())
                    val at = data.indices.step(4).single { i -> (0..2).all { data[i + it] == payload[it] } }
                    data[at + 3] = payload[3]
                }
                0x139b -> {
                    val context = payload.single().toInt()
                    current[context] = defaults.getValue(context).copyOf()
                }
                else -> error("Unexpected write ${topic.toString(16)}")
            }
        }
    }

    companion object {
        private fun bytes(vararg values: Int) = values.map(Int::toByte).toByteArray()
        // Semantic GNP records from the documented model schemas; not hardware captures.
        private fun music() = bytes(0, 0, 0, 5, 0, 0, 1, 4, 0, 0, 2, 0, 0, 1, 0, 1, 0, 1, 1, 2, 0, 1, 2, 3)
        private fun incoming() = bytes(1, 0, 0, 8, 1, 0, 1, 6, 1, 0, 2, 0, 1, 1, 0, 8, 1, 1, 1, 6, 1, 1, 2, 0)
        private fun ongoing() = bytes(2, 0, 0, 9, 2, 0, 1, 11, 2, 0, 2, 0, 2, 1, 0, 9, 2, 1, 1, 11, 2, 1, 2, 0)
    }
}
