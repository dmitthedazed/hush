package com.occaecat.hush.mdr.v1

/**
 * What a v1 headset says it can do (CONNECT_RET_SUPPORT_FUNCTION, v1/table1/param/FunctionType). Everything
 * Hush asks for after the handshake follows from this set, as in Sound Connect's initializer (tv/d.java).
 */
enum class FunctionType(val code: Int) {
    CODEC_INDICATOR(0x13),
    BLE_SETUP(0x14),
    LEFT_RIGHT_BATTERY_LEVEL(0x15),
    LEFT_RIGHT_CONNECTION_STATUS(0x17),
    CRADLE_BATTERY_LEVEL(0x18),
    POWER_OFF(0x21),
    CONCIERGE_DATA(0x22),
    TANDEM_KEEP_ALIVE(0x23),
    FW_UPDATE(0x30),
    PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT(0x38),
    VOICE_GUIDANCE(0x39),
    VPT(0x41),
    SOUND_POSITION(0x42),
    PRESET_EQ(0x51),
    EBB(0x52),
    PRESET_EQ_NONCUSTOMIZABLE(0x53),
    NOISE_CANCELLING(0x61),
    NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE(0x62),
    AMBIENT_SOUND_MODE(0x63),
    AUTO_NC_ASM(0x71),
    NC_OPTIMIZER(0x81),
    VIBRATOR_ALERT_NOTIFICATION(0x92),
    PLAYBACK_CONTROLLER(0xA1),
    TRAINING_MODE(0xB1),
    ACTION_LOG_NOTIFIER(0xC1),
    GENERAL_SETTING1(0xD1),
    GENERAL_SETTING2(0xD2),
    GENERAL_SETTING3(0xD3),
    CONNECTION_MODE(0xE1),
    UPSCALING(0xE2),
    VIBRATOR(0xF1),
    POWER_SAVING_MODE(0xF2),
    CONTROL_BY_WEARING(0xF3),
    AUTO_POWER_OFF(0xF4),
    SMART_TALKING_MODE(0xF5),
    ASSIGNABLE_SETTINGS(0xF6);

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun of(code: Int): FunctionType? = byCode[code]
    }
}
