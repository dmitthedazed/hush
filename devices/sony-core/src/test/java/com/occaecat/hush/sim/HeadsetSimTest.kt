package com.occaecat.hush.sim

import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.Charge
import com.occaecat.hush.mdr.Codec
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.v1.Alert
import com.occaecat.hush.mdr.v1.Audio
import com.occaecat.hush.mdr.v1.EqEbb
import com.occaecat.hush.mdr.v1.GeneralSettings
import com.occaecat.hush.mdr.v1.Init
import com.occaecat.hush.mdr.v1.NcAsm
import com.occaecat.hush.mdr.v1.Play
import com.occaecat.hush.mdr.v1.SystemSettings
import com.occaecat.hush.mdr.v1.VoiceGuidance
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

/** HeadphonesController end to end against simulated headsets. */
@OptIn(ExperimentalCoroutinesApi::class)
class HeadsetSimTest {
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

    private fun connect(sim: SimHeadset) = controller.start("00:00:5E:00:53:00", "Sim", sim)

    private fun await(what: String, timeoutMs: Long = 10_000, predicate: (HeadphonesState) -> Boolean): HeadphonesState =
        runBlocking { withTimeoutOrNull(timeoutMs) { controller.state.first(predicate) } }
            ?: fail("timed out waiting for $what; state: ${controller.state.value}") as Nothing

    /** The last request of the session start has been answered, or was one the headset never answers. */
    private fun awaitInit(sim: SimHeadset) {
        val last = Init.plan(controller.state.value.protocolVersion ?: 0, controller.state.value.functions).lastOrNull()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val s = controller.state.value
            val plan = s.protocolVersion?.let { Init.plan(it, s.functions) }
            if (plan != null && plan.isNotEmpty() && plan.last() in sim.received) {
                Thread.sleep(300) // the replies to the last few requests
                return
            }
            Thread.sleep(20)
        }
        fail("init did not finish; sent ${sim.received.size}, last expected $last")
    }

    @Test
    fun xm4SessionStartFillsTheState() {
        val sim = SimHeadset(Profiles.xm4())
        connect(sim)
        awaitInit(sim)
        val s = controller.state.value
        assertEquals(Phase.CONNECTED, s.phase)
        assertEquals("WH-1000XM4", s.modelName)
        assertEquals("3.0.1", s.firmware)
        assertEquals(0x7000, s.protocolVersion)
        assertEquals(100, s.battery)
        assertEquals(Codec.AAC, s.codec)
        assertEquals(true, s.dseeAuto)
        assertEquals(AmbientMode.NOISE_CANCELLING, s.ncAsm?.mode)
        assertEquals(6, s.eq?.bands?.size)
        assertEquals(6, s.eqBands.size)
        assertEquals(true, s.optimizerResult?.personalMeasured)
        assertEquals(true, s.pauseWhenRemoved)
        assertEquals(listOf(0x10, 0x11), s.autoPowerOffOptions)
        assertEquals(false, s.speakToChat)
        assertEquals(1, s.assignableKeys.size)
        assertEquals(setOf(0xD1, 0xD2), s.generalSettings.keys)
        assertEquals(30, s.volume)
        assertEquals(true, s.voiceGuidance)
        assertEquals(4, s.voiceGuidanceLanguage)
        assertEquals(3, s.peers?.size)
        assertEquals("12345678", s.serial)

        // The real XM4 answered every GET of the session start; only SETs went unanswered.
        val unanswered = sim.received.filter { sim.answerFor(it) == null }.map { it.toString() }
        assertEquals(listOf("94 01 00"), unanswered)
    }

    @Test
    fun xm3SessionStartFillsTheState() {
        val sim = SimHeadset(Profiles.xm3(), SimHeadset.Quirks(replyBeforeAck = true))
        connect(sim)
        awaitInit(sim)
        val s = controller.state.value
        assertEquals(Phase.CONNECTED, s.phase)
        assertEquals("WH-1000XM3", s.modelName)
        assertEquals("4.5.2", s.firmware)
        assertEquals(AmbientMode.NOISE_CANCELLING, s.ncAsm?.mode)
        assertTrue(s.generalSettings.isNotEmpty())
        val unanswered = sim.received.filter { sim.answerFor(it) == null }.map { it.toString() }
        // Alerts on (a SET) goes unanswered, as on the XM4.
        assertEquals(listOf("94 01 00"), unanswered)
    }

    @Test
    fun wf1000xm3SessionStartAndEarbudControls() {
        val sim = SimHeadset(Profiles.wf1000xm3())
        connect(sim)
        awaitInit(sim)
        val s = controller.state.value
        assertEquals("WF-1000XM3", s.modelName)
        assertEquals(0x5000, s.protocolVersion)
        assertEquals(Charge(70, false), s.leftBattery)
        assertEquals(Charge(65, false), s.rightBattery)
        assertEquals(Charge(40, true), s.caseBattery)
        assertEquals(true to true, s.earbudsConnected)
        assertEquals(AmbientMode.AMBIENT, s.ncAsm?.mode)
        assertEquals(12, s.ncAsm?.ambientLevel)
        assertEquals(listOf(AmbientMode.NOISE_CANCELLING, AmbientMode.AMBIENT, AmbientMode.OFF), s.noiseModes())
        assertEquals(2, s.assignableKeys.size)
        assertEquals(listOf(0x00, 0x20), s.assignable)
        assertEquals(false, s.voiceGuidanceCaps?.language)
        assertEquals(null, s.peers)
        assertEquals(emptyMap<Int, Any>(), s.generalSettings)
        // The single-battery query is in every session start; earbuds have nothing to say to it.
        val unanswered = sim.received.filter { sim.answerFor(it) == null }.map { it.toString() }
        assertEquals(listOf("94 01 00", "10 00"), unanswered)

        // One earbud out, then charged in the case.
        sim.push(Table.ONE, 0x27, 0x01, 0x01, 0x00)
        await("right earbud out") { it.earbudsConnected == (true to false) }
        sim.push(Table.ONE, 0x13, 0x01, 72, 0x00, 66, 0x01)
        await("earbud batteries") { it.rightBattery == Charge(66, true) }
        sim.push(Table.ONE, 0x13, 0x02, 39, 0x00)
        await("case battery") { it.caseBattery == Charge(39, false) }

        // The right key gets a new preset; the left one keeps its own.
        sim.alerts[Reply(Table.ONE, byteArrayOf(0xF8.toByte(), 0x06))] = byteArrayOf(0x99.toByte(), 0x01, Alert.KEY_ASSIGN_CHANGE.toByte(), 0x00)
        controller.setAssignable(1, 0x10)
        expect(sim, "right key", SystemSettings.setAssignable(listOf(0x00, 0x10))) { it.assignable == listOf(0x00, 0x10) }
        controller.setAmbientMode(AmbientMode.NOISE_CANCELLING)
        expect(sim, "nc on", NcAsm.set(controller.state.value.ncAsm!!)) { it.ncAsm?.mode == AmbientMode.NOISE_CANCELLING }
    }

    private fun xm4(quirks: SimHeadset.Quirks = SimHeadset.Quirks()) = SimHeadset(Profiles.xm4(), quirks).also {
        connect(it)
        awaitInit(it)
    }

    /** [request] reached the headset and, once the headset's answer is in, the state passes [check]. */
    private fun expect(sim: SimHeadset, what: String, request: Request, check: (HeadphonesState) -> Boolean) {
        assertTrue("$what: headset never got $request; got ${sim.received.takeLast(5)}", sim.awaitRequest(request))
        await(what, 3_000, check)
        Thread.sleep(200) // the notify may land after an optimistic update: it must agree with it
        assertTrue("$what: state after the notify: ${controller.state.value}", check(controller.state.value))
    }

    @Test
    fun xm4ActionsSendTheirFrameAndTheNotifyLands() {
        val sim = xm4()
        sim.alerts[Reply(Table.ONE, byteArrayOf(0xF8.toByte(), 0x06))] = byteArrayOf(0x99.toByte(), 0x01, Alert.KEY_ASSIGN_CHANGE.toByte(), 0x00)
        sim.alerts[Reply(Table.ONE, byteArrayOf(0xE8.toByte(), 0x01))] = byteArrayOf(0x99.toByte(), 0x01, Alert.CONNECTION_MODE_CHANGE.toByte(), 0x00)

        controller.setAmbientMode(AmbientMode.AMBIENT)
        val ambient = controller.state.value.ncAsm!!
        expect(sim, "ambient", NcAsm.set(ambient)) { it.ncAsm?.mode == AmbientMode.AMBIENT && it.ncAsm?.ambientLevel == 20 }

        controller.selectEqPreset(0x11)
        expect(sim, "eq preset", EqEbb.selectPreset(0x11)) { it.eq?.presetId == 0x11 && it.eq?.bands?.size == 6 }
        val bands = controller.state.value.eq!!.bands
        controller.setEqBand(0, 12)
        expect(sim, "eq band", EqEbb.setBands(0xA0, listOf(12) + bands.drop(1))) { it.eq?.presetId == 0xA0 && it.eq?.bands?.first() == 12 }

        controller.setDsee(false)
        expect(sim, "dsee", Audio.setUpscaling(false)) { it.dseeAuto == false }
        controller.setSpeakToChat(true)
        expect(sim, "speak to chat", SystemSettings.setSpeakToChat(true)) { it.speakToChat == true }
        controller.setPauseWhenRemoved(false)
        expect(sim, "pause when removed", SystemSettings.setPauseWhenRemoved(false)) { it.pauseWhenRemoved == false }
        controller.setAutoPowerOff(0x10)
        expect(sim, "auto power off", SystemSettings.setAutoPowerOff(0x10, 0x00)) { it.autoPowerOff == (0x10 to 0x00) }
        controller.setVoiceGuidance(false)
        expect(sim, "voice guidance", VoiceGuidance.set(false)) { it.voiceGuidance == false }
        controller.setVolume(10)
        expect(sim, "volume", Play.setVolume(10)) { it.volume == 10 }
        controller.setGeneralSetting(0xD1, false)
        expect(sim, "touch panel", GeneralSettings.set(0xD1, false)) { it.generalValues[0xD1] == false }

        // Settings that make the headset ask first: Hush answers the alert, then the headset applies them.
        controller.setCustomButton(0x31)
        expect(sim, "custom button", Alert.answer(Alert.KEY_ASSIGN_CHANGE, true)) { it.customButton == 0x31 && it.alert == null }
        assertTrue(SystemSettings.setAssignable(listOf(0x31)) in sim.received)
        controller.setSoundQualityMode(true)
        expect(sim, "sound quality", Alert.answer(Alert.CONNECTION_MODE_CHANGE, true)) { it.soundQualityPreferConnection == true }
        assertEquals(Phase.CONNECTED, controller.state.value.phase)
    }

    @Test
    fun xm4HeadsetSideChangesReachTheState() {
        val sim = xm4()
        sim.push(Table.ONE, 0x13, 0x00, 80, 0x01)
        await("battery") { it.battery == 80 && it.charging }
        sim.push(Table.ONE, 0x1B, 0x00, Codec.SBC.code)
        await("codec") { it.codec == Codec.SBC }
        val ambient = NcAsm.set(NcAsmState(AmbientMode.AMBIENT, 5, false, ncSettingType = 2, asmSettingType = 1, kind = NcAsmKind.NC_ASM)).payload
        sim.push(Reply(Table.ONE, ambient.copyOf().also { it[0] = 0x69 }))
        await("nc button") { it.ncAsm?.mode == AmbientMode.AMBIENT && it.ncAsm?.ambientLevel == 5 }
        sim.push(Table.TWO, 0x49, 0x01, 0x01, 0x00)
        await("voice guidance") { it.voiceGuidance == false }

        // A track change says only that it changed: Hush re-reads the names.
        val before = sim.received.count { it == Play.param(0x00) }
        sim.push(Table.ONE, 0xA9, 0x01, 0x00)
        Thread.sleep(800)
        assertTrue(sim.received.count { it == Play.param(0x00) } > before)

        // An alert Hush did not ask for is shown, and declining it re-reads what it may have changed.
        sim.push(Table.ONE, 0x99, 0x01, Alert.FIRMWARE_UPDATE_DISCONNECT, 0x00)
        await("alert") { it.alert?.first == Alert.FIRMWARE_UPDATE_DISCONNECT }
        controller.answerAlert(Alert.FIRMWARE_UPDATE_DISCONNECT, false)
        assertTrue(sim.awaitRequest(Alert.answer(Alert.FIRMWARE_UPDATE_DISCONNECT, false)))
        assertTrue(sim.awaitRequest(SystemSettings.get(SystemSettings.ASSIGNABLE_SETTINGS)))
        assertEquals(null, controller.state.value.alert)
    }

    @Test
    fun xm4SurvivesTrickledAndCutOffFrames() {
        val sim = xm4(SimHeadset.Quirks(trickle = true, cutOffEvery = 3))
        assertEquals("WH-1000XM4", controller.state.value.modelName)
        assertEquals(3, controller.state.value.peers?.size)
        assertEquals(1, sim.opens)
    }

    @Test
    fun xm4NotificationsDuringTheSessionStart() {
        val sim = SimHeadset(Profiles.xm4())
        sim.onRequest = { r ->
            if (r == EqEbb.get()) {
                sim.push(Table.ONE, 0x13, 0x00, 55, 0x00)
                sim.push(Table.ONE, 0x1B, 0x00, Codec.LDAC.code)
            }
        }
        connect(sim)
        awaitInit(sim)
        val s = controller.state.value
        assertEquals(55, s.battery)
        assertEquals(Codec.LDAC, s.codec)
        assertEquals(3, s.peers?.size)
    }

    @Test
    fun xm4RetriedFramesAreSentOnce() {
        val sim = xm4()
        sim.quirks = SimHeadset.Quirks(dropFirstCopies = 1)
        controller.setVibrator(true)
        controller.setDsee(false)
        assertTrue(sim.awaitRequest(Audio.setUpscaling(false)))
        assertEquals(1, sim.received.count { it == Audio.setUpscaling(false) })
        assertEquals(Phase.CONNECTED, controller.state.value.phase)
    }

    @Test
    fun xm4LinkDropIsNoticed() {
        val sim = xm4()
        sim.drop()
        await("reconnecting", 3_000) { it.phase == Phase.RECONNECTING && it.reconnectReason == "Connection lost" }
    }

    /** Mutated replies and notifies of every kind must not end the session or stop events from landing. */
    @Test
    fun xm4SessionSurvivesGarbage() {
        val sim = xm4()
        val random = kotlin.random.Random(7)
        val all = Profiles.xm4() + Profiles.xm3() + Profiles.wf1000xm3()
        repeat(1_500) {
            val r = all.random(random)
            val p = r.payload.copyOf(random.nextInt(1, r.payload.size + 8))
            for (i in 1 until p.size) if (random.nextInt(4) == 0) p[i] = random.nextInt(256).toByte()
            sim.push(Reply(r.table, p))
        }
        sim.push(Table.ONE, 0x13, 0x00, 42, 0x00)
        await("battery after the garbage", 30_000) { it.battery == 42 }
        assertEquals(Phase.CONNECTED, controller.state.value.phase)
        controller.setVolume(7)
        assertTrue(sim.awaitRequest(Play.setVolume(7)))
    }
}
