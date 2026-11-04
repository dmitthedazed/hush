package com.occaecat.hush.tv.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.Composable
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.ui.Labels

private val TouchTitles = setOf("TOUCH_PANEL_SETTING", "TOUCH_SENSOR_CONTROL_PANEL")

@Composable
fun DeviceScreen(state: HeadphonesState, vm: HeadphonesController) {
    val dialogs = rememberDialogState()
    val busy = state.audioSettling || state.phase != Phase.CONNECTED
    SectionScaffold("Device") {
        if (sessionBanner(state, vm)) {
            state.speakToChat?.let { stc ->
                item { SectionHeader("Speak-to-Chat") }
                item {
                    SwitchRow(
                        Icons.Outlined.ChatBubbleOutline, "Speak-to-Chat",
                        "Pause music and let sound in when you start talking", stc, onCheckedChange = vm::setSpeakToChat,
                    )
                }
                val d = state.speakToChatDetail
                if (stc && d != null) {
                    item {
                        ValueRow(Icons.Outlined.SignalCellularAlt, "Voice detection sensitivity", Labels.stcSensitivity[d.sensitivity]) {
                            dialogs.choice = Choice("Voice detection sensitivity", Labels.stcSensitivity.toList(), d.sensitivity) {
                                vm.setSpeakToChatDetail(d.copy(sensitivity = it))
                            }
                        }
                    }
                    item {
                        SwitchRow(Icons.Outlined.RecordVoiceOver, "Voice passthrough", "Make voices easier to hear", d.voiceFocus) {
                            vm.setSpeakToChatDetail(d.copy(voiceFocus = it))
                        }
                    }
                    item {
                        ValueRow(Icons.Outlined.Timelapse, "End Speak-to-Chat", Labels.stcModeOut[d.modeOutTime]) {
                            dialogs.choice = Choice("End Speak-to-Chat", Labels.stcModeOut.toList(), d.modeOutTime) {
                                vm.setSpeakToChatDetail(d.copy(modeOutTime = it))
                            }
                        }
                    }
                }
            }

            item { SectionHeader("NC optimizer") }
            item {
                val r = state.optimizerResult
                val result = listOfNotNull(
                    r?.personalMeasured?.let { if (it) "Fitted to how you wear them" else "Wearing not measured" },
                    r?.pressureTenthsAtm?.let { "tuned for %.1f atm".format(it / 10f) },
                ).joinToString(", ").ifEmpty { "Optimizes noise cancelling for you and the air pressure" }
                ValueRow(
                    Icons.Outlined.Speed,
                    if (state.optimizerRunning) "Optimizing… keep them on and stay still" else "NC optimizer",
                    if (state.optimizerRunning) "Cancel" else "Optimize",
                    supporting = if (state.optimizerRunning) null else result,
                    enabled = state.phase == Phase.CONNECTED,
                ) { if (state.optimizerRunning) vm.cancelOptimizer() else vm.startOptimizer() }
            }

            val multipoint = state.generalSettings.values.firstOrNull { it.title == "MULTIPOINT_SETTING" }
            multipoint?.let { gs ->
                state.generalValues[gs.id]?.let { on ->
                    item { SectionHeader("Connection") }
                    item {
                        SwitchRow(
                            Icons.Outlined.Devices, Labels.gsTitle(gs.title), Labels.gsSupporting(gs.title), on,
                            enabled = !busy && state.generalEnabled[gs.id] != false,
                        ) { v ->
                            dialogs.confirmReconnect(if (v) "LDAC cannot be used while this is on." else null) {
                                vm.setGeneralSetting(gs.id, v)
                            }
                        }
                    }
                }
            }

            val controlGs = state.generalSettings.values
                .filter { it.title != "MULTIPOINT_SETTING" && it.isBoolean && state.generalValues[it.id] != null }
                .sortedBy { it.id }
            if (state.customButton != null || controlGs.isNotEmpty() || state.pauseWhenRemoved != null) {
                item { SectionHeader("Controls") }
            }
            state.customButton?.let { preset ->
                item {
                    ValueRow(
                        Icons.Outlined.RadioButtonChecked, "CUSTOM button",
                        Labels.customButton[preset] ?: Labels.hex(preset), enabled = !busy,
                    ) {
                        val opts = (Labels.xm4CustomButtonOptions + preset).distinct()
                            .map { it to (Labels.customButton[it] ?: Labels.hex(it)) }
                        dialogs.choice = Choice("CUSTOM button", opts, preset) { v ->
                            if (v != preset) dialogs.confirmReconnect { vm.setCustomButton(v) }
                        }
                    }
                }
            }
            controlGs.forEach { gs ->
                item {
                    SwitchRow(
                        if (gs.title in TouchTitles) Icons.Outlined.TouchApp else Icons.Outlined.SettingsSuggest,
                        Labels.gsTitle(gs.title), Labels.gsSupporting(gs.title), state.generalValues[gs.id] == true,
                        enabled = state.generalEnabled[gs.id] != false,
                    ) { vm.setGeneralSetting(gs.id, it) }
                }
            }
            state.pauseWhenRemoved?.let { on ->
                item {
                    SwitchRow(
                        Icons.Outlined.PauseCircleOutline, "Pause when taken off",
                        if (state.pauseWhenRemovedAvailable) "Resume when you put them back on" else "Not available right now",
                        on, enabled = state.pauseWhenRemovedAvailable, onCheckedChange = vm::setPauseWhenRemoved,
                    )
                }
            }

            item { SectionHeader("Power & system") }
            state.autoPowerOff?.let { (active, _) ->
                item {
                    ValueRow(Icons.Outlined.Timer, "Automatic power off", Labels.autoPowerOff[active] ?: Labels.hex(active)) {
                        val ids = state.autoPowerOffOptions.ifEmpty { listOf(0x10, 0x11) }
                        dialogs.choice = Choice(
                            "Automatic power off",
                            ids.map { it to (Labels.autoPowerOff[it] ?: Labels.hex(it)) }, active, vm::setAutoPowerOff,
                        )
                    }
                }
            }
            state.voiceGuidance?.let { on ->
                item {
                    SwitchRow(
                        Icons.Outlined.Campaign, "Notifications & voice guidance",
                        state.voiceGuidanceLanguage?.let { "Language: ${Labels.languages[it] ?: "#$it"}" },
                        on, onCheckedChange = vm::setVoiceGuidance,
                    )
                }
            }
            item { InfoRow(Icons.Outlined.Info, "Firmware", state.firmware ?: "—") }
        }

        item { SectionHeader("Headphones") }
        item { ValueRow(Icons.Outlined.SwapHoriz, "Switch headphones", null, onClick = vm::disconnect) }
        item {
            val name = state.deviceName ?: "headphones"
            ValueRow(Icons.Outlined.DeleteOutline, "Forget $name", null) {
                dialogs.confirm = Confirm("Hush stops using $name until you pick them again. The Bluetooth pairing stays.", "Forget", vm::forget)
            }
        }
        if (state.phase == Phase.CONNECTED) item {
            ValueRow(Icons.Outlined.PowerSettingsNew, "Turn off headphones", null) {
                dialogs.confirm = Confirm("Turn the headphones off now?", "Turn off") { vm.powerOff() }
            }
        }
    }
    DialogHost(dialogs)
}
