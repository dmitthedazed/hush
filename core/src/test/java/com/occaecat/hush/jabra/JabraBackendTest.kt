package com.occaecat.hush.jabra

import com.occaecat.hush.mdr.AmbientMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class JabraBackendTest {
    @Test fun initializationPublishesOnlySupportedConfirmedState() = withBackend { backend, _ ->
        val state = backend.state.value
        assertEquals("Jabra Elite 10", state.modelName)
        assertEquals("4.6.0", state.firmware)
        assertEquals(AmbientMode.NOISE_CANCELLING, state.ncAsm!!.mode)
        assertEquals(3, state.ncAsm.ambientLevel)
        assertEquals(3, state.ancLevel)
        assertEquals(30, state.sleepMinutes)
        assertEquals(1, state.announcementMode)
        assertEquals(85, state.battery)
        assertEquals(3, state.batteries.size)
        assertEquals("jabra-gnp", state.eqCaps!!.namespace)
        assertTrue(state.eqCaps.presets.isEmpty())
        assertFalse(state.capabilities.powerOff)
        assertFalse(state.capabilities.voiceFocus)
    }

    @Test fun unsupportedIdentityNeverSendsInitializationWrites() = runBlocking {
        val fake = FakeGnp().apply { pid = bytes(1, 2) }
        val scope = CoroutineScope(currentCoroutineContext() + SupervisorJob())
        val backend = JabraBackend(fake, scope)
        try {
            backend.open()
            assertTrue(runCatching { backend.initialize() }.exceptionOrNull() is IOException)
            assertTrue(fake.writes.isEmpty())
            assertNull(backend.state.value.ncAsm)
        } finally { backend.close(); scope.cancel() }
    }

    @Test fun initializationReadsSupportedEventsBeforeSubscribing() = withBackend(
        FakeGnp().apply { supportedEvents = bytes(0, 0, 1, 0x90, 0, 0, 0, 1) }
    ) { _, fake ->
        assertTrue(fake.eventsRead)
        assertArrayEquals(bytes(0, 0, 0, 0x90, 0, 0, 0, 0),
            fake.writes.single { it.first == JabraProtocol.EVENTS }.second)
    }

    @Test fun optionalUnsupportedSettingsStayHidden() = withBackend(
        FakeGnp().apply { unsupported += listOf(JabraProtocol.EQ, JabraProtocol.SLEEP, JabraProtocol.ANNOUNCEMENTS, 0x138e) }
    ) { backend, fake ->
        assertNull(backend.state.value.eqCaps)
        assertNull(backend.state.value.pauseWhenRemoved)
        assertNull(backend.state.value.sleepMinutes)
        assertNull(backend.state.value.announcementMode)
        fake.writes.clear()
        assertTrue(runCatching { backend.setSleepMinutes(30) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(fake.writes.isEmpty())
    }

    @Test fun rejectedWritesDoNotOptimisticallyChangeTheMode() = withBackend { backend, fake ->
        fake.rejectWrite = true
        assertTrue(runCatching { backend.setMode(AmbientMode.OFF) }.exceptionOrNull() is JabraRejectedException)
        assertEquals(AmbientMode.NOISE_CANCELLING, backend.state.value.ncAsm!!.mode)
        assertNotNull(backend.state.value.error)
    }

    @Test fun equalizerGainsArePublishedOnlyAfterReadback() = withBackend { backend, fake ->
        val changed = eqFixture().also { it[6] = 0; it[7] = 60; it[13] = 0xff.toByte(); it[14] = 0xc4.toByte() }
        fake.nextEqualizerResponse = changed
        backend.setEqBands(listOf(60, -60))
        assertEquals(listOf(60, -60), backend.state.value.eq!!.bands)
        assertEquals(JabraProtocol.EQ, fake.writes.last().first)
        fake.applyWrites = false
        assertTrue(runCatching { backend.setEqBands(listOf(0, 0)) }.exceptionOrNull() is IOException)
        assertEquals(listOf(60, -60), backend.state.value.eq!!.bands)
    }

    @Test fun applyingBandsEnablesEqualizerAndRestoringCanPreserveBypass() = withBackend(
        FakeGnp().apply { enabled = 0 }
    ) { backend, fake ->
        fake.writes.clear()
        backend.setEqBands(listOf(60, -60), enabled = true)
        assertTrue(backend.state.value.eq!!.enabled)
        assertEquals(listOf(JabraProtocol.EQ, JabraProtocol.EQ_ENABLED), fake.writes.map { it.first })
        assertArrayEquals(bytes(2, 0, 0xff, 0xc4, 1, 0x77, 0, 60, 0x10, 0,
            5, 0xdc, 0xff, 0xc4, 0x20, 0), fake.writes.first().second)
        backend.setEqBands(listOf(-120, 120), enabled = false)
        assertFalse(backend.state.value.eq!!.enabled)
        assertEquals(listOf(-120, 120), backend.state.value.eq!!.bands)
    }

    @Test fun rapidDifferentBandEditsPreserveEachOtherAndCoalesceOnlyTheirOwnBand() = withBackend(
        FakeGnp().apply { enabled = 0 }
    ) { backend, fake ->
        fake.writes.clear()
        val gate = CompletableDeferred<Unit>()
        fake.blockNextWrite = gate
        val first = launch(start = CoroutineStart.UNDISPATCHED) { backend.setEqBand(0, 60) }
        val superseded = launch(start = CoroutineStart.UNDISPATCHED) { backend.setEqBand(1, -60) }
        val latest = launch(start = CoroutineStart.UNDISPATCHED) { backend.setEqBand(1, -90) }
        assertEquals(listOf(-120, 120), backend.state.value.eq!!.bands)
        assertFalse(backend.state.value.eq!!.enabled)
        gate.complete(Unit)
        first.join()
        superseded.join()
        latest.join()
        assertEquals(listOf(60, -90), backend.state.value.eq!!.bands)
        assertTrue(backend.state.value.eq!!.enabled)
        assertEquals(2, fake.writes.count { it.first == JabraProtocol.EQ })
        assertEquals(listOf(JabraProtocol.EQ, JabraProtocol.EQ_ENABLED,
            JabraProtocol.EQ, JabraProtocol.EQ_ENABLED), fake.writes.map { it.first })
    }

    @Test fun elite10StatusAccessDeniedDoesNotHideOrDisableTheReadableCurve() = withBackend(
        FakeGnp().apply { equalizerResponse = elite10CapturedEq(); equalizerStatusError = 0xfd }
    ) { backend, fake ->
        assertNotNull(backend.state.value.eqCaps)
        assertFalse(backend.state.value.eqCaps!!.supportsEnable)
        assertTrue(backend.state.value.eq!!.enabled)
        assertEquals(listOf(270, -118, 0, 0, 0), backend.state.value.eq!!.bands)
        fake.writes.clear()
        backend.setEqBands(listOf(300, -118, 0, 0, 0), enabled = true)
        assertEquals(listOf(JabraProtocol.EQ), fake.writes.map { it.first })
        assertEquals(listOf(300, -118, 0, 0, 0), backend.state.value.eq!!.bands)
        fake.writes.clear()
        backend.setEqBand(0, 330)
        assertEquals(listOf(JabraProtocol.EQ), fake.writes.map { it.first })
        assertEquals(listOf(330, -118, 0, 0, 0), backend.state.value.eq!!.bands)
        fake.writes.clear()
        assertTrue(runCatching { backend.setEqBands(listOf(0, 0, 0, 0, 0), enabled = false) }
            .exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { backend.setEqEnabled(false) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(fake.writes.isEmpty())
        assertEquals(listOf(330, -118, 0, 0, 0), backend.state.value.eq!!.bands)
    }

    @Test fun unsuccessfulReadbackRetainsTheActualHardwareState() = withBackend { backend, fake ->
        fake.applyWrites = false
        assertTrue(runCatching { backend.setMode(AmbientMode.OFF) }.exceptionOrNull() is IOException)
        assertEquals(AmbientMode.NOISE_CANCELLING, backend.state.value.ncAsm!!.mode)
        fake.applyWrites = true
        backend.setMode(AmbientMode.OFF)
        assertEquals(AmbientMode.OFF, backend.state.value.ncAsm!!.mode)
        assertNull(backend.state.value.error)
    }

    @Test fun strengthIsInvertedAndQueuedSliderChangesAreCoalesced() = withBackend { backend, fake ->
        fake.writes.clear()
        val gate = CompletableDeferred<Unit>()
        fake.blockNextWrite = gate
        val first = launch(start = CoroutineStart.UNDISPATCHED) { backend.setAmbientLevel(1) }
        val queued = (2..5).map { value -> launch(start = CoroutineStart.UNDISPATCHED) { backend.setAmbientLevel(value) } }
        gate.complete(Unit)
        first.join()
        queued.forEach { it.join() }
        assertEquals(2, fake.writes.size)
        assertArrayEquals(bytes(2, 4), fake.writes.first().second)
        assertArrayEquals(bytes(2, 0), fake.writes.last().second)
        assertEquals(5, backend.state.value.ncAsm!!.ambientLevel)
    }

    @Test fun closeCancelsAnInFlightDelayedConfirmation() = withBackend { backend, fake ->
        val operation = async(start = CoroutineStart.UNDISPATCHED) { runCatching { backend.setMode(AmbientMode.OFF) } }
        backend.close()
        val result = withTimeout(500) { operation.await() }
        assertTrue(result.isFailure)
        assertEquals(AmbientMode.NOISE_CANCELLING, backend.state.value.ncAsm!!.mode)
        assertTrue(fake.closed.isCompleted)
    }

    @Test fun physicalButtonPushTriggersAReadOfTheNewMode() = withBackend { backend, fake ->
        fake.mode = 2
        fake.events.emit(GnpFrame(9, 4, 0, GnpFrame.EVENT, 0x0d, 0x4c, bytes(9, 1, 4)))
        // Push deliberately contains stale ANC: state must come from the readback.
        withTimeout(2_000) {
            while (backend.state.value.ncAsm?.mode != AmbientMode.AMBIENT) delay(10)
        }
    }

    @Test fun elite85tPauseUsesTheInvertedWireSetting() = withBackend(
        FakeGnp().apply { pid = bytes(0xcd, 0x24); pause = 0 }
    ) { backend, fake ->
        assertTrue(backend.state.value.pauseWhenRemoved == true)
        backend.setPauseWhenRemoved(false)
        assertArrayEquals(bytes(1), fake.writes.last().second)
        assertEquals(0x1383, fake.writes.last().first)
        assertFalse(backend.state.value.pauseWhenRemoved!!)
    }

    private fun withBackend(fake: FakeGnp = FakeGnp(), body: suspend CoroutineScope.(JabraBackend, FakeGnp) -> Unit) = runBlocking {
        val owner = CoroutineScope(currentCoroutineContext() + SupervisorJob(currentCoroutineContext()[Job]))
        val backend = JabraBackend(fake, owner)
        try {
            backend.open()
            backend.initialize()
            body(backend, fake)
        } finally { backend.close(); owner.cancel() }
    }
}

private class FakeGnp : GnpClient {
    override val closed = CompletableDeferred<Throwable?>()
    override val events = MutableSharedFlow<GnpFrame>(extraBufferCapacity = 8)
    var pid = bytes(0x46, 0x2e)
    var mode = 4
    var ambient = 2
    var anc = 1
    var pause = 1
    var sleep = 6
    var announcements = 1
    var enabled = 1
    var rejectWrite = false
    var applyWrites = true
    var blockNextWrite: CompletableDeferred<Unit>? = null
    var equalizerResponse = eqFixture()
    var nextEqualizerResponse: ByteArray? = null
    var equalizerStatusError: Int? = null
    var supportedEvents = bytes(0, 0, 7, 0xff)
    var eventsRead = false
    val unsupported = mutableSetOf<Int>()
    val writes = mutableListOf<Pair<Int, ByteArray>>()

    override suspend fun open() = Unit
    override suspend fun read(topic: Int, payload: ByteArray): ByteArray {
        if (closed.isCompleted) throw IOException("closed")
        if (topic in unsupported) throw JabraRejectedException(0xfa)
        return when (topic) {
            JabraProtocol.PRODUCT_ID -> pid
            JabraProtocol.FIRMWARE -> bytes(5) + "4.6.0".toByteArray()
            JabraProtocol.SERIAL -> bytes(4) + "test".toByteArray()
            JabraProtocol.EVENTS -> {
                check(payload.isEmpty())
                eventsRead = true
                supportedEvents
            }
            JabraProtocol.AMBIENCE -> when (payload.single().u()) {
                0 -> bytes(7, 5, 4, 0, 0)
                1 -> bytes(mode)
                2 -> bytes(ambient)
                3 -> bytes(anc)
                else -> throw IOException("Unexpected selector")
            }
            JabraProtocol.BATTERY -> bytes(0x80, 90, 0, 0, 2, 0x15, 0, 80, 5, 0, 85)
            JabraProtocol.EQ -> equalizerResponse
            JabraProtocol.EQ_ENABLED -> {
                equalizerStatusError?.let { throw JabraRejectedException(it) }
                bytes(enabled)
            }
            JabraProtocol.SLEEP -> bytes(sleep)
            JabraProtocol.ANNOUNCEMENTS -> bytes(announcements)
            0x138e -> bytes(0, pause)
            0x1383 -> bytes(pause)
            else -> throw IOException("Unexpected read ${topic.toString(16)}")
        }
    }

    override suspend fun write(topic: Int, payload: ByteArray) {
        if (closed.isCompleted) throw IOException("closed")
        if (rejectWrite || topic in unsupported) throw JabraRejectedException(0xfa)
        if (topic == JabraProtocol.EVENTS) {
            check(eventsRead) { "Supported events must be read first" }
            if (payload.size != supportedEvents.size || payload.indices.any {
                payload[it].u() and supportedEvents[it].u().inv() != 0
            }) throw JabraRejectedException(0xfa)
        }
        writes += topic to payload.copyOf()
        val gate = blockNextWrite
        blockNextWrite = null
        gate?.await()
        if (!applyWrites) return
        when (topic) {
            JabraProtocol.AMBIENCE -> when (payload[0].u()) {
                1 -> mode = payload[1].u()
                2 -> ambient = payload[1].u()
                3 -> anc = payload[1].u()
            }
            JabraProtocol.EQ_ENABLED -> enabled = payload.single().u()
            JabraProtocol.EQ -> {
                equalizerResponse = nextEqualizerResponse ?: equalizerResponse.copyOf().also { reply ->
                    reply[1] = payload[2]
                    reply[2] = payload[3]
                    repeat(payload[0].u()) { band ->
                        payload.copyInto(reply, destinationOffset = 4 + 7 * band,
                            startIndex = 4 + 6 * band, endIndex = 10 + 6 * band)
                    }
                }
                nextEqualizerResponse = null
            }
            JabraProtocol.SLEEP -> sleep = payload.single().u()
            JabraProtocol.ANNOUNCEMENTS -> announcements = payload.single().u()
            0x138e, 0x1383 -> pause = payload.last().u()
        }
    }

    override fun close() { closed.complete(null) }
}
