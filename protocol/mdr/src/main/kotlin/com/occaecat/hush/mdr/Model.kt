package com.occaecat.hush.mdr

/**
 * Headset state as Hush models it, independent of the protocol generation that carried it.
 * Struct names in the comments point at Sound Connect's v1 payloads (vm0.*, tm0.*).
 */

/** A general setting names itself (GsTitleTitle enum names); a list one also names its choices. */
data class GeneralSetting(
    val id: Int,
    val title: String,
    val isBoolean: Boolean,
    /** LIST_TYPE: the headset's names for its choices, by index. */
    val options: List<String> = emptyList(),
)

data class SpeakToChatDetail(val sensitivity: Int, val voiceFocus: Boolean, val modeOutTime: Int)

data class OptimizerResult(val personalMeasured: Boolean?, val pressureTenthsAtm: Int?)

enum class AmbientMode { NOISE_CANCELLING, AMBIENT, OFF }

/**
 * Which NcAsmInquiredType carries noise control: NC on/off only (WH-CH700N-like), NC and ambient sound
 * together (most models), or ambient sound only (WI-C/WI-SP-like).
 */
enum class NcAsmKind(val code: Int) { NC(0x01), NC_ASM(0x02), ASM(0x03) }

/** NC/ASM state; for [NcAsmKind.NC_ASM] the struct is vm0.a0. */
data class NcAsmState(
    val mode: AmbientMode,
    val ambientLevel: Int,
    val voiceFocus: Boolean,
    // Raw setting-type bytes as reported by the device; echoed back on SET so we match its capability.
    val ncSettingType: Int = 0x02,
    /** NC value sent when NC is on: DUAL (2) or SINGLE (1, wind noise reduction) for DUAL_SINGLE_OFF, ON (1) otherwise. */
    val ncOnValue: Int = 0x02,
    val asmSettingType: Int = 0x01,
    val kind: NcAsmKind = NcAsmKind.NC_ASM,
    /** v2 only: bytes after the fields Hush models (noise adaptation), echoed back unchanged on SET. */
    val extra: List<Int> = emptyList(),
) {
    /** NcAsmSettingType DUAL_SINGLE_OFF with the single-mic value: Sound Connect's "Wind Noise Reduction". */
    val windReduction get() = ncSettingType == NC_DUAL_SINGLE_OFF && ncOnValue == 0x01

    companion object {
        const val NC_DUAL_SINGLE_OFF = 0x02
    }
}

/** NCASM_RET_CAPABILITY: what the noise control above can do. */
data class NcAsmCapability(
    val kind: NcAsmKind,
    val ncSettingType: Int?,
    val asmSettingType: Int?,
    /** Steps of the NORMAL ambient level (20 on most models); 1 for an on/off ambient mode. */
    val asmSteps: Int,
    /** AsmId VOICE is offered: "Focus on voice". */
    val voice: Boolean,
) {
    val hasNc get() = kind != NcAsmKind.ASM
    val hasAsm get() = kind != NcAsmKind.NC
    val asmLevels get() = asmSettingType == 0x01
    /** DUAL_SINGLE_OFF with a level slider: NC, Wind Noise Reduction and the ambient levels (NC_MODE_SWITCH_ASM_SEAMLESS). */
    val windReduction get() = ncSettingType == NcAsmState.NC_DUAL_SINGLE_OFF && asmSettingType == 0x01
}

data class EqPreset(val id: Int, val name: String)

data class EqState(val presetId: Int, val bands: List<Int>)

data class EqCapability(val bandCount: Int, val levelSteps: Int, val presets: List<EqPreset>)

/** EQEBB_RET_EXTENDED_INFO: per band, EqBandInformationType (1 Hz, 2 kHz, 0x10 specific, e.g. Clear Bass) and value. */
data class EqBand(val infoType: Int, val value: Int) {
    val hz: Int? get() = when (infoType) { 0x01 -> value; 0x02 -> value * 1000; else -> null }
}

/** Electronic Bass Booster (EQEBB type 2): a single level within [range]. */
data class Ebb(val level: Int, val range: IntRange)

enum class Codec(val code: Int, val label: String) {
    UNSETTLED(0x00, "—"), SBC(0x01, "SBC"), AAC(0x02, "AAC"), LDAC(0x10, "LDAC"),
    APTX(0x20, "aptX"), APTX_HD(0x21, "aptX HD"), OTHER(0xFF, "Other");

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

/** One battery: the headset, an earbud or the case. BatteryChargingStatus UNKNOWN (0xF0) counts as not charging. */
data class Charge(val level: Int, val charging: Boolean)

/** A source device the headset has paired with; [order] > 0 means connected (1 = first connected). */
data class PeerDevice(val address: String, val name: String, val order: Int) {
    val connected get() = order > 0
}

/** VPT_RET_CAPABILITY VPT: the headset's surround presets (VptPresetId, name). */
data class VptPreset(val id: Int, val name: String)

/**
 * One assignable key (SYSTEM_RET_CAPABILITY ASSIGNABLE_SETTINGS, AsCapabilityKey): AssignableSettingsKey
 * (0 left, 1 right, 2 CUSTOM, 3 C), its type (0 touch sensor, 1 button) and the presets it can take.
 */
data class AssignableKey(val key: Int, val keyType: Int, val defaultPreset: Int, val presets: List<Int>)

/** NC optimizer capability (OPT_RET_CAPABILITY): how long it runs and which measurements it makes. */
data class OptimizerCapability(val seconds: Int, val personal: Boolean, val barometric: Boolean)

/** Table 2 voice guidance capability: whether it can be switched off and which VoiceGuidanceLanguage it knows. */
data class VoiceGuidanceCapability(val onOff: Boolean, val language: Boolean, val languages: List<Int>)

/**
 * v2 listening modes (AUDIO group). Each is its own on/off on the headset, but Sound Connect offers them as one
 * "Listening mode" choice, so only one is on at a time. Null fields: the headset does not have that mode.
 */
data class ListeningModes(
    /** Background Music Effect, and its RoomSize: 0 my room, 1 living room, 2 cafe. */
    val bgm: Boolean? = null,
    val roomSize: Int = 0,
    /** 360 Upmix for cinema (UPMIX_CINEMA). */
    val cinema: Boolean? = null,
    /** UPMIX_SERIES UpmixItemId: 0 none, 1 cinema, 2 game, 3 music. */
    val upmix: Int? = null,
    /** Voice boost (VOICE_CONTENTS). */
    val voiceBoost: Boolean? = null,
    val leakageReduction: Boolean? = null,
) {
    val current: ListeningMode get() = when {
        bgm == true -> ListeningMode.BGM
        cinema == true -> ListeningMode.CINEMA
        upmix == 1 -> ListeningMode.CINEMA
        upmix == 2 -> ListeningMode.GAME
        upmix == 3 -> ListeningMode.MUSIC
        voiceBoost == true -> ListeningMode.VOICE_BOOST
        leakageReduction == true -> ListeningMode.LEAKAGE_REDUCTION
        else -> ListeningMode.STANDARD
    }

    val available: List<ListeningMode> get() = buildList {
        add(ListeningMode.STANDARD)
        if (bgm != null) add(ListeningMode.BGM)
        if (cinema != null || upmix != null) add(ListeningMode.CINEMA)
        if (upmix != null) { add(ListeningMode.GAME); add(ListeningMode.MUSIC) }
        if (voiceBoost != null) add(ListeningMode.VOICE_BOOST)
        if (leakageReduction != null) add(ListeningMode.LEAKAGE_REDUCTION)
    }

    /** This state with only [mode] on. */
    fun with(mode: ListeningMode) = copy(
        bgm = bgm?.let { mode == ListeningMode.BGM },
        cinema = cinema?.let { mode == ListeningMode.CINEMA && upmix == null },
        upmix = upmix?.let { when (mode) { ListeningMode.CINEMA -> 1; ListeningMode.GAME -> 2; ListeningMode.MUSIC -> 3; else -> 0 } },
        voiceBoost = voiceBoost?.let { mode == ListeningMode.VOICE_BOOST },
        leakageReduction = leakageReduction?.let { mode == ListeningMode.LEAKAGE_REDUCTION },
    )
}

enum class ListeningMode { STANDARD, BGM, CINEMA, GAME, MUSIC, VOICE_BOOST, LEAKAGE_REDUCTION }
