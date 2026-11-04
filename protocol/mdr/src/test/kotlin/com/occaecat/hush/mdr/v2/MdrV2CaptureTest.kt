package com.occaecat.hush.mdr.v2

import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.Charge
import com.occaecat.hush.mdr.Codec
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.mdr.Frame
import com.occaecat.hush.mdr.FrameParser
import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.hex
import com.occaecat.hush.mdr.v1.FunctionType as V1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Replays what real v2 headsets sent (core/src/test/resources/mdr/WF-1000XM5-6.1.0 and WF-LC900-2.0.3*, from
 * mos9527's tests, MIT): a WF-1000XM5 earbud set and the LinkBuds Clip with its EQ and listening-mode sessions.
 */
class MdrV2CaptureTest {
    private fun replay(dir: String): List<Pair<Table, MdrEvent>> {
        val files = File(javaClass.classLoader!!.getResource("mdr/$dir")!!.toURI()).listFiles { f -> f.name.endsWith(".bin") }!!
        val parser = FrameParser()
        return files.sortedBy { it.name }.flatMap { f ->
            val bytes = f.readBytes()
            parser.feed(bytes, bytes.size).flatMap { frame ->
                val table = if (frame.type == Frame.Type.DATA_MDR_NO2) Table.TWO else Table.ONE
                when (val e = MdrV2.parse(table, frame.payload)) {
                    is MdrEvent.Several -> e.events.map { table to it }
                    else -> listOf(table to e)
                }
            }
        }
    }

    private val xm5 by lazy { replay("WF-1000XM5-6.1.0").map { it.second } }
    private val lc900 by lazy { replay("WF-LC900-2.0.3").map { it.second } }
    private val lc900Eq by lazy { replay("WF-LC900-2.0.3-equalizer").map { it.second } }

    private inline fun <reified T : MdrEvent> List<MdrEvent>.one(): T = filterIsInstance<T>().first()

    @Test
    fun everyFrameDecodes() {
        for (dir in listOf("WF-1000XM5-6.1.0", "WF-LC900-2.0.3", "WF-LC900-2.0.3-equalizer", "WF-LC900-2.0.3-listening")) {
            val unknown = replay(dir).map { it.second }.filterIsInstance<MdrEvent.Unknown>().map { it.payload.hex() }
            assertEquals(dir, emptyList<String>(), unknown)
        }
    }

    @Test
    fun readsTheWf1000xm5Identity() {
        val info = xm5.one<MdrEvent.ProtocolInfo>()
        assertEquals(MdrEvent.ProtocolInfo(0x03003018, v2 = true, table1 = true, table2 = true), info)
        assertTrue(info.version in Init.SUPPORTED_VERSIONS)
        assertEquals("WF-1000XM5", xm5.one<MdrEvent.ModelName>().name)
        assertEquals("6.1.0", xm5.one<MdrEvent.FirmwareVersion>().version)
        assertEquals(MdrEvent.SeriesAndColor(0x30, 0x03), xm5.one<MdrEvent.SeriesAndColor>())
    }

    @Test
    fun wf1000xm5Functions() {
        val lists = xm5.filterIsInstance<MdrEvent.SupportedFunctions>()
        val f = V2Functions(lists.first { it.table == Table.ONE }.codes, lists.first { it.table == Table.TWO }.codes)
        assertEquals(46, f.t1.size)
        assertEquals(11, f.t2.size) // one code is 0x3D, escaped on the wire
        assertEquals(NcAsmV2.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS, NcAsmV2.typeFor(f))
        assertEquals(EqEbb.PRESET_EQ, EqEbb.eqType(f))
        assertEquals(Power.AUTO_POWER_OFF_WEARING_DETECTION, Power.autoPowerOffType(f))
        assertEquals(SystemSettings.SMART_TALKING_MODE_TYPE2, SystemSettings.speakToChatType(f))
        assertEquals(SystemSettings.ASSIGNABLE_SETTINGS, SystemSettings.assignableType(f))
        assertEquals(0x02, Peripheral.typeFor(f))
        assertEquals(0x01, VoiceGuidance.typeFor(f))
        assertEquals(listOf(Power.battery(Power.LR_BATTERY_WITH_THRESHOLD), Power.battery(Power.CRADLE_BATTERY_WITH_THRESHOLD)), Power.batteries(f))

        val v1 = f.v1Equivalents()
        for (t in listOf(V1.LEFT_RIGHT_BATTERY_LEVEL, V1.CRADLE_BATTERY_LEVEL, V1.LEFT_RIGHT_CONNECTION_STATUS, V1.CODEC_INDICATOR,
            V1.PRESET_EQ, V1.NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE, V1.PLAYBACK_CONTROLLER, V1.UPSCALING, V1.CONTROL_BY_WEARING,
            V1.AUTO_POWER_OFF, V1.SMART_TALKING_MODE, V1.ASSIGNABLE_SETTINGS, V1.PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT, V1.VOICE_GUIDANCE)) {
            assertTrue(t.name, t.code in v1)
        }
        assertTrue(0xD1 in v1 && 0xD2 in v1)
        assertFalse(V1.NC_OPTIMIZER.code in v1)
    }

    @Test
    fun wf1000xm5State() {
        // 67 17 01 00 01 00 14: everything off, ambient remembered at 20, normal (not voice) mode
        val nc = xm5.one<MdrEvent.NcAsm>().state
        assertEquals(AmbientMode.OFF, nc.mode)
        assertEquals(20, nc.ambientLevel)
        assertEquals(NcAsmKind.NC_ASM, nc.kind)
        assertFalse(nc.voiceFocus)

        // 23 09: left 0 % (no bud), right 100 %, thresholds after; 23 0A: case 53 %
        assertEquals(MdrEvent.EarbudBatteries(Charge(0, false), Charge(100, false)), xm5.one<MdrEvent.EarbudBatteries>())
        assertEquals(MdrEvent.CaseBattery(Charge(53, false)), xm5.one<MdrEvent.CaseBattery>())
        assertEquals(MdrEvent.AutoPowerOff(0x10, 0x00), xm5.one<MdrEvent.AutoPowerOff>())
        assertEquals(EqState(0xA0, listOf(20, 11, 10, 13, 9, 12)), xm5.one<MdrEvent.Eq>().state)
        assertEquals(Codec.AAC, xm5.one<MdrEvent.AudioCodec>().codec)
        assertEquals(2, xm5.one<MdrEvent.UpscalingCaps>().type)
        assertTrue(xm5.one<MdrEvent.UpscalingSetting>().auto)
        assertEquals(12, xm5.one<MdrEvent.PlayVolume>().volume)
        assertEquals(2, xm5.one<MdrEvent.PlayStatus>().status)
        assertTrue(xm5.one<MdrEvent.PauseWhenRemoved>().on)
        assertFalse(xm5.one<MdrEvent.SpeakToChat>().enabled)
        assertEquals(listOf(0x35, 0x20), xm5.one<MdrEvent.CustomButton>().presets)
        assertTrue(xm5.one<MdrEvent.VoiceGuidance>().on)
        assertEquals(1, xm5.one<MdrEvent.VoiceGuidanceLanguage>().language)
        assertFalse(xm5.one<MdrEvent.PairingMode>().on)

        val gs = xm5.filterIsInstance<MdrEvent.GsCapability>().associate { it.setting.id to it.setting }
        assertEquals("SIDETONE_SETTING", gs[0xD1]?.title)
        assertEquals("MULTIPOINT_SETTING", gs[0xD2]?.title)
        assertTrue(gs.values.all { it.isBoolean })
        assertEquals(listOf(MdrEvent.GsValue(0xD1, false), MdrEvent.GsValue(0xD2, true)), xm5.filterIsInstance<MdrEvent.GsValue>())

        val peers = xm5.one<MdrEvent.Peers>()
        assertEquals(8, peers.devices.size)
        assertTrue(peers.devices.all { it.address.length == 17 && it.address[2] == ':' && it.name.isNotEmpty() })
        // Nothing is playing in the capture: every name is NOTHING, so the titles stay unset.
        assertTrue(xm5.filterIsInstance<MdrEvent.PlayName>().all { it.text == null })
    }

    @Test
    fun linkBudsClip() {
        assertEquals(0x03003015, lc900.one<MdrEvent.ProtocolInfo>().version)
        assertEquals("LinkBuds Clip", lc900.one<MdrEvent.ModelName>().name)
        val lists = lc900.filterIsInstance<MdrEvent.SupportedFunctions>()
        val f = V2Functions(lists.first { it.table == Table.ONE }.codes, lists.first { it.table == Table.TWO }.codes)
        // Open-ear: no noise control
        assertEquals(null, NcAsmV2.typeFor(f))
        assertFalse(V1.NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE.code in f.v1Equivalents())
    }

    @Test
    fun linkBudsClipTenBandEq() {
        val caps = lc900Eq.filterIsInstance<MdrEvent.EqCaps>()
        assertEquals(10, caps.single().caps.bandCount)
        assertEquals(13, caps.single().caps.levelSteps)
        val eq = lc900Eq.filterIsInstance<MdrEvent.Eq>()
        assertTrue(eq.isNotEmpty())
        assertTrue(eq.all { it.state.bands.size in setOf(0, 10) && it.state.bands.all { b -> b in 0..12 } })
    }
}
