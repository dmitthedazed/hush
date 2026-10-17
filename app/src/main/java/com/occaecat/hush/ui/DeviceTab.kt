@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import com.occaecat.hush.assistant.Assistant
import com.occaecat.hush.assistant.Assistants
import com.occaecat.hush.mdr.PeerDevice
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SegmentedListItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.material3.Surface
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.occaecat.hush.R
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase

private val TouchTitles = setOf("TOUCH_PANEL_SETTING", "TOUCH_SENSOR_CONTROL_PANEL")

@Composable
fun DeviceTab(state: HeadphonesState, vm: HeadphonesController) {
    val dialogs = rememberDialogState()
    var guide by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var details by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var fwPage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val fw = rememberFirmwareCheck(state)
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val link by DeepLink.target.collectAsState()
    LaunchedEffect(link) { if (link == "firmware") { fwPage = true; DeepLink.consume() } }
    val busy = state.audioSettling || state.phase != Phase.CONNECTED
    val connected = state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING

    // The card below already shows status, battery and codec.
    TabScaffold(title = "Device", subtitle = null) {
        if (state.phase == Phase.RECONNECTING || state.audioSettling) wideItem { ReconnectingBanner(state, ItemWidth) }
        if (!connected) wideItem { NotConnectedPlaceholder(state, ItemWidth) }

        // ---------------------------------------------------------------- identity + quick actions
        item { DeviceCard(state, vm, connected, ItemWidth.padding(top = Space.s)) }

        // Where the headphones are matters most while they're *not* connected, so this stays outside.
        if (!connected) item { InsightsSection(state, ItemWidth) { page = it } }
        if (!connected) item {
            Column(ItemWidth.padding(bottom = Space.l)) {
                SectionHeader("About")
                SegmentedGroup {
                    ValueRow(0, 3, Icons.AutoMirrored.Outlined.HelpOutline, "Help", "Guide, tour and Sony support") { page = "help" }
                    ValueRow(1, 3, Icons.Outlined.Backup, "Backup & restore", "Move Hush to a new phone") { page = "backup" }
                    ValueRow(2, 3, Icons.Outlined.Favorite, "About Hush", "Version, author, licenses") { page = "about" }
                }
            }
        }

        if (connected) {
            // ---------------------------------------------------------------- buttons
            val touchGs = state.generalSettings.values
                .filter { it.title != "MULTIPOINT_SETTING" && it.isBoolean && state.generalValues[it.id] != null }
                .sortedBy { it.id }
            item {
                val ai = rememberAssistantUi()
                val rows = buildList<@Composable (Int, Int) -> Unit> {
                    state.customButton?.let { preset ->
                        add { i, n ->
                            ValueRow(i, n, Icons.Outlined.RadioButtonChecked, "CUSTOM button",
                                Labels.customButton[preset] ?: Labels.hex(preset), enabled = !busy) {
                                val opts = (Labels.xm4CustomButtonOptions + preset).distinct()
                                    .map { it to (Labels.customButton[it] ?: Labels.hex(it)) }
                                dialogs.choice = Choice("CUSTOM button", opts, preset) { v ->
                                    if (v != preset) dialogs.confirmReconnect { vm.setCustomButton(v) }
                                }
                            }
                        }
                    }
                    // Touch and hold sits next to the CUSTOM button: both are about what the headphone buttons do.
                    add { i, n -> AssistantRow(ai, i, n) }
                    touchGs.forEach { gs ->
                        add { i, n ->
                            SwitchRow(i, n, if (gs.title in TouchTitles) Icons.Outlined.TouchApp else Icons.Outlined.SettingsSuggest,
                                Labels.gsTitle(gs.title), Labels.gsSupporting(gs.title), state.generalValues[gs.id] == true,
                                enabled = state.generalEnabled[gs.id] != false) { vm.setGeneralSetting(gs.id, it) }
                        }
                    }
                    // Sound Connect's instruction guide (gestures, Quick Attention, CUSTOM button), from Sony's Help Guide.
                    if (state.guideCategories?.isEmpty() != true) add { i, n ->
                        ValueRow(i, n, Icons.AutoMirrored.Outlined.MenuBook, "How to use", "Gestures and buttons") { guide = true }
                    }
                }
                Column(ItemWidth) {
                    SectionHeader("Buttons")
                    SegmentedGroup { rows.forEachIndexed { i, row -> row(i, rows.size) } }
                    AssistantHint(ai, state)
                }
                AssistantPicker(ai)
            }

            // ---------------------------------------------------------------- wearing
            item {
                Column(ItemWidth) {
                    SectionHeader("Wearing")
                    val n = if (state.pauseWhenRemoved != null) 2 else 1
                    SegmentedGroup {
                        OptimizerRow(state, vm, 0, n)
                        state.pauseWhenRemoved?.let { on ->
                            SwitchRow(1, n, Icons.Outlined.PauseCircleOutline, "Pause when taken off",
                                if (state.pauseWhenRemovedAvailable) "Resume when you put them back on" else "Not available right now",
                                on, enabled = state.pauseWhenRemovedAvailable, onCheckedChange = vm::setPauseWhenRemoved)
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- insights
            item { InsightsSection(state, ItemWidth) { page = it } }

            // ---------------------------------------------------------------- conversation
            state.speakToChat?.let { stc ->
                item {
                    Column(ItemWidth) {
                        SectionHeader("Conversation")
                        val d = state.speakToChatDetail
                        val count = if (stc && d != null) 4 else 1
                        SegmentedGroup {
                            SwitchRow(0, count, Icons.Outlined.ChatBubbleOutline, "Speak-to-Chat",
                                "Pause music and let sound in when you start talking", stc, onCheckedChange = vm::setSpeakToChat)
                            if (stc && d != null) {
                                ValueRow(1, count, Icons.Outlined.SignalCellularAlt, "Voice detection sensitivity",
                                    Labels.stcSensitivity[d.sensitivity]) {
                                    dialogs.choice = Choice("Voice detection sensitivity", Labels.stcSensitivity.toList(), d.sensitivity) {
                                        vm.setSpeakToChatDetail(d.copy(sensitivity = it))
                                    }
                                }
                                SwitchRow(2, count, Icons.Outlined.RecordVoiceOver, "Voice passthrough",
                                    "Make voices easier to hear", d.voiceFocus) { vm.setSpeakToChatDetail(d.copy(voiceFocus = it)) }
                                ValueRow(3, count, Icons.Outlined.Timelapse, "End Speak-to-Chat", Labels.stcModeOut[d.modeOutTime]) {
                                    dialogs.choice = Choice("End Speak-to-Chat", Labels.stcModeOut.toList(), d.modeOutTime) {
                                        vm.setSpeakToChatDetail(d.copy(modeOutTime = it))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- connection
            item { ConnectionSection(state, vm, busy, dialogs, ItemWidth) }

            // ---------------------------------------------------------------- power & prompts
            val power = buildList<@Composable (Int, Int) -> Unit> {
                state.autoPowerOff?.let { (active, _) ->
                    add { i, n ->
                        ValueRow(i, n, Icons.Outlined.Timer, "Automatic power off",
                            Labels.autoPowerOff[active] ?: Labels.hex(active)) {
                            val ids = state.autoPowerOffOptions.ifEmpty { listOf(0x10, 0x11) }
                            dialogs.choice = Choice("Automatic power off",
                                ids.map { it to (Labels.autoPowerOff[it] ?: Labels.hex(it)) }, active, vm::setAutoPowerOff)
                        }
                    }
                }
                state.voiceGuidance?.let { on ->
                    add { i, n ->
                        SwitchRow(i, n, Icons.Outlined.Campaign, "Notifications & voice guidance",
                            state.voiceGuidanceLanguage?.let { "Language: ${Labels.languages[it] ?: "#$it"}" },
                            on, onCheckedChange = vm::setVoiceGuidance)
                    }
                }
            }
            if (power.isNotEmpty()) item {
                Column(ItemWidth) {
                    SectionHeader("Power & prompts")
                    SegmentedGroup { power.forEachIndexed { i, row -> row(i, power.size) } }
                }
            }

            // ---------------------------------------------------------------- about
            item {
                Column(ItemWidth.padding(bottom = Space.l)) {
                    SectionHeader("About")
                    SegmentedGroup {
                        ValueRow(0, 5, Icons.Outlined.SystemUpdate, "Firmware update", firmwareSummary(fw, state)) { fwPage = true }
                        ValueRow(1, 5, Icons.AutoMirrored.Outlined.HelpOutline, "Help", "Guide, tour and Sony support") { page = "help" }
                        ValueRow(2, 5, Icons.Outlined.Backup, "Backup & restore", "Move Hush to a new phone") { page = "backup" }
                        ValueRow(3, 5, Icons.Outlined.Info, "Device details",
                            listOfNotNull(state.updateInfo[4], state.updateInfo[3]).joinToString(" · ").ifEmpty { null }) { details = true }
                        ValueRow(4, 5, Icons.Outlined.Favorite, "About Hush", "Version, author, licenses") { page = "about" }
                    }
                }
            }
        }
    }
    DialogHost(dialogs)
    if (guide) GuidePage(state) { guide = false }
    if (details) DeviceDetailsPage(state) { details = false }
    if (fwPage) FirmwarePage(state, fw) { fwPage = false }
    when (val p = page) {
        null -> {}
        "help" -> HelpPage(state, onGuide = { page = null; guide = true }, onTour = { page = "tour" }) { page = null }
        "tour" -> TourPage(state) { page = null }
        "backup" -> BackupPage(state) { page = null }
        "about" -> AboutPage { page = null }
        else -> InsightsPage(p, state) { page = null }
    }
}

/**
 * Who the headphones are, at a glance, with the actions that act on the whole device. Firmware and the
 * switch / turn off / forget actions used to be separate list sections at the bottom.
 */
@Composable
private fun DeviceCard(state: HeadphonesState, vm: HeadphonesController, connected: Boolean, modifier: Modifier) {
    var confirm by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    // Device is about the hardware: the model, not the phone's nickname for it.
    val name = state.modelName ?: state.displayName
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh) {
        Column(Modifier.padding(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(renderFor(state.modelColor)), contentDescription = null,
                    modifier = Modifier.size(88.dp),
                    contentScale = ContentScale.Fit,
                    alpha = if (connected) 1f else 0.5f,
                    colorFilter = if (connected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                )
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLargeEmphasized)
                    val facts = listOfNotNull(
                        state.battery?.takeIf { connected }?.let { "$it%" + if (state.charging) " · charging" else "" },
                        state.codec?.label?.takeIf { connected && it != "—" },
                        state.firmware?.let { "v$it" },
                    )
                    if (facts.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        facts.forEach { Fact(it) }
                    }
                }
            }
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                FilledTonalButton(onClick = vm::disconnect, Modifier.weight(1f), contentPadding = pad) {
                    Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Switch", maxLines = 1)
                }
                if (state.phase == Phase.CONNECTED) FilledTonalButton(onClick = { confirm = "off" }, Modifier.weight(1f), contentPadding = pad) {
                    Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Turn off", maxLines = 1)
                }
                OutlinedButton(onClick = { confirm = "forget" }, Modifier.weight(1f), contentPadding = pad) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Forget", maxLines = 1)
                }
            }
        }
    }
    when (confirm) {
        "off" -> ConfirmDialog("Turn on the headphones with their power button to use them again.",
            onConfirm = { vm.powerOff() }, onDismiss = { confirm = null }, title = "Turn off $name?", confirm = "Turn off")
        "forget" -> ConfirmDialog("Hush stops connecting to $name. They stay paired with the phone.",
            onConfirm = vm::forget, onDismiss = { confirm = null }, title = "Forget $name?", confirm = "Forget")
    }
}

@Composable
private fun Fact(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = Space.s, vertical = 2.dp))
    }
}

/** NC optimizer as a row: the last result in the supporting line, the action on the right. */
@Composable
private fun OptimizerRow(state: HeadphonesState, vm: HeadphonesController, index: Int, count: Int) {
    val r = state.optimizerResult
    val running = state.optimizerRunning
    SegmentedListItem(
        onClick = { if (running) vm.cancelOptimizer() else vm.startOptimizer() },
        enabled = state.phase == Phase.CONNECTED,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.colors(
            containerColor = if (running) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        leadingContent = { if (running) LoadingIndicator(Modifier.size(40.dp)) else LeadIcon(Icons.Outlined.Speed) },
        supportingContent = {
            Text(
                if (running) "Keep the headphones on and stay still"
                else listOfNotNull(
                    r?.personalMeasured?.let { if (it) "Fitted to you" else "Fit not measured" },
                    r?.pressureTenthsAtm?.let { "%.1f atm".format(it / 10f) },
                ).joinToString(" · ").ifEmpty { "Tune noise cancelling to your fit and air pressure" },
            )
        },
        trailingContent = {
            if (running) androidx.compose.material3.TextButton(onClick = vm::cancelOptimizer) { Text("Cancel") }
            else FilledTonalButton(onClick = vm::startOptimizer, enabled = state.phase == Phase.CONNECTED) { Text("Optimize") }
        },
        content = { Text("NC optimizer") },
    )
}


/**
 * Multipoint and pairing. The XM4 doesn't report its other connections (no PAIRING_DEVICE_MANAGEMENT function),
 * but it does take Sound Connect's pairing-mode command, so a second source can be paired from here; headsets
 * that do report their paired sources get the full list with connect / disconnect / unpair.
 */
@Composable
private fun ConnectionSection(state: HeadphonesState, vm: HeadphonesController, busy: Boolean, dialogs: DialogState, modifier: Modifier) {
    val multipoint = state.generalSettings.values.firstOrNull { it.title == "MULTIPOINT_SETTING" }
    val mpOn = multipoint?.let { state.generalValues[it.id] }
    val peers = state.peers
    Column(modifier) {
        SectionHeader("Connection")
        SegmentedGroup {
            val rows = buildList<@Composable (Int, Int) -> Unit> {
                if (multipoint != null && mpOn != null) add { i, n ->
                    SwitchRow(i, n, Icons.Outlined.Devices, Labels.gsTitle(multipoint.title), Labels.gsSupporting(multipoint.title),
                        mpOn, enabled = !busy && state.generalEnabled[multipoint.id] != false) { v ->
                        dialogs.confirmReconnect(if (v) "LDAC cannot be used while this is on." else null) { vm.setGeneralSetting(multipoint.id, v) }
                    }
                }
                peers?.forEach { p ->
                    add { i, n -> PeerRow(i, n, p, p.order == state.peerPlayback && p.connected, state.address, vm, dialogs) }
                }
                add { i, n ->
                    SegmentedListItem(
                        onClick = {
                            when {
                                state.pairingMode -> vm.setPairingMode(false)
                                // The headset only goes discoverable with multipoint on; offer to turn it on first.
                                mpOn == false && multipoint != null -> dialogs.confirmReconnect(
                                    "Pairing another device needs multipoint, and LDAC cannot be used while it is on. " +
                                        "When the headphones are back, tap Pair a new device again.",
                                ) { vm.setGeneralSetting(multipoint.id, true) }
                                else -> vm.setPairingMode(true)
                            }
                        },
                        enabled = !busy,
                        shapes = ListItemDefaults.segmentedShapes(index = i, count = n),
                        colors = ListItemDefaults.colors(
                            containerColor = if (state.pairingMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        leadingContent = {
                            if (state.pairingMode) LoadingIndicator(Modifier.size(40.dp)) else LeadIcon(Icons.Outlined.BluetoothSearching)
                        },
                        supportingContent = {
                            Text(
                                if (state.pairingMode) "Discoverable now — pick ${state.modelName ?: "the headphones"} in the other device's Bluetooth settings. Tap to stop."
                                else if (mpOn == false) "Needs multipoint — tap to turn it on first"
                                else "Make the headphones discoverable for another phone, tablet or laptop",
                            )
                        },
                        trailingContent = if (state.pairingMode) null else {
                            { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        },
                        content = { Text(if (state.pairingMode) "Pairing mode on" else "Pair a new device") },
                    )
                }
            }
            rows.forEachIndexed { i, r -> r(i, rows.size) }
        }
        if (peers == null && mpOn == true) {
            Text(
                "These headphones don't report which other devices are connected; the one playing gets the sound.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
            )
        }
        state.peerResult?.let { (code, addr) ->
        val text = when (code) {
            0x00 -> "Disconnected"; 0x01 -> "Couldn't disconnect"; 0x10 -> "Connected"; 0x11 -> "Couldn't connect"
            0x20 -> "Removed"; 0x21 -> "Couldn't remove"; else -> null
        }
        if (text != null) {
            val name = peers?.firstOrNull { it.address.equals(addr, ignoreCase = true) }?.name ?: addr
            Text("$text: $name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Space.m))
        }
        LaunchedEffect(code, addr) {
            kotlinx.coroutines.delay(4_000)
            vm.clearPeerResult()
        }
    }
    }
}

@Composable
private fun PeerRow(
    index: Int, count: Int, p: PeerDevice, playing: Boolean, phoneAddress: String?, vm: HeadphonesController, dialogs: DialogState,
) {
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    if (removing) ConfirmDialog(
        "${p.name} will have to pair again to connect.",
        onConfirm = { vm.unpairPeer(p.address) },
        onDismiss = { removing = false },
        title = "Remove ${p.name}?",
        confirm = "Remove",
    )
    val isThisPhone = phoneAddress != null && p.address.equals(phoneAddress, ignoreCase = true)
    SegmentedListItem(
        onClick = { if (p.connected) vm.disconnectPeer(p.address) else vm.connectPeer(p.address) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { LeadIcon(if (p.connected) Icons.Outlined.BluetoothConnected else Icons.Outlined.Bluetooth, enabled = p.connected) },
        supportingContent = {
            Text(listOfNotNull(
                if (p.connected) "Connected" else "Not connected",
                "playing".takeIf { playing },
                "this phone".takeIf { isThisPhone },
            ).joinToString(" · "))
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (p.connected) "Disconnect" else "Connect") },
                        onClick = { menu = false; if (p.connected) vm.disconnectPeer(p.address) else vm.connectPeer(p.address) },
                    )
                    DropdownMenuItem(text = { Text("Remove from headphones") }, onClick = { menu = false; removing = true })
                }
            }
        },
        content = { Text(p.name) },
    )
}

/**
 * AI assistant: the headphones' voice button (touch and hold) opens ChatGPT or Claude in voice mode.
 */
private class AssistantUi(who: Assistant, handles: Boolean) {
    var who by mutableStateOf(who)
    var handles by mutableStateOf(handles)
    var picking by mutableStateOf(false)
}

@Composable
private fun rememberAssistantUi(): AssistantUi {
    val context = LocalContext.current
    val ui = remember { AssistantUi(Assistants.current(context), Assistants.handlesVoiceButton(context)) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ui.handles = Assistants.handlesVoiceButton(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return ui
}

/** Touch and hold on the right ear cup asks the phone for voice input; Hush can hand that to ChatGPT or Claude. */
@Composable
private fun AssistantRow(ui: AssistantUi, index: Int, count: Int) {
    ValueRow(index, count, Icons.Outlined.AutoAwesome, "Touch and hold", ui.who.label) { ui.picking = true }
}

@Composable
private fun AssistantHint(ui: AssistantUi, state: HeadphonesState) {
    val context = LocalContext.current
    val who = ui.who
    // CUSTOM button on Gemini / Alexa takes the touch-and-hold for itself.
    val buttonTaken = state.customButton == 0x31 || state.customButton == 0x32
    val hint = when {
        who == Assistant.PHONE -> null
        !Assistants.installed(context, who) -> "${who.label} isn't installed. Get it from Google Play, then sign in."
        buttonTaken -> "The CUSTOM button is set to ${if (state.customButton == 0x31) "Gemini" else "Amazon Alexa"}, " +
            "which takes over touch and hold. Set it to Ambient Sound Control to use ${who.label}."
        !ui.handles -> "Touch and hold the right ear cup. The first time, Android asks which app to use: " +
            "pick \u201cHush AI assistant\u201d and \u201cAlways\u201d."
        else -> if (who.pkg != null) "Touch and hold the right ear cup to talk to ${who.label}." else "Touch and hold the right ear cup: ${who.label.lowercase()}."
    }
    hint?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (buttonTaken || !Assistants.installed(context, who)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
        )
    }
}

@Composable
private fun AssistantPicker(ui: AssistantUi) {
    val context = LocalContext.current
    if (ui.picking) {
        ChoiceSheet(
            Choice("Touch and hold", Assistant.entries.map { it.ordinal to it.label }, ui.who.ordinal) { v ->
                val a = Assistant.entries[v]
                Assistants.set(context, a)
                ui.who = a
                ui.handles = Assistants.handlesVoiceButton(context)
            },
        ) { ui.picking = false }
    }
}
