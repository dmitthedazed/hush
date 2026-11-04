package com.occaecat.hush.mdr.v2

import com.occaecat.hush.mdr.ListeningModes
import com.occaecat.hush.mdr.MdrProtocol
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.SpeakToChatDetail

/**
 * [MdrProtocol] for one v2 headset: which inquired type each action uses depends on the functions it declared,
 * so this is built per session from [functions]. v2 has no surround (VPT), sound position, power saving or training
 * mode commands Hush knows the layout of, and its SENSE group is a settings transaction rather than v1's "start
 * adapting"; those return null.
 */
class V2Protocol(val functions: V2Functions) : MdrProtocol {
    override val v2 = true

    private val ncType = NcAsmV2.typeFor(functions)
    private val eqType = EqEbb.eqType(functions)
    private val powerOffType = Power.autoPowerOffType(functions)
    private val stcType = SystemSettings.speakToChatType(functions)
    private val assignableType = SystemSettings.assignableType(functions)
    private val connectionModeType = Audio.connectionModeType(functions)
    private val playType = Play.type(functions)
    private val peripheralType = Peripheral.typeFor(functions)
    private val optimizerType = Optimizer.typeFor(functions)
    private val voiceGuidanceType = VoiceGuidance.typeFor(functions)

    override fun ncAsm(s: NcAsmState, dragging: Boolean) = ncType?.let { NcAsm.set(it, s, dragging) }
    override fun senseStart(): Request? = null
    override fun optimizerStart() = optimizerType?.let(Optimizer::start)
    override fun optimizerCancel() = optimizerType?.let(Optimizer::cancel)
    override fun optimizerResult() = optimizerType?.let(Optimizer::result)

    // A non-customizable model only takes preset selections; Hush keeps them in its own inquired type.
    override fun eqPreset(presetId: Int, customizable: Boolean) = eqType?.let { EqEbb.selectPreset(it, presetId) }
    override fun eqBands(presetId: Int, bands: List<Int>) =
        eqType?.takeIf { it != EqEbb.PRESET_EQ_NONCUSTOMIZABLE }?.let { EqEbb.setBands(it, presetId, bands) }
    override fun ebb(level: Int) = if (functions.has(FunctionType1.EBB)) EqEbb.setEbb(level) else null
    override fun vpt(presetId: Int): Request? = null
    override fun soundPosition(presetId: Int): Request? = null
    override fun upscaling(auto: Boolean) = Audio.setUpscaling(auto)
    override fun listeningModes(from: ListeningModes, to: ListeningModes) = Audio.listening(functions, from, to)
    override fun soundQualityMode(preferConnection: Boolean) = connectionModeType?.let { Audio.setConnectionMode(it, preferConnection) }
    override fun readSoundQualityMode() = connectionModeType?.let { Audio.get(it) }
    override fun lowLatencyMode() =
        connectionModeType?.takeIf { it == Audio.CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO }?.let { Audio.setPriorMode(it, 0x02) }

    override fun speakToChat(on: Boolean) = stcType?.let { SystemSettings.setSpeakToChat(it, on) }
    override fun speakToChatDetail(d: SpeakToChatDetail) = stcType?.let { SystemSettings.setSpeakToChatDetail(it, d) }
    override fun pauseWhenRemoved(on: Boolean) = SystemSettings.setOnOff(SystemSettings.PLAYBACK_CONTROL_BY_WEARING, on)
    override fun autoPowerOff(activeId: Int, selectTimeId: Int) = powerOffType?.let { Power.setAutoPowerOff(it, activeId, selectTimeId) }
    override fun vibrator(on: Boolean) = SystemSettings.setOnOff(SystemSettings.VIBRATOR, on)
    override fun powerSaving(on: Boolean): Request? = null
    override fun trainingMode(on: Boolean): Request? = null
    override fun assignable(presets: List<Int>) = assignableType?.let { SystemSettings.setAssignable(it, presets) }
    override fun readAssignable() = assignableType?.let { SystemSettings.get(it) }
    override fun alertAnswer(type: Int, positive: Boolean) = Alert.answer(Alert.fromV1(type), positive)

    override fun general(id: Int, on: Boolean) = GeneralSettings.set(id, on)
    override fun generalIndex(id: Int, index: Int) = GeneralSettings.setIndex(id, index)
    override fun readGeneral(id: Int) = GeneralSettings.get(id)

    override fun volume(v: Int) = Play.setVolume(v)
    override fun playControl(control: Int) = playType?.let { Play.control(it, control) }
    override fun readPlayback() = listOfNotNull(playType?.let(Play::names))

    override fun readPeers() = peripheralType?.let(Peripheral::devices)
    override fun pairingMode(on: Boolean) = peripheralType?.let { Peripheral.setPairingMode(it, on) }
    override fun readPairingMode() = peripheralType?.let(Peripheral::status)
    override fun peerAction(action: Int, address: String) = peripheralType?.let { Peripheral.action(it, action, address) }
    override fun voiceGuidance(on: Boolean, language: Int?) = voiceGuidanceType?.let { VoiceGuidance.set(it, on, language) }
    // Type 1 reports a language but Sony only writes one through type 2 (ap0/k0).
    override fun voiceGuidanceLanguage(on: Boolean, language: Int) =
        voiceGuidanceType?.takeIf { it == VoiceGuidance.SUPPORT_LANGUAGE_SWITCH }?.let { VoiceGuidance.set(it, on, language) }
    override fun maxVolumeOn(on: Boolean) = if (MaxVolume.supported(functions)) MaxVolume.setOn(on) else null
    override fun maxVolumeLevel(level: Int) = if (MaxVolume.supported(functions)) MaxVolume.setLevel(level) else null
    override fun voiceGuidanceVolume(volume: Int, sample: Boolean) =
        VoiceGuidance.volumeTypeFor(functions)?.let { VoiceGuidance.setVolume(it, volume, sample) }

    override fun readBattery(functions: Set<Int>) = Power.batteries(this.functions)
    override fun powerOff() = Power.powerOff()
}
