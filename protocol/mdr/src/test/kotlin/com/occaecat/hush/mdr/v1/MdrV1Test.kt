package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.AssignableKey
import com.occaecat.hush.mdr.Charge
import com.occaecat.hush.mdr.EqPreset
import com.occaecat.hush.mdr.GeneralSetting
import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.OptimizerResult
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.VoiceGuidanceCapability
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MdrV1Test {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun parse(vararg v: Int) = MdrV1.parse(Table.ONE, b(*v))
    private fun parse2(vararg v: Int) = MdrV1.parse(Table.TWO, b(*v))

    @Test
    fun parsesBatteryAndNcAsm() {
        assertEquals(MdrEvent.Battery(70, true), parse(0x11, 0x00, 70, 0x01))
        val nc = parse(0x67, 0x02, 0x11, 0x02, 0x00, 0x01, 0x01, 14) as MdrEvent.NcAsm
        assertEquals(AmbientMode.AMBIENT, nc.state.mode)
        assertEquals(14, nc.state.ambientLevel)
        assertEquals(true, nc.state.voiceFocus)
        val ncOn = parse(0x69, 0x02, 0x11, 0x02, 0x02, 0x01, 0x00, 0x00) as MdrEvent.NcAsm
        assertEquals(AmbientMode.NOISE_CANCELLING, ncOn.state.mode)
        // Re-encoding echoes the device's own setting types.
        assertArrayEquals(b(0x68, 0x02, 0x11, 0x02, 0x02, 0x01, 0x00, 0x00), NcAsm.set(ncOn.state).payload)
    }

    @Test
    fun parsesTrueWirelessBatteries() {
        // vm0/x: left level, left charging, right level, right charging.
        assertEquals(MdrEvent.EarbudBatteries(Charge(80, false), Charge(60, true)), parse(0x13, 0x01, 80, 0x00, 60, 0x01))
        assertEquals(MdrEvent.CaseBattery(Charge(40, true)), parse(0x11, 0x02, 40, 0x01))
        assertEquals(MdrEvent.EarbudsConnected(true, false), parse(0x27, 0x01, 0x01, 0x00))
    }

    @Test
    fun ncOnlyAndAmbientOnlyModels() {
        val nc = parse(0x67, 0x01, 0x00, 0x01) as MdrEvent.NcAsm
        assertEquals(NcAsmKind.NC, nc.state.kind)
        assertEquals(AmbientMode.NOISE_CANCELLING, nc.state.mode)
        assertArrayEquals(b(0x68, 0x01, 0x00, 0x00), NcAsm.set(nc.state.copy(mode = AmbientMode.OFF)).payload)

        val asm = parse(0x69, 0x03, 0x11, 0x01, 0x00, 12) as MdrEvent.NcAsm
        assertEquals(NcAsmKind.ASM, asm.state.kind)
        assertEquals(AmbientMode.AMBIENT, asm.state.mode)
        assertEquals(12, asm.state.ambientLevel)
        assertArrayEquals(b(0x68, 0x03, 0x11, 0x01, 0x01, 12), NcAsm.set(asm.state.copy(voiceFocus = true)).payload)

        val caps = (parse(0x61, 0x03, 0x01, 0x02, 0x00, 20, 0x01, 20) as MdrEvent.NcAsmCaps).caps
        assertEquals(NcAsmKind.ASM, caps.kind)
        assertEquals(20, caps.asmSteps)
        assertTrue(caps.voice)
    }

    @Test
    fun windNoiseReductionIsTheSingleNcValue() {
        val single = (parse(0x67, 0x02, 0x11, 0x02, 0x01, 0x01, 0x00, 0x00) as MdrEvent.NcAsm).state
        assertTrue(single.windReduction)
        assertArrayEquals(b(0x68, 0x02, 0x11, 0x02, 0x01, 0x01, 0x00, 0x00), NcAsm.set(single).payload)
    }

    @Test
    fun parsesEqCapabilityNames() {
        val caps = parse(0x51, 0x01, 6, 21, 2, 0x00, 3, 'O'.code, 'f'.code, 'f'.code, 0xa0, 0) as MdrEvent.EqCaps
        assertEquals(listOf(EqPreset(0, "Off"), EqPreset(0xa0, "")), caps.caps.presets)
        assertEquals(MdrEvent.EbbCaps(-10..10), parse(0x51, 0x02, 0xf6, 0x0a))
        assertEquals(MdrEvent.EbbLevel(-3), parse(0x59, 0x02, 0xfd))
    }

    @Test
    fun parsesSurroundAndSoundPosition() {
        val vpt = parse(0x41, 0x01, 2, 0x00, 3, 'O'.code, 'f'.code, 'f'.code, 0x02, 5, 'A'.code, 'r'.code, 'e'.code, 'n'.code, 'a'.code)
        assertEquals(listOf("Off", "Arena"), (vpt as MdrEvent.VptCaps).presets.map { it.name })
        assertEquals(MdrEvent.SoundPosition(0x11), parse(0x49, 0x02, 0x11))
        assertArrayEquals(b(0x48, 0x01, 0x02), Vpt.setSurround(0x02).payload)
    }

    @Test
    fun parsesAssignableKeys() {
        // Two earbud keys; the left offers ambient control and volume, each preset with one tap → function pair.
        val caps = parse(
            0xf1, 0x06, 2,
            0x00, 0x00, 0x00, 2, 0x00, 1, 0x00, 0x01, 0x10, 1, 0x00, 0x11,
            0x01, 0x00, 0x20, 1, 0x20, 1, 0x00, 0x20,
        )
        assertEquals(
            MdrEvent.AssignableCaps(listOf(AssignableKey(0, 0, 0x00, listOf(0x00, 0x10)), AssignableKey(1, 0, 0x20, listOf(0x20)))),
            caps,
        )
        assertArrayEquals(b(0xf8, 0x06, 2, 0x10, 0x20), SystemSettings.setAssignable(listOf(0x10, 0x20)).payload)
    }

    @Test
    fun parsesListGeneralSetting() {
        val name = "ASSIGNABLE_KEY_SETTING"
        val p = b(0xd1, 0xd1, 0x02, name.length) + name.toByteArray() + b(0x00, 0x02, 2) +
            b(0x02, 2) + "NC".toByteArray() + b(0x00) + b(0x02, 2) + "GA".toByteArray() + b(0x00)
        assertEquals(MdrEvent.GsCapability(GeneralSetting(0xd1, name, false, listOf("NC", "GA"))), MdrV1.parse(Table.ONE, p))
        assertEquals(MdrEvent.GsListValue(0xd1, 1), parse(0xd9, 0xd1, 0x02, 0x01))
    }

    @Test
    fun recognisesProtocolV2() {
        assertEquals(MdrEvent.ProtocolInfo(0x4010), parse(0x01, 0x00, 0x40, 0x10))
        assertEquals(MdrEvent.ProtocolInfo(0, v2 = true), parse(0x01, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00))
    }

    /** WH-1000XM2 answering the handshake (mos9527/SonyHeadphonesClient#61): the reply comes before the ACK. */
    @Test
    fun xm2RepliesBeforeAck() {
        val wire = b(0x3e, 0x0c, 0x00, 0x00, 0x00, 0x00, 0x04, 0x01, 0x00, 0x40, 0x00, 0x51, 0x3c) +
            b(0x3e, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x02, 0x3c)
        val frames = com.occaecat.hush.mdr.FrameParser().feed(wire)
        assertEquals(listOf(com.occaecat.hush.mdr.Frame.Type.DATA_MDR, com.occaecat.hush.mdr.Frame.Type.ACK), frames.map { it.type })
        assertEquals(MdrEvent.ProtocolInfo(0x4000), MdrV1.parse(Table.ONE, frames[0].payload))
        assertTrue(0x4000 in Init.SUPPORTED_VERSIONS)
    }

    /** Captured from a real WH-1000XM4 (FW 3.0.1) on 2026-10-12. */
    @Test
    fun parsesCapturedXm4Settings() {
        assertEquals(MdrEvent.SpeakToChatDetails(SpeakToChatDetail(0, false, 1)), parse(0xfb, 0x05, 0x00, 0x00, 0x00, 0x01))
        assertEquals(MdrEvent.WearingStatus(true), parse(0xf3, 0x03, 0x00))
        assertEquals(MdrEvent.PauseWhenRemoved(true), parse(0xf7, 0x03, 0x00, 0x01))
        assertEquals(MdrEvent.AutoPowerOffOptions(listOf(0x10, 0x11)), parse(0xf1, 0x04, 0x02, 0x10, 0x11))
        assertEquals(MdrEvent.AutoPowerOff(0x11, 0x00), parse(0xf7, 0x04, 0x01, 0x11, 0x00))
        assertEquals(MdrEvent.CustomButton(listOf(0x00)), parse(0xf7, 0x06, 0x01, 0x00))
        assertEquals(MdrEvent.SoundQualityMode(false), parse(0xe7, 0x01, 0x00, 0x00))
        assertEquals(
            MdrEvent.Optimizer(OptimizerResult(personalMeasured = true, pressureTenthsAtm = 10)),
            parse(0x87, 0x01, 0x01, 0x01, 0x01, 0x0a),
        )
        val touch = b(0xd1, 0xd1, 0x02, 0x13) + "TOUCH_PANEL_SETTING".toByteArray() + b(0x00, 0x01, 0x00)
        assertEquals(MdrEvent.GsCapability(GeneralSetting(0xd1, "TOUCH_PANEL_SETTING", true)), MdrV1.parse(Table.ONE, touch))
        assertEquals(MdrEvent.GsValue(0xd2, false), parse(0xd7, 0xd2, 0x01, 0x00))
        // Table 2 reuses opcode 0x47; it must not be read as a table-1 command.
        assertEquals(MdrEvent.VoiceGuidance(true), parse2(0x47, 0x01, 0x01, 0x01))
        assertEquals(MdrEvent.VoiceGuidanceLanguage(4), parse2(0x47, 0x01, 0x02, 0x04))
        // bn0/i: a headset without a language switch ends the capability before the list.
        assertEquals(MdrEvent.VoiceGuidanceCaps(VoiceGuidanceCapability(true, false, emptyList())), parse2(0x41, 0x01, 0x01, 0x00))
        assertEquals(MdrEvent.VoiceGuidanceCaps(VoiceGuidanceCapability(true, true, listOf(1, 4))), parse2(0x41, 0x01, 0x01, 0x01, 0x02, 0x01, 0x04))
    }

    @Test
    fun planFollowsDeclaredFunctions() {
        // A WF-SP800N-like earbud: earbud batteries, case, ambient only, assignable keys, no playback controller.
        fun heads(plan: List<com.occaecat.hush.mdr.Request>) = plan.map { (it.payload[0].toInt() and 0xFF) to (it.payload[1].toInt() and 0xFF) }
        val plan = heads(Init.plan(0x7000, setOf(0x15, 0x18, 0x63, 0x51, 0xF6, 0xF3)))
        assertTrue(0x10 to 0x01 in plan)
        assertTrue(0x10 to 0x02 in plan)
        assertTrue(0x60 to 0x03 in plan)
        assertTrue(0x60 to 0x02 !in plan)
        assertTrue(plan.none { it.first == 0xA0 })
        assertTrue(0x04 to 0x04 in plan) // instruction guide from protocol 0x5000
        assertTrue(0x04 to 0x04 !in heads(Init.plan(0x4010, emptySet())))
    }
}
