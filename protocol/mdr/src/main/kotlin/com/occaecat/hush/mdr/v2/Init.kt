package com.occaecat.hush.mdr.v2

import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.Table

/**
 * The v2 session start, after Sound Connect's initializer (vv/e.java `call()`, decompile.md §4–5) and libmdr's
 * RequestInitV2: protocol info, capability, device info, the support function lists (table 2 only when the
 * protocol info enables it), then capabilities before values for each declared function.
 */
object Init {
    /** Versions Sound Connect accepts (vv/e.java:63, Int32BE); Hush logs others and carries on. */
    val SUPPORTED_VERSIONS: Set<Int> = setOf(
        0x01000000, 0x02000000, 0x02000001, 0x02001000, 0x03000000, 0x03000001, 0x03000002, 0x03000003, 0x03000004,
        0x03000005, 0x03000006, 0x03000007, 0x03000008, 0x03000009, 0x03000010, 0x03000011, 0x03000012, 0x03000013,
        0x03000014, 0x03000015, 0x03000016, 0x03000017, 0x03000018, 0x03000019, 0x03000020, 0x03001001, 0x03001002,
        0x03001003, 0x03001004, 0x03002000, 0x03002001, 0x03002002, 0x03002003, 0x03002004, 0x03002005, 0x03002006,
        0x03002007, 0x03002008, 0x03002009, 0x03002012, 0x03002013, 0x03002014, 0x03002015, 0x03002016, 0x03002017,
        0x03002018, 0x03002019, 0x03003000, 0x03003001, 0x03003002, 0x03003003, 0x03003004, 0x03003005, 0x03003006,
        0x03003007, 0x03003008, 0x03003009, 0x03003010, 0x03003011, 0x03003012, 0x03003013, 0x03003014, 0x03003015,
        0x03003016, 0x03003017, 0x03003018, 0x03003019, 0x03003020, 0x03003021, 0x03003023, 0x03003024, 0x03003025,
        0x03003026, 0x03003032, 0x03003033, 0x03003037,
    )

    /** Sound Connect's k(v): the instruction guide is only asked for from 0x03001004 on, except two versions. */
    fun hasInstructionGuide(version: Int) = version >= 0x03001004 && version != 0x03002013 && version != 0x03002014

    /** After CONNECT_RET_PROTOCOL_INFO; the replies to the support functions decide the rest. */
    fun handshake(version: Int, table1: Boolean, table2: Boolean): List<Request> = buildList {
        add(Connect.capabilityInfo())
        add(Connect.deviceInfo(0x01))
        add(Connect.deviceInfo(0x02))
        add(Connect.deviceInfo(0x03))
        if (hasInstructionGuide(version)) add(Connect.deviceInfo(0x04))
        if (table1) add(Connect.supportFunction(Table.ONE))
        if (table2) add(Connect.supportFunction(Table.TWO))
    }

    fun plan(f: V2Functions): List<Request> = buildList {
        // Alerts first: without them the headset refuses changes that drop its links.
        if (f.has(FunctionType1.FIXED_MESSAGE)) add(Alert.enable())

        addAll(Power.batteries(f))
        if (f.has(FunctionType1.CONNECTION_STATUS)) add(Common.status(Common.CONNECTION_STATUS))
        if (f.has(FunctionType1.CODEC_INDICATOR)) add(Common.status(Common.AUDIO_CODEC))
        if (f.any(FunctionType1.UPSCALING_AUTO_OFF, FunctionType1.UPSCALING_AUTO_OFF_WITH_STATUS_DISABLE_REASON)) {
            add(Audio.capability(Audio.UPSCALING)); add(Audio.get(Audio.UPSCALING))
            if (f.has(FunctionType1.UPSCALING_INDICATOR)) add(Common.status(Common.UPSCALING_EFFECT))
        }
        Audio.connectionModeType(f)?.let { add(Audio.get(it)) }
        Audio.listeningTypes(f).forEach { add(Audio.get(it)) }

        EqEbb.eqType(f)?.let { add(EqEbb.capability(it)); add(EqEbb.bandInfo(it)); add(EqEbb.get(it)) }
        if (f.has(FunctionType1.EBB)) { add(EqEbb.capability(EqEbb.EBB)); add(EqEbb.get(EqEbb.EBB)) }

        NcAsmV2.typeFor(f)?.let { add(NcAsm.capability(it)); add(NcAsm.get(it)) }
        Optimizer.typeFor(f)?.let { add(Optimizer.capability(it)); add(Optimizer.status(it)); add(Optimizer.result(it)) }

        if (f.has(FunctionType1.VIBRATOR_ON_OFF)) add(SystemSettings.get(SystemSettings.VIBRATOR))
        if (f.has(FunctionType1.PLAYBACK_CONTROL_BY_WEARING_REMOVING_HEADPHONE_ON_OFF)) {
            add(SystemSettings.get(SystemSettings.PLAYBACK_CONTROL_BY_WEARING))
        }
        Power.autoPowerOffType(f)?.let { add(Power.capability(it)); add(Power.param(it)) }
        SystemSettings.speakToChatType(f)?.let { add(SystemSettings.get(it)); add(SystemSettings.getExtended(it)) }
        SystemSettings.assignableType(f)?.let { add(SystemSettings.capability(it)); add(SystemSettings.get(it)) }

        for (id in GeneralSettings.ids) if (id in f.t1) {
            add(GeneralSettings.capability(id)); add(GeneralSettings.status(id)); add(GeneralSettings.get(id))
        }

        Play.type(f)?.let { add(Play.capability(it)); add(Play.status(it)); add(Play.names(it)); add(Play.volume()) }
        VoiceGuidance.typeFor(f)?.let { t -> VoiceGuidance.capability(t)?.let(::add); add(VoiceGuidance.get(t)) }
        VoiceGuidance.volumeTypeFor(f)?.let { add(VoiceGuidance.get(it)) }
        Update.typeFor(f)?.let { add(Update.param(it)) }
        if (MaxVolume.supported(f)) { add(MaxVolume.status()); add(MaxVolume.param()) }
        Peripheral.typeFor(f)?.let { add(Peripheral.capability(it)); add(Peripheral.status(it)); add(Peripheral.devices(it)) }
    }
}
