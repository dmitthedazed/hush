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
        val payload = b(Cmd.NCASM_SET_PARAM, 0x02, 0x11, 0x02, 0x00, 0x01, 0x00, 0x3e)
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
    fun parsesBatteryAndNcAsm() {
        assertEquals(MdrEvent.Battery(70, true), Mdr.parse(b(0x11, 0x00, 70, 0x01)))
        val nc = Mdr.parse(b(0x67, 0x02, 0x11, 0x02, 0x00, 0x01, 0x01, 14)) as MdrEvent.NcAsm
        assertEquals(AmbientMode.AMBIENT, nc.state.mode)
        assertEquals(14, nc.state.ambientLevel)
        assertEquals(true, nc.state.voiceFocus)
        val ncOn = Mdr.parse(b(0x69, 0x02, 0x11, 0x02, 0x02, 0x01, 0x00, 0x00)) as MdrEvent.NcAsm
        assertEquals(AmbientMode.NOISE_CANCELLING, ncOn.state.mode)
        // Re-encoding echoes the device's own setting types.
        assertArrayEquals(b(0x68, 0x02, 0x11, 0x02, 0x02, 0x01, 0x00, 0x00), Mdr.setNcAsm(ncOn.state))
    }

    @Test
    fun parsesEqCapabilityNames() {
        val caps = Mdr.parse(b(0x51, 0x01, 6, 21, 2, 0x00, 3, 'O'.code, 'f'.code, 'f'.code, 0xa0, 0)) as MdrEvent.EqCaps
        assertEquals(listOf(EqPreset(0, "Off"), EqPreset(0xa0, "")), caps.caps.presets)
    }

    /** Captured from a real WH-1000XM4 (FW 3.0.1) on 2026-10-12. */
    @Test
    fun parsesCapturedXm4Settings() {
        assertEquals(MdrEvent.SpeakToChatDetails(SpeakToChatDetail(0, false, 1)), Mdr.parse(b(0xfb, 0x05, 0x00, 0x00, 0x00, 0x01)))
        assertEquals(MdrEvent.WearingStatus(true), Mdr.parse(b(0xf3, 0x03, 0x00)))
        assertEquals(MdrEvent.PauseWhenRemoved(true), Mdr.parse(b(0xf7, 0x03, 0x00, 0x01)))
        assertEquals(MdrEvent.AutoPowerOffOptions(listOf(0x10, 0x11)), Mdr.parse(b(0xf1, 0x04, 0x02, 0x10, 0x11)))
        assertEquals(MdrEvent.AutoPowerOff(0x11, 0x00), Mdr.parse(b(0xf7, 0x04, 0x01, 0x11, 0x00)))
        assertEquals(MdrEvent.CustomButton(listOf(0x00)), Mdr.parse(b(0xf7, 0x06, 0x01, 0x00)))
        assertEquals(MdrEvent.SoundQualityMode(false), Mdr.parse(b(0xe7, 0x01, 0x00, 0x00)))
        assertEquals(
            MdrEvent.Optimizer(OptimizerResult(personalMeasured = true, pressureTenthsAtm = 10)),
            Mdr.parse(b(0x87, 0x01, 0x01, 0x01, 0x01, 0x0a)),
        )
        val touch = b(0xd1, 0xd1, 0x02, 0x13) + "TOUCH_PANEL_SETTING".toByteArray() + b(0x00, 0x01, 0x00)
        assertEquals(MdrEvent.GsCapability(GeneralSetting(0xd1, "TOUCH_PANEL_SETTING", true)), Mdr.parse(touch))
        assertEquals(MdrEvent.GsValue(0xd2, false), Mdr.parse(b(0xd7, 0xd2, 0x01, 0x00)))
        // Table 2 reuses opcode 0x47; it must not be read as a table-1 command.
        assertEquals(MdrEvent.VoiceGuidance(true), Mdr.parse(b(0x47, 0x01, 0x01, 0x01), table2 = true))
        assertEquals(MdrEvent.VoiceGuidanceLanguage(4), Mdr.parse(b(0x47, 0x01, 0x02, 0x04), table2 = true))
    }
}
