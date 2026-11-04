package com.occaecat.hush.sim

import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.Charge
import com.occaecat.hush.mdr.Codec
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.v1.FunctionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

/** HeadphonesController end to end against a simulated protocol v2 headset (the WF-1000XM5 profile). */
@OptIn(ExperimentalCoroutinesApi::class)
class HeadsetSimV2Test {
    private val main = Executors.newSingleThreadExecutor { Thread(it, "sim-main").apply { isDaemon = true } }
    private lateinit var controller: HeadphonesController

    @Before
    fun setUp() {
        Dispatchers.setMain(main.asCoroutineDispatcher())
        controller = HeadphonesController(FakeApp())
    }

    @After
    fun tearDown() {
        controller.disconnect()
        Dispatchers.resetMain()
        main.shutdownNow()
    }

    private fun await(what: String, timeoutMs: Long = 10_000, predicate: (HeadphonesState) -> Boolean): HeadphonesState =
        runBlocking { withTimeoutOrNull(timeoutMs) { controller.state.first(predicate) } }
            ?: fail("timed out waiting for $what; state: ${controller.state.value}") as Nothing

    private fun t1(vararg b: Int) = Request(Table.ONE, ByteArray(b.size) { b[it].toByte() })
    private fun t2(vararg b: Int) = Request(Table.TWO, ByteArray(b.size) { b[it].toByte() })

    /** The session start ends with the paired-source list. */
    private fun started(sim: SimHeadset): HeadphonesState {
        controller.start("00:00:5E:00:53:00", "Sim", sim)
        assertTrue("session start", sim.awaitRequest(t2(0x36, 0x02), 10_000))
        return await("peers") { it.peers != null }
    }

    @Test
    fun sessionStartFillsTheState() {
        val sim = SimHeadset(Profiles.wf1000xm5(), v2 = true)
        val s = started(sim)
        assertEquals(Phase.CONNECTED, s.phase)
        assertTrue(s.protocolV2)
        assertEquals(0x03003018, s.protocolVersion)
        assertEquals("WF-1000XM5", s.modelName)
        assertEquals("6.1.0", s.firmware)
        assertEquals(3, s.modelColor)
        assertTrue(s.trueWireless)
        assertTrue(s.has(FunctionType.PLAYBACK_CONTROLLER) && s.has(FunctionType.SMART_TALKING_MODE))
        assertEquals(46, s.v2Functions?.t1?.size)
        assertEquals(Charge(100, false), s.rightBattery)
        assertEquals(Charge(53, false), s.caseBattery)
        assertEquals(Codec.AAC, s.codec)
        assertEquals(true, s.dseeAuto)
        assertEquals(AmbientMode.OFF, s.ncAsm?.mode)
        assertEquals(NcAsmKind.NC_ASM, s.ncAsmCaps?.kind)
        assertEquals(20, s.ncAsmCaps?.asmSteps)
        assertEquals(true, s.ncAsmCaps?.voice)
        assertEquals(listOf(20, 11, 10, 13, 9, 12), s.eq?.bands)
        assertEquals(3, s.eqCaps?.presets?.size)
        assertEquals(10, s.eqFlat)
        assertEquals(true, s.pauseWhenRemoved)
        assertEquals(listOf(0x10, 0x11), s.autoPowerOffOptions)
        assertEquals(0x10 to 0x00, s.autoPowerOff)
        assertEquals(false, s.speakToChat)
        assertEquals(listOf(0x35, 0x20), s.assignable)
        assertEquals(setOf(0xD1, 0xD2), s.generalSettings.keys)
        assertEquals(true, s.generalValues[0xD2])
        assertEquals(12, s.volume)
        assertEquals(30, s.volumeSteps)
        assertEquals(true, s.voiceGuidance)
        assertEquals(8, s.peers?.size)
        // Nothing went out in v1's encoding: battery is POWER (0x22) on v2, COMMON (0x10) on v1.
        assertTrue(sim.received.none { it.table == Table.ONE && it.payload[0].toInt() == 0x10 })
    }

    @Test
    fun controlsSpeakV2() {
        val sim = SimHeadset(Profiles.wf1000xm5(), v2 = true)
        started(sim)

        controller.setAmbientMode(AmbientMode.AMBIENT)
        assertTrue(sim.awaitRequest(t1(0x68, 0x17, 0x01, 0x01, 0x01, 0x00, 0x14)))
        await("ambient") { it.ncAsm?.mode == AmbientMode.AMBIENT }
        controller.setAmbientMode(AmbientMode.NOISE_CANCELLING)
        assertTrue(sim.awaitRequest(t1(0x68, 0x17, 0x01, 0x01, 0x00, 0x00, 0x14)))

        controller.setSpeakToChat(true)
        assertTrue(sim.awaitRequest(t1(0xF8, 0x0C, 0x00, 0x01)))
        await("speak-to-chat on") { it.speakToChat == true }

        controller.setPauseWhenRemoved(false)
        assertTrue(sim.awaitRequest(t1(0xF8, 0x01, 0x01)))
        controller.selectEqPreset(0x10)
        assertTrue(sim.awaitRequest(t1(0x58, 0x00, 0x10, 0x00)))
        controller.setAutoPowerOff(0x11)
        assertTrue(sim.awaitRequest(t1(0x28, 0x05, 0x11, 0x00)))
        controller.setVoiceGuidance(false)
        assertTrue(sim.awaitRequest(t2(0x48, 0x01, 0x01)))
        controller.refreshBattery()
        assertTrue(sim.awaitRequest(t1(0x22, 0x0A)))
        controller.nextTrack()
        assertTrue(sim.awaitRequest(t1(0xA4, 0x01, 0x00, 0x02)))
    }

    @Test
    fun v2ReplyOverTheV1ServiceSwitchesGeneration() {
        // A v2 headset reached through the v1 UUID (no SDP record cached): its 8-byte protocol info flips the session.
        val sim = SimHeadset(Profiles.wf1000xm5(), v2 = false)
        val s = started(sim)
        assertTrue(s.protocolV2)
        assertEquals("WF-1000XM5", s.modelName)
        assertEquals(0x03003018, s.protocolVersion)
        assertTrue(s.has(FunctionType.NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE))
        await("noise state") { it.ncAsm != null }
    }
}
