package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.ListeningModes
import com.occaecat.hush.mdr.MdrProtocol
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.Request
import com.occaecat.hush.mdr.SpeakToChatDetail

/** [MdrProtocol] over the v1 builders in Requests.kt; what the controller sent before v2 existed. */
object V1Protocol : MdrProtocol {
    override val v2 = false

    override fun ncAsm(s: NcAsmState, dragging: Boolean) = NcAsm.set(s, dragging)
    override fun senseStart() = Sense.start()
    override fun optimizerStart() = Optimizer.start()
    override fun optimizerCancel() = Optimizer.cancel()
    override fun optimizerResult() = Optimizer.result()

    override fun eqPreset(presetId: Int, customizable: Boolean) =
        EqEbb.selectPreset(presetId, if (customizable) EqEbb.PRESET_EQ else EqEbb.PRESET_EQ_NONCUSTOMIZABLE)
    override fun eqBands(presetId: Int, bands: List<Int>) = EqEbb.setBands(presetId, bands)
    override fun ebb(level: Int) = EqEbb.setEbb(level)
    override fun vpt(presetId: Int) = Vpt.setSurround(presetId)
    override fun soundPosition(presetId: Int) = Vpt.setSoundPosition(presetId)
    override fun upscaling(auto: Boolean) = Audio.setUpscaling(auto)
    override fun soundQualityMode(preferConnection: Boolean) = Audio.setSoundQualityMode(preferConnection)
    override fun readSoundQualityMode() = Audio.soundQualityMode()

    override fun speakToChat(on: Boolean) = SystemSettings.setSpeakToChat(on)
    override fun speakToChatDetail(d: SpeakToChatDetail) = SystemSettings.setSpeakToChatDetail(d)
    override fun pauseWhenRemoved(on: Boolean) = SystemSettings.setPauseWhenRemoved(on)
    override fun autoPowerOff(activeId: Int, selectTimeId: Int) = SystemSettings.setAutoPowerOff(activeId, selectTimeId)
    override fun vibrator(on: Boolean) = SystemSettings.setVibrator(on)
    override fun powerSaving(on: Boolean) = SystemSettings.setPowerSaving(on)
    override fun trainingMode(on: Boolean) = Sports.set(on)
    override fun assignable(presets: List<Int>) = SystemSettings.setAssignable(presets)
    override fun readAssignable() = SystemSettings.get(SystemSettings.ASSIGNABLE_SETTINGS)
    override fun alertAnswer(type: Int, positive: Boolean) = Alert.answer(type, positive)

    override fun general(id: Int, on: Boolean) = GeneralSettings.set(id, on)
    override fun generalIndex(id: Int, index: Int) = GeneralSettings.setIndex(id, index)
    override fun readGeneral(id: Int) = GeneralSettings.get(id)

    override fun volume(v: Int) = Play.setVolume(v)
    override fun playControl(control: Int) = Play.control(control)
    override fun readPlayback() = listOf(0x00, 0x02, 0x01).map(Play::param)

    override fun readPeers() = Peripheral.devices()
    override fun pairingMode(on: Boolean) = Peripheral.setPairingMode(on)
    override fun readPairingMode() = Peripheral.status()
    override fun peerAction(action: Int, address: String) = Peripheral.action(action, address)
    override fun voiceGuidance(on: Boolean, language: Int?) = VoiceGuidance.set(on)
    override fun lowLatencyMode(): Request? = null
    override fun listeningModes(from: ListeningModes, to: ListeningModes) = emptyList<Request>()
    override fun maxVolumeOn(on: Boolean): Request? = null
    override fun maxVolumeLevel(level: Int): Request? = null
    override fun voiceGuidanceLanguage(on: Boolean, language: Int): Request? = null
    override fun voiceGuidanceVolume(volume: Int, sample: Boolean): Request? = null

    override fun readBattery(functions: Set<Int>) = buildList {
        add(Common.battery(0x00))
        if (FunctionType.LEFT_RIGHT_BATTERY_LEVEL.code in functions) add(Common.battery(0x01))
        if (FunctionType.CRADLE_BATTERY_LEVEL.code in functions) add(Common.battery(0x02))
    }
    override fun powerOff() = Common.powerOff()
}
