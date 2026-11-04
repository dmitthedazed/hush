package com.occaecat.hush.ui

import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.AssignableKey
import com.occaecat.hush.mdr.Charge
import com.occaecat.hush.mdr.Codec
import com.occaecat.hush.mdr.Ebb
import com.occaecat.hush.mdr.EqBand
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.mdr.GeneralSetting
import com.occaecat.hush.mdr.NcAsmCapability
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.OptimizerResult
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.VptPreset

/** A fully populated, connected WH-1000XM4 for layout checks (see MainActivity's demo extra). */
val demoState = HeadphonesState(
    phase = Phase.CONNECTED,
    address = "AC:80:0A:00:00:00",
    deviceName = "WH-1000XM4",
    modelName = "WH-1000XM4",
    firmware = "2.6.0",
    protocolVersion = 0x7000,
    functions = HeadphonesController.XM4_FUNCTIONS,
    battery = 80,
    codec = Codec.LDAC,
    dseeAuto = true,
    dseeActive = true,
    ncAsm = NcAsmState(AmbientMode.AMBIENT, ambientLevel = 14, voiceFocus = true),
    eq = EqState(presetId = 0xA0, bands = listOf(10, 13, 11, 9, 10, 12)),
    speakToChat = true,
    speakToChatDetail = SpeakToChatDetail(sensitivity = 0, voiceFocus = true, modeOutTime = 1),
    pauseWhenRemoved = true,
    autoPowerOff = 0x11 to 0,
    autoPowerOffOptions = listOf(0x10, 0x11),
    customButton = 0x00,
    soundQualityPreferConnection = false,
    generalSettings = mapOf(
        1 to GeneralSetting(1, "MULTIPOINT_SETTING", true),
        2 to GeneralSetting(2, "TOUCH_SENSOR_CONTROL_PANEL", true),
    ),
    generalValues = mapOf(1 to false, 2 to true),
    optimizerResult = OptimizerResult(personalMeasured = true, pressureTenthsAtm = 10),
    voiceGuidance = true,
    voiceGuidanceLanguage = 1,
)

/**
 * Other MDR v1 models for the demo (`--es demo WH-1000XM3`). The WH-1000XM3 is what its real capture reports
 * (core/src/test/resources/mdr/WH-1000XM3-4.5.2); the others are plausible capability sets for their kind of model:
 * earbuds with a case, an NC earbud with a bass booster, a neckband without noise control.
 */
val demoStates: Map<String, HeadphonesState> = mapOf(
    "WH-1000XM4" to demoState,
    "WH-1000XM3" to demoState.copy(
        deviceName = "WH-1000XM3", modelName = "WH-1000XM3", modelColor = 0x01, firmware = "4.5.2", protocolVersion = 0x4010,
        functions = setOf(0x11, 0xC1, 0x12, 0x13, 0x14, 0x30, 0x71, 0x62, 0x81, 0x42, 0x41, 0x51, 0xA1, 0xE1, 0xE2, 0xD1, 0xD2, 0xF4, 0x39),
        upscalingType = 0x00,
        ncAsmCaps = NcAsmCapability(NcAsmKind.NC_ASM, ncSettingType = 0x02, asmSettingType = 0x01, asmSteps = 20, voice = true),
        ncAsm = NcAsmState(AmbientMode.NOISE_CANCELLING, ambientLevel = 14, voiceFocus = false, ncOnValue = 0x01),
        eqBands = listOf(EqBand(0x10, 1), EqBand(0x01, 400), EqBand(0x01, 1000), EqBand(0x01, 2500), EqBand(0x01, 6300), EqBand(0x01, 15662)),
        vptPresets = listOf(0x00, 0x01, 0x02, 0x03, 0x04).map { VptPreset(it, "") }, vpt = 0x00,
        soundPositionType = 0x01, soundPosition = 0x00,
        speakToChat = null, speakToChatDetail = null, pauseWhenRemoved = null, customButton = null,
        autoPowerOffOptions = listOf(0x00, 0x01, 0x02, 0x03, 0x11), autoPowerOff = 0x11 to 0x00,
        generalSettings = mapOf(
            0xD1 to GeneralSetting(0xD1, "ASSIGNABLE_KEY_SETTING", false,
                listOf("ASSIGNABLE_KEY_ELEM_NCASM", "ASSIGNABLE_KEY_ELEM_GOOGLE_ASSISTANT", "ASSIGNABLE_KEY_ELEM_AMAZON_ALEXA")),
            0xD2 to GeneralSetting(0xD2, "TOUCH_PANEL_SETTING", true),
        ),
        generalValues = mapOf(0xD2 to true), generalListValues = mapOf(0xD1 to 0),
    ),
    "WF-1000XM3" to demoState.copy(
        deviceName = "WF-1000XM3", modelName = "WF-1000XM3", modelColor = 0x01, firmware = "4.0.2", protocolVersion = 0x5000,
        functions = setOf(0x13, 0x15, 0x17, 0x18, 0x21, 0x30, 0x39, 0x51, 0x62, 0x81, 0xA1, 0xE2, 0xF3, 0xF4, 0xF6),
        battery = null, leftBattery = Charge(70, false), rightBattery = Charge(65, false), caseBattery = Charge(40, true),
        earbudsConnected = true to true, upscalingType = 0x00, codec = Codec.AAC,
        ncAsmCaps = NcAsmCapability(NcAsmKind.NC_ASM, ncSettingType = 0x00, asmSettingType = 0x01, asmSteps = 20, voice = true),
        ncAsm = NcAsmState(AmbientMode.AMBIENT, ambientLevel = 12, voiceFocus = true, ncSettingType = 0x00, ncOnValue = 0x01),
        speakToChat = null, speakToChatDetail = null, customButton = 0x00,
        assignableKeys = listOf(
            AssignableKey(0x00, 0x00, 0x00, listOf(0x00, 0x10, 0x20, 0x30, 0x31, 0x32, 0xFF)),
            AssignableKey(0x01, 0x00, 0x20, listOf(0x00, 0x10, 0x20, 0x30, 0x31, 0x32, 0xFF)),
        ),
        assignable = listOf(0x00, 0x20), soundQualityPreferConnection = null,
        generalSettings = emptyMap(), generalValues = emptyMap(),
    ),
    "WF-SP800N" to demoState.copy(
        deviceName = "WF-SP800N", modelName = "WF-SP800N", modelColor = 0x02, firmware = "1.0.1", protocolVersion = 0x7000,
        functions = setOf(0x13, 0x15, 0x17, 0x18, 0x21, 0x30, 0x39, 0x51, 0x52, 0x62, 0xA1, 0xF3, 0xF4, 0xF6),
        battery = null, leftBattery = Charge(90, false), rightBattery = Charge(85, false), caseBattery = Charge(100, false),
        earbudsConnected = true to false, dseeAuto = null, codec = Codec.AAC,
        ncAsmCaps = NcAsmCapability(NcAsmKind.NC_ASM, ncSettingType = 0x00, asmSettingType = 0x01, asmSteps = 20, voice = true),
        ncAsm = NcAsmState(AmbientMode.NOISE_CANCELLING, ambientLevel = 10, voiceFocus = false, ncSettingType = 0x00, ncOnValue = 0x01),
        ebb = Ebb(3, -10..10),
        speakToChat = null, speakToChatDetail = null, customButton = 0x00,
        assignableKeys = listOf(AssignableKey(0x00, 0x01, 0x00, listOf(0x00, 0x10, 0x20)), AssignableKey(0x01, 0x01, 0x20, listOf(0x00, 0x10, 0x20))),
        assignable = listOf(0x00, 0x20), soundQualityPreferConnection = null,
        generalSettings = emptyMap(), generalValues = emptyMap(), optimizerResult = null,
    ),
    "WI-C100" to demoState.copy(
        deviceName = "WI-C100", modelName = "WI-C100", modelColor = 0x01, firmware = "1.1.0", protocolVersion = 0x7010,
        functions = setOf(0x21, 0x30, 0x39, 0x51, 0xA1, 0xE2, 0xF4),
        battery = 60, codec = Codec.AAC, upscalingType = 0x01, dseeActive = false,
        ncAsm = null, ncAsmCaps = null, eq = EqState(presetId = 0x10, bands = listOf(10, 12, 11, 10, 11, 13)),
        speakToChat = null, speakToChatDetail = null, pauseWhenRemoved = null, customButton = null,
        soundQualityPreferConnection = null, generalSettings = emptyMap(), generalValues = emptyMap(), optimizerResult = null,
        powerSaving = false,
    ),
)
