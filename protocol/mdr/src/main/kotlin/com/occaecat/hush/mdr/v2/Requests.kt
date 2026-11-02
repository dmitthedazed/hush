package com.occaecat.hush.mdr.v2

import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.ListeningModes
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.bytesOf

/*
 * MDR v2 requests, one object per command group. Layouts follow Sound Connect's v2 payload classes (jadx
 * `sn0/`, `jn0/`, `yn0/` …, research/v2/decompile.md) and libmdr's ProtocolV2T1/T2 structs (mos9527/
 * SonyHeadphonesClient, MIT), which agree wherever both were read. The second byte is the group's inquired type.
 *
 * Differences from v1 that bite: OnOffSettingValue is inverted (ON = 0, OFF = 1), GsSettingType is
 * BOOLEAN 0 / LIST 1, AlertInquiredType FIXED_MESSAGE is 0, and battery / power live in the POWER group.
 *
 * Nothing here has been sent to real v2 hardware by Hush; see docs/MDR_V2.md for what was checked against captures.
 */

private fun t1(command: Command1, vararg rest: Int) = Request(Table.ONE, bytesOf(command.code, *rest))
private fun t2(command: Command2, vararg rest: Int) = Request(Table.TWO, bytesOf(command.code, *rest))

/** OnOffSettingValue / GsSettingValue / EnableDisable: 0 on (enable), 1 off. */
internal fun Boolean.onOff() = if (this) 0x00 else 0x01
private fun Boolean.bit() = if (this) 0x01 else 0x00

private const val ENGLISH = 0x01

object Connect {
    fun protocolInfo() = t1(Command1.CONNECT_GET_PROTOCOL_INFO, 0x00)
    fun capabilityInfo() = t1(Command1.CONNECT_GET_CAPABILITY_INFO, 0x00)
    /** DeviceInfoType: 1 model name, 2 firmware, 3 series and colour, 4 instruction guide. */
    fun deviceInfo(type: Int) = t1(Command1.CONNECT_GET_DEVICE_INFO, type)
    fun supportFunction(table: Table) =
        if (table == Table.ONE) t1(Command1.CONNECT_GET_SUPPORT_FUNCTION, 0x00) else t2(Command2.CONNECT_GET_SUPPORT_FUNCTION, 0x00)
}

/** COMMON_GET_STATUS with CommonInquiredType: 1 earbud connection, 2 codec, 3 upscaling effect. */
object Common {
    const val CONNECTION_STATUS = 0x01
    const val AUDIO_CODEC = 0x02
    const val UPSCALING_EFFECT = 0x03
    fun status(type: Int) = t1(Command1.COMMON_GET_STATUS, type)
}

/**
 * OPT group, NC optimizer (jadx `vn0/`). OptInquiredType: 0 personal + barometric, 1 personal, 2 barometric. SET_STATUS is
 * [type, ENABLE, OptimizerControl 0 cancel / 1 start]; replies are [type, EnableDisable, OptimizerStatus].
 */
object Optimizer {
    const val PERSONAL_BAROMETRIC = 0x00
    const val PERSONAL = 0x01
    const val BAROMETRIC = 0x02

    fun typeFor(f: V2Functions): Int? = when {
        f.has(FunctionType1.NC_OPTIMIZER_PERSONAL_BAROMETRIC) -> PERSONAL_BAROMETRIC
        f.has(FunctionType1.NC_OPTIMIZER_PERSONAL) -> PERSONAL
        f.has(FunctionType1.NC_OPTIMIZER_BAROMETRIC) -> BAROMETRIC
        else -> null
    }

    fun capability(type: Int) = t1(Command1.OPT_GET_CAPABILITY, type)
    fun status(type: Int) = t1(Command1.OPT_GET_STATUS, type)
    fun result(type: Int) = t1(Command1.OPT_GET_PARAM, type)
    fun start(type: Int) = t1(Command1.OPT_SET_STATUS, type, 0x00, 0x01)
    fun cancel(type: Int) = t1(Command1.OPT_SET_STATUS, type, 0x00, 0x00)
}

/** POWER group (PowerInquiredType): batteries, power off, auto power off. */
object Power {
    const val BATTERY = 0x00
    const val LEFT_RIGHT_BATTERY = 0x01
    const val CRADLE_BATTERY = 0x02
    const val POWER_OFF = 0x03
    const val AUTO_POWER_OFF = 0x04
    const val AUTO_POWER_OFF_WEARING_DETECTION = 0x05
    const val BATTERY_WITH_THRESHOLD = 0x08
    const val LR_BATTERY_WITH_THRESHOLD = 0x09
    const val CRADLE_BATTERY_WITH_THRESHOLD = 0x0A

    fun battery(type: Int) = t1(Command1.POWER_GET_STATUS, type)
    fun capability(type: Int) = t1(Command1.POWER_GET_CAPABILITY, type)
    fun param(type: Int) = t1(Command1.POWER_GET_PARAM, type)
    /** PowerOffSettingValue USER_POWER_OFF (1); Gadgetbridge and libmdr send the same `24 03 01`. */
    fun powerOff() = t1(Command1.POWER_SET_STATUS, POWER_OFF, 0x01)
    /** [current, last selected] AutoPowerOffElements, the same codes as v1 (0x00 5 min … 0x10 when taken off, 0x11 off). */
    fun setAutoPowerOff(type: Int, current: Int, lastSelected: Int) = t1(Command1.POWER_SET_PARAM, type, current, lastSelected)

    fun batteries(f: V2Functions): List<Request> = buildList {
        when {
            f.has(FunctionType1.BATTERY_LEVEL_INDICATOR) -> add(battery(BATTERY))
            f.has(FunctionType1.BATTERY_LEVEL_WITH_THRESHOLD) -> add(battery(BATTERY_WITH_THRESHOLD))
        }
        when {
            f.has(FunctionType1.LEFT_RIGHT_BATTERY_LEVEL_INDICATOR) -> add(battery(LEFT_RIGHT_BATTERY))
            f.has(FunctionType1.LR_BATTERY_LEVEL_WITH_THRESHOLD) -> add(battery(LR_BATTERY_WITH_THRESHOLD))
        }
        when {
            f.has(FunctionType1.CRADLE_BATTERY_LEVEL_INDICATOR) -> add(battery(CRADLE_BATTERY))
            f.has(FunctionType1.CRADLE_BATTERY_LEVEL_WITH_THRESHOLD) -> add(battery(CRADLE_BATTERY_WITH_THRESHOLD))
        }
        if (isEmpty()) add(battery(BATTERY))
    }

    /** AUTO_POWER_OFF (by time) or AUTO_POWER_OFF_WEARING_DETECTION, as the headset declared. */
    fun autoPowerOffType(f: V2Functions): Int? = when {
        f.has(FunctionType1.AUTO_POWER_OFF) -> AUTO_POWER_OFF
        f.has(FunctionType1.AUTO_POWER_OFF_WITH_WEARING_DETECTION) -> AUTO_POWER_OFF_WEARING_DETECTION
        else -> null
    }
}

/**
 * The NcAsmInquiredType a headset uses for noise control, chosen from its functions as Sound Connect does
 * (vv/e.java), and the fields of its NCASM payloads after `[cmd, type, ValueChangeStatus, NcAsmTotalEffect]`.
 */
enum class NcAsmV2(val code: Int, val function: FunctionType1, val kind: NcAsmKind, val fields: List<Field>, val extra: Int = 0) {
    MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS_NA(
        0x19, FunctionType1.MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT_NOISE_ADAPTATION,
        NcAsmKind.NC_ASM, listOf(Field.MODE, Field.ASM_MODE, Field.LEVEL), extra = 2,
    ),
    MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS(
        0x17, FunctionType1.MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT,
        NcAsmKind.NC_ASM, listOf(Field.MODE, Field.ASM_MODE, Field.LEVEL),
    ),
    MODE_NC_ASM_DUAL_SINGLE_NC_MODE_SWITCH_AND_ASM_SEAMLESS(
        0x16, FunctionType1.MODE_NC_ASM_NOISE_CANCELLING_DUAL_SINGLE_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT,
        NcAsmKind.NC_ASM, listOf(Field.MODE, Field.NC_VALUE, Field.ASM_MODE, Field.LEVEL),
    ),
    MODE_NC_ASM_AUTO_NC_MODE_SWITCH_AND_ASM_SEAMLESS(
        0x15, FunctionType1.MODE_NC_ASM_NOISE_CANCELLING_DUAL_AUTO_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT,
        NcAsmKind.NC_ASM, listOf(Field.MODE, Field.NC_VALUE, Field.ASM_MODE, Field.LEVEL),
    ),
    NC_MODE_SWITCH_AND_ASM_SEAMLESS(
        0x14, FunctionType1.NOISE_CANCELLING_DUAL_SINGLE_OFF_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT,
        NcAsmKind.NC_ASM, listOf(Field.NC_VALUE, Field.ASM_MODE, Field.LEVEL),
    ),
    NC_ON_OFF_AND_ASM_SEAMLESS(
        0x13, FunctionType1.NOISE_CANCELLING_ONOFF_AND_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT,
        NcAsmKind.NC_ASM, listOf(Field.NC_ON_OFF, Field.ASM_MODE, Field.LEVEL),
    ),
    ASM_SEAMLESS(0x22, FunctionType1.AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT, NcAsmKind.ASM, listOf(Field.ASM_MODE, Field.LEVEL)),
    NC_ON_OFF(0x01, FunctionType1.NOISE_CANCELLING_ONOFF, NcAsmKind.NC, listOf(Field.NC_ON_OFF));

    /** NcAsmMode (0 NC, 1 ASM), NcValue (0 off, 1 single, 2 dual, 3.. auto), NcAsmOnOffValue (1 on), AmbientSoundMode (1 voice), level. */
    enum class Field { MODE, NC_VALUE, NC_ON_OFF, ASM_MODE, LEVEL }

    val hasLevel get() = Field.LEVEL in fields
    /** NcValue ON_SINGLE vs ON_DUAL: the wind-noise switch, kept in [NcAsmState.ncOnValue] as on v1. */
    val dualSingle get() = this == MODE_NC_ASM_DUAL_SINGLE_NC_MODE_SWITCH_AND_ASM_SEAMLESS || this == NC_MODE_SWITCH_AND_ASM_SEAMLESS

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code }
        /** The first type, in [entries] order, whose function the headset declared. */
        fun typeFor(f: V2Functions) = entries.firstOrNull { f.has(it.function) }
    }
}

object NcAsm {
    fun capability(type: NcAsmV2) = t1(Command1.NCASM_GET_CAPABILITY, type.code)
    fun get(type: NcAsmV2) = t1(Command1.NCASM_GET_PARAM, type.code)

    /**
     * NCASM_SET_PARAM in [type]'s layout. ValueChangeStatus 0 while a slider is dragged, 1 once it is let go.
     * Unverified on hardware: what a mode-switch type expects in NcValue while ambient sound is on (Hush sends
     * the remembered NC value, as libmdr does) and the noise-adaptation tail, echoed back from the last state.
     */
    fun set(type: NcAsmV2, s: NcAsmState, dragging: Boolean): Request {
        val nc = s.mode == AmbientMode.NOISE_CANCELLING
        val values = type.fields.map {
            when (it) {
                NcAsmV2.Field.MODE -> if (s.mode == AmbientMode.AMBIENT) 0x01 else 0x00
                NcAsmV2.Field.NC_VALUE -> if (nc || type.fields.contains(NcAsmV2.Field.MODE)) s.ncOnValue.takeIf { v -> v != 0 } ?: 0x02 else 0x00
                NcAsmV2.Field.NC_ON_OFF -> nc.bit()
                NcAsmV2.Field.ASM_MODE -> s.voiceFocus.bit()
                NcAsmV2.Field.LEVEL -> s.ambientLevel.coerceAtLeast(0)
            }
        }
        val tail = s.extra.takeIf { it.size == type.extra } ?: List(type.extra) { 0 }
        return t1(
            Command1.NCASM_SET_PARAM, type.code, (!dragging).bit(), (s.mode != AmbientMode.OFF).bit(),
            *values.toIntArray(), *tail.toIntArray(),
        )
    }
}

/** EqEbbInquiredType: 0 preset EQ, 1 EBB, 2 preset EQ (not customizable), 3 with ULT mode, 4 with error code. */
object EqEbb {
    const val PRESET_EQ = 0x00
    const val EBB = 0x01
    const val PRESET_EQ_NONCUSTOMIZABLE = 0x02
    const val PRESET_EQ_AND_ULT_MODE = 0x03
    const val PRESET_EQ_AND_ERRORCODE = 0x04

    fun eqType(f: V2Functions): Int? = when {
        f.has(FunctionType1.PRESET_EQ) -> PRESET_EQ
        f.has(FunctionType1.PRESET_EQ_NON_CUSTOMIZABLE) -> PRESET_EQ_NONCUSTOMIZABLE
        f.has(FunctionType1.PRESET_EQ_AND_ULT_MODE) -> PRESET_EQ_AND_ULT_MODE
        f.has(FunctionType1.PRESET_EQ_AND_ERRORCODE) -> PRESET_EQ_AND_ERRORCODE
        else -> null
    }

    /** The capability names its presets in [ENGLISH]; Sound Connect's jn0/b takes the language for every type, EBB too. */
    fun capability(type: Int) = t1(Command1.EQEBB_GET_CAPABILITY, type, ENGLISH)
    fun get(type: Int) = t1(Command1.EQEBB_GET_PARAM, type)
    /** Band centre frequencies (jn0/c, e0): [type, n, (EqBandInformationType, u16)×n], the same as v1. */
    fun bandInfo(type: Int) = t1(Command1.EQEBB_GET_EXTENDED_INFO, type)
    /** A preset alone, no band levels: a frame with both is rejected (WF-LC900, as on v1). */
    fun selectPreset(type: Int, presetId: Int) =
        if (type == PRESET_EQ_AND_ULT_MODE) t1(Command1.EQEBB_SET_PARAM, type, presetId, 0x00, 0x00)
        else t1(Command1.EQEBB_SET_PARAM, type, presetId, 0x00)
    fun setBands(type: Int, presetId: Int, bands: List<Int>) =
        if (type == PRESET_EQ_AND_ULT_MODE) t1(Command1.EQEBB_SET_PARAM, type, presetId, 0x00, bands.size, *bands.toIntArray())
        else t1(Command1.EQEBB_SET_PARAM, type, presetId, bands.size, *bands.toIntArray())
    fun setEbb(level: Int) = t1(Command1.EQEBB_SET_PARAM, EBB, level and 0xFF)
}

/** AudioInquiredType: 0 connection mode, 1 upscaling (DSEE), 2 connection mode with LDAC status. */
object Audio {
    const val CONNECTION_MODE = 0x00
    const val UPSCALING = 0x01
    const val CONNECTION_MODE_WITH_LDAC_STATUS = 0x02
    /** PriorMode with a third value, 2 LOW_LATENCY_PRIOR_BETA: LE Audio (fn0/c1, QualityPriorValue). */
    const val CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO = 0x05

    fun connectionModeType(f: V2Functions): Int? = when {
        f.has(FunctionType1.CONNECTION_MODE_SOUND_QUALITY_CONNECTION_QUALITY) -> CONNECTION_MODE
        f.has(FunctionType1.CONNECTION_MODE_SOUND_QUALITY_SOUND_WITH_LDAC_STATUS_QUALITY_CONNECTION_QUALITY) -> CONNECTION_MODE_WITH_LDAC_STATUS
        f.has(FunctionType1.CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO) -> CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO
        else -> null
    }

    fun capability(type: Int) = t1(Command1.AUDIO_GET_CAPABILITY, type)
    fun get(type: Int) = t1(Command1.AUDIO_GET_PARAM, type)
    /** UpscalingTypeAutoOff: 0 off, 1 auto. */
    fun setUpscaling(auto: Boolean) = t1(Command1.AUDIO_SET_PARAM, UPSCALING, auto.bit())
    /** PriorMode: 0 sound quality, 1 stable connection. */
    fun setConnectionMode(type: Int, preferConnection: Boolean) = setPriorMode(type, preferConnection.bit())
    /** Type 5 also carries EnableDisable, always ENABLE in Sound Connect (r10/e, connectionmode/h). */
    fun setPriorMode(type: Int, prior: Int) =
        if (type == CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO) t1(Command1.AUDIO_SET_PARAM, type, prior, 0x00)
        else t1(Command1.AUDIO_SET_PARAM, type, prior)

    const val BGM_MODE = 0x03
    const val UPMIX_CINEMA = 0x04
    const val VOICE_CONTENTS = 0x06
    const val SOUND_LEAKAGE_REDUCTION = 0x07
    const val BGM_MODE_AND_ERRORCODE = 0x09
    const val UPMIX_SERIES = 0x0A

    fun bgmType(f: V2Functions): Int? = when {
        f.has(FunctionType1.BGM_MODE_SMALL_MIDDLE_LARGE) -> BGM_MODE
        f.has(FunctionType1.BGM_MODE_SMALL_MIDDLE_LARGE_AND_ERRORCODE) -> BGM_MODE_AND_ERRORCODE
        else -> null
    }

    /** The listening-mode parameters [f] has, to read at session start. */
    fun listeningTypes(f: V2Functions): List<Int> = listOfNotNull(
        bgmType(f),
        UPMIX_CINEMA.takeIf { f.has(FunctionType1.UPMIX_CINEMA) },
        UPMIX_SERIES.takeIf { f.has(FunctionType1.UPMIX_SERIES) },
        VOICE_CONTENTS.takeIf { f.has(FunctionType1.VOICE_CONTENTS) },
        SOUND_LEAKAGE_REDUCTION.takeIf { f.has(FunctionType1.SOUND_LEAKAGE_REDUCTION) },
    )

    /** BGM: [type, OnOffSettingValue, RoomSize] (fn0/a1); the others [type, OnOffSettingValue | UpmixItemId] (g1, h1, j1, f1). */
    fun setBgm(type: Int, on: Boolean, roomSize: Int) = t1(Command1.AUDIO_SET_PARAM, type, on.onOff(), roomSize)
    fun setOnOff(type: Int, on: Boolean) = t1(Command1.AUDIO_SET_PARAM, type, on.onOff())
    fun setUpmix(item: Int) = t1(Command1.AUDIO_SET_PARAM, UPMIX_SERIES, item)

    /** Switches go off before the new one goes on, so the headset never sees two modes at once. */
    fun listening(f: V2Functions, from: ListeningModes, to: ListeningModes): List<Request> {
        val bgm = bgmType(f)
        fun writes(on: Boolean) = buildList {
            if (bgm != null && to.bgm != null && (to.bgm != from.bgm || to.roomSize != from.roomSize) && to.bgm == on) add(setBgm(bgm, on, to.roomSize))
            if (to.cinema != null && to.cinema != from.cinema && to.cinema == on) add(setOnOff(UPMIX_CINEMA, on))
            if (to.upmix != null && to.upmix != from.upmix && (to.upmix != 0) == on) add(setUpmix(to.upmix))
            if (to.voiceBoost != null && to.voiceBoost != from.voiceBoost && to.voiceBoost == on) add(setOnOff(VOICE_CONTENTS, on))
            if (to.leakageReduction != null && to.leakageReduction != from.leakageReduction && to.leakageReduction == on) {
                add(setOnOff(SOUND_LEAKAGE_REDUCTION, on))
            }
        }
        return writes(false) + writes(true)
    }
}

/** SystemInquiredType settings. */
object SystemSettings {
    const val VIBRATOR = 0x00
    const val PLAYBACK_CONTROL_BY_WEARING = 0x01
    const val SMART_TALKING_MODE_TYPE1 = 0x02
    const val ASSIGNABLE_SETTINGS = 0x03
    const val SMART_TALKING_MODE_TYPE2 = 0x0C
    const val ASSIGNABLE_SETTINGS_WITH_LIMITATION = 0x0E

    fun speakToChatType(f: V2Functions): Int? = when {
        f.has(FunctionType1.SMART_TALKING_MODE_TYPE2) -> SMART_TALKING_MODE_TYPE2
        f.has(FunctionType1.SMART_TALKING_MODE_TYPE1) -> SMART_TALKING_MODE_TYPE1
        else -> null
    }

    fun assignableType(f: V2Functions): Int? = when {
        f.has(FunctionType1.ASSIGNABLE_SETTING_WITH_LIMITATION) -> ASSIGNABLE_SETTINGS_WITH_LIMITATION
        f.has(FunctionType1.ASSIGNABLE_SETTING) -> ASSIGNABLE_SETTINGS
        else -> null
    }

    fun capability(type: Int) = t1(Command1.SYSTEM_GET_CAPABILITY, type)
    fun get(type: Int) = t1(Command1.SYSTEM_GET_PARAM, type)
    fun getExtended(type: Int) = t1(Command1.SYSTEM_GET_EXT_PARAM, type)

    /** SystemSetParamCommon: [type, OnOffSettingValue]; pause when taken off is type 1 (V2ControlByWearingRepository). */
    fun setOnOff(type: Int, on: Boolean) = t1(Command1.SYSTEM_SET_PARAM, type, on.onOff())
    /** [type, mode OnOffSettingValue, preview OnOffSettingValue (always off)]. */
    fun setSpeakToChat(type: Int, on: Boolean) = t1(Command1.SYSTEM_SET_PARAM, type, on.onOff(), false.onOff())
    /** TYPE2: [DetectSensitivity, ModeOutTime]; TYPE1 also carries voice focus between them. */
    fun setSpeakToChatDetail(type: Int, d: SpeakToChatDetail) =
        if (type == SMART_TALKING_MODE_TYPE1) t1(Command1.SYSTEM_SET_EXT_PARAM, type, d.sensitivity, d.voiceFocus.onOff(), d.modeOutTime)
        else t1(Command1.SYSTEM_SET_EXT_PARAM, type, d.sensitivity, d.modeOutTime)
    fun setAssignable(type: Int, presets: List<Int>) = t1(Command1.SYSTEM_SET_PARAM, type, presets.size, *presets.toIntArray())
}

/** FIXED_MESSAGE alerts (AlertInquiredType 0): without them the headset refuses changes that drop its links. */
object Alert {
    fun enable() = t1(Command1.ALERT_SET_STATUS, 0x00, 0x00)
    /** [messageType] is the v2 AlertMessageType; AlertAction 0 negative, 1 positive. */
    fun answer(messageType: Int, positive: Boolean) = t1(Command1.ALERT_SET_PARAM, 0x00, messageType, positive.bit())

    /**
     * v2 AlertMessageType → the v1 code Hush's controller and UI know: v2 numbers the first four from 0
     * (connection mode, key assign, firmware disconnect, assistant available) where v1 starts at 1; from 5 on
     * the codes match (6 multipoint with LDAC off, 7 multipoint, 8 battery). Unverified on hardware.
     */
    fun toV1(type: Int) = if (type in 0x00..0x03) type + 1 else type
    fun fromV1(type: Int) = if (type in 0x01..0x04) type - 1 else type
}

/** General settings 1–4 (GsInquiredType 0xD1..0xD4), described by the headset itself. */
object GeneralSettings {
    val ids = listOf(0xD1, 0xD2, 0xD3, 0xD4)
    fun capability(id: Int) = t1(Command1.GENERAL_SETTING_GET_CAPABILITY, id, ENGLISH)
    fun status(id: Int) = t1(Command1.GENERAL_SETTING_GET_STATUS, id)
    fun get(id: Int) = t1(Command1.GENERAL_SETTING_GET_PARAM, id)
    /** GsSettingType BOOLEAN (0) with GsSettingValue ON = 0. */
    fun set(id: Int, on: Boolean) = t1(Command1.GENERAL_SETTING_SET_PARAM, id, 0x00, on.onOff())
    /** GsSettingType LIST (1). */
    fun setIndex(id: Int, index: Int) = t1(Command1.GENERAL_SETTING_SET_PARAM, id, 0x01, index)
}

/** Playback controller: PlayInquiredType 1–3 for track info and control, 0x20 the music volume. */
object Play {
    const val MUSIC_VOLUME = 0x20

    fun supported(f: V2Functions) = type(f) != null

    fun type(f: V2Functions): Int? = when {
        f.any(FunctionType1.PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT, FunctionType1.PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT_AND_MUTE) -> 0x01
        f.has(FunctionType1.PLAYBACK_CONTROLLER_WITH_CALL_VOLUME_ADJUSTMENT_AND_FUNCTION_CHANGE) -> 0x02
        f.has(FunctionType1.PLAYBACK_CONTROLLER_WITH_FUNCTION_CHANGE) -> 0x03
        else -> null
    }

    fun capability(type: Int) = t1(Command1.PLAY_GET_CAPABILITY, type)
    fun status(type: Int) = t1(Command1.PLAY_GET_STATUS, type)
    /** Track, album, artist and genre in one reply. */
    fun names(type: Int) = t1(Command1.PLAY_GET_PARAM, type)
    fun volume() = t1(Command1.PLAY_GET_PARAM, MUSIC_VOLUME)
    fun setVolume(v: Int) = t1(Command1.PLAY_SET_PARAM, MUSIC_VOLUME, v)
    /** [type, EnableDisable ENABLE, PlaybackControl]. */
    fun control(type: Int, control: Int) = t1(Command1.PLAY_SET_STATUS, type, 0x00, control)
}

/** Table 2 paired-source list (PeripheralInquiredType 0 classic BT, 2 with the class of device). */
object Peripheral {
    fun typeFor(f: V2Functions): Int? = when {
        f.any(FunctionType2.PAIRING_DEVICE_MANAGEMENT_WITH_BLUETOOTH_CLASS_OF_DEVICE_CLASSIC_BT,
            FunctionType2.PAIRING_DEVICE_MANAGEMENT_WITH_BLUETOOTH_CLASS_OF_DEVICE_CLASSIC_LE) -> 0x02
        f.has(FunctionType2.PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT) -> 0x00
        else -> null
    }

    fun capability(type: Int) = t2(Command2.PERI_GET_CAPABILITY, type)
    fun status(type: Int) = t2(Command2.PERI_GET_STATUS, type)
    fun devices(type: Int) = t2(Command2.PERI_GET_PARAM, type)
    /** PeripheralBluetoothMode 1 (inquiry scan) makes the headset discoverable, 0 ends it; then EnableDisable ENABLE. */
    fun setPairingMode(type: Int, on: Boolean) = t2(Command2.PERI_SET_STATUS, type, on.bit(), 0x00)
    /** ConnectivityActionType 0 disconnect, 1 connect, 2 unpair; the address as its 17-character text. */
    fun action(type: Int, action: Int, address: String) = Request(
        Table.TWO,
        bytesOf(Command2.PERI_SET_EXTENDED_PARAM.code, type, action) + address.uppercase().toByteArray(Charsets.US_ASCII),
    )
}

/** Table 2 voice guidance (VoiceGuidanceInquiredType 0–3 by model). */
object VoiceGuidance {
    fun typeFor(f: V2Functions): Int? = when {
        f.has(FunctionType2.VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_NOT_SUPPORT_LANGUAGE_SWITCH) -> 0x00
        f.any(FunctionType2.VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_SUPPORT_LANGUAGE_SWITCH,
            FunctionType2.VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_SUPPORT_LANGUAGE_SWITCH_AND_VOLUME_ADJUSTMENT) -> 0x01
        f.has(FunctionType2.VOICE_GUIDANCE_SETTING_SUPPORT_LANGUAGE_SWITCH) -> 0x02
        f.has(FunctionType2.VOICE_GUIDANCE_SETTING_ONLY_ON_OFF_SWITCH) -> 0x03
        else -> null
    }

    const val SUPPORT_LANGUAGE_SWITCH = 0x02
    const val VOLUME = 0x20
    const val VOLUME_FIXED_TO_5_STEPS = 0x21

    /** VoiceGuidanceInquiredType for the prompt volume (−2..+2), if the headset has one. */
    fun volumeTypeFor(f: V2Functions): Int? = when {
        f.has(FunctionType2.VOICE_GUIDANCE_SETTING_MTK_TRANSFER_WITHOUT_DISCONNECTION_SUPPORT_LANGUAGE_SWITCH_AND_VOLUME_ADJUSTMENT) -> VOLUME
        f.has(FunctionType2.VOICE_GUIDANCE_VOLUME_SETTING_MTK_FIXED_TO_5_STEPS) -> VOLUME_FIXED_TO_5_STEPS
        else -> null
    }

    /** Types 0–2 answer with whether the prompts can be switched off and which languages they have (ap0/r, s, t, u). */
    fun capability(type: Int) = if (type == 0x03) null else t2(Command2.VOICE_GUIDANCE_GET_CAPABILITY, type)
    fun get(type: Int) = t2(Command2.VOICE_GUIDANCE_GET_PARAM, type)
    /** Types 0, 1, 3: [type, OnOffSettingValue]. Type 2 always carries the language too (ap0/k0). */
    fun set(type: Int, on: Boolean, language: Int?) = when {
        type != SUPPORT_LANGUAGE_SWITCH -> t2(Command2.VOICE_GUIDANCE_SET_PARAM, type, on.onOff())
        language != null -> t2(Command2.VOICE_GUIDANCE_SET_PARAM, type, on.onOff(), language)
        else -> null
    }
    /** [type, volume −2..+2 as a signed byte, OnOffSettingValue: play a sample prompt] (ap0/l0, b70/q). */
    fun setVolume(type: Int, volume: Int, sample: Boolean) =
        t2(Command2.VOICE_GUIDANCE_SET_PARAM, type, volume.coerceIn(-2, 2) and 0xFF, sample.onOff())
}

/**
 * Table 2 SAFE_LISTENING, Max Volume Control (SafeListeningInquiredType 5): on/off through STATUS (uo0/p, w), the
 * limit 0..4 through PARAM (to0/g, j). Sound Connect's "Max Volume Setting" slider.
 */
object MaxVolume {
    const val TYPE = 0x05
    const val STEPS = 5
    fun supported(f: V2Functions) = f.has(FunctionType2.MAX_VOLUME_LEVEL_LIMIT)
    fun status() = t2(Command2.SAFE_LISTENING_GET_STATUS, TYPE)
    fun param() = t2(Command2.SAFE_LISTENING_GET_PARAM, TYPE)
    fun setOn(on: Boolean) = t2(Command2.SAFE_LISTENING_SET_STATUS, TYPE, on.onOff())
    fun setLevel(level: Int) = t2(Command2.SAFE_LISTENING_SET_PARAM, TYPE, level.coerceIn(0, STEPS - 1))
}

/**
 * UPDT_GET_PARAM (ho0/b). The UpdtInquiredType follows the firmware-update function the headset declared; the reply
 * carries the identity Sony's update server needs (ho0/q).
 */
object Update {
    fun typeFor(f: V2Functions): Int? = when {
        f.has(FunctionType1.FW_UPDATE_MTK_TRANSFER_WITHOUT_DISCONNECTION) -> 0x02
        f.has(FunctionType1.FW_UPDATE_MTK_TRANSFER_WITHOUT_DISCONNECTION_AUTO_UPDATE) -> 0x04
        f.has(FunctionType1.FW_UPDATE_MTK_TRANSFER_WITH_REPAIR_MODE) -> 0x05
        f.has(FunctionType1.FW_UPDATE_MTK_TRANSFER_WITH_AC_CONNECTION_CHECK) -> 0x06
        f.has(FunctionType1.FW_UPDATE_USING_MC_APP) -> 0x07
        f.has(FunctionType1.FW_UPDATE_TANDEM) -> 0x10
        else -> null
    }

    fun param(type: Int) = t1(Command1.UPDT_GET_PARAM, type)
}
