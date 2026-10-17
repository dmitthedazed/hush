package com.occaecat.hush.ui

import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.Codec
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.mdr.GeneralSetting
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.OptimizerResult
import com.occaecat.hush.mdr.SpeakToChatDetail

/** A fully populated, connected WH-1000XM4 for layout checks (see MainActivity's demo extra). */
val demoState = HeadphonesState(
    phase = Phase.CONNECTED,
    address = "00:00:00:00:00:00",
    deviceName = "WH-1000XM4",
    modelName = "WH-1000XM4",
    firmware = "2.6.0",
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
