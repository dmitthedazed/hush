package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.Request

/**
 * The v1 session start, after Sound Connect's initializer (tv/d.java `call()`): a handshake, then requests
 * chosen by what the headset declared. Capabilities come before values so replies arrive in a usable order.
 */
object Init {
    /** Protocol versions Sound Connect accepts (tv/d.java f81747b); others fail with UNAVAILABLE_PROTOCOL_VERSION. */
    val SUPPORTED_VERSIONS = setOf(0x1000, 0x2000, 0x3000, 0x4000, 0x4010, 0x5000, 0x6000, 0x7000, 0x7010)

    /** Sent before anything else; the reply to [Connect.supportFunction] decides the rest. */
    fun handshake(): List<Request> = listOf(
        Connect.protocolInfo(),
        Connect.capabilityInfo(),
        Connect.modelName(),
        Connect.firmwareVersion(),
        Connect.seriesAndColor(),
        Connect.supportFunction(),
    )

    fun plan(version: Int, codes: Set<Int>): List<Request> {
        val f = codes.mapNotNull(FunctionType::of).toSet()
        fun has(t: FunctionType) = t in f
        return buildList {
            // Alerts first: without them the headset refuses changes that drop its links.
            add(Alert.enable())
            if (version >= 0x5000) add(Connect.instructionGuide())

            add(Common.battery(0x00))
            if (has(FunctionType.LEFT_RIGHT_BATTERY_LEVEL)) add(Common.battery(0x01))
            if (has(FunctionType.CRADLE_BATTERY_LEVEL)) add(Common.battery(0x02))
            if (has(FunctionType.LEFT_RIGHT_CONNECTION_STATUS)) add(Common.earbudConnection())
            if (has(FunctionType.CODEC_INDICATOR)) add(Common.codec())
            if (has(FunctionType.UPSCALING)) {
                add(Audio.capability(0x02)); add(Audio.upscaling()); add(Common.upscalingEffect())
            }
            if (has(FunctionType.CONNECTION_MODE)) { add(Audio.capability(0x01)); add(Audio.soundQualityMode()) }

            if (has(FunctionType.VPT)) { add(Vpt.capability(0x01)); add(Vpt.get(0x01)) }
            if (has(FunctionType.SOUND_POSITION)) { add(Vpt.capability(0x02)); add(Vpt.get(0x02)) }
            val eq = when {
                has(FunctionType.PRESET_EQ) -> EqEbb.PRESET_EQ
                has(FunctionType.PRESET_EQ_NONCUSTOMIZABLE) -> EqEbb.PRESET_EQ_NONCUSTOMIZABLE
                else -> null
            }
            eq?.let { add(EqEbb.capability(it)); add(EqEbb.bandInfo(it)); add(EqEbb.get(it)) }
            if (has(FunctionType.EBB)) { add(EqEbb.capability(EqEbb.EBB)); add(EqEbb.get(EqEbb.EBB)) }

            val nc = when {
                has(FunctionType.NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE) -> NcAsmKind.NC_ASM
                has(FunctionType.NOISE_CANCELLING) -> NcAsmKind.NC
                has(FunctionType.AMBIENT_SOUND_MODE) -> NcAsmKind.ASM
                else -> null
            }
            nc?.let { add(NcAsm.capability(it)); add(NcAsm.get(it)) }
            if (has(FunctionType.AUTO_NC_ASM)) add(Sense.capability())
            if (has(FunctionType.NC_OPTIMIZER)) { add(Optimizer.capability()); add(Optimizer.status()); add(Optimizer.result()) }

            if (has(FunctionType.VIBRATOR)) { add(SystemSettings.capability(SystemSettings.VIBRATOR)); add(SystemSettings.get(SystemSettings.VIBRATOR)) }
            if (has(FunctionType.POWER_SAVING_MODE)) add(SystemSettings.get(SystemSettings.POWER_SAVING_MODE))
            if (has(FunctionType.CONTROL_BY_WEARING)) {
                add(SystemSettings.status(SystemSettings.CONTROL_BY_WEARING)); add(SystemSettings.get(SystemSettings.CONTROL_BY_WEARING))
            }
            if (has(FunctionType.AUTO_POWER_OFF)) {
                add(SystemSettings.capability(SystemSettings.AUTO_POWER_OFF)); add(SystemSettings.get(SystemSettings.AUTO_POWER_OFF))
            }
            if (has(FunctionType.SMART_TALKING_MODE)) {
                add(SystemSettings.get(SystemSettings.SMART_TALKING_MODE)); add(SystemSettings.getExtended(SystemSettings.SMART_TALKING_MODE))
            }
            if (has(FunctionType.ASSIGNABLE_SETTINGS)) {
                add(SystemSettings.capability(SystemSettings.ASSIGNABLE_SETTINGS)); add(SystemSettings.get(SystemSettings.ASSIGNABLE_SETTINGS))
            }
            if (has(FunctionType.TRAINING_MODE)) { add(Sports.capability()); add(Sports.get()) }

            for (id in GeneralSettings.ids) if (id in codes) {
                add(GeneralSettings.capability(id)); add(GeneralSettings.status(id)); add(GeneralSettings.get(id))
            }

            if (has(FunctionType.FW_UPDATE)) {
                add(Update.serialNumber())
                // Category, service id, nation, language, then the version-gated ones Sound Connect reads (tv/d.java R()).
                for (type in listOf(0x02, 0x03, 0x04, 0x05)) add(Update.param(type))
                if (version >= 0x2000) { add(Update.param(0x07)); add(Update.param(0x08)) }
                if (version >= 0x4000) add(Update.param(0x09))
                if (version >= 0x5000) { add(Update.param(0x0A)); add(Update.param(0x0B)) }
            }
            if (has(FunctionType.BLE_SETUP)) { add(Common.bluetoothInfo(0x01)); add(Common.bluetoothInfo(0x00)) }

            if (has(FunctionType.PLAYBACK_CONTROLLER)) {
                add(Play.capability()); add(Play.status())
                for (t in listOf(0x20, 0x00, 0x02, 0x01)) add(Play.param(t))
            }
            if (has(FunctionType.VOICE_GUIDANCE)) {
                add(VoiceGuidance.capability()); add(VoiceGuidance.get()); add(VoiceGuidance.language())
            }
            if (has(FunctionType.PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT)) {
                add(Peripheral.capability()); add(Peripheral.status()); add(Peripheral.devices())
            }
        }
    }
}
