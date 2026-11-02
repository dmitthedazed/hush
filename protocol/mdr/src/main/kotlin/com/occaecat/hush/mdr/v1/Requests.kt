package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.bytesOf

/*
 * MDR v1 requests, one object per command group (tm0.* builders in Sound Connect). The byte after the
 * command is the group's "inquired type"; Hush always asks for names in English (DisplayLanguage 1).
 */

private fun t1(command: Command1, vararg rest: Int) = Request(Table.ONE, bytesOf(command.code, *rest))
private fun t2(command: Command2, vararg rest: Int) = Request(Table.TWO, bytesOf(command.code, *rest))
private fun Boolean.bit() = if (this) 1 else 0

/** CommonStatus: 0 enable, 1 disable. */
private fun Boolean.status() = if (this) 0x00 else 0x01

private const val ENGLISH = 0x01

object Connect {
    fun protocolInfo() = t1(Command1.CONNECT_GET_PROTOCOL_INFO, 0x00)
    fun capabilityInfo() = t1(Command1.CONNECT_GET_CAPABILITY_INFO, 0x00)
    fun supportFunction() = t1(Command1.CONNECT_GET_SUPPORT_FUNCTION, 0x00)
    fun modelName() = t1(Command1.CONNECT_GET_DEVICE_INFO, 0x01)
    fun firmwareVersion() = t1(Command1.CONNECT_GET_DEVICE_INFO, 0x02)
    /** SERIES_AND_COLOR_INFO: [05, 03, series, ModelColor]. */
    fun seriesAndColor() = t1(Command1.CONNECT_GET_DEVICE_INFO, 0x03)
    /** INSTRUCTION_GUIDE: [05, 04, n, GuidanceCategory×n], protocol 0x5000 and up. */
    fun instructionGuide() = t1(Command1.CONNECT_GET_DEVICE_INFO, 0x04)
}

object Common {
    /** BatteryInquiredType: 0 the headset, 1 left and right earbuds, 2 the charging case. */
    fun battery(type: Int = 0x00) = t1(Command1.COMMON_GET_BATTERY_LEVEL, type)
    fun upscalingEffect() = t1(Command1.COMMON_GET_UPSCALING_EFFECT, 0x00)
    fun codec() = t1(Command1.COMMON_GET_AUDIO_CODEC, 0x00)
    /** BluetoothDeviceInfoType: 0 address, 1 BLE hash. */
    fun bluetoothInfo(type: Int) = t1(Command1.COMMON_GET_BLUETOOTH_DEVICE_INFO, type)
    fun powerOff() = t1(Command1.COMMON_SET_POWER_OFF, 0x00, 0x01)
    fun earbudConnection() = t1(Command1.COMMON_GET_CONNECTION_STATUS, 0x01)
    /** Sound Connect's "eco timer" (j30/a): the headset keeps the link alive for [minutes]; null stops it. */
    fun keepAlive(minutes: Int?) = t1(Command1.COMMON_SET_LINK_CONTROL, 0x00, (minutes != null).status(), minutes ?: 0)
}

object Update {
    /**
     * UPDT_GET_PARAM for any UpdateInquiredType: 2 category, 3 service id, 4 nation code, 5 language,
     * 6 serial, 11 device-binding id are length-prefixed text; 7 BLE TX power (signed dBm), 8 / 10 battery
     * thresholds (%), 9 UpdateMethod are one byte (tm0/x2, vm0/t0..y0).
     */
    fun param(type: Int) = t1(Command1.UPDT_GET_PARAM, type)
    fun serialNumber() = param(0x06)
}

object Vpt {
    /** VptInquiredType 1 surround (VPT), 2 sound position; the capability names its presets in [ENGLISH] (tm0/l0). */
    fun capability(type: Int) = t1(Command1.VPT_GET_CAPABILITY, type, ENGLISH)
    fun get(type: Int) = t1(Command1.VPT_GET_PARAM, type)
    fun setSurround(presetId: Int) = t1(Command1.VPT_SET_PARAM, 0x01, presetId)
    fun setSoundPosition(presetId: Int) = t1(Command1.VPT_SET_PARAM, 0x02, presetId)
}

object EqEbb {
    const val PRESET_EQ = 0x01
    const val EBB = 0x02
    const val PRESET_EQ_NONCUSTOMIZABLE = 0x03

    fun capability(type: Int = PRESET_EQ) = t1(Command1.EQEBB_GET_CAPABILITY, type, ENGLISH)
    fun get(type: Int = PRESET_EQ) = t1(Command1.EQEBB_GET_PARAM, type)
    fun bandInfo(type: Int = PRESET_EQ) = t1(Command1.EQEBB_GET_EXTENDED_INFO, type)
    fun selectPreset(presetId: Int, type: Int = PRESET_EQ) = t1(Command1.EQEBB_SET_PARAM, type, presetId, 0x00)
    fun setBands(presetId: Int, bands: List<Int>) =
        t1(Command1.EQEBB_SET_PARAM, PRESET_EQ, presetId, bands.size, *bands.toIntArray())
    fun setEbb(level: Int) = t1(Command1.EQEBB_SET_PARAM, EBB, level and 0xFF)
}

object NcAsm {
    fun capability(kind: NcAsmKind) = t1(Command1.NCASM_GET_CAPABILITY, kind.code)
    fun get(kind: NcAsmKind = NcAsmKind.NC_ASM) = t1(Command1.NCASM_GET_PARAM, kind.code)

    /**
     * NCASM_SET_PARAM in the layout of the state's kind. NcAsmEffect: 0x10 while a slider is being dragged,
     * 0x11 once the adjustment is complete, 0x00 when everything is off. An on/off ambient type (AsmSettingType 0)
     * takes 1 for on instead of a level.
     */
    fun set(s: NcAsmState, dragging: Boolean = false): Request {
        val effect = when {
            s.mode == AmbientMode.OFF -> 0x00
            dragging -> 0x10
            else -> 0x11
        }
        val asmValue = when {
            s.mode != AmbientMode.AMBIENT -> 0
            s.asmSettingType == 0x00 -> 1
            else -> s.ambientLevel
        }
        return when (s.kind) {
            NcAsmKind.NC -> t1(Command1.NCASM_SET_PARAM, 0x01, s.ncSettingType, (s.mode == AmbientMode.NOISE_CANCELLING).bit())
            NcAsmKind.ASM -> t1(Command1.NCASM_SET_PARAM, 0x03, effect, s.asmSettingType, s.voiceFocus.bit(), asmValue)
            NcAsmKind.NC_ASM -> t1(
                Command1.NCASM_SET_PARAM, 0x02, effect, s.ncSettingType,
                if (s.mode == AmbientMode.NOISE_CANCELLING) s.ncOnValue else 0x00,
                s.asmSettingType, s.voiceFocus.bit(), asmValue,
            )
        }
    }
}

object Sense {
    fun capability() = t1(Command1.SENSE_GET_CAPABILITY, 0x01)
    /** AUTO_NC_ASM, ENABLE, START: the headset starts adapting NC/ASM on its own (a50/a, tm0/n3). */
    fun start() = t1(Command1.SENSE_SET_STATUS, 0x01, 0x00, 0x01)
}

object Optimizer {
    fun capability() = t1(Command1.OPT_GET_CAPABILITY, 0x01)
    fun status() = t1(Command1.OPT_GET_STATUS, 0x01)
    fun result() = t1(Command1.OPT_GET_PARAM, 0x01)
    fun start() = t1(Command1.OPT_SET_STATUS, 0x01, 0x00, 0x01)
    fun cancel() = t1(Command1.OPT_SET_STATUS, 0x01, 0x00, 0x00)
}

/**
 * The headset asks the app to confirm changes that drop its links (tm0/o0, vm0/u). AlertMessageType below;
 * without alerts enabled it silently refuses those changes.
 */
object Alert {
    const val CONNECTION_MODE_CHANGE = 0x01
    const val KEY_ASSIGN_CHANGE = 0x02
    const val FIRMWARE_UPDATE_DISCONNECT = 0x03
    const val GOOGLE_ASSISTANT_AVAILABLE = 0x04
    const val DUAL_VOICE_ASSISTANT_UNAVAILABLE = 0x05
    const val MULTIPOINT_ON = 0x06
    const val MULTIPOINT_OFF = 0x07
    const val BATTERY_EQ_AND_UPSCALING = 0x08

    fun capability() = t1(Command1.ALERT_GET_CAPABILITY, 0x01)
    /** FIXED_MESSAGE alerts on: without them the headset silently refuses changes that drop its links. */
    fun enable() = t1(Command1.ALERT_SET_STATUS, 0x01, 0x00)
    fun answer(type: Int, positive: Boolean) = t1(Command1.ALERT_SET_PARAM, 0x01, type, positive.bit())
}

/** PLAYBACK_CONTROLLER (PlayInquiredType 1): track info, play state, the headset's volume. */
object Play {
    fun capability() = t1(Command1.PLAY_GET_CAPABILITY, 0x01)
    fun status() = t1(Command1.PLAY_GET_STATUS, 0x01)
    /** PlaybackDetailedDataType: 0 track, 1 album, 2 artist, 0x20 volume. */
    fun param(type: Int) = t1(Command1.PLAY_GET_PARAM, 0x01, type)
    fun setVolume(v: Int) = t1(Command1.PLAY_SET_PARAM, 0x01, 0x20, v)
    /** PlaybackControl: 1 pause, 2 next, 3 previous, 7 play. */
    fun control(control: Int) = t1(Command1.PLAY_SET_STATUS, 0x01, 0x00, control)
}

/** Training mode on sports models (SportsInquiredType 1). */
object Sports {
    fun capability() = t1(Command1.SPORTS_GET_CAPABILITY, 0x01)
    fun get() = t1(Command1.SPORTS_GET_PARAM, 0x01)
    fun set(on: Boolean) = t1(Command1.SPORTS_SET_PARAM, 0x01, 0x00, on.bit())
}

/** General settings 1–3 (GsInquiredType 0xD1..0xD3): the headset describes each one itself. */
object GeneralSettings {
    val ids = listOf(0xD1, 0xD2, 0xD3)
    fun capability(id: Int) = t1(Command1.GENERAL_SETTING_GET_CAPABILITY, id, ENGLISH)
    fun status(id: Int) = t1(Command1.GENERAL_SETTING_GET_STATUS, id)
    fun get(id: Int) = t1(Command1.GENERAL_SETTING_GET_PARAM, id)
    fun set(id: Int, on: Boolean) = t1(Command1.GENERAL_SETTING_SET_PARAM, id, 0x01 /* BOOLEAN_TYPE */, on.bit())
    fun setIndex(id: Int, index: Int) = t1(Command1.GENERAL_SETTING_SET_PARAM, id, 0x02 /* LIST_TYPE */, index)
}

/** AudioInquiredType 1 connection mode (sound quality vs. stable connection), 2 upscaling (DSEE). */
object Audio {
    fun capability(type: Int) = t1(Command1.AUDIO_GET_CAPABILITY, type)
    fun soundQualityMode() = t1(Command1.AUDIO_GET_PARAM, 0x01)
    fun upscaling() = t1(Command1.AUDIO_GET_PARAM, 0x02)
    fun setSoundQualityMode(preferConnection: Boolean) = t1(Command1.AUDIO_SET_PARAM, 0x01, 0x00, preferConnection.bit())
    fun setUpscaling(auto: Boolean) = t1(Command1.AUDIO_SET_PARAM, 0x02, 0x00, auto.bit())
}

/** SystemInquiredType settings, plus CONTROL_BY_WEARING (3), which Sound Connect handles in its KMP module. */
object SystemSettings {
    const val VIBRATOR = 0x01
    const val POWER_SAVING_MODE = 0x02
    const val CONTROL_BY_WEARING = 0x03
    const val AUTO_POWER_OFF = 0x04
    const val SMART_TALKING_MODE = 0x05
    const val ASSIGNABLE_SETTINGS = 0x06

    fun capability(type: Int) = t1(Command1.SYSTEM_GET_CAPABILITY, type)
    fun status(type: Int) = t1(Command1.SYSTEM_GET_STATUS, type)
    fun get(type: Int) = t1(Command1.SYSTEM_GET_PARAM, type)
    fun getExtended(type: Int) = t1(Command1.SYSTEM_GET_EXTENDED_PARAM, type)

    fun setVibrator(on: Boolean) = t1(Command1.SYSTEM_SET_PARAM, VIBRATOR, 0x00, on.bit())
    fun setPowerSaving(on: Boolean) = t1(Command1.SYSTEM_SET_PARAM, POWER_SAVING_MODE, 0x00, on.bit())
    fun setPauseWhenRemoved(on: Boolean) = t1(Command1.SYSTEM_SET_PARAM, CONTROL_BY_WEARING, 0x00, on.bit())
    /** ACTIVE_AND_SELECTIME_ID; Sound Connect writes it with SET_PARAM (q00/b). */
    fun setAutoPowerOff(activeId: Int, selectTimeId: Int) =
        t1(Command1.SYSTEM_SET_PARAM, AUTO_POWER_OFF, 0x01, activeId, selectTimeId)
    fun setSpeakToChat(on: Boolean) = t1(Command1.SYSTEM_SET_PARAM, SMART_TALKING_MODE, 0x01, on.bit())
    fun setSpeakToChatDetail(d: SpeakToChatDetail) = t1(
        Command1.SYSTEM_SET_EXTENDED_PARAM, SMART_TALKING_MODE, 0x00, d.sensitivity, d.voiceFocus.bit(), d.modeOutTime,
    )
    /** One AssignableSettingsPreset per key, in the order of the capability's key list. */
    fun setAssignable(presets: List<Int>) =
        t1(Command1.SYSTEM_SET_PARAM, ASSIGNABLE_SETTINGS, presets.size, *presets.toIntArray())
}

/** Table 2: the headset's own paired-source list, multipoint connect/disconnect and pairing mode. */
object Peripheral {
    fun capability() = t2(Command2.PERIPHERAL_GET_CAPABILITY, 0x01)
    fun status() = t2(Command2.PERIPHERAL_GET_STATUS, 0x01)
    fun devices() = t2(Command2.PERIPHERAL_GET_PARAM, 0x01)
    /** INQUIRY_SCAN_MODE(1) makes the headset discoverable for a new source; NORMAL_MODE(0) ends it. */
    fun setPairingMode(on: Boolean) = t2(Command2.PERIPHERAL_SET_STATUS, 0x01, on.bit(), 0x00)
    /** ActionType: 0 disconnect, 1 connect, 2 unpair; the address is sent as its 17-character text. */
    fun action(action: Int, address: String) = Request(
        Table.TWO,
        bytesOf(Command2.PERIPHERAL_SET_EX_PARAM.code, 0x01, 0x00, action) + address.uppercase().toByteArray(Charsets.US_ASCII),
    )
}

/** Table 2 voice guidance (VoiceGuidanceInquiredType 1). DetailedDataType: 1 on/off, 2 language. */
object VoiceGuidance {
    fun capability() = t2(Command2.VOICE_GUIDANCE_GET_CAPABILITY, 0x01)
    fun get() = t2(Command2.VOICE_GUIDANCE_GET_PARAM, 0x01, 0x01)
    fun language() = t2(Command2.VOICE_GUIDANCE_GET_PARAM, 0x01, 0x02)
    fun set(on: Boolean) = t2(Command2.VOICE_GUIDANCE_SET_PARAM, 0x01, 0x01, on.bit())
}
