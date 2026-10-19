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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
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
import com.occaecat.hush.sony.R
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase

/** What Sony's device sections open: dialogs, the guide, help, backup, insights. */
class SonyDeviceUi {
    val dialogs = DialogState()
    var guide by mutableStateOf(false)
    var page by mutableStateOf<String?>(null)
}

/** Sony's cards on the shared Device page after its settings groups (sonySettings): multipoint and pairing. */
internal fun LazyStaggeredGridScope.sonyDevice(state: HeadphonesState, vm: HeadphonesController, ui: SonyDeviceUi) = with(ui) {
    val busy = state.audioSettling || state.phase != Phase.CONNECTED
    if (state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING) {
        item { ConnectionSection(state, vm, busy, dialogs, ItemWidth) }
    }
}

/** Sony's rows in the shared About group. */
internal fun sonyAbout(ui: SonyDeviceUi) = listOf(
    com.occaecat.hush.devices.AboutRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help", "Guide, tour and Sony support") { ui.page = "help" },
    com.occaecat.hush.devices.AboutRow(Icons.Outlined.Backup, "Backup & restore", "Move Hush to a new phone") { ui.page = "backup" },
)

@Composable
internal fun SonyDeviceOverlays(state: HeadphonesState, ui: SonyDeviceUi) = with(ui) {
    DialogHost(dialogs)
    if (guide) GuidePage(state) { guide = false }
    when (val p = page) {
        null -> {}
        "help" -> HelpPage(state, onGuide = { page = null; guide = true }, onTour = { page = "tour" }) { page = null }
        "tour" -> TourPage(state) { page = null }
        "backup" -> BackupPage(state) { page = null }
        else -> {}
    }
}

/** In the shared device card's action row: connect or disconnect, and turn the headset off. */
@Composable
internal fun RowScope.SonyCardActions(state: HeadphonesState, vm: HeadphonesController) {
    var confirm by remember { mutableStateOf(false) }
    val name = state.modelName ?: state.displayName
    val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
    val live = state.phase == Phase.CONNECTED || state.phase == Phase.CONNECTING || state.phase == Phase.RECONNECTING
    FilledTonalButton(onClick = { if (live) vm.disconnect() else state.address?.let(vm::connect) }, Modifier.weight(1f), contentPadding = pad) {
        Icon(if (live) Icons.Outlined.LinkOff else Icons.Outlined.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(if (live) "Disconnect" else "Connect", maxLines = 1)
    }
    if (state.phase == Phase.CONNECTED) FilledTonalButton(onClick = { confirm = true }, Modifier.weight(1f), contentPadding = pad) {
        Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Turn off", maxLines = 1)
    }
    if (confirm) ConfirmDialog("Turn on the headphones with their power button to use them again.",
        onConfirm = { vm.powerOff() }, onDismiss = { confirm = false }, title = "Turn off $name?", confirm = "Turn off")
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
        SectionHeader("Connection", top = Space.s)
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
