package com.occaecat.hush.mdr

/** What a headset said, decoded. A protocol generation's parser turns its payloads into these. */
sealed interface MdrEvent {
    // ---- connect ----
    /**
     * CONNECT_RET_PROTOCOL_INFO; [v2] when the reply has the 8-byte v2 layout. The v1 parser leaves [version] at 0
     * for such a reply; the v2 parser reads it (e.g. 0x03001004) and whether the headset has each command table.
     */
    data class ProtocolInfo(
        val version: Int,
        val v2: Boolean = false,
        val table1: Boolean = true,
        val table2: Boolean = false,
    ) : MdrEvent
    /** CONNECT_RET_CAPABILITY_INFO: a counter that changes with the capability set, and the device's unique id. */
    data class CapabilityInfo(val counter: Int, val uniqueId: String) : MdrEvent
    data class ModelName(val name: String) : MdrEvent
    data class FirmwareVersion(val version: String) : MdrEvent
    /** ModelSeries and ModelColor (0 default, 1 black, 2 white, 3 silver, 5 blue …, Sony's ModelColor). */
    data class SeriesAndColor(val series: Int, val color: Int) : MdrEvent
    /** GuidanceCategory codes of the model's instruction guide (0x30 touch panel, 0x50 Quick Attention, 0x60 CUSTOM button …). */
    data class GuideCategories(val codes: List<Int>) : MdrEvent
    /** FunctionType codes; v2 headsets send one list per command [table], with codes that overlap between them. */
    data class SupportedFunctions(val codes: Set<Int>, val table: Table = Table.ONE) : MdrEvent

    // ---- common ----
    data class Battery(val level: Int, val charging: Boolean) : MdrEvent
    data class EarbudBatteries(val left: Charge, val right: Charge) : MdrEvent
    data class CaseBattery(val charge: Charge) : MdrEvent
    data class EarbudsConnected(val left: Boolean, val right: Boolean) : MdrEvent
    data class AudioCodec(val codec: Codec) : MdrEvent
    data class UpscalingStatus(val active: Boolean) : MdrEvent
    /** COMMON_RET_BLUETOOTH_DEVICE_INFO: BluetoothDeviceInfoType 0 address, 1 BLE hash. */
    data class BluetoothInfo(val type: Int, val value: String) : MdrEvent
    data class SerialNumber(val serial: String) : MdrEvent
    /** A firmware-update parameter (UpdateInquiredType → value as text; one-byte values as decimal). */
    data class UpdateParam(val type: Int, val value: String) : MdrEvent

    // ---- sound ----
    data class VptCaps(val presets: List<VptPreset>) : MdrEvent
    data class Vpt(val presetId: Int) : MdrEvent
    data class SoundPositionCaps(val type: Int) : MdrEvent
    data class SoundPosition(val presetId: Int) : MdrEvent
    /** [customizable] false for PRESET_EQ_NONCUSTOMIZABLE: presets can be picked, bands not edited. */
    data class EqCaps(val caps: EqCapability, val customizable: Boolean = true) : MdrEvent
    data class Eq(val state: EqState) : MdrEvent
    data class EqBands(val bands: List<EqBand>) : MdrEvent
    data class EbbCaps(val range: IntRange) : MdrEvent
    data class EbbLevel(val level: Int) : MdrEvent
    data class NcAsmCaps(val caps: NcAsmCapability) : MdrEvent
    data class NcAsm(val state: NcAsmState) : MdrEvent
    /** SENSE_RET_CAPABILITY: the headset adapts NC/ASM itself (SenseTableType). */
    data class SenseCaps(val tableType: Int) : MdrEvent
    data class OptimizerCaps(val caps: OptimizerCapability) : MdrEvent
    data class OptimizerState(val running: Boolean, val status: Int) : MdrEvent
    data class Optimizer(val result: OptimizerResult) : MdrEvent
    /** [lowLatency]: v2 LE Audio priority (LOW_LATENCY_PRIOR_BETA). */
    data class SoundQualityMode(val preferConnection: Boolean, val lowLatency: Boolean = false) : MdrEvent
    data class UpscalingSetting(val auto: Boolean) : MdrEvent
    /** AUDIO_RET_CAPABILITY UPSCALING: UpscalingType 0 DSEE HX, 1 DSEE, 2 DSEE HX AI (Extreme). */
    data class UpscalingCaps(val type: Int) : MdrEvent

    // ---- system ----
    data class Vibrator(val on: Boolean) : MdrEvent
    data class PowerSaving(val on: Boolean) : MdrEvent
    data class SpeakToChat(val enabled: Boolean) : MdrEvent
    data class SpeakToChatDetails(val detail: SpeakToChatDetail) : MdrEvent
    data class WearingStatus(val available: Boolean) : MdrEvent
    data class PauseWhenRemoved(val on: Boolean) : MdrEvent
    data class AutoPowerOffOptions(val ids: List<Int>) : MdrEvent
    data class AutoPowerOff(val activeId: Int, val selectTimeId: Int) : MdrEvent
    data class AssignableCaps(val keys: List<AssignableKey>) : MdrEvent
    /** The preset of each assignable key, in the order of [AssignableCaps.keys]. */
    data class CustomButton(val presets: List<Int>) : MdrEvent
    data class GsCapability(val setting: GeneralSetting) : MdrEvent
    data class GsValue(val id: Int, val on: Boolean) : MdrEvent
    data class GsListValue(val id: Int, val index: Int) : MdrEvent
    /** ProtocolStatus: ENABLE(0) means the setting can currently be changed. */
    data class GsStatus(val id: Int, val enabled: Boolean) : MdrEvent
    /** [actionType] 0 = confirmation only, 1 = positive/negative. */
    data class Alert(val type: Int, val actionType: Int) : MdrEvent
    data class TrainingModeCaps(val effectType: Int) : MdrEvent
    data class TrainingMode(val on: Boolean) : MdrEvent

    // ---- playback controller ----
    data class PlayCapability(val volumeSteps: Int) : MdrEvent
    /** PlaybackStatus: 1 play, 2 pause, 3 stop. */
    data class PlayStatus(val status: Int) : MdrEvent
    /** PLAYBACK_CONTROLLER: the headset's own view of the source's player (tm0 k2/l2). */
    data class PlayVolume(val volume: Int) : MdrEvent
    /** [notify]: sent by the headset on its own (0xA9), not an answer to a request. */
    data class PlayName(val type: Int, val text: String?, val notify: Boolean = false) : MdrEvent

    // ---- table 2 ----
    /** Headset's paired-source list; [playbackOrder] is the [PeerDevice.order] that holds the audio right now. */
    data class Peers(val devices: List<PeerDevice>, val playbackOrder: Int) : MdrEvent
    data class PeerCaps(val maxPaired: Int, val maxConnected: Int) : MdrEvent
    data class PairingMode(val on: Boolean) : MdrEvent
    /** ResultType of a connect/disconnect/unpair (0x00..0x33) for [address]. */
    data class PeerResult(val result: Int, val address: String) : MdrEvent
    data class VoiceGuidanceCaps(val caps: VoiceGuidanceCapability) : MdrEvent
    data class VoiceGuidance(val on: Boolean) : MdrEvent
    data class VoiceGuidanceLanguage(val language: Int) : MdrEvent
    /** One field of [ListeningModes] (v2 AUDIO); [update] fills it in. */
    data class Listening(val update: (ListeningModes) -> ListeningModes) : MdrEvent
    /** Max Volume Control (v2 safe listening): whether the limit is on, and the limit 0..4. */
    data class MaxVolumeOn(val on: Boolean) : MdrEvent
    data class MaxVolumeLevel(val level: Int) : MdrEvent
    /** Prompt volume, −2..+2 (v2 only). */
    data class VoiceGuidanceVolume(val volume: Int) : MdrEvent

    /** One payload that carries several things (v2 sends track, album and artist in one reply). */
    data class Several(val events: List<MdrEvent>) : MdrEvent

    /** Recognised and deliberately ignored (statuses, logs, acks of our own writes). */
    data class Ignored(val command: String) : MdrEvent
    data class Unknown(val payload: ByteArray) : MdrEvent {
        override fun equals(other: Any?) = other is Unknown && other.payload.contentEquals(payload)
        override fun hashCode() = payload.contentHashCode()
    }
}
