package com.occaecat.hush.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
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
        buttons(state, vm, ui, busy),
        wearing(state, vm),
        conversation(state, vm),
        power(state, vm),
    )
}

private fun listenSwitches(state: HeadphonesState, vm: HeadphonesController): SettingsGroup? {
    val rows = listOfNotNull(
        state.dseeAuto?.let {
            Setting.Toggle(Icons.Outlined.AutoAwesome, "DSEE Extreme",
                if (state.dseeActive) "Upscaling now" else "Restores detail in compressed audio", it, onChange = vm::setDsee)
        },
        state.speakToChat?.let {
            Setting.Toggle(Icons.Outlined.ChatBubbleOutline, "Speak-to-Chat", "Pause and let sound in when you talk", it,
                onChange = vm::setSpeakToChat)
        },
    )
    return if (rows.isEmpty()) null else SettingsGroup(null, rows, Tab.LISTEN)
}

@Composable
private fun buttons(state: HeadphonesState, vm: HeadphonesController, ui: SonyDeviceUi, busy: Boolean): SettingsGroup {
    val context = LocalContext.current
    var who by remember { mutableStateOf(Assistants.current(context)) }
    val handles = rememberOnResume { Assistants.handlesVoiceButton(context) }
    val rows = buildList {
        state.customButton?.let { preset ->
            val opts = (Labels.xm4CustomButtonOptions + preset).distinct().map { it to (Labels.customButton[it] ?: Labels.hex(it)) }
            add(Setting.Choice(Icons.Outlined.RadioButtonChecked, "CUSTOM button", opts, preset, enabled = !busy,
                confirm = { reconnect() }, onSelect = vm::setCustomButton))
        }
        // Touch and hold sits next to the CUSTOM button: both are about what the headphone buttons do.
        add(Setting.Choice(Icons.Outlined.AutoAwesome, "Touch and hold", Assistant.entries.map { it.ordinal to it.label }, who.ordinal) { v ->
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

private fun wearing(state: HeadphonesState, vm: HeadphonesController): SettingsGroup {
    val r = state.optimizerResult
    val running = state.optimizerRunning
    val optimizer = Setting.Action(
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
    return SettingsGroup("Wearing", listOfNotNull(optimizer, pause))
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
    val rows = listOfNotNull(
        state.autoPowerOff?.let { (active, _) ->
            val ids = (state.autoPowerOffOptions.ifEmpty { listOf(0x10, 0x11) } + active).distinct()
            Setting.Choice(Icons.Outlined.Timer, "Automatic power off", ids.map { it to (Labels.autoPowerOff[it] ?: Labels.hex(it)) }, active,
                onSelect = vm::setAutoPowerOff)
        },
        state.voiceGuidance?.let { on ->
            Setting.Toggle(Icons.Outlined.Campaign, "Notifications & voice guidance",
                state.voiceGuidanceLanguage?.let { "Language: ${Labels.languages[it] ?: "#$it"}" }, on, onChange = vm::setVoiceGuidance)
        },
    )
    return if (rows.isEmpty()) null else SettingsGroup("Power & prompts", rows)
}
