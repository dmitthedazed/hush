package com.occaecat.hush.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.SpatialAudio
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.assistant.Assistant
import com.occaecat.hush.assistant.Assistants
import com.occaecat.hush.devices.Confirm
import com.occaecat.hush.devices.Setting
import com.occaecat.hush.devices.SettingsGroup
import com.occaecat.hush.devices.Tab
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.ListeningMode
import com.occaecat.hush.mdr.v1.FunctionType

private val TouchTitles = setOf("TOUCH_PANEL_SETTING", "TOUCH_SENSOR_CONTROL_PANEL")

private fun reconnect(extra: String? = null) =
    Confirm("Reconnection is necessary", listOfNotNull(extra, Labels.RECONNECT + " Change?").joinToString(" "))

/** Sony's settings as shared rows: Listen switches, buttons, wearing, conversation, power and prompts. */
@Composable
internal fun sonySettings(state: HeadphonesState, vm: HeadphonesController, ui: SonyDeviceUi): List<SettingsGroup> {
    if (state.phase != Phase.CONNECTED && state.phase != Phase.RECONNECTING) return emptyList()
    val busy = state.audioSettling || state.phase != Phase.CONNECTED
    return listOfNotNull(
        listenSwitches(state, vm),
        soundEffects(state, vm),
        buttons(state, vm, ui, busy),
        wearing(state, vm),
        conversation(state, vm),
        power(state, vm),
    )
}

private fun listenSwitches(state: HeadphonesState, vm: HeadphonesController): SettingsGroup? {
    val rows = listOfNotNull(
        state.dseeAuto?.let {
            Setting.Toggle(Icons.Outlined.AutoAwesome, Labels.upscaling[state.upscalingType ?: 0x02] ?: "DSEE",
                if (state.dseeActive) "Upscaling now" else "Restores detail in compressed audio", it, onChange = vm::setDsee)
        },
        // NC_MODE_SWITCH_ASM_SEAMLESS models: noise cancelling with the outer microphones only, which wind disturbs less.
        state.ncAsm?.takeIf { state.ncAsmCaps?.windReduction == true }?.let { nc ->
            Setting.Toggle(Icons.Outlined.Air, "Wind noise reduction", "Noise cancelling that copes with wind",
                nc.mode == AmbientMode.NOISE_CANCELLING && nc.windReduction, onChange = vm::setWindReduction)
        },
        state.trainingMode?.let {
            Setting.Toggle(Icons.Outlined.FitnessCenter, "Training mode", "Separate sound settings for workouts", it,
                onChange = vm::setTrainingMode)
        },
        state.speakToChat?.let {
            Setting.Toggle(Icons.Outlined.ChatBubbleOutline, "Speak-to-Chat", "Pause and let sound in when you talk", it,
                onChange = vm::setSpeakToChat)
        },
    )
    return if (rows.isEmpty()) null else SettingsGroup(null, rows, Tab.LISTEN)
}

private fun listeningLabel(m: ListeningMode) = when (m) {
    ListeningMode.STANDARD -> "Standard"
    ListeningMode.BGM -> "Background Music"
    ListeningMode.CINEMA -> "Cinema"
    ListeningMode.GAME -> "Game"
    ListeningMode.MUSIC -> "Music"
    ListeningMode.VOICE_BOOST -> "Voice boost"
    ListeningMode.LEAKAGE_REDUCTION -> "Sound leakage reduction"
}

/** Surround (VPT), Sound Position Control and the bass booster (EBB) of older models, on the Sound page. */
private fun soundEffects(state: HeadphonesState, vm: HeadphonesController): SettingsGroup? {
    val rows = listOfNotNull(
        state.ebb?.let { ebb ->
            Setting.Choice(Icons.Outlined.GraphicEq, "Bass boost", ebb.range.map { it to if (it > 0) "+$it" else "$it" }, ebb.level,
                onSelect = vm::setEbb)
        },
        state.vptPresets.takeIf { it.isNotEmpty() }?.let { presets ->
            Setting.Choice(Icons.Outlined.SurroundSound, "Surround (VPT)",
                presets.map { it.id to it.name.ifEmpty { Labels.surround[it.id] ?: Labels.hex(it.id) } }, state.vpt, onSelect = vm::setVpt)
        },
        state.soundPositionType?.let {
            Setting.Choice(Icons.Outlined.SpatialAudio, "Sound position", Labels.soundPosition.toList(), state.soundPosition,
                onSelect = vm::setSoundPosition)
        },        state.listening?.let { l ->
            Setting.Choice(Icons.Outlined.Headphones, "Listening mode", l.available.map { it.ordinal to listeningLabel(it) }, l.current.ordinal) {
                vm.setListeningMode(ListeningMode.entries[it])
            }
        },
        state.maxVolumeOn?.let {
            Setting.Toggle(Icons.Outlined.Hearing, "Max Volume Control", "Keep the volume under a limit", it, onChange = vm::setMaxVolumeOn)
        },
        state.maxVolumeLevel?.takeIf { state.maxVolumeOn == true }?.let { lv ->
            // MaxVolume.STEPS levels, 0 the quietest.
            Setting.Choice(Icons.Outlined.Hearing, "Max volume", (0..4).map { it to "Level ${it + 1}" }, lv, onSelect = vm::setMaxVolumeLevel)
        },
        state.listening?.takeIf { it.current == ListeningMode.BGM }?.let { l ->
            Setting.Choice(Icons.Outlined.MeetingRoom, "Room size", listOf(0 to "My room", 1 to "Living room", 2 to "Cafe"), l.roomSize) {
                vm.setListeningMode(ListeningMode.BGM, roomSize = it)
            }
        },
    )
    return if (rows.isEmpty()) null else SettingsGroup("Sound effects", rows, Tab.SOUND)
}

@Composable
private fun buttons(state: HeadphonesState, vm: HeadphonesController, ui: SonyDeviceUi, busy: Boolean): SettingsGroup {
    val context = LocalContext.current
    var who by remember { mutableStateOf(Assistants.current(context)) }
    val handles = rememberOnResume { Assistants.handlesVoiceButton(context) }
    val rows = buildList {
        // One row per key the headset lets the user reassign, with the presets it lists for that key.
        if (state.assignableKeys.isNotEmpty()) state.assignableKeys.forEachIndexed { i, key ->
            val preset = state.assignable.getOrNull(i) ?: key.defaultPreset
            val opts = (key.presets + preset).distinct().map { it to (Labels.customButton[it] ?: Labels.hex(it)) }
            add(Setting.Choice(Icons.Outlined.RadioButtonChecked, Labels.assignableKey[key.key] ?: "Button ${i + 1}", opts, preset,
                enabled = !busy, confirm = { reconnect() }) { vm.setAssignable(i, it) })
        } else state.customButton?.let { preset ->
            val opts = (Labels.xm4CustomButtonOptions + preset).distinct().map { it to (Labels.customButton[it] ?: Labels.hex(it)) }
            add(Setting.Choice(Icons.Outlined.RadioButtonChecked, "CUSTOM button", opts, preset, enabled = !busy,
                confirm = { reconnect() }, onSelect = vm::setCustomButton))
        }
        // Touch and hold on the right ear cup sits next to the CUSTOM button: both are about what the headphone buttons do.
        if (!state.trueWireless) add(Setting.Choice(Icons.Outlined.AutoAwesome, "Touch and hold", Assistant.entries.map { it.ordinal to it.label }, who.ordinal) { v ->
            Assistant.entries[v].let { Assistants.set(context, it); who = it }
        })
        state.generalSettings.values
            .filter { it.title != "MULTIPOINT_SETTING" && it.isBoolean && state.generalValues[it.id] != null }
            .sortedBy { it.id }
            .forEach { gs ->
                add(Setting.Toggle(if (gs.title in TouchTitles) Icons.Outlined.TouchApp else Icons.Outlined.SettingsSuggest,
                    Labels.gsTitle(gs.title), Labels.gsSupporting(gs.title), state.generalValues[gs.id] == true,
                    enabled = state.generalEnabled[gs.id] != false) { vm.setGeneralSetting(gs.id, it) })
            }
        // LIST_TYPE settings name their choices themselves, e.g. what the WH-1000XM3's NC/AMBIENT button does.
        state.generalSettings.values
            .filter { !it.isBoolean && it.options.isNotEmpty() && state.generalListValues[it.id] != null }
            .sortedBy { it.id }
            .forEach { gs ->
                add(Setting.Choice(Icons.Outlined.SettingsSuggest, Labels.gsTitle(gs.title),
                    gs.options.mapIndexed { i, o -> i to Labels.gsOption(o) }, state.generalListValues[gs.id],
                    enabled = !busy && state.generalEnabled[gs.id] != false, confirm = { reconnect() }) { vm.setGeneralListSetting(gs.id, it) })
            }
        // Sound Connect's instruction guide (gestures, Quick Attention, CUSTOM button), from Sony's Help Guide.
        if (state.guideCategories?.isEmpty() != true) {
            add(Setting.Action(Icons.AutoMirrored.Outlined.MenuBook, "How to use", "Gestures and buttons") { ui.guide = true })
        }
    }
    // CUSTOM button on Gemini / Alexa takes the touch-and-hold for itself.
    val buttonTaken = state.customButton == 0x31 || state.customButton == 0x32
    val installed = Assistants.installed(context, who)
    val hint = when {
        who == Assistant.PHONE -> null
        !installed -> "${who.label} isn't installed. Get it from Google Play, then sign in."
        buttonTaken -> "The CUSTOM button is set to ${if (state.customButton == 0x31) "Gemini" else "Amazon Alexa"}, " +
            "which takes over touch and hold. Set it to Ambient Sound Control to use ${who.label}."
        !handles -> "Touch and hold the right ear cup. The first time, Android asks which app to use: " +
            "pick “Hush AI assistant” and “Always”."
        else -> null
    }
    return SettingsGroup("Buttons", rows, note = hint, noteIsError = hint != null && (buttonTaken || !installed))
}

private fun wearing(state: HeadphonesState, vm: HeadphonesController): SettingsGroup? {
    val r = state.optimizerResult
    val running = state.optimizerRunning
    val optimizer = if (!state.has(FunctionType.NC_OPTIMIZER)) null else Setting.Action(
        Icons.Outlined.Speed, "NC optimizer",
        if (running) "Keep the headphones on and stay still"
        else listOfNotNull(
            r?.personalMeasured?.let { if (it) "Fitted to you" else "Fit not measured" },
            r?.pressureTenthsAtm?.let { "%.1f atm".format(it / 10f) },
        ).joinToString(" · ").ifEmpty { "Tune noise cancelling to your fit and air pressure" },
        enabled = state.phase == Phase.CONNECTED,
        busy = running,
        button = if (running) "Cancel" else "Optimize",
    ) { if (running) vm.cancelOptimizer() else vm.startOptimizer() }
    val pause = state.pauseWhenRemoved?.let { on ->
        Setting.Toggle(Icons.Outlined.PauseCircleOutline, "Pause when taken off",
            if (state.pauseWhenRemovedAvailable) "Resume when you put them back on" else "Not available right now",
            on, enabled = state.pauseWhenRemovedAvailable, onChange = vm::setPauseWhenRemoved)
    }
    val rows = listOfNotNull(optimizer, pause)
    return if (rows.isEmpty()) null else SettingsGroup("Wearing", rows)
}

private fun conversation(state: HeadphonesState, vm: HeadphonesController): SettingsGroup? {
    val stc = state.speakToChat ?: return null
    val d = state.speakToChatDetail
    val rows = buildList {
        add(Setting.Toggle(Icons.Outlined.ChatBubbleOutline, "Speak-to-Chat",
            "Pause music and let sound in when you start talking", stc, onChange = vm::setSpeakToChat))
        if (stc && d != null) {
            add(Setting.Choice(Icons.Outlined.SignalCellularAlt, "Voice detection sensitivity", Labels.stcSensitivity.toList(), d.sensitivity) {
                vm.setSpeakToChatDetail(d.copy(sensitivity = it))
            })
            add(Setting.Toggle(Icons.Outlined.RecordVoiceOver, "Voice passthrough", "Make voices easier to hear", d.voiceFocus) {
                vm.setSpeakToChatDetail(d.copy(voiceFocus = it))
            })
            add(Setting.Choice(Icons.Outlined.Timelapse, "End Speak-to-Chat", Labels.stcModeOut.toList(), d.modeOutTime) {
                vm.setSpeakToChatDetail(d.copy(modeOutTime = it))
            })
        }
    }
    return SettingsGroup("Conversation", rows)
}

private fun power(state: HeadphonesState, vm: HeadphonesController): SettingsGroup? {
    val languages = state.voiceGuidanceCaps?.languages.orEmpty()
    val pickLanguage = vm.canSetVoiceGuidanceLanguage && languages.size > 1
    val rows = listOfNotNull(
        state.autoPowerOff?.let { (active, _) ->
            val ids = (state.autoPowerOffOptions.ifEmpty { listOf(0x10, 0x11) } + active).distinct()
            Setting.Choice(Icons.Outlined.Timer, "Automatic power off", ids.map { it to (Labels.autoPowerOff[it] ?: Labels.hex(it)) }, active,
                onSelect = vm::setAutoPowerOff)
        },
        state.powerSaving?.let {
            Setting.Toggle(Icons.Outlined.BatterySaver, "Power saving mode", "Longer battery life", it, onChange = vm::setPowerSaving)
        },
        state.vibrator?.let {
            Setting.Toggle(Icons.Outlined.Vibration, "Vibration", "Vibrate for notifications and alerts", it, onChange = vm::setVibrator)
        },
        state.voiceGuidance?.let { on ->
            Setting.Toggle(Icons.Outlined.Campaign, "Notifications & voice guidance",
                state.voiceGuidanceLanguage?.takeUnless { pickLanguage }?.let { "Language: ${Labels.languages[it] ?: "#$it"}" }, on,
                onChange = vm::setVoiceGuidance)
        },
        if (!pickLanguage) null else Setting.Choice(Icons.Outlined.Translate, "Voice guidance language",
            languages.map { it to (Labels.languages[it] ?: "#$it") }, state.voiceGuidanceLanguage, enabled = state.voiceGuidance != false,
            onSelect = vm::setVoiceGuidanceLanguage),
        state.voiceGuidanceVolume?.let { v ->
            Setting.Choice(Icons.AutoMirrored.Outlined.VolumeUp, "Voice guidance volume", (-2..2).map { it to if (it > 0) "+$it" else "$it" }, v,
                enabled = state.voiceGuidance != false, onSelect = vm::setVoiceGuidanceVolume)
        },
    )
    return if (rows.isEmpty()) null else SettingsGroup("Power & prompts", rows)
}
