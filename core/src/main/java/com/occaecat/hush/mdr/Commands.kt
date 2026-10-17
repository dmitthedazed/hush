package com.occaecat.hush.mdr

/**
 * MDR protocol v1, command table 1 (com.sony.songpal.tandemfamily.message.mdr.v1.table1.Command).
 * Every payload starts with the command byte; the second byte is usually an "inquired type".
 */
object Cmd {
    const val CONNECT_GET_PROTOCOL_INFO = 0x00
    const val CONNECT_RET_PROTOCOL_INFO = 0x01
    const val CONNECT_GET_DEVICE_INFO = 0x04
    const val CONNECT_RET_DEVICE_INFO = 0x05

    const val COMMON_GET_BATTERY_LEVEL = 0x10
    const val COMMON_RET_BATTERY_LEVEL = 0x11
    const val COMMON_NTFY_BATTERY_LEVEL = 0x13
    const val COMMON_GET_UPSCALING_EFFECT = 0x14
    const val COMMON_RET_UPSCALING_EFFECT = 0x15
    const val COMMON_NTFY_UPSCALING_EFFECT = 0x17
    const val COMMON_GET_AUDIO_CODEC = 0x18
    const val COMMON_RET_AUDIO_CODEC = 0x19
    const val COMMON_NTFY_AUDIO_CODEC = 0x1B
    const val COMMON_SET_POWER_OFF = 0x22

    const val EQEBB_GET_CAPABILITY = 0x50
    const val EQEBB_RET_CAPABILITY = 0x51
    const val EQEBB_GET_PARAM = 0x56
    const val EQEBB_RET_PARAM = 0x57
    const val EQEBB_SET_PARAM = 0x58
    const val EQEBB_NTFY_PARAM = 0x59

    const val NCASM_GET_PARAM = 0x66
    const val NCASM_RET_PARAM = 0x67
    const val NCASM_SET_PARAM = 0x68
    const val NCASM_NTFY_PARAM = 0x69

    const val AUDIO_GET_PARAM = 0xE6
    const val AUDIO_RET_PARAM = 0xE7
    const val AUDIO_SET_PARAM = 0xE8
    const val AUDIO_NTFY_PARAM = 0xE9

    const val ALERT_SET_STATUS = 0x94
    const val ALERT_SET_PARAM = 0x98
    const val ALERT_NTFY_PARAM = 0x99

    const val OPT_GET_STATUS = 0x82
    const val OPT_RET_STATUS = 0x83
    const val OPT_SET_STATUS = 0x84
    const val OPT_NTFY_STATUS = 0x85
    const val OPT_GET_PARAM = 0x86
    const val OPT_RET_PARAM = 0x87
    const val OPT_NTFY_PARAM = 0x89

    const val GS_GET_CAPABILITY = 0xD0
    const val GS_RET_CAPABILITY = 0xD1
    const val GS_GET_STATUS = 0xD2
    const val GS_RET_STATUS = 0xD3
    const val GS_NTFY_STATUS = 0xD5
    const val GS_GET_PARAM = 0xD6
    const val GS_RET_PARAM = 0xD7
    const val GS_SET_PARAM = 0xD8
    const val GS_NTFY_PARAM = 0xD9

    const val SYSTEM_GET_CAPABILITY = 0xF0
    const val SYSTEM_RET_CAPABILITY = 0xF1
    const val SYSTEM_GET_STATUS = 0xF2
    const val SYSTEM_RET_STATUS = 0xF3
    const val SYSTEM_NTFY_STATUS = 0xF5
    const val SYSTEM_GET_PARAM = 0xF6
    const val SYSTEM_RET_PARAM = 0xF7
    const val SYSTEM_SET_PARAM = 0xF8
    const val SYSTEM_NTFY_PARAM = 0xF9
    const val SYSTEM_GET_EXT_PARAM = 0xFA
    const val SYSTEM_RET_EXT_PARAM = 0xFB
    const val SYSTEM_SET_EXT_PARAM = 0xFC
    const val SYSTEM_NTFY_EXT_PARAM = 0xFD
}

/** MDR protocol v1, command table 2 (sent as DATA_MDR_NO2 frames; opcodes overlap table 1). */
object Cmd2 {
    // Peripheral: the headset's own list of paired sources, multipoint connect/disconnect, pairing mode
    // (an0/*, q30/a.java + q30/b.java; PeripheralInquiredType PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT = 1).
    const val PERIPHERAL_GET_STATUS = 0x32
    const val PERIPHERAL_RET_STATUS = 0x33
    const val PERIPHERAL_SET_STATUS = 0x34
    const val PERIPHERAL_NTFY_STATUS = 0x35
    const val PERIPHERAL_GET_PARAM = 0x36
    const val PERIPHERAL_RET_PARAM = 0x37
    const val PERIPHERAL_NTFY_PARAM = 0x39
    const val PERIPHERAL_SET_EX_PARAM = 0x3C
    const val PERIPHERAL_NTFY_EX_PARAM = 0x3D
    const val VOICE_GUIDANCE_GET_PARAM = 0x46
    const val VOICE_GUIDANCE_RET_PARAM = 0x47
    const val VOICE_GUIDANCE_SET_PARAM = 0x48
    const val VOICE_GUIDANCE_NTFY_PARAM = 0x49
}

/**
 * AlertMessageType: the headset asks the app to confirm changes that drop its links (tm0/o0, vm0/u).
 * Without alerts enabled (ALERT_SET_STATUS) it silently refuses those changes.
 */
object AlertType {
    const val CONNECTION_MODE_CHANGE = 0x01
    const val KEY_ASSIGN_CHANGE = 0x02
    const val FIRMWARE_UPDATE_DISCONNECT = 0x03
    const val GOOGLE_ASSISTANT_AVAILABLE = 0x04
    const val DUAL_VOICE_ASSISTANT_UNAVAILABLE = 0x05
    const val MULTIPOINT_ON = 0x06
    const val MULTIPOINT_OFF = 0x07
    const val BATTERY_EQ_AND_UPSCALING = 0x08
}

/** SystemInquiredType (+ type 3, handled by the KMP module as control-by-wearing). */
object SystemType {
    const val CONTROL_BY_WEARING = 0x03
    const val AUTO_POWER_OFF = 0x04
    const val SMART_TALKING_MODE = 0x05
    const val ASSIGNABLE_SETTINGS = 0x06
}

/** General settings are self-describing: the headset names each one (GsTitleTitle enum names). */
data class GeneralSetting(val id: Int, val title: String, val isBoolean: Boolean)

data class SpeakToChatDetail(val sensitivity: Int, val voiceFocus: Boolean, val modeOutTime: Int)

data class OptimizerResult(val personalMeasured: Boolean?, val pressureTenthsAtm: Int?)

enum class AmbientMode { NOISE_CANCELLING, AMBIENT, OFF }

/** NC/ASM state as the "NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE" (0x02) struct, vm0.a0. */
data class NcAsmState(
    val mode: AmbientMode,
    val ambientLevel: Int,
    val voiceFocus: Boolean,
    // Raw setting-type bytes as reported by the device; echoed back on SET so we match its capability.
    val ncSettingType: Int = 0x02,
    val ncOnValue: Int = 0x02,
    val asmSettingType: Int = 0x01,
)

data class EqPreset(val id: Int, val name: String)

data class EqState(val presetId: Int, val bands: List<Int>)

data class EqCapability(val bandCount: Int, val levelSteps: Int, val presets: List<EqPreset>)

enum class Codec(val code: Int, val label: String) {
    UNSETTLED(0x00, "—"), SBC(0x01, "SBC"), AAC(0x02, "AAC"), LDAC(0x10, "LDAC"),
    APTX(0x20, "aptX"), APTX_HD(0x21, "aptX HD"), OTHER(0xFF, "Other");

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

/** A source device the headset has paired with; [order] > 0 means connected (1 = first connected). */
data class PeerDevice(val address: String, val name: String, val order: Int) {
    val connected get() = order > 0
}

/** FunctionType codes from CONNECT_RET_SUPPORT_FUNCTION that the UI cares about. */
object Function {
    const val PAIRING_DEVICE_MANAGEMENT = 0x38
    const val VOICE_GUIDANCE = 0x39
    const val PLAYBACK_CONTROLLER = 0xA1
    const val POWER_SAVING_MODE = 0xF2
}

sealed interface MdrEvent {
    /** PLAYBACK_CONTROLLER: the headset's own view of the source's player (tm0 k2/l2). */
    data class PlayVolume(val volume: Int) : MdrEvent
    /** ModelSeries and ModelColor (0 default, 1 black, 2 white, 3 silver, 5 blue …, Sony's ModelColor). */
    data class SeriesAndColor(val series: Int, val color: Int) : MdrEvent
    /** GuidanceCategory codes of the model's instruction guide (0x30 touch panel, 0x50 Quick Attention, 0x60 CUSTOM button …). */
    data class GuideCategories(val codes: List<Int>) : MdrEvent
    data class SerialNumber(val serial: String) : MdrEvent
    /** A firmware-update parameter (UpdateInquiredType → value as text; one-byte values as decimal). */
    data class UpdateParam(val type: Int, val value: String) : MdrEvent
    /** [notify]: sent by the headset on its own (0xA9), not an answer to a request. */
    data class PlayName(val type: Int, val text: String?, val notify: Boolean = false) : MdrEvent
    /** PlaybackStatus: 1 play, 2 pause, 3 stop. */
    data class PlayStatus(val status: Int) : MdrEvent
    data class PlayCapability(val volumeSteps: Int) : MdrEvent
    data class SupportedFunctions(val codes: Set<Int>) : MdrEvent
    /** Headset's paired-source list; [playbackOrder] is the [PeerDevice.order] that holds the audio right now. */
    data class Peers(val devices: List<PeerDevice>, val playbackOrder: Int) : MdrEvent
    data class PairingMode(val on: Boolean) : MdrEvent
    /** ResultType of a connect/disconnect/unpair (0x00..0x33) for [address]. */
    data class PeerResult(val result: Int, val address: String) : MdrEvent
    data class ProtocolInfo(val version: Int) : MdrEvent
    data class ModelName(val name: String) : MdrEvent
    data class FirmwareVersion(val version: String) : MdrEvent
    data class Battery(val level: Int, val charging: Boolean) : MdrEvent
    data class AudioCodec(val codec: Codec) : MdrEvent
    data class UpscalingStatus(val active: Boolean) : MdrEvent
    data class UpscalingSetting(val auto: Boolean) : MdrEvent
    data class NcAsm(val state: NcAsmState) : MdrEvent
    data class EqCaps(val caps: EqCapability) : MdrEvent
    data class Eq(val state: EqState) : MdrEvent
    data class SpeakToChat(val enabled: Boolean) : MdrEvent
    data class SpeakToChatDetails(val detail: SpeakToChatDetail) : MdrEvent
    data class WearingStatus(val available: Boolean) : MdrEvent
    data class PauseWhenRemoved(val on: Boolean) : MdrEvent
    data class AutoPowerOffOptions(val ids: List<Int>) : MdrEvent
    data class AutoPowerOff(val activeId: Int, val selectTimeId: Int) : MdrEvent
    data class CustomButton(val presets: List<Int>) : MdrEvent
    data class SoundQualityMode(val preferConnection: Boolean) : MdrEvent
    data class GsCapability(val setting: GeneralSetting) : MdrEvent
    data class GsValue(val id: Int, val on: Boolean) : MdrEvent
    /** [actionType] 0 = confirmation only, 1 = positive/negative. */
    data class Alert(val type: Int, val actionType: Int) : MdrEvent
    /** ProtocolStatus: ENABLE(0) means the setting can currently be changed. */
    data class GsStatus(val id: Int, val enabled: Boolean) : MdrEvent
    data class OptimizerState(val running: Boolean, val status: Int) : MdrEvent
    data class Optimizer(val result: OptimizerResult) : MdrEvent
    data class VoiceGuidance(val on: Boolean) : MdrEvent
    data class VoiceGuidanceLanguage(val language: Int) : MdrEvent
    data class Unknown(val payload: ByteArray) : MdrEvent
}

object Mdr {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // ---- requests ----
    fun getProtocolInfo() = bytes(Cmd.CONNECT_GET_PROTOCOL_INFO, 0x00)
    fun getSupportFunction() = bytes(0x06, 0x00)

    // PLAYBACK_CONTROLLER (PlayInquiredType 1): track info, play state, the headset's volume.
    fun getPlayCapability() = bytes(0xA0, 0x01)
    fun getPlayStatus() = bytes(0xA2, 0x01)
    /** PlaybackDetailedDataType: 0 track, 1 album, 2 artist, 0x20 volume. */
    fun getPlayParam(type: Int) = bytes(0xA6, 0x01, type)
    fun setVolume(v: Int) = bytes(0xA8, 0x01, 0x20, v)
    /** PlaybackControl: 1 pause, 2 next, 3 previous, 7 play. */
    fun playControl(control: Int) = bytes(0xA4, 0x01, 0x00, control)

    // Table 2 peripheral requests.
    fun getPeerStatus() = bytes(Cmd2.PERIPHERAL_GET_STATUS, 0x01)
    fun getPeers() = bytes(Cmd2.PERIPHERAL_GET_PARAM, 0x01)
    /** INQUIRY_SCAN_MODE(1) makes the headset discoverable for a new source; NORMAL_MODE(0) ends it. */
    fun setPairingMode(on: Boolean) = bytes(Cmd2.PERIPHERAL_SET_STATUS, 0x01, if (on) 0x01 else 0x00, 0x00)
    /** ActionType: 0 disconnect, 1 connect, 2 unpair; the address is sent as its 17-character text. */
    fun peerAction(action: Int, address: String) =
        bytes(Cmd2.PERIPHERAL_SET_EX_PARAM, 0x01, 0x00, action) + address.uppercase().toByteArray(Charsets.US_ASCII)
    fun getModelName() = bytes(Cmd.CONNECT_GET_DEVICE_INFO, 0x01)
    fun getFirmwareVersion() = bytes(Cmd.CONNECT_GET_DEVICE_INFO, 0x02)
    /** DeviceInfoInquiredType SERIES_AND_COLOR_INFO: [05, 03, series, ModelColor]. */
    fun getSeriesAndColor() = bytes(Cmd.CONNECT_GET_DEVICE_INFO, 0x03)
    /** DeviceInfoInquiredType INSTRUCTION_GUIDE: [05, 04, n, GuidanceCategory×n] — the tutorial pages this model has. */
    fun getInstructionGuide() = bytes(Cmd.CONNECT_GET_DEVICE_INFO, 0x04)
    /** Table 1 UPDT_GET_PARAM (0x36) SERIAL_NUMBER: [37, 06, len, ASCII] — Sound Connect reads it for firmware updates (n20/a). */
    fun getSerialNumber() = bytes(0x36, 0x06)
    /**
     * Table 1 UPDT_GET_PARAM for any UpdateInquiredType: 2 category, 3 service id, 4 nation code, 5 language,
     * 6 serial, 11 device-binding id are length-prefixed text; 7 BLE TX power (signed dBm), 8 / 10 battery
     * thresholds (%), 9 UpdateMethod are one byte (tm0/x2, vm0/t0..y0).
     */
    fun getUpdateParam(type: Int) = bytes(0x36, type)
    fun getBattery() = bytes(Cmd.COMMON_GET_BATTERY_LEVEL, 0x00)
    fun getCodec() = bytes(Cmd.COMMON_GET_AUDIO_CODEC, 0x00)
    fun getUpscalingEffect() = bytes(Cmd.COMMON_GET_UPSCALING_EFFECT, 0x00)
    fun getUpscalingSetting() = bytes(Cmd.AUDIO_GET_PARAM, 0x02)
    fun getNcAsm() = bytes(Cmd.NCASM_GET_PARAM, 0x02)
    fun getEqCapability() = bytes(Cmd.EQEBB_GET_CAPABILITY, 0x01, 0x01 /* English names */)
    fun getEq() = bytes(Cmd.EQEBB_GET_PARAM, 0x01)
    fun getSpeakToChat() = bytes(Cmd.SYSTEM_GET_PARAM, 0x05)

    fun getSpeakToChatDetail() = bytes(Cmd.SYSTEM_GET_EXT_PARAM, SystemType.SMART_TALKING_MODE)
    fun getWearingStatus() = bytes(Cmd.SYSTEM_GET_STATUS, SystemType.CONTROL_BY_WEARING)
    fun getPauseWhenRemoved() = bytes(Cmd.SYSTEM_GET_PARAM, SystemType.CONTROL_BY_WEARING)
    fun getAutoPowerOffCapability() = bytes(Cmd.SYSTEM_GET_CAPABILITY, SystemType.AUTO_POWER_OFF)
    fun getAutoPowerOff() = bytes(Cmd.SYSTEM_GET_PARAM, SystemType.AUTO_POWER_OFF)
    fun getCustomButton() = bytes(Cmd.SYSTEM_GET_PARAM, SystemType.ASSIGNABLE_SETTINGS)
    fun getSoundQualityMode() = bytes(Cmd.AUDIO_GET_PARAM, 0x01)
    fun getGsCapability(id: Int) = bytes(Cmd.GS_GET_CAPABILITY, id, 0x01 /* English */)
    fun getGs(id: Int) = bytes(Cmd.GS_GET_PARAM, id)
    fun getGsStatus(id: Int) = bytes(Cmd.GS_GET_STATUS, id)
    fun getOptimizerStatus() = bytes(Cmd.OPT_GET_STATUS, 0x01)
    fun getOptimizerResult() = bytes(Cmd.OPT_GET_PARAM, 0x01)

    // table 2
    fun getVoiceGuidance() = bytes(Cmd2.VOICE_GUIDANCE_GET_PARAM, 0x01, 0x01)
    fun getVoiceGuidanceLanguage() = bytes(Cmd2.VOICE_GUIDANCE_GET_PARAM, 0x01, 0x02)
    fun setVoiceGuidance(on: Boolean) = bytes(Cmd2.VOICE_GUIDANCE_SET_PARAM, 0x01, 0x01, if (on) 1 else 0)

    fun setSpeakToChatDetail(d: SpeakToChatDetail) = bytes(
        Cmd.SYSTEM_SET_EXT_PARAM, SystemType.SMART_TALKING_MODE, 0x00,
        d.sensitivity, if (d.voiceFocus) 1 else 0, d.modeOutTime,
    )
    fun setPauseWhenRemoved(on: Boolean) = bytes(Cmd.SYSTEM_SET_PARAM, SystemType.CONTROL_BY_WEARING, 0x00, if (on) 1 else 0)
    fun setAutoPowerOff(activeId: Int, selectTimeId: Int) =
        bytes(Cmd.SYSTEM_SET_PARAM, SystemType.AUTO_POWER_OFF, 0x01, activeId, selectTimeId)
    fun setCustomButton(preset: Int) = bytes(Cmd.SYSTEM_SET_PARAM, SystemType.ASSIGNABLE_SETTINGS, 0x01, preset)
    fun setSoundQualityMode(preferConnection: Boolean) =
        bytes(Cmd.AUDIO_SET_PARAM, 0x01, 0x00, if (preferConnection) 1 else 0)
    fun setGs(id: Int, on: Boolean) = bytes(Cmd.GS_SET_PARAM, id, 0x01 /* BOOLEAN_TYPE */, if (on) 1 else 0)
    fun startOptimizer() = bytes(Cmd.OPT_SET_STATUS, 0x01, 0x00, 0x01)
    fun cancelOptimizer() = bytes(Cmd.OPT_SET_STATUS, 0x01, 0x00, 0x00)

    fun enableAlerts() = bytes(Cmd.ALERT_SET_STATUS, 0x01 /* FIXED_MESSAGE */, 0x00 /* ENABLE */)
    fun answerAlert(type: Int, positive: Boolean) = bytes(Cmd.ALERT_SET_PARAM, 0x01, type, if (positive) 1 else 0)

    fun powerOff() = bytes(Cmd.COMMON_SET_POWER_OFF, 0x00, 0x01)

    /**
     * NCASM_SET_PARAM with struct 0x02. The leading effect byte is NcAsmEffect:
     * 0x10 while a slider is being dragged, 0x11 once the adjustment is complete, 0x00 when everything is off.
     */
    fun setNcAsm(s: NcAsmState, dragging: Boolean = false): ByteArray {
        val effect = when {
            s.mode == AmbientMode.OFF -> 0x00
            dragging -> 0x10
            else -> 0x11
        }
        val ncValue = if (s.mode == AmbientMode.NOISE_CANCELLING) s.ncOnValue else 0x00
        val asmLevel = if (s.mode == AmbientMode.AMBIENT) s.ambientLevel else 0
        return bytes(
            Cmd.NCASM_SET_PARAM, 0x02, effect, s.ncSettingType, ncValue,
            s.asmSettingType, if (s.voiceFocus) 0x01 else 0x00, asmLevel,
        )
    }

    fun setEqPreset(presetId: Int) = bytes(Cmd.EQEBB_SET_PARAM, 0x01, presetId, 0x00)

    fun setEqBands(presetId: Int, bands: List<Int>) =
        bytes(Cmd.EQEBB_SET_PARAM, 0x01, presetId, bands.size, *bands.toIntArray())

    fun setUpscaling(auto: Boolean) = bytes(Cmd.AUDIO_SET_PARAM, 0x02, 0x00, if (auto) 0x01 else 0x00)

    fun setSpeakToChat(on: Boolean) = bytes(Cmd.SYSTEM_SET_PARAM, 0x05, 0x01, if (on) 0x01 else 0x00)

    // ---- responses / notifications ----
    fun parse(p: ByteArray, table2: Boolean = false): MdrEvent {
        if (p.isEmpty()) return MdrEvent.Unknown(p)
        if (table2) return parseTable2(p)
        val u = { i: Int -> p[i].toInt() and 0xFF }
        return runCatching {
            when (u(0)) {
                Cmd.CONNECT_RET_PROTOCOL_INFO -> MdrEvent.ProtocolInfo((u(2) shl 8) or u(3))
                0xA1 -> MdrEvent.PlayCapability(u(2))
                0xA3, 0xA5 -> MdrEvent.PlayStatus(u(3))
                0xA7, 0xA9 -> when (u(2)) {
                    0x20 -> MdrEvent.PlayVolume(u(3))
                    // The "track changed" notify is just `a9 01 00`, without the text.
                    0x00, 0x01, 0x02 -> MdrEvent.PlayName(u(2), if (p.size > 4 && u(3) == 0x02 && u(4) > 0) String(p, 5, minOf(u(4), p.size - 5, 128), Charsets.UTF_8) else null, notify = u(0) == 0xA9)
                    else -> MdrEvent.Unknown(p)
                }
                0x37 -> when (u(1)) {
                    0x06 -> if (p.size > 3) MdrEvent.SerialNumber(String(p, 3, minOf(u(2), 128, p.size - 3), Charsets.US_ASCII).trim()) else MdrEvent.Unknown(p)
                    0x02, 0x03, 0x04, 0x05, 0x0B -> MdrEvent.UpdateParam(u(1), String(p, 3, minOf(u(2), 128, p.size - 3), Charsets.US_ASCII).trim())
                    0x07 -> MdrEvent.UpdateParam(7, p[2].toInt().toString()) // signed dBm
                    0x08, 0x09, 0x0A -> MdrEvent.UpdateParam(u(1), u(2).toString())
                    else -> MdrEvent.Unknown(p)
                }
                0x07 -> MdrEvent.SupportedFunctions((0 until u(2)).map { u(3 + it) }.toSet())
                Cmd.CONNECT_RET_DEVICE_INFO -> {
                    val text = { String(p, 3, minOf(u(2), 128, p.size - 3), Charsets.UTF_8) }
                    when (u(1)) {
                        0x01 -> MdrEvent.ModelName(text())
                        0x02 -> MdrEvent.FirmwareVersion(text())
                        0x03 -> MdrEvent.SeriesAndColor(u(2), u(3))
                        0x04 -> MdrEvent.GuideCategories(List(minOf(u(2), p.size - 3)) { u(3 + it) })
                        else -> MdrEvent.Unknown(p)
                    }
                }
                Cmd.COMMON_RET_BATTERY_LEVEL, Cmd.COMMON_NTFY_BATTERY_LEVEL ->
                    if (u(1) == 0x00) MdrEvent.Battery(u(2), u(3) == 0x01) else MdrEvent.Unknown(p)
                Cmd.COMMON_RET_AUDIO_CODEC, Cmd.COMMON_NTFY_AUDIO_CODEC -> MdrEvent.AudioCodec(Codec.of(u(2)))
                Cmd.COMMON_RET_UPSCALING_EFFECT, Cmd.COMMON_NTFY_UPSCALING_EFFECT ->
                    MdrEvent.UpscalingStatus(u(3) == 0x01)
                Cmd.AUDIO_RET_PARAM, Cmd.AUDIO_NTFY_PARAM -> when (u(1)) {
                    0x01 -> MdrEvent.SoundQualityMode(u(3) == 0x01)
                    0x02 -> MdrEvent.UpscalingSetting(u(3) == 0x01)
                    else -> MdrEvent.Unknown(p)
                }
                Cmd.NCASM_RET_PARAM, Cmd.NCASM_NTFY_PARAM -> parseNcAsm(p)
                Cmd.EQEBB_RET_CAPABILITY -> parseEqCaps(p)
                Cmd.EQEBB_RET_PARAM, Cmd.EQEBB_NTFY_PARAM -> {
                    val n = u(3)
                    MdrEvent.Eq(EqState(u(2), List(n) { u(4 + it) }))
                }
                Cmd.SYSTEM_RET_PARAM, Cmd.SYSTEM_NTFY_PARAM -> when (u(1)) {
                    // [cmd, 0x03, ON_OFF(0), value]
                    SystemType.CONTROL_BY_WEARING -> MdrEvent.PauseWhenRemoved(u(3) == 0x01)
                    // [cmd, 0x04, ACTIVE_AND_SELECTIME_ID(1), activeId, selectTimeId]
                    SystemType.AUTO_POWER_OFF -> MdrEvent.AutoPowerOff(u(3), u(4))
                    // RET: [cmd, 0x05, ON_OFF(0), value]; NTFY: [cmd, 0x05, MODE_ON_OFF(1) | PREVIEW_MODE_ON_OFF(2), value]
                    // (ym0/c, ym0/d). Turning it on from the headset sends MODE=on then PREVIEW=off; the preview flag must
                    // not be read as the setting itself.
                    SystemType.SMART_TALKING_MODE -> if (u(2) == 0x02) MdrEvent.Unknown(p) else MdrEvent.SpeakToChat(u(3) == 0x01)
                    // [cmd, 0x06, count, preset...]
                    SystemType.ASSIGNABLE_SETTINGS -> MdrEvent.CustomButton(List(u(2)) { u(3 + it) })
                    else -> MdrEvent.Unknown(p)
                }
                Cmd.SYSTEM_RET_STATUS, Cmd.SYSTEM_NTFY_STATUS ->
                    if (u(1) == SystemType.CONTROL_BY_WEARING) MdrEvent.WearingStatus(u(2) == 0x00) else MdrEvent.Unknown(p)
                Cmd.SYSTEM_RET_CAPABILITY ->
                    if (u(1) == SystemType.AUTO_POWER_OFF) MdrEvent.AutoPowerOffOptions(List(u(2)) { u(3 + it) })
                    else MdrEvent.Unknown(p)
                Cmd.SYSTEM_RET_EXT_PARAM, Cmd.SYSTEM_NTFY_EXT_PARAM ->
                    // [cmd, 0x05, TYPE_1(0), sensitivity, voiceFocus, modeOutTime]
                    if (u(1) == SystemType.SMART_TALKING_MODE && u(2) == 0x00)
                        MdrEvent.SpeakToChatDetails(SpeakToChatDetail(u(3), u(4) == 0x01, u(5)))
                    else MdrEvent.Unknown(p)
                Cmd.ALERT_NTFY_PARAM ->
                    if (u(1) == 0x01) MdrEvent.Alert(u(2), u(3)) else MdrEvent.Unknown(p)
                Cmd.GS_RET_CAPABILITY -> parseGsCapability(p)
                Cmd.GS_RET_STATUS, Cmd.GS_NTFY_STATUS -> MdrEvent.GsStatus(u(1), u(2) == 0x00)
                Cmd.GS_RET_PARAM, Cmd.GS_NTFY_PARAM ->
                    if (u(2) == 0x01) MdrEvent.GsValue(u(1), u(3) == 0x01) else MdrEvent.Unknown(p)
                Cmd.OPT_RET_STATUS, Cmd.OPT_NTFY_STATUS ->
                    // [cmd, NC_OPTIMIZER, CommonStatus, OptimizerStatus]
                    if (u(1) == 0x01) MdrEvent.OptimizerState(u(3) in 0x01..0x10, u(3)) else MdrEvent.Unknown(p)
                Cmd.OPT_RET_PARAM, Cmd.OPT_NTFY_PARAM ->
                    // [cmd, NC_OPTIMIZER, personalType, personalValue, barometricType, barometricValue]
                    if (u(1) == 0x01) MdrEvent.Optimizer(
                        OptimizerResult(
                            personalMeasured = if (u(2) == 0x01) u(3) == 0x01 else null,
                            pressureTenthsAtm = if (u(4) == 0x01 && u(5) != 0) u(5) else null,
                        )
                    ) else MdrEvent.Unknown(p)
                else -> MdrEvent.Unknown(p)
            }
        }.getOrElse { MdrEvent.Unknown(p) }
    }

    private fun parseTable2(p: ByteArray): MdrEvent {
        val u = { i: Int -> p[i].toInt() and 0xFF }
        return runCatching {
            when (u(0)) {
                // [cmd, 01, n, (address[17], order, nameLen, name)×n, playbackOrder] (an0/o, an0/h)
                Cmd2.PERIPHERAL_RET_PARAM, Cmd2.PERIPHERAL_NTFY_PARAM -> if (u(1) != 0x01) MdrEvent.Unknown(p) else {
                    var i = 3
                    val list = List(u(2)) {
                        val addr = String(p, i, 17, Charsets.US_ASCII)
                        val order = u(i + 17)
                        val len = u(i + 18)
                        val name = String(p, i + 19, len, Charsets.UTF_8)
                        i += 19 + len
                        PeerDevice(addr, name, order)
                    }
                    MdrEvent.Peers(list, if (i < p.size) u(i) else 0)
                }
                // [cmd, 01, BluetoothModeStatus, CommonStatus]
                Cmd2.PERIPHERAL_RET_STATUS, Cmd2.PERIPHERAL_NTFY_STATUS ->
                    if (u(1) == 0x01) MdrEvent.PairingMode(u(2) == 0x01) else MdrEvent.Unknown(p)
                // [cmd, 01, CONNECTION_CONTROL(0), ResultType, address[17]]
                Cmd2.PERIPHERAL_NTFY_EX_PARAM ->
                    if (u(1) == 0x01 && p.size >= 21) MdrEvent.PeerResult(u(3), String(p, 4, 17, Charsets.US_ASCII)) else MdrEvent.Unknown(p)
                // [cmd, VOICE_GUIDANCE_SETTING(1), DetailedDataType, value]
                Cmd2.VOICE_GUIDANCE_RET_PARAM, Cmd2.VOICE_GUIDANCE_NTFY_PARAM -> when (u(2)) {
                    0x01 -> MdrEvent.VoiceGuidance(u(3) == 0x01)
                    0x02 -> MdrEvent.VoiceGuidanceLanguage(u(3))
                    else -> MdrEvent.Unknown(p)
                }
                else -> MdrEvent.Unknown(p)
            }
        }.getOrElse { MdrEvent.Unknown(p) }
    }

    /** [cmd, gsId, strFormat, titleLen, title, subLen, sub, settingType, ...] (um0.f / xm0.d). */
    private fun parseGsCapability(p: ByteArray): MdrEvent {
        val u = { i: Int -> p[i].toInt() and 0xFF }
        val id = u(1)
        val titleLen = u(3).let { if (it > 128) 0 else it }
        val title = String(p, 4, titleLen, Charsets.UTF_8)
        val subLen = u(4 + titleLen).let { if (it > 128) 0 else it }
        val type = u(5 + titleLen + subLen)
        return MdrEvent.GsCapability(GeneralSetting(id, title, type == 0x01))
    }

    private fun parseNcAsm(p: ByteArray): MdrEvent {
        val u = { i: Int -> p[i].toInt() and 0xFF }
        if (u(1) != 0x02) return MdrEvent.Unknown(p)
        // [cmd, 0x02, effect, ncSettingType, ncValue, asmSettingType, asmId, asmLevel]
        val effectOn = u(2) != 0x00
        val ncValue = u(4)
        val asmLevel = u(7)
        val mode = when {
            !effectOn -> AmbientMode.OFF
            ncValue != 0x00 -> AmbientMode.NOISE_CANCELLING
            asmLevel == 0 -> AmbientMode.OFF
            else -> AmbientMode.AMBIENT
        }
        return MdrEvent.NcAsm(
            NcAsmState(
                mode = mode,
                ambientLevel = asmLevel,
                voiceFocus = u(6) == 0x01,
                ncSettingType = u(3),
                ncOnValue = if (ncValue != 0) ncValue else if (u(3) == 0x00) 0x01 else 0x02,
                asmSettingType = u(5),
            )
        )
    }

    private fun parseEqCaps(p: ByteArray): MdrEvent {
        val u = { i: Int -> p[i].toInt() and 0xFF }
        if (u(1) != 0x01 && u(1) != 0x02) return MdrEvent.Unknown(p)
        val bandCount = u(2)
        val steps = u(3)
        val count = u(4)
        val presets = mutableListOf<EqPreset>()
        var i = 5
        repeat(count) {
            val id = u(i)
            val len = u(i + 1).let { if (it > 128) 0 else it }
            presets += EqPreset(id, String(p, i + 2, len, Charsets.UTF_8))
            i += len + 2
        }
        return MdrEvent.EqCaps(EqCapability(bandCount, steps, presets))
    }
}
