package com.occaecat.hush.sim

import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase
import com.occaecat.hush.diag.HeadsetProbe
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.v1.Init
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

/** The self test against simulated headsets: a sound one passes everything, a broken setting is caught. */
@OptIn(ExperimentalCoroutinesApi::class)
class HeadsetProbeTest {
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

    private fun start(sim: SimHeadset) {
        controller.start("00:00:5E:00:53:00", "Sim", sim)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val s = controller.state.value
            val plan = s.protocolVersion?.let { Init.plan(it, s.functions) }
            if (s.phase == Phase.CONNECTED && plan != null && plan.isNotEmpty() && plan.last() in sim.received) {
                Thread.sleep(300)
                return
            }
            Thread.sleep(20)
        }
        fail("init did not finish")
    }

    private fun probe(sim: SimHeadset): HeadsetProbe.Report {
        start(sim)
        return runBlocking { HeadsetProbe(controller).run("test") }
    }

    private fun assertAllPass(r: HeadsetProbe.Report) {
        val bad = r.checks.filter { it.verdict != HeadsetProbe.Verdict.PASS }
        assertTrue(r.text, bad.isEmpty())
        assertTrue(r.checks.isNotEmpty())
    }

    @Test
    fun xm4PassesEveryCheck() {
        val r = probe(SimHeadset(Profiles.xm4()))
        assertAllPass(r)
        val names = r.checks.map { it.name }
        for (n in listOf("Noise control: ambient sound", "Ambient sound level", "Equalizer preset", "DSEE", "Speak-to-Chat",
            "Pause when taken off", "Automatic power off", "Voice guidance", "Volume")) assertTrue("$n in $names", n in names)
        assertTrue(r.text, "Model: WH-1000XM4   firmware: 3.0.1   protocol: 0x7000" in r.text)
        assertTrue(r.text, "== Replies Hush could not read (0) ==" in r.text)
        assertTrue(r.text, "NO REPLY" !in r.text)
        assertEquals("hush-WH-1000XM4-3.0.1", r.fileStem)
    }

    @Test
    fun otherProfilesPass() {
        assertAllPass(probe(SimHeadset(Profiles.xm3(), SimHeadset.Quirks(replyBeforeAck = true))))
        controller.disconnect()
        controller = HeadphonesController(FakeApp())
        assertAllPass(probe(SimHeadset(Profiles.wf1000xm3())))
    }

    @Test
    fun aSettingTheHeadsetIgnoresFails() {
        val sim = SimHeadset(Profiles.xm4())
        // DSEE (Audio SET_PARAM upscaling) acknowledged but never applied: no notify, the read back says the old value.
        sim.onSet = { if (it.table == Table.ONE && it.payload[0] == 0xE8.toByte()) emptyList() else null }
        val r = probe(sim)
        val dsee = r.checks.single { it.name == "DSEE" }
        assertEquals(r.text, HeadsetProbe.Verdict.PARTIAL, dsee.verdict)
        assertTrue(dsee.notes.joinToString("\n"), dsee.notes.any { "no notify" in it })
        assertTrue(r.checks.filter { it !== dsee }.all { it.verdict == HeadsetProbe.Verdict.PASS })
    }

    @Test
    fun theCaptureLeavesNothingPersonal() {
        val sim = SimHeadset(Profiles.xm4())
        val r = probe(sim)
        val all = r.text + r.capture + r.replies
        // The profile's own stand-ins are what a real capture would carry in their place.
        for (personal in listOf("Phone", "Laptop", "Tablet", "12345678")) assertFalse("$personal left in", personal in all)
        assertTrue(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}").findAll(all).all { it.value.startsWith("00:00:5E:00:53:") })
        // The replies file is a simulator profile again.
        val replies = r.replies.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(replies.size >= Profiles.xm4().size / 2)
        assertTrue(replies.all { it.startsWith("T1 ") || it.startsWith("T2 ") })
        assertTrue(r.capture.lines().any { "-> T1 00 00" in it })
    }
}
