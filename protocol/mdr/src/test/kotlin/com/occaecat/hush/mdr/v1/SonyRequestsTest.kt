package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.hex
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Every request Hush can send, read back by Sound Connect's own message class for its opcode. */
class SonyRequestsTest {

    private fun all(): List<Pair<String, Request>> = buildList {
        fun add(name: String, r: Request) = add(name to r)
        add("protocolInfo", Connect.protocolInfo()); add("capabilityInfo", Connect.capabilityInfo())
        add("supportFunction", Connect.supportFunction()); add("modelName", Connect.modelName())
        add("firmware", Connect.firmwareVersion()); add("seriesAndColor", Connect.seriesAndColor())
        add("instructionGuide", Connect.instructionGuide())
        for (t in 0..2) add("battery $t", Common.battery(t))
        add("upscalingEffect", Common.upscalingEffect()); add("codec", Common.codec())
        for (t in 0..1) add("btInfo $t", Common.bluetoothInfo(t))
        add("powerOff", Common.powerOff()); add("earbudConnection", Common.earbudConnection())
        add("keepAlive 5", Common.keepAlive(5)); add("keepAlive off", Common.keepAlive(null))
        for (t in listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11)) add("updt $t", Update.param(t))
        for (t in 1..2) { add("vpt cap $t", Vpt.capability(t)); add("vpt get $t", Vpt.get(t)) }
        add("surround", Vpt.setSurround(0x01)); add("soundPosition", Vpt.setSoundPosition(0x03))
        for (t in listOf(EqEbb.PRESET_EQ, EqEbb.EBB, EqEbb.PRESET_EQ_NONCUSTOMIZABLE)) {
            add("eq cap $t", EqEbb.capability(t)); add("eq get $t", EqEbb.get(t)); add("eq ext $t", EqEbb.bandInfo(t))
        }
        add("eq preset", EqEbb.selectPreset(0x10)); add("eq preset fixed", EqEbb.selectPreset(0x10, EqEbb.PRESET_EQ_NONCUSTOMIZABLE))
        for (n in listOf(5, 6, 10)) add("eq bands $n", EqEbb.setBands(0xA0, List(n) { 10 + it }))
        add("ebb +3", EqEbb.setEbb(3)); add("ebb -3", EqEbb.setEbb(-3))
        for (k in NcAsmKind.entries) { add("nc cap $k", NcAsm.capability(k)); add("nc get $k", NcAsm.get(k)) }
        // NcSettingType (NC only) knows ON_OFF and LEVEL_ADJUSTMENT; NcAsmSettingType adds DUAL_SINGLE_OFF.
        for (k in NcAsmKind.entries) for (m in AmbientMode.entries) for (nc in if (k == NcAsmKind.NC) 0..1 else 0..2) for (asm in 0..1) {
            for (drag in listOf(false, true)) for (voice in listOf(false, true)) {
                val s = NcAsmState(m, 12, voice, ncSettingType = nc, ncOnValue = if (nc == 0x02) 0x01 else 0x01, asmSettingType = asm, kind = k)
                add("nc set $k $m nc=$nc asm=$asm drag=$drag voice=$voice", NcAsm.set(s, drag))
            }
        }
        add("sense cap", Sense.capability()); add("sense start", Sense.start())
        add("opt cap", Optimizer.capability()); add("opt status", Optimizer.status()); add("opt result", Optimizer.result())
        add("opt start", Optimizer.start()); add("opt cancel", Optimizer.cancel())
        add("alert cap", Alert.capability()); add("alert enable", Alert.enable())
        for (t in 1..8) for (p in listOf(false, true)) add("alert answer $t $p", Alert.answer(t, p))
        add("play cap", Play.capability()); add("play status", Play.status())
        for (t in listOf(0x00, 0x01, 0x02, 0x20)) add("play param $t", Play.param(t))
        add("volume", Play.setVolume(15)); for (c in listOf(1, 2, 3, 7)) add("play control $c", Play.control(c))
        add("sports cap", Sports.capability()); add("sports get", Sports.get()); add("sports on", Sports.set(true))
        for (id in GeneralSettings.ids) {
            add("gs cap $id", GeneralSettings.capability(id)); add("gs status $id", GeneralSettings.status(id))
            add("gs get $id", GeneralSettings.get(id)); add("gs set $id", GeneralSettings.set(id, true))
            add("gs index $id", GeneralSettings.setIndex(id, 1))
        }
        for (t in 1..2) add("audio cap $t", Audio.capability(t))
        add("audio quality", Audio.soundQualityMode()); add("audio upscaling", Audio.upscaling())
        add("set quality", Audio.setSoundQualityMode(true)); add("set upscaling", Audio.setUpscaling(true))
        for (t in 1..6) {
            add("sys cap $t", SystemSettings.capability(t)); add("sys status $t", SystemSettings.status(t))
            add("sys get $t", SystemSettings.get(t)); add("sys ext $t", SystemSettings.getExtended(t))
        }
        add("vibrator", SystemSettings.setVibrator(true)); add("powerSaving", SystemSettings.setPowerSaving(true))
        add("pauseWhenRemoved", SystemSettings.setPauseWhenRemoved(true))
        add("autoPowerOff", SystemSettings.setAutoPowerOff(0x11, 0x00))
        add("speakToChat", SystemSettings.setSpeakToChat(true))
        add("speakToChat detail", SystemSettings.setSpeakToChatDetail(SpeakToChatDetail(1, true, 2)))
        // AssignableSettingsPreset: ambient sound control, volume, playback, voice recognition, Google Assistant, none.
        for (p in listOf(0x00, 0x10, 0x20, 0x30, 0x31, 0xFF)) add("assignable $p", SystemSettings.setAssignable(listOf(p)))
        add("assignable L/R", SystemSettings.setAssignable(listOf(0x10, 0x20)))
        add("peri cap", Peripheral.capability()); add("peri status", Peripheral.status()); add("peri devices", Peripheral.devices())
        add("pairing on", Peripheral.setPairingMode(true)); add("pairing off", Peripheral.setPairingMode(false))
        for (a in 0..2) add("peri action $a", Peripheral.action(a, "aa:bb:cc:dd:ee:ff"))
        add("vg cap", VoiceGuidance.capability()); add("vg get", VoiceGuidance.get())
        add("vg language", VoiceGuidance.language()); add("vg on", VoiceGuidance.set(true))
    }

    /**
     * Requests Sony's per-opcode class cannot read back, each checked another way:
     * - general settings: um0.* are send-only; built with Sony's constructors in [generalSettingsMatchSony];
     * - SYSTEM type 3 (control by wearing): Sound Connect sends it from its KMP module; see [controlByWearingMatchesSony];
     * - EQ preset on a non-customizable EQ: h20/c.java sends `58 03 id 00` (vm0.s), but f3.g only reads types 1 and 2.
     */
    private fun readElsewhere(name: String) =
        name.startsWith("gs ") || name.matches(Regex("sys \\w+ 3")) || name == "pauseWhenRemoved" || name == "eq preset fixed"

    @Test
    fun soundConnectReadsEveryRequestUnchanged() {
        val sony = SonyReference.instance
        assumeTrue("tools/sonyref/sound-connect.jar not made", sony != null)
        val problems = all().filterNot { readElsewhere(it.first) }.mapNotNull { (name, r) ->
            val cmd = sony!!.commandName(r.table, r.payload[0].toInt() and 0xFF)
            when (val res = sony.read(r.table, r.payload)) {
                is SonyReference.Result.Rejected -> "$name ($cmd) ${r.payload.hex()}: rejected, ${res.why}"
                is SonyReference.Result.Read -> res.rewritten?.takeIf { !it.contentEquals(r.payload) }
                    ?.let { "$name ($cmd) ${r.payload.hex()}: Sony writes back ${it.hex()}" }
            }
        }
        if (problems.isNotEmpty()) fail("${problems.size} requests differ from Sound Connect:\n" + problems.joinToString("\n"))
    }

    private fun sony() = SonyReference.instance.also { assumeTrue("tools/sonyref/sound-connect.jar not made", it != null) }!!

    private fun same(name: String, ours: Request, theirs: ByteArray) {
        if (!ours.payload.contentEquals(theirs)) fail("$name: Hush ${ours.payload.hex()}, Sound Connect ${theirs.hex()}")
    }

    @Test
    fun generalSettingsMatchSony() {
        val sony = sony()
        val p = "com.sony.songpal.tandemfamily.message.mdr.v1.table1.param."
        val english = sony.enum(p + "DisplayLanguage", "ENGLISH")
        GeneralSettings.ids.forEachIndexed { i, id ->
            val gs = sony.enum(p + "generalsetting.GsInquiredType", "GENERAL_SETTING${i + 1}")
            same("capability $id", GeneralSettings.capability(id), sony.bytes(sony.new("um0.a", gs, english)))
            same("status $id", GeneralSettings.status(id), sony.bytes(sony.new("um0.c", gs)))
            same("get $id", GeneralSettings.get(id), sony.bytes(sony.new("um0.b", gs)))
            for (on in listOf(false, true)) {
                val value = sony.new("xm0.a", sony.enum(p + "CommonOnOffSettingValue", if (on) "ON" else "OFF"))
                same("set $id $on", GeneralSettings.set(id, on), sony.bytes(sony.new("um0.i", gs, value)))
            }
            for (index in 0..3) same("index $id $index", GeneralSettings.setIndex(id, index), sony.bytes(sony.new("um0.i", gs, sony.new("xm0.c", index))))
        }
    }

    @Test
    fun controlByWearingMatchesSony() {
        val sony = sony()
        val k = "com.sony.songpal.mdr.kmp.tandem.command.v1."
        for (on in listOf(false, true)) {
            val msg = sony.new("xi0.q", sony.enum(k + "ProtocolSettingType", "ON_OFF"), sony.enum(k + "ProtocolSettingValueOnOff", if (on) "ON" else "OFF"))
            same("pause when removed $on", SystemSettings.setPauseWhenRemoved(on), sony.call(sony.instanceOf("xi0.r"), "b", msg) as ByteArray)
        }
    }
}
