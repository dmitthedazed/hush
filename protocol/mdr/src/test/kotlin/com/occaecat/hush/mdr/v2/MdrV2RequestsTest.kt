package com.occaecat.hush.mdr.v2

import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.Frame
import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.hex
import com.occaecat.hush.mdr.v1.Alert as V1Alert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v2 request bytes against what other implementations put on the wire (libmdr, Gadgetbridge's tests, the ohm
 * LinkBuds S notes, SonyBridge; facts only, no code taken from the GPL/AGPL ones) and Sound Connect's classes.
 */
class MdrV2RequestsTest {
    private fun hex(r: Request?) = r?.payload?.hex()

    private val xm5 = V2Functions(
        t1 = setOf(0x11, 0x12, 0x13, 0x23, 0x25, 0x29, 0x2A, 0x50, 0x6B, 0x90, 0xA1, 0xD1, 0xD2, 0xE2, 0xF1, 0xF3, 0xFC),
        t2 = setOf(0x32, 0x42),
    )

    @Test
    fun frameMatchesGadgetbridgesCodecRequest() {
        // GET_AUDIO_CODEC from Gadgetbridge's v2 test: 3e:0c:01:00:00:00:02:12:02:23:3c
        val r = Common.status(Common.AUDIO_CODEC)
        assertEquals("3e 0c 01 00 00 00 02 12 02 23 3c", Frame.encode(Frame.Type.DATA_MDR, 1, r.payload).hex())
    }

    @Test
    fun connect() {
        assertEquals("00 00", hex(Connect.protocolInfo()))
        assertEquals("02 00", hex(Connect.capabilityInfo()))
        assertEquals("04 02", hex(Connect.deviceInfo(0x02)))
        assertEquals(Table.TWO, Connect.supportFunction(Table.TWO).table)
        assertEquals("06 00", hex(Connect.supportFunction(Table.TWO)))
    }

    @Test
    fun noiseControlDualType() {
        val t = NcAsmV2.MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS
        // ohm's LinkBuds S notes: ambient on, normal, level 1 / everything off with level 8 remembered
        assertEquals("68 17 01 01 01 00 01", hex(NcAsm.set(t, NcAsmState(AmbientMode.AMBIENT, 1, false), dragging = false)))
        assertEquals("68 17 01 00 00 00 08", hex(NcAsm.set(t, NcAsmState(AmbientMode.OFF, 8, false), dragging = false)))
        // noise cancelling; a dragged slider sends ValueChangeStatus UNDER_CHANGING (0)
        assertEquals("68 17 01 01 00 00 14", hex(NcAsm.set(t, NcAsmState(AmbientMode.NOISE_CANCELLING, 20, false), dragging = false)))
        assertEquals("68 17 00 01 01 01 0c", hex(NcAsm.set(t, NcAsmState(AmbientMode.AMBIENT, 12, true), dragging = true)))
    }

    @Test
    fun noiseControlRoundTrips() {
        for (type in NcAsmV2.entries) {
            val modes = when (type.kind) {
                NcAsmKind.NC -> listOf(AmbientMode.NOISE_CANCELLING, AmbientMode.OFF)
                NcAsmKind.ASM -> listOf(AmbientMode.AMBIENT, AmbientMode.OFF)
                NcAsmKind.NC_ASM -> AmbientMode.entries
            }
            for (mode in modes) {
                val sent = NcAsmState(mode, if (type.hasLevel) 7 else 0, voiceFocus = type.hasLevel, ncOnValue = 0x02,
                    kind = type.kind, extra = List(type.extra) { 1 })
                val ntfy = NcAsm.set(type, sent, dragging = false).payload.also { it[0] = Command1.NCASM_NTFY_PARAM.code.toByte() }
                val got = (MdrV2.parse(Table.ONE, ntfy) as MdrEvent.NcAsm).state
                assertEquals("$type $mode", mode, got.mode)
                assertEquals("$type $mode", type.kind, got.kind)
                if (type.hasLevel) assertEquals(7, got.ambientLevel)
                assertEquals(sent.extra, got.extra)
            }
        }
    }

    @Test
    fun windNoiseOnTheDualSingleType() {
        val t = NcAsmV2.MODE_NC_ASM_DUAL_SINGLE_NC_MODE_SWITCH_AND_ASM_SEAMLESS
        // NcValue ON_SINGLE (1) is the wind-noise mode, as v1's NcDualSingleValue
        val r = NcAsm.set(t, NcAsmState(AmbientMode.NOISE_CANCELLING, 20, false, ncSettingType = NcAsmState.NC_DUAL_SINGLE_OFF, ncOnValue = 1), false)
        assertEquals("68 16 01 01 00 01 00 14", hex(r))
        val state = (MdrV2.parse(Table.ONE, r.payload.also { it[0] = 0x69 }) as MdrEvent.NcAsm).state
        assertEquals(true, state.windReduction)
    }

    @Test
    fun system() {
        // Speak-to-Chat: OnOffSettingValue ON = 0, preview off (GB, SonyBridge, BudsLink all send f8 0c 00/01 01)
        assertEquals("f8 0c 00 01", hex(SystemSettings.setSpeakToChat(SystemSettings.SMART_TALKING_MODE_TYPE2, true)))
        assertEquals("f8 0c 01 01", hex(SystemSettings.setSpeakToChat(SystemSettings.SMART_TALKING_MODE_TYPE2, false)))
        assertEquals("fc 0c 02 01", hex(SystemSettings.setSpeakToChatDetail(SystemSettings.SMART_TALKING_MODE_TYPE2, SpeakToChatDetail(2, true, 1))))
        // Pause when taken off: SYSTEM type 1 (Sound Connect's V2ControlByWearingRepository: table 1, 0xF8, 1, ProtocolOnOff)
        assertEquals("f8 01 00", hex(SystemSettings.setOnOff(SystemSettings.PLAYBACK_CONTROL_BY_WEARING, true)))
        assertEquals("f8 03 02 35 20", hex(SystemSettings.setAssignable(SystemSettings.ASSIGNABLE_SETTINGS, listOf(0x35, 0x20))))
    }

    @Test
    fun powerAndAudio() {
        assertEquals("22 00", hex(Power.battery(Power.BATTERY)))
        assertEquals("24 03 01", hex(Power.powerOff()))
        assertEquals("28 05 10 00", hex(Power.setAutoPowerOff(Power.AUTO_POWER_OFF_WEARING_DETECTION, 0x10, 0x00)))
        assertEquals("e8 01 01", hex(Audio.setUpscaling(true)))
        assertEquals("e8 00 01", hex(Audio.setConnectionMode(Audio.CONNECTION_MODE, preferConnection = true)))
    }

    @Test
    fun equalizer() {
        assertEquals("50 00 01", hex(EqEbb.capability(EqEbb.PRESET_EQ)))
        assertEquals("58 00 10 00", hex(EqEbb.selectPreset(EqEbb.PRESET_EQ, 0x10)))
        assertEquals("58 00 a0 06 0a 0a 0b 0c 0d 0e", hex(EqEbb.setBands(EqEbb.PRESET_EQ, 0xA0, listOf(10, 10, 11, 12, 13, 14))))
    }

    @Test
    fun alertsUseV1CodesInsideHush() {
        assertEquals("94 00 00", hex(Alert.enable()))
        // v2 DISCONNECT_CAUSED_BY_CHANGING_KEY_ASSIGN (1) is v1 KEY_ASSIGN_CHANGE (2) in the controller
        val e = MdrV2.parse(Table.ONE, byteArrayOf(0x99.toByte(), 0x00, 0x01, 0x01)) as MdrEvent.Alert
        assertEquals(V1Alert.KEY_ASSIGN_CHANGE, e.type)
        assertEquals("98 00 01 01", hex(V2Protocol(xm5).alertAnswer(V1Alert.KEY_ASSIGN_CHANGE, true)))
        assertEquals("98 00 07 00", hex(V2Protocol(xm5).alertAnswer(V1Alert.MULTIPOINT_OFF, false)))
    }

    @Test
    fun tableTwo() {
        val p = V2Protocol(xm5)
        assertEquals("T2 36 02", p.readPeers().toString())
        assertEquals("T2 34 02 01 00", p.pairingMode(true).toString())
        assertEquals("T2 3c 02 01 " + "AA:BB:CC:DD:EE:FF".toByteArray().hex(), p.peerAction(1, "aa:bb:cc:dd:ee:ff").toString())
        assertEquals("T2 48 01 00", p.voiceGuidance(true, null).toString())
    }

    @Test
    fun protocolPicksTypesFromFunctions() {
        val p = V2Protocol(xm5)
        assertEquals("a4 01 00 07", hex(p.playControl(0x07)))
        assertEquals(listOf("a6 01"), p.readPlayback().map { it.payload.hex() })
        assertEquals(listOf("22 09", "22 0a"), p.readBattery(emptySet()).map { it.payload.hex() })
        assertEquals("28 05 11 00", hex(p.autoPowerOff(0x11, 0x00)))
        assertNull(p.vpt(1))
        assertNull(p.optimizerStart())
        assertNull(V2Protocol(V2Functions(emptySet())).ncAsm(NcAsmState(AmbientMode.OFF, 0, false), false))
    }

    @Test
    fun initFollowsTheFunctions() {
        val plan = Init.plan(xm5).map { it.toString() }
        for (expected in listOf("94 00 00", "22 09", "22 0a", "12 01", "12 02", "e0 01", "e6 01", "50 00 01", "56 00", "60 17", "66 17",
            "f6 01", "20 05", "26 05", "f6 0c", "fa 0c", "f0 03", "f6 03", "d0 d1 01", "d6 d2", "a0 01", "a6 20",
            "T2 46 01", "T2 30 02", "T2 36 02")) {
            assertEquals(expected, true, expected in plan)
        }
        assertEquals(listOf("02 00", "04 01", "04 02", "04 03", "04 04", "06 00", "T2 06 00"),
            Init.handshake(0x03003018, table1 = true, table2 = true).map { it.toString() })
        assertEquals(false, "04 04" in Init.handshake(0x03000015, table1 = true, table2 = false).map { it.toString() })
    }
}
