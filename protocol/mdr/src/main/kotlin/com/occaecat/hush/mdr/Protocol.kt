package com.occaecat.hush.mdr

/**
 * The requests behind each user action, in one protocol generation's encoding. The controller holds the one its
 * session speaks; null (or an empty list) means that generation has no such command, and nothing is sent.
 * Values are always in Hush's v1-shaped model ([NcAsmState], v1 AlertMessageType codes …); a v2 implementation
 * translates them.
 */
interface MdrProtocol {
    val v2: Boolean

    fun ncAsm(s: NcAsmState, dragging: Boolean): Request?
    fun senseStart(): Request?
    fun optimizerStart(): Request?
    fun optimizerCancel(): Request?
    fun optimizerResult(): Request?

    fun eqPreset(presetId: Int, customizable: Boolean): Request?
    fun eqBands(presetId: Int, bands: List<Int>): Request?
    fun ebb(level: Int): Request?
    fun vpt(presetId: Int): Request?
    fun soundPosition(presetId: Int): Request?
    fun upscaling(auto: Boolean): Request?
    /** The writes that take the headset from [from] to [to] (switch the old mode off first, then the new one on). */
    fun listeningModes(from: ListeningModes, to: ListeningModes): List<Request>
    fun soundQualityMode(preferConnection: Boolean): Request?
    fun readSoundQualityMode(): Request?
    /** v2 "LE Audio (low latency)" connection priority; null when the headset has no such mode. */
    fun lowLatencyMode(): Request?

    fun speakToChat(on: Boolean): Request?
    fun speakToChatDetail(d: SpeakToChatDetail): Request?
    fun pauseWhenRemoved(on: Boolean): Request?
    fun autoPowerOff(activeId: Int, selectTimeId: Int): Request?
    fun vibrator(on: Boolean): Request?
    fun powerSaving(on: Boolean): Request?
    fun trainingMode(on: Boolean): Request?
    fun assignable(presets: List<Int>): Request?
    fun readAssignable(): Request?
    /** [type] is a v1 AlertMessageType code (see [com.occaecat.hush.mdr.v1.Alert]). */
    fun alertAnswer(type: Int, positive: Boolean): Request?

    fun general(id: Int, on: Boolean): Request?
    fun generalIndex(id: Int, index: Int): Request?
    fun readGeneral(id: Int): Request?

    fun volume(v: Int): Request?
    /** PlaybackControl: 1 pause, 2 next, 3 previous, 7 play (the same codes in both generations). */
    fun playControl(control: Int): Request?
    /** Track, artist and album again. */
    fun readPlayback(): List<Request>

    fun readPeers(): Request?
    fun pairingMode(on: Boolean): Request?
    fun readPairingMode(): Request?
    /** ActionType: 0 disconnect, 1 connect, 2 unpair. */
    fun peerAction(action: Int, address: String): Request?
    /** [language] is the current one; a v2 language-switch headset needs it in every write. */
    fun voiceGuidance(on: Boolean, language: Int?): Request?
    /** Null when the prompt language cannot be changed from the phone. */
    fun voiceGuidanceLanguage(on: Boolean, language: Int): Request?
    fun maxVolumeOn(on: Boolean): Request?
    /** 0..4. */
    fun maxVolumeLevel(level: Int): Request?
    /** −2..+2; [sample] plays a prompt at the new volume. */
    fun voiceGuidanceVolume(volume: Int, sample: Boolean): Request?

    /** [functions]: the session's (v1-equivalent) FunctionType codes. */
    fun readBattery(functions: Set<Int>): List<Request>
    fun powerOff(): Request?
}
