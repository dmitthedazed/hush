package com.occaecat.hush.mdr.v1

import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.AssignableKey
import com.occaecat.hush.mdr.Charge
import com.occaecat.hush.mdr.Codec
import com.occaecat.hush.mdr.EqBand
import com.occaecat.hush.mdr.EqCapability
import com.occaecat.hush.mdr.EqPreset
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.mdr.GeneralSetting
import com.occaecat.hush.mdr.MdrEvent
import com.occaecat.hush.mdr.NcAsmCapability
import com.occaecat.hush.mdr.NcAsmKind
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.mdr.OptimizerCapability
import com.occaecat.hush.mdr.OptimizerResult
import com.occaecat.hush.mdr.PeerDevice
import com.occaecat.hush.mdr.Reader
import com.occaecat.hush.mdr.SpeakToChatDetail
import com.occaecat.hush.mdr.Table
import com.occaecat.hush.mdr.VoiceGuidanceCapability
import com.occaecat.hush.mdr.VptPreset

/**
 * Decodes MDR v1 payloads (RET replies and NTFY notifications) into [MdrEvent]s. Layouts follow Sound Connect's
 * tm0.* payloads and vm0.* structs; a payload that is too short or unexpected becomes [MdrEvent.Unknown].
 */
object MdrV1 {
    fun parse(table: Table, p: ByteArray): MdrEvent {
        if (p.isEmpty()) return MdrEvent.Unknown(p)
        return runCatching {
            val r = Reader(p, 1)
            when (table) {
                Table.ONE -> Command1.of(p[0].toInt() and 0xFF)?.let { table1(it, r, p) }
                Table.TWO -> Command2.of(p[0].toInt() and 0xFF)?.let { table2(it, r) }
            }
        }.getOrNull() ?: MdrEvent.Unknown(p)
    }

    private fun ignored(c: Enum<*>) = MdrEvent.Ignored(c.name)

    /** OptimizerInquiredType 2 is NC_MUSIC_OPTIMIZER, which Hush does not drive; anything else is malformed. */
    private fun optOther(c: Command1, p: ByteArray) = if (p[1].toInt() == 0x02) ignored(c) else null

    private fun table1(c: Command1, r: Reader, p: ByteArray): MdrEvent? = when (c) {
        Command1.CONNECT_RET_PROTOCOL_INFO ->
            // v1: [01, 00, version16]; v2 headsets answer with eight bytes (Gadgetbridge tells them apart the same way).
            if (p.size == 8) MdrEvent.ProtocolInfo(0, v2 = true) else { r.u8(); MdrEvent.ProtocolInfo(r.u16()) }
        Command1.CONNECT_RET_CAPABILITY_INFO -> { r.u8(); MdrEvent.CapabilityInfo(r.u8(), r.str()) }
        Command1.CONNECT_RET_DEVICE_INFO -> when (r.u8()) {
            0x01 -> MdrEvent.ModelName(r.str())
            0x02 -> MdrEvent.FirmwareVersion(r.str())
            0x03 -> MdrEvent.SeriesAndColor(r.u8(), r.u8())
            0x04 -> MdrEvent.GuideCategories(r.list { u8() })
            else -> null
        }
        Command1.CONNECT_RET_SUPPORT_FUNCTION -> { r.u8(); MdrEvent.SupportedFunctions(r.list { u8() }.toSet()) }

        // [cmd, BatteryInquiredType, …]: one level + charging; left, charging, right, charging (vm0/x); the case.
        Command1.COMMON_RET_BATTERY_LEVEL, Command1.COMMON_NTFY_BATTERY_LEVEL -> when (r.u8()) {
            0x00 -> MdrEvent.Battery(r.u8(), r.u8() == 0x01)
            0x01 -> MdrEvent.EarbudBatteries(r.charge(), r.charge())
            0x02 -> MdrEvent.CaseBattery(r.charge())
            else -> null
        }
        Command1.COMMON_RET_UPSCALING_EFFECT, Command1.COMMON_NTFY_UPSCALING_EFFECT -> { r.pos = 3; MdrEvent.UpscalingStatus(r.bool()) }
        Command1.COMMON_RET_AUDIO_CODEC, Command1.COMMON_NTFY_AUDIO_CODEC -> { r.u8(); MdrEvent.AudioCodec(Codec.of(r.u8())) }
        Command1.COMMON_RET_BLUETOOTH_DEVICE_INFO -> MdrEvent.BluetoothInfo(r.u8(), r.str())
        Command1.COMMON_RET_CONNECTION_STATUS, Command1.COMMON_NTFY_CONNECTION_STATUS ->
            if (r.u8() == 0x01) MdrEvent.EarbudsConnected(r.bool(), r.bool()) else null
        Command1.COMMON_RET_CONCIERGE_DATA, Command1.COMMON_NTFY_LINK_CONTROL, Command1.UPDT_NTFY_STATUS -> ignored(c)

        Command1.UPDT_RET_PARAM -> when (val type = r.u8()) {
            0x06 -> MdrEvent.SerialNumber(r.str().trim())
            0x02, 0x03, 0x04, 0x05, 0x0B -> MdrEvent.UpdateParam(type, r.str().trim())
            0x07 -> MdrEvent.UpdateParam(type, r.s8().toString()) // signed dBm
            0x08, 0x09, 0x0A -> MdrEvent.UpdateParam(type, r.u8().toString())
            else -> null
        }

        // VPT: [cmd, 1, n, (VptPresetId, name)×n]; sound position: [cmd, 2, SoundPositionType].
        Command1.VPT_RET_CAPABILITY -> when (r.u8()) {
            0x01 -> MdrEvent.VptCaps(r.list { VptPreset(u8(), str()) })
            0x02 -> MdrEvent.SoundPositionCaps(r.u8())
            else -> null
        }
        Command1.VPT_RET_PARAM, Command1.VPT_NTFY_PARAM -> when (r.u8()) {
            0x01 -> MdrEvent.Vpt(r.u8())
            0x02 -> MdrEvent.SoundPosition(r.u8())
            else -> null
        }
        Command1.VPT_RET_STATUS, Command1.VPT_NTFY_STATUS -> ignored(c)

        // EQ: [cmd, 1|3, bands, steps, n, (EqPresetId, name)×n]; EBB: [cmd, 2, min, max] (signed, tm0/z1).
        Command1.EQEBB_RET_CAPABILITY -> when (val type = r.u8()) {
            EqEbb.PRESET_EQ, EqEbb.PRESET_EQ_NONCUSTOMIZABLE ->
                MdrEvent.EqCaps(EqCapability(r.u8(), r.u8(), r.list { EqPreset(u8(), str()) }), customizable = type == EqEbb.PRESET_EQ)
            EqEbb.EBB -> MdrEvent.EbbCaps(r.s8()..r.s8())
            else -> null
        }
        Command1.EQEBB_RET_PARAM, Command1.EQEBB_NTFY_PARAM -> when (r.u8()) {
            EqEbb.PRESET_EQ, EqEbb.PRESET_EQ_NONCUSTOMIZABLE -> MdrEvent.Eq(EqState(r.u8(), r.list { u8() }))
            EqEbb.EBB -> MdrEvent.EbbLevel(r.s8())
            else -> null
        }
        Command1.EQEBB_RET_EXTENDED_INFO -> { r.u8(); MdrEvent.EqBands(r.list { EqBand(u8(), u16()) }) }
        Command1.EQEBB_RET_STATUS, Command1.EQEBB_NTFY_STATUS -> ignored(c)

        Command1.NCASM_RET_CAPABILITY -> ncAsmCapability(r)
        Command1.NCASM_RET_PARAM, Command1.NCASM_NTFY_PARAM -> ncAsm(r)
        Command1.NCASM_RET_STATUS, Command1.NCASM_NTFY_STATUS -> ignored(c)

        Command1.SENSE_RET_CAPABILITY -> { r.u8(); MdrEvent.SenseCaps(r.u8()) }

        // [cmd, NC_OPTIMIZER, seconds, PersonalMeasureType, seconds, BarometricMeasureType, seconds]
        Command1.OPT_RET_CAPABILITY -> if (r.u8() != 0x01) optOther(c, p) else {
            val seconds = r.u8()
            val personal = r.u8() == 0x01; r.u8()
            MdrEvent.OptimizerCaps(OptimizerCapability(seconds, personal, r.u8() == 0x01))
        }
        // [cmd, NC_OPTIMIZER, CommonStatus, OptimizerStatus]
        Command1.OPT_RET_STATUS, Command1.OPT_NTFY_STATUS ->
            if (r.u8() != 0x01) optOther(c, p) else { r.u8(); r.u8().let { MdrEvent.OptimizerState(it in 0x01..0x10, it) } }
        // [cmd, NC_OPTIMIZER, personalType, personalValue, barometricType, barometricValue]
        Command1.OPT_RET_PARAM, Command1.OPT_NTFY_PARAM -> if (r.u8() != 0x01) optOther(c, p) else {
            val pType = r.u8(); val pValue = r.u8(); val bType = r.u8(); val bValue = r.u8()
            MdrEvent.Optimizer(
                OptimizerResult(
                    personalMeasured = if (pType == 0x01) pValue == 0x01 else null,
                    pressureTenthsAtm = if (bType == 0x01 && bValue != 0) bValue else null,
                )
            )
        }

        Command1.ALERT_RET_CAPABILITY -> ignored(c)
        // AlertInquiredType 1 FIXED_MESSAGE asks for an answer; 2 VIBRATOR_ALERT_NOTIFICATION only informs.
        Command1.ALERT_NTFY_PARAM -> when (r.u8()) { 0x01 -> MdrEvent.Alert(r.u8(), r.u8()); 0x02 -> ignored(c); else -> null }

        Command1.PLAY_RET_CAPABILITY -> { r.u8(); MdrEvent.PlayCapability(r.u8()) }
        Command1.PLAY_RET_STATUS, Command1.PLAY_NTFY_STATUS -> { r.pos = 3; MdrEvent.PlayStatus(r.u8()) }
        Command1.PLAY_RET_PARAM, Command1.PLAY_NTFY_PARAM -> { r.u8(); playParam(r, notify = c == Command1.PLAY_NTFY_PARAM) }

        // Training mode: [cmd, 1, CommonOnOffSettingType, TrainingModeAvailableEffectType]; param [cmd, 1, type, value].
        Command1.SPORTS_RET_CAPABILITY -> if (r.u8() == 0x01) { r.u8(); MdrEvent.TrainingModeCaps(r.u8()) } else null
        Command1.SPORTS_RET_PARAM, Command1.SPORTS_NTFY_PARAM -> if (r.u8() == 0x01) { r.u8(); MdrEvent.TrainingMode(r.bool()) } else null
        Command1.SPORTS_RET_STATUS, Command1.SPORTS_NTFY_STATUS,
        Command1.SPORTS_RET_EXTENDED_PARAM, Command1.SPORTS_NTFY_EXTENDED_PARAM, Command1.LOG_NTFY_PARAM -> ignored(c)

        Command1.GENERAL_SETTING_RET_CAPABILITY -> generalSetting(r)
        Command1.GENERAL_SETTING_RET_STATUS, Command1.GENERAL_SETTING_NTFY_STATUS -> MdrEvent.GsStatus(r.u8(), r.u8() == 0x00)
        Command1.GENERAL_SETTING_RET_PARAM, Command1.GENERAL_SETTING_NTFY_PARAM -> {
            val id = r.u8()
            when (r.u8()) {
                0x01 -> MdrEvent.GsValue(id, r.bool())
                0x02 -> MdrEvent.GsListValue(id, r.u8())
                else -> null
            }
        }

        // Upscaling: [cmd, 2, UpscalingType, UpscalingSettingType]; connection mode: [cmd, 1, ConnectionModeSettingType].
        Command1.AUDIO_RET_CAPABILITY -> if (r.u8() == 0x02) MdrEvent.UpscalingCaps(r.u8()) else ignored(c)
        Command1.AUDIO_RET_PARAM, Command1.AUDIO_NTFY_PARAM -> when (r.u8()) {
            0x01 -> { r.u8(); MdrEvent.SoundQualityMode(r.bool()) }
            0x02 -> { r.u8(); MdrEvent.UpscalingSetting(r.bool()) }
            else -> null
        }
        Command1.AUDIO_RET_STATUS, Command1.AUDIO_NTFY_STATUS -> ignored(c)

        Command1.SYSTEM_RET_CAPABILITY -> when (r.u8()) {
            SystemSettings.AUTO_POWER_OFF -> MdrEvent.AutoPowerOffOptions(r.list { u8() })
            SystemSettings.ASSIGNABLE_SETTINGS -> MdrEvent.AssignableCaps(r.list { assignableKey() })
            else -> ignored(c)
        }
        Command1.SYSTEM_RET_PARAM, Command1.SYSTEM_NTFY_PARAM -> systemParam(r)
        // CONTROL_BY_WEARING status [cmd, 3, CommonStatus]: ENABLE means the wearing sensor is usable right now.
        Command1.SYSTEM_RET_STATUS, Command1.SYSTEM_NTFY_STATUS ->
            if (r.u8() == SystemSettings.CONTROL_BY_WEARING) MdrEvent.WearingStatus(r.u8() == 0x00) else ignored(c)
        // [cmd, 5, TYPE_1(0), sensitivity, voiceFocus, modeOutTime]
        Command1.SYSTEM_RET_EXTENDED_PARAM, Command1.SYSTEM_NTFY_EXTENDED_PARAM ->
            if (r.u8() == SystemSettings.SMART_TALKING_MODE && r.u8() == 0x00)
                MdrEvent.SpeakToChatDetails(SpeakToChatDetail(r.u8(), r.bool(), r.u8()))
            else ignored(c)

        Command1.TEST_COMMAND -> ignored(c)
        else -> null // requests and SET commands never come from the headset
    }

    private fun Reader.charge() = Charge(u8(), u8() == 0x01)

    private fun Reader.assignableKey(): AssignableKey {
        val key = u8(); val type = u8(); val default = u8()
        // Each preset lists the action → function pairs it maps to; Hush only needs the preset ids (wm0/b, wm0/d).
        val presets = list { u8().also { list { u8(); u8() } } }
        return AssignableKey(key, type, default, presets)
    }

    private fun playParam(r: Reader, notify: Boolean): MdrEvent? = when (val type = r.u8()) {
        0x20 -> MdrEvent.PlayVolume(r.u8())
        // [type, PlaybackNameStatus, name]; the "track changed" notify is just `a9 01 00`, without the text.
        0x00, 0x01, 0x02 -> {
            val text = if (r.remaining >= 2 && r.u8() == 0x02) r.str().take(128).ifEmpty { null } else null
            MdrEvent.PlayName(type, text, notify)
        }
        // GENRE_NAME, PLAYER_NAME: Hush shows track, artist and album only
        0x03 -> MdrEvent.Ignored("PLAY genre")
        0x10 -> MdrEvent.Ignored("PLAY player")
        else -> null
    }

    private fun systemParam(r: Reader): MdrEvent? = when (r.u8()) {
        // [cmd, type, ON_OFF(0), value]
        SystemSettings.VIBRATOR -> { r.u8(); MdrEvent.Vibrator(r.bool()) }
        SystemSettings.POWER_SAVING_MODE -> { r.u8(); MdrEvent.PowerSaving(r.bool()) }
        SystemSettings.CONTROL_BY_WEARING -> { r.u8(); MdrEvent.PauseWhenRemoved(r.bool()) }
        // [cmd, 4, ACTIVE_AND_SELECTIME_ID(1), activeId, selectTimeId]
        SystemSettings.AUTO_POWER_OFF -> { r.u8(); MdrEvent.AutoPowerOff(r.u8(), r.u8()) }
        // RET: [cmd, 5, ON_OFF(0), value]; NTFY: [cmd, 5, MODE_ON_OFF(1) | PREVIEW_MODE_ON_OFF(2), value] (ym0/c, ym0/d).
        // Turning it on from the headset sends MODE=on then PREVIEW=off; the preview flag must not be read as the setting.
        SystemSettings.SMART_TALKING_MODE -> if (r.u8() == 0x02) MdrEvent.Ignored("SMART_TALKING_MODE preview") else MdrEvent.SpeakToChat(r.bool())
        // [cmd, 6, n, preset×n]
        SystemSettings.ASSIGNABLE_SETTINGS -> MdrEvent.CustomButton(r.list { u8() })
        else -> null
    }

    /** [cmd, gsId, strFormat, title, summary, GsSettingType, (n, (strFormat, name, summary)×n for LIST_TYPE)] (um0.f / xm0.d). */
    private fun generalSetting(r: Reader): MdrEvent {
        val id = r.u8(); r.u8()
        val title = r.str(); r.str()
        val type = r.u8()
        val options = if (type == 0x02) r.list { u8(); str().also { str() } } else emptyList()
        return MdrEvent.GsCapability(GeneralSetting(id, title, type == 0x01, options))
    }

    /**
     * NC: [cmd, 1, NcSettingType]; NC+ASM: [cmd, 2, NcAsmSettingType, ncStep, AsmSettingType, n, (AsmId, steps)×n];
     * ASM: [cmd, 3, AsmSettingType, n, (AsmId, steps)×n] (tm0/d2).
     */
    private fun ncAsmCapability(r: Reader): MdrEvent? {
        val code = r.u8()
        val kind = NcAsmKind.entries.firstOrNull { it.code == code } ?: return null
        var nc: Int? = null
        var asm: Int? = null
        var levels = emptyList<Pair<Int, Int>>()
        when (kind) {
            NcAsmKind.NC -> nc = r.u8()
            NcAsmKind.NC_ASM -> { nc = r.u8(); r.u8(); asm = r.u8(); levels = r.list { u8() to u8() } }
            NcAsmKind.ASM -> { asm = r.u8(); levels = r.list { u8() to u8() } }
        }
        val normal = levels.firstOrNull { it.first == 0x00 }?.second ?: levels.firstOrNull()?.second ?: 0
        return MdrEvent.NcAsmCaps(NcAsmCapability(kind, nc, asm, normal, levels.any { it.first == 0x01 }))
    }

    /**
     * NC: [cmd, 1, NcSettingType, value]; NC+ASM: [cmd, 2, effect, NcAsmSettingType, ncValue, AsmSettingType, AsmId,
     * asmValue] (vm0.a0); ASM: [cmd, 3, effect, AsmSettingType, AsmId, asmValue].
     */
    private fun ncAsm(r: Reader): MdrEvent? = when (r.u8()) {
        0x01 -> {
            val type = r.u8()
            val on = r.u8() != 0x00
            MdrEvent.NcAsm(NcAsmState(if (on) AmbientMode.NOISE_CANCELLING else AmbientMode.OFF, 0, false, type, 0x01, 0x00, NcAsmKind.NC))
        }
        0x02 -> {
            val effectOn = r.u8() != 0x00
            val ncType = r.u8(); val ncValue = r.u8(); val asmType = r.u8(); val asmId = r.u8(); val asmValue = r.u8()
            val mode = when {
                !effectOn -> AmbientMode.OFF
                ncValue != 0x00 -> AmbientMode.NOISE_CANCELLING
                asmValue == 0 -> AmbientMode.OFF
                else -> AmbientMode.AMBIENT
            }
            MdrEvent.NcAsm(
                NcAsmState(
                    mode = mode,
                    ambientLevel = if (asmType == 0x00) 0 else asmValue,
                    voiceFocus = asmId == 0x01,
                    ncSettingType = ncType,
                    ncOnValue = if (ncValue != 0) ncValue else if (ncType == 0x00) 0x01 else 0x02,
                    asmSettingType = asmType,
                )
            )
        }
        0x03 -> {
            val effectOn = r.u8() != 0x00
            val asmType = r.u8(); val asmId = r.u8(); val asmValue = r.u8()
            val mode = if (effectOn && asmValue != 0) AmbientMode.AMBIENT else AmbientMode.OFF
            MdrEvent.NcAsm(
                NcAsmState(mode, if (asmType == 0x00) 0 else asmValue, asmId == 0x01, ncSettingType = 0x00, ncOnValue = 0x00,
                    asmSettingType = asmType, kind = NcAsmKind.ASM)
            )
        }
        else -> null
    }

    private fun table2(c: Command2, r: Reader): MdrEvent? = when (c) {
        // [cmd, 01, maxPaired, maxConnected, FileTransferInMultiConnection]
        Command2.PERIPHERAL_RET_CAPABILITY -> if (r.u8() == 0x01) MdrEvent.PeerCaps(r.u8(), r.u8()) else null
        // [cmd, 01, BluetoothModeStatus, CommonStatus]
        Command2.PERIPHERAL_RET_STATUS, Command2.PERIPHERAL_NTFY_STATUS -> if (r.u8() == 0x01) MdrEvent.PairingMode(r.bool()) else null
        // [cmd, 01, n, (address[17], order, name)×n, playbackOrder] (an0/o, an0/h)
        Command2.PERIPHERAL_RET_PARAM, Command2.PERIPHERAL_NTFY_PARAM -> if (r.u8() != 0x01) null else {
            val list = r.list { PeerDevice(ascii(17), "", u8()).let { it.copy(name = str()) } }
            MdrEvent.Peers(list, if (r.remaining > 0) r.u8() else 0)
        }
        // [cmd, 01, CONNECTION_CONTROL(0), ResultType, address[17]]
        Command2.PERIPHERAL_NTFY_EX_PARAM -> if (r.u8() == 0x01) { r.u8(); MdrEvent.PeerResult(r.u8(), r.ascii(17)) } else null
        // [cmd, 01, SupportsSwitch on/off, SupportsSwitch language, n, VoiceGuidanceLanguage×n] (bn0/i);
        // without a language switch the payload ends after it, with no list.
        Command2.VOICE_GUIDANCE_RET_CAPABILITY -> if (r.u8() != 0x01) null else {
            val onOff = r.bool()
            val language = r.bool()
            MdrEvent.VoiceGuidanceCaps(VoiceGuidanceCapability(onOff, language, if (language) r.list { u8() } else emptyList()))
        }
        // [cmd, VOICE_GUIDANCE_SETTING(1), DetailedDataType, value]
        Command2.VOICE_GUIDANCE_RET_PARAM, Command2.VOICE_GUIDANCE_NTFY_PARAM -> { r.u8(); when (r.u8()) {
            0x01 -> MdrEvent.VoiceGuidance(r.bool())
            0x02 -> MdrEvent.VoiceGuidanceLanguage(r.u8())
            else -> ignored(c)
        } }
        Command2.VOICE_GUIDANCE_RET_STATUS, Command2.VOICE_GUIDANCE_NTFY_STATUS -> ignored(c)
        else -> null
    }
}
