package com.occaecat.hush.mdr.v2

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

/**
 * Decodes MDR v2 payloads into the same [MdrEvent]s the v1 parser produces, so the controller and UI do not care
 * which generation they talk to. Layouts: Sound Connect's v2 payload classes and libmdr's ProtocolV2T1/T2 structs
 * (see Requests.kt); the ones checked against real captures are listed in docs/MDR_V2.md. Groups Hush does not
 * drive (firmware update, LE Audio, sensors, party …) are [MdrEvent.Ignored]; malformed payloads are [MdrEvent.Unknown].
 */
object MdrV2 {
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

    /** OnOffSettingValue: 0 on. */
    private fun Reader.on() = u8() == 0x00

    private fun Reader.charge() = Charge(u8(), u8() == 0x01) // BatteryChargingStatus CHARGING; CHARGED (3) is not charging

    private fun table1(c: Command1, r: Reader, p: ByteArray): MdrEvent? = when (c) {
        // [01, 00, version Int32BE, EnableDisable table 1, EnableDisable table 2]; a 4-byte reply is a v1 headset.
        Command1.CONNECT_RET_PROTOCOL_INFO -> {
            r.u8()
            if (p.size == 4) MdrEvent.ProtocolInfo(r.u16())
            else MdrEvent.ProtocolInfo((r.u16() shl 16) or r.u16(), v2 = true, table1 = r.u8() == 0x00, table2 = r.u8() == 0x00)
        }
        Command1.CONNECT_RET_CAPABILITY_INFO -> { r.u8(); MdrEvent.CapabilityInfo(r.u8(), r.str()) }
        Command1.CONNECT_RET_DEVICE_INFO -> when (r.u8()) {
            0x01 -> MdrEvent.ModelName(r.str())
            0x02 -> MdrEvent.FirmwareVersion(r.str())
            0x03 -> MdrEvent.SeriesAndColor(r.u8(), r.u8())
            0x04 -> MdrEvent.GuideCategories(r.list { u8() })
            else -> null
        }
        // [07, 00, n, (FunctionType, priority)×n]
        Command1.CONNECT_RET_SUPPORT_FUNCTION -> { r.u8(); MdrEvent.SupportedFunctions(r.list { u8().also { u8() } }.toSet(), Table.ONE) }

        // [cmd, CommonInquiredType, …]: earbud links (CONNECTED 1), codec, upscaling effect (VALID 1).
        Command1.COMMON_RET_STATUS, Command1.COMMON_NTFY_STATUS -> when (r.u8()) {
            Common.CONNECTION_STATUS -> MdrEvent.EarbudsConnected(r.u8() == 0x01, r.u8() == 0x01)
            Common.AUDIO_CODEC -> MdrEvent.AudioCodec(Codec.of(r.u8()))
            Common.UPSCALING_EFFECT -> { r.u8(); MdrEvent.UpscalingStatus(r.u8() == 0x01) }
            else -> ignored(c)
        }
        Command1.COMMON_RET_CAPABILITY, Command1.COMMON_NTFY_PARAM -> ignored(c)

        Command1.POWER_RET_CAPABILITY -> when (r.u8()) {
            Power.AUTO_POWER_OFF, Power.AUTO_POWER_OFF_WEARING_DETECTION -> MdrEvent.AutoPowerOffOptions(r.list { u8() })
            else -> ignored(c)
        }
        // Batteries: [type, level, BatteryChargingStatus]; L/R: left, right; the threshold variants append thresholds.
        Command1.POWER_RET_STATUS, Command1.POWER_NTFY_STATUS -> when (r.u8()) {
            Power.BATTERY, Power.BATTERY_WITH_THRESHOLD -> r.charge().let { MdrEvent.Battery(it.level, it.charging) }
            Power.LEFT_RIGHT_BATTERY, Power.LR_BATTERY_WITH_THRESHOLD -> MdrEvent.EarbudBatteries(r.charge(), r.charge())
            Power.CRADLE_BATTERY, Power.CRADLE_BATTERY_WITH_THRESHOLD -> MdrEvent.CaseBattery(r.charge())
            else -> ignored(c)
        }
        // [type, current AutoPowerOffElements, last selected]
        Command1.POWER_RET_PARAM, Command1.POWER_NTFY_PARAM -> when (r.u8()) {
            Power.AUTO_POWER_OFF, Power.AUTO_POWER_OFF_WEARING_DETECTION -> MdrEvent.AutoPowerOff(r.u8(), r.u8())
            else -> ignored(c)
        }

        // EQ: [type, bands, steps, (ULT steps), n, (EqPresetId, name)×n]; EBB: [1, min, max] (signed).
        Command1.EQEBB_RET_CAPABILITY -> when (val type = r.u8()) {
            EqEbb.PRESET_EQ, EqEbb.PRESET_EQ_NONCUSTOMIZABLE, EqEbb.PRESET_EQ_AND_ULT_MODE, EqEbb.PRESET_EQ_AND_ERRORCODE -> {
                val bands = r.u8(); val steps = r.u8()
                if (type == EqEbb.PRESET_EQ_AND_ULT_MODE) r.u8()
                MdrEvent.EqCaps(EqCapability(bands, steps, r.list { EqPreset(u8(), str()) }), customizable = type != EqEbb.PRESET_EQ_NONCUSTOMIZABLE)
            }
            EqEbb.EBB -> MdrEvent.EbbCaps(r.s8()..r.s8())
            else -> ignored(c)
        }
        // [type, EqPresetId, (EqUltModeStatus), n, level×n]; 6-band models offset levels by 10 like v1, 10-band by 6.
        Command1.EQEBB_RET_PARAM, Command1.EQEBB_NTFY_PARAM -> when (val type = r.u8()) {
            EqEbb.PRESET_EQ, EqEbb.PRESET_EQ_NONCUSTOMIZABLE, EqEbb.PRESET_EQ_AND_ERRORCODE -> MdrEvent.Eq(EqState(r.u8(), r.list { u8() }))
            EqEbb.PRESET_EQ_AND_ULT_MODE -> { val id = r.u8(); r.u8(); MdrEvent.Eq(EqState(id, r.list { u8() })) }
            EqEbb.EBB -> MdrEvent.EbbLevel(r.s8())
            else -> if (type >= 0x30) ignored(c) else null
        }
        Command1.EQEBB_RET_STATUS, Command1.EQEBB_NTFY_STATUS -> ignored(c)
        Command1.EQEBB_RET_EXTENDED_INFO -> { r.u8(); MdrEvent.EqBands(r.list { EqBand(u8(), u16()) }) }

        Command1.NCASM_RET_CAPABILITY -> ncAsmCapability(c, r)
        Command1.NCASM_RET_PARAM, Command1.NCASM_NTFY_PARAM -> ncAsm(c, r)
        Command1.NCASM_RET_STATUS, Command1.NCASM_NTFY_STATUS -> ignored(c)

        // [00 FIXED_MESSAGE, AlertMessageType, AlertActionType]; the type is translated to Hush's v1 codes.
        Command1.ALERT_NTFY_PARAM -> if (r.u8() == 0x00) MdrEvent.Alert(Alert.toV1(r.u8()), r.u8()) else ignored(c)
        Command1.ALERT_RET_STATUS, Command1.ALERT_NTFY_STATUS -> ignored(c)

        // [type, music steps, (call steps)]
        Command1.PLAY_RET_CAPABILITY -> when (r.u8()) {
            0x01, 0x02, 0x03 -> MdrEvent.PlayCapability(r.u8())
            else -> ignored(c)
        }
        // [type, EnableDisable, PlaybackStatus (1 play, 2 pause, 3 stop), …]
        Command1.PLAY_RET_STATUS, Command1.PLAY_NTFY_STATUS -> when (r.u8()) {
            0x01, 0x02, 0x03 -> { r.u8(); MdrEvent.PlayStatus(r.u8()) }
            else -> ignored(c)
        }
        Command1.PLAY_RET_PARAM, Command1.PLAY_NTFY_PARAM -> playParam(c, r)

        Command1.GENERAL_SETTING_RET_CAPABILITY -> generalSetting(r)
        Command1.GENERAL_SETTING_RET_STATUS, Command1.GENERAL_SETTING_NTFY_STATUS -> MdrEvent.GsStatus(r.u8(), r.u8() == 0x00)
        // [id, GsSettingType (0 boolean, 1 list), GsSettingValue (0 on) | index]
        Command1.GENERAL_SETTING_RET_PARAM, Command1.GENERAL_SETTING_NTFY_PARAM -> {
            val id = r.u8()
            when (r.u8()) {
                0x00 -> MdrEvent.GsValue(id, r.on())
                0x01 -> MdrEvent.GsListValue(id, r.u8())
                else -> null
            }
        }

        Command1.AUDIO_RET_CAPABILITY -> if (r.u8() == Audio.UPSCALING) MdrEvent.UpscalingCaps(r.u8()) else ignored(c)
        // Connection mode: [type, PriorMode (1 stable connection)]; upscaling: [1, UpscalingTypeAutoOff (1 auto)].
        Command1.AUDIO_RET_PARAM, Command1.AUDIO_NTFY_PARAM -> when (r.u8()) {
            Audio.CONNECTION_MODE, Audio.CONNECTION_MODE_WITH_LDAC_STATUS -> MdrEvent.SoundQualityMode(r.u8() == 0x01)
            Audio.CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO -> r.u8().let { MdrEvent.SoundQualityMode(it == 0x01, lowLatency = it == 0x02) }
            Audio.UPSCALING -> MdrEvent.UpscalingSetting(r.u8() == 0x01)
            // [type, OnOffSettingValue, RoomSize] (fn0/h0); [type, OnOffSettingValue] (m0, p0, l0); [type, UpmixItemId] (n0)
            Audio.BGM_MODE, Audio.BGM_MODE_AND_ERRORCODE -> { val on = r.on(); val room = r.u8(); MdrEvent.Listening { it.copy(bgm = on, roomSize = room) } }
            Audio.UPMIX_CINEMA -> r.on().let { on -> MdrEvent.Listening { it.copy(cinema = on) } }
            Audio.UPMIX_SERIES -> r.u8().let { item -> MdrEvent.Listening { it.copy(upmix = item) } }
            Audio.VOICE_CONTENTS -> r.on().let { on -> MdrEvent.Listening { it.copy(voiceBoost = on) } }
            Audio.SOUND_LEAKAGE_REDUCTION -> r.on().let { on -> MdrEvent.Listening { it.copy(leakageReduction = on) } }
            else -> ignored(c)
        }
        Command1.AUDIO_RET_STATUS, Command1.AUDIO_NTFY_STATUS -> ignored(c)

        Command1.SYSTEM_RET_CAPABILITY -> when (r.u8()) {
            SystemSettings.ASSIGNABLE_SETTINGS -> MdrEvent.AssignableCaps(r.list { assignableKey() })
            SystemSettings.ASSIGNABLE_SETTINGS_WITH_LIMITATION -> { r.u8(); MdrEvent.AssignableCaps(r.list { assignableKey() }) }
            else -> ignored(c)
        }
        // [type, EnableDisable]: for pause-when-removed, whether the wearing sensor can be used right now.
        Command1.SYSTEM_RET_STATUS, Command1.SYSTEM_NTFY_STATUS ->
            if (r.u8() == SystemSettings.PLAYBACK_CONTROL_BY_WEARING) MdrEvent.WearingStatus(r.u8() == 0x00) else ignored(c)
        Command1.SYSTEM_RET_PARAM, Command1.SYSTEM_NTFY_PARAM -> systemParam(c, r)
        Command1.SYSTEM_RET_EXT_PARAM, Command1.SYSTEM_NTFY_EXT_PARAM -> when (r.u8()) {
            // TYPE2 [DetectSensitivity, ModeOutTime]; TYPE1 [DetectSensitivity, voice focus OnOff, ModeOutTime]
            SystemSettings.SMART_TALKING_MODE_TYPE2 -> MdrEvent.SpeakToChatDetails(SpeakToChatDetail(r.u8(), false, r.u8()))
            SystemSettings.SMART_TALKING_MODE_TYPE1 -> MdrEvent.SpeakToChatDetails(SpeakToChatDetail(r.u8(), r.on(), r.u8()))
            else -> ignored(c)
        }

        Command1.OPT_RET_CAPABILITY -> optimizerCapability(c, r)
        // [type, EnableDisable, OptimizerStatus 0 idle, 1 personal, 2 barometric, 0x10 optimizing, 0x11 end]
        Command1.OPT_RET_STATUS, Command1.OPT_NTFY_STATUS ->
            if (r.u8() !in OPTIMIZER_TYPES) ignored(c) else { r.u8(); r.u8().let { MdrEvent.OptimizerState(it in 0x01..0x10, it) } }
        Command1.OPT_RET_PARAM, Command1.OPT_NTFY_PARAM -> optimizerResult(c, r)

        // [type, category, service id, nation code, language, serial, …] as length-prefixed text (ho0/q), reported
        // under the v1 UpdateInquiredType numbers (2–6) so the firmware check reads them the same way.
        Command1.UPDT_RET_PARAM -> if (r.u8() !in UPDATE_TYPES) ignored(c) else MdrEvent.Several(
            (0x02..0x05).map { MdrEvent.UpdateParam(it, r.str().trim()) } + MdrEvent.SerialNumber(r.str().trim())
        )
        Command1.UPDT_RET_CAPABILITY, Command1.UPDT_RET_STATUS, Command1.UPDT_NTFY_STATUS,
        Command1.UPDT_NTFY_PARAM, Command1.UPDT_NTFY_MESSAGE,
        Command1.LEA_RET_CAPABILITY, Command1.LEA_RET_STATUS, Command1.LEA_NTFY_STATUS, Command1.LEA_RET_PARAM,
        Command1.LEA_NTFY_PARAM, Command1.LEA_RET_EXTENDED_PARAM, Command1.LEA_NTFY_EXTENDED_PARAM,
        Command1.SENSE_RET_CAPABILITY, Command1.SENSE_RET_STATUS, Command1.SENSE_NTFY_STATUS, Command1.SENSE_RET_PARAM,
        Command1.SENSE_NTFY_PARAM, Command1.SENSE_RET_EXT_INFO,
        Command1.ALERT_RET_CAPABILITY,
        Command1.SAR_AUTO_PLAY_RET_CAPABILITY, Command1.SAR_AUTO_PLAY_RET_STATUS, Command1.SAR_AUTO_PLAY_NTFY_STATUS,
        Command1.SAR_AUTO_PLAY_RET_PARAM, Command1.SAR_AUTO_PLAY_NTFY_PARAM,
        Command1.LOG_NTFY_PARAM, Command1.TEST_COMMAND -> ignored(c)
        else -> null // requests and SET commands never come from the headset
    }

    private val UPDATE_TYPES = setOf(0x02, 0x04, 0x05, 0x06, 0x07, 0x10)
    private val OPTIMIZER_TYPES = Optimizer.PERSONAL_BAROMETRIC..Optimizer.BAROMETRIC

    /** [type, total seconds, then personal and/or barometric seconds] (vn0/m, n, o; DeviceCapabilityTableset2Builder). */
    private fun optimizerCapability(c: Command1, r: Reader): MdrEvent = when (r.u8()) {
        Optimizer.PERSONAL_BAROMETRIC -> { val total = r.u8(); r.u8(); r.u8(); MdrEvent.OptimizerCaps(OptimizerCapability(total, personal = true, barometric = true)) }
        Optimizer.PERSONAL -> { val total = r.u8(); r.u8(); MdrEvent.OptimizerCaps(OptimizerCapability(total, personal = true, barometric = false)) }
        Optimizer.BAROMETRIC -> { val total = r.u8(); r.u8(); MdrEvent.OptimizerCaps(OptimizerCapability(total, personal = false, barometric = true)) }
        else -> ignored(c) // SOUND_FIELD_OPTIMIZATION, TV_SOUND_BOOSTER
    }

    /** PersonalValue 0 unmeasured / 1 measured; BarometricPressure 0 unmeasured, 7..10 tenths of an atmosphere. */
    private fun optimizerResult(c: Command1, r: Reader): MdrEvent {
        fun pressure(v: Int) = v.takeIf { it != 0 }
        return when (r.u8()) {
            Optimizer.PERSONAL_BAROMETRIC -> MdrEvent.Optimizer(OptimizerResult(r.u8() == 0x01, pressure(r.u8())))
            Optimizer.PERSONAL -> MdrEvent.Optimizer(OptimizerResult(r.u8() == 0x01, null))
            Optimizer.BAROMETRIC -> MdrEvent.Optimizer(OptimizerResult(null, pressure(r.u8())))
            else -> ignored(c)
        }
    }

    /** Seamless types: [type, n, (AmbientSoundMode, min, max, step)×n] (un0/b); NC on/off: [type]. */
    private fun ncAsmCapability(c: Command1, r: Reader): MdrEvent {
        val type = NcAsmV2.of(r.u8()) ?: return ignored(c)
        val levels = if (type.hasLevel) r.list { val mode = u8(); u8(); val max = u8(); u8(); mode to max } else emptyList()
        val normal = levels.firstOrNull { it.first == 0x00 }?.second ?: levels.firstOrNull()?.second ?: 0
        return MdrEvent.NcAsmCaps(
            NcAsmCapability(
                kind = type.kind,
                ncSettingType = when { type.kind == NcAsmKind.ASM -> null; type.dualSingle -> NcAsmState.NC_DUAL_SINGLE_OFF; else -> 0x00 },
                asmSettingType = if (type.hasLevel) 0x01 else null,
                asmSteps = normal,
                voice = levels.any { it.first == 0x01 },
            )
        )
    }

    /** [type, ValueChangeStatus, NcAsmTotalEffect (1 on), the type's fields (see [NcAsmV2]), noise-adaptation tail]. */
    private fun ncAsm(c: Command1, r: Reader): MdrEvent {
        val type = NcAsmV2.of(r.u8()) ?: return ignored(c)
        r.u8()
        val on = r.u8() == 0x01
        val v = type.fields.associateWith { r.u8() }
        val extra = List(type.extra) { r.u8() }
        val ncValue = v[NcAsmV2.Field.NC_VALUE]
        val mode = when {
            !on -> AmbientMode.OFF
            v.containsKey(NcAsmV2.Field.MODE) -> if (v[NcAsmV2.Field.MODE] == 0x00) AmbientMode.NOISE_CANCELLING else AmbientMode.AMBIENT
            ncValue != null -> if (ncValue != 0x00) AmbientMode.NOISE_CANCELLING else AmbientMode.AMBIENT
            v.containsKey(NcAsmV2.Field.NC_ON_OFF) && v[NcAsmV2.Field.NC_ON_OFF] == 0x01 -> AmbientMode.NOISE_CANCELLING
            type.kind == NcAsmKind.NC -> AmbientMode.OFF
            else -> AmbientMode.AMBIENT
        }
        return MdrEvent.NcAsm(
            NcAsmState(
                mode = mode,
                ambientLevel = v[NcAsmV2.Field.LEVEL] ?: 0,
                voiceFocus = v[NcAsmV2.Field.ASM_MODE] == 0x01,
                ncSettingType = if (type.dualSingle) NcAsmState.NC_DUAL_SINGLE_OFF else 0x00,
                ncOnValue = ncValue?.takeIf { it != 0 } ?: 0x02,
                asmSettingType = if (type.hasLevel) 0x01 else 0x00,
                kind = type.kind,
                extra = extra,
            )
        )
    }

    /** Names: [type, (PlaybackNameStatus, name)×4] in the order track, album, artist, genre; 2 SETTLED has text. */
    private fun playParam(c: Command1, r: Reader): MdrEvent? = when (r.u8()) {
        Play.MUSIC_VOLUME -> MdrEvent.PlayVolume(r.u8())
        0x30 -> MdrEvent.PlayVolume(r.u8()) // MUSIC_VOLUME_WITH_MUTE
        0x01, 0x02, 0x03 -> {
            val names = List(4) { val settled = r.u8() == 0x02; r.str().takeIf { settled }?.take(128)?.ifEmpty { null } }
            MdrEvent.Several(listOf(MdrEvent.PlayName(0x00, names[0]), MdrEvent.PlayName(0x01, names[1]), MdrEvent.PlayName(0x02, names[2])))
        }
        else -> ignored(c)
    }

    private fun systemParam(c: Command1, r: Reader): MdrEvent = when (r.u8()) {
        SystemSettings.VIBRATOR -> MdrEvent.Vibrator(r.on())
        SystemSettings.PLAYBACK_CONTROL_BY_WEARING -> MdrEvent.PauseWhenRemoved(r.on())
        // [type, mode, preview]: only the mode is the setting.
        SystemSettings.SMART_TALKING_MODE_TYPE1, SystemSettings.SMART_TALKING_MODE_TYPE2 -> MdrEvent.SpeakToChat(r.on())
        // [type, n, Preset×n]
        SystemSettings.ASSIGNABLE_SETTINGS, SystemSettings.ASSIGNABLE_SETTINGS_WITH_LIMITATION -> MdrEvent.CustomButton(r.list { u8() })
        else -> ignored(c)
    }

    /**
     * [Key, Type, default Preset, n, (Preset, n, (Action, Function)×n, n, (Action, default Function, n, Function×n)×n)×n]
     * (libmdr AssignableSettingsKey); Hush keeps the preset ids.
     */
    private fun Reader.assignableKey(): AssignableKey {
        val key = u8(); val type = u8(); val default = u8()
        val presets = list { u8().also { list { u8(); u8() }; list { u8(); u8(); list { u8() } } } }
        return AssignableKey(key, type, default, presets)
    }

    /** [id, GsSettingType, GsStringFormat, title, summary, (n, (format, name, summary)×n for LIST)]. */
    private fun generalSetting(r: Reader): MdrEvent {
        val id = r.u8()
        val type = r.u8(); r.u8()
        val title = r.str(); r.str()
        val options = if (type == 0x01) r.list { u8(); str().also { str() } } else emptyList()
        return MdrEvent.GsCapability(GeneralSetting(id, title, type == 0x00, options))
    }

    private fun table2(c: Command2, r: Reader): MdrEvent? = when (c) {
        Command2.CONNECT_RET_SUPPORT_FUNCTION -> { r.u8(); MdrEvent.SupportedFunctions(r.list { u8().also { u8() } }.toSet(), Table.TWO) }

        // [type, maxPaired, maxConnected, FileTransferInMultiConnection]
        Command2.PERI_RET_CAPABILITY -> if (r.u8() in PAIRING_TYPES) MdrEvent.PeerCaps(r.u8(), r.u8()) else ignored(c)
        // [type, PeripheralBluetoothMode (1 inquiry scan), EnableDisable]
        Command2.PERI_RET_STATUS, Command2.PERI_NTFY_STATUS -> if (r.u8() in PAIRING_TYPES) MdrEvent.PairingMode(r.u8() == 0x01) else ignored(c)
        // [type, n, (address[17], connected order, (class of device, 3 bytes, type 2 only), name)×n, playback order]
        Command2.PERI_RET_PARAM, Command2.PERI_NTFY_PARAM -> when (val type = r.u8()) {
            in PAIRING_TYPES -> {
                val list = r.list {
                    val address = ascii(17); val order = u8()
                    if (type == 0x02) bytes(3)
                    PeerDevice(address, str(), order)
                }
                MdrEvent.Peers(list, if (r.remaining > 0) r.u8() else 0)
            }
            else -> ignored(c)
        }
        // [type, ConnectivityActionType, PeripheralResult (the v1 ResultType codes), address[17]]
        Command2.PERI_NTFY_EXTENDED_PARAM -> if (r.u8() in PAIRING_TYPES) { r.u8(); MdrEvent.PeerResult(r.u8(), r.ascii(17)) } else ignored(c)

        Command2.VOICE_GUIDANCE_RET_CAPABILITY -> voiceGuidanceCapability(c, r)
        // Max Volume Control: [5, OnOffSettingValue] / [5, level 0..4]; the exposure log types are not read.
        Command2.SAFE_LISTENING_RET_STATUS, Command2.SAFE_LISTENING_NTFY_STATUS ->
            if (r.u8() == MaxVolume.TYPE) MdrEvent.MaxVolumeOn(r.on()) else ignored(c)
        Command2.SAFE_LISTENING_RET_PARAM, Command2.SAFE_LISTENING_NTFY_PARAM ->
            if (r.u8() == MaxVolume.TYPE) MdrEvent.MaxVolumeLevel(r.u8()) else ignored(c)
        // [type, OnOffSettingValue, (language for the language-switch types)]; [0x20/0x21, volume]; 0x30.. sounds are ignored.
        Command2.VOICE_GUIDANCE_RET_PARAM, Command2.VOICE_GUIDANCE_NTFY_PARAM -> when (r.u8()) {
            VoiceGuidance.VOLUME, VoiceGuidance.VOLUME_FIXED_TO_5_STEPS -> MdrEvent.VoiceGuidanceVolume(r.s8())
            0x00, 0x03 -> MdrEvent.VoiceGuidance(r.on())
            0x01, 0x02 -> {
                val on = MdrEvent.VoiceGuidance(r.on())
                if (r.remaining > 0) MdrEvent.Several(listOf(on, MdrEvent.VoiceGuidanceLanguage(r.u8()))) else on
            }
            else -> ignored(c)
        }

        Command2.CONNECT_GET_SUPPORT_FUNCTION, Command2.POWER_GET_CAPABILITY, Command2.POWER_GET_STATUS, Command2.POWER_SET_STATUS,
        Command2.POWER_GET_PARAM, Command2.POWER_SET_PARAM, Command2.PERI_GET_CAPABILITY, Command2.PERI_GET_STATUS,
        Command2.PERI_SET_STATUS, Command2.PERI_GET_PARAM, Command2.PERI_SET_PARAM, Command2.PERI_SET_EXTENDED_PARAM,
        Command2.VOICE_GUIDANCE_GET_CAPABILITY, Command2.VOICE_GUIDANCE_GET_STATUS, Command2.VOICE_GUIDANCE_SET_STATUS,
        Command2.VOICE_GUIDANCE_GET_PARAM, Command2.VOICE_GUIDANCE_SET_PARAM, Command2.VOICE_GUIDANCE_GET_EXTENDED_PARAM,
        Command2.SAFE_LISTENING_GET_CAPABILITY, Command2.SAFE_LISTENING_GET_STATUS, Command2.SAFE_LISTENING_SET_STATUS,
        Command2.SAFE_LISTENING_GET_PARAM, Command2.SAFE_LISTENING_SET_PARAM, Command2.SAFE_LISTENING_GET_EXTENDED_PARAM,
        Command2.LEA_GET_CAPABILITY, Command2.LEA_GET_STATUS, Command2.LEA_GET_PARAM, Command2.LEA_SET_PARAM,
        Command2.PARTY_GET_CAPABILITY, Command2.PARTY_GET_STATUS, Command2.PARTY_SET_STATUS, Command2.PARTY_GET_PARAM,
        Command2.PARTY_SET_PARAM, Command2.PARTY_SET_EXTENDED_PARAM, Command2.SYSTEM_GET_CAPABILITY, Command2.SYSTEM_GET_STATUS,
        Command2.SYSTEM_SET_STATUS, Command2.SYSTEM_GET_PARAM, Command2.SYSTEM_SET_PARAM, Command2.SYSTEM_GET_EXTENDED_PARAM,
        Command2.SYSTEM_SET_EXTENDED_PARAM -> null
        else -> ignored(c)
    }

    /**
     * Types 0/1: [type, length of the rest, 3 × EnableDisable, VoiceGuidanceSupportsSwitch, (type 1: n, language×n)]
     * (ap0/r, s, t);
     * type 2: [type, VoiceGuidanceSupportsSwitch, n, language×n] (ap0/u).
     */
    private fun voiceGuidanceCapability(c: Command2, r: Reader): MdrEvent = when (val type = r.u8()) {
        0x00, 0x01 -> {
            r.u8(); r.u8(); r.u8(); r.u8()
            val onOff = r.u8() == 0x01
            val languages = if (type == 0x01) r.list { u8() } else emptyList()
            MdrEvent.VoiceGuidanceCaps(VoiceGuidanceCapability(onOff, languages.isNotEmpty(), languages))
        }
        VoiceGuidance.SUPPORT_LANGUAGE_SWITCH -> {
            val onOff = r.u8() == 0x01
            val languages = r.list { u8() }
            MdrEvent.VoiceGuidanceCaps(VoiceGuidanceCapability(onOff, languages.isNotEmpty(), languages))
        }
        else -> ignored(c)
    }

    /** PeripheralInquiredType 0 PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT, 2 …_WITH_BLUETOOTH_CLASS_OF_DEVICE. */
    private val PAIRING_TYPES = setOf(0x00, 0x02)
}
