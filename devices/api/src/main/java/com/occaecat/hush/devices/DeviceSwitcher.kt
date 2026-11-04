@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.devices

import com.occaecat.hush.ui.segShapes
import com.occaecat.hush.ui.segShapes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.occaecat.hush.ui.SegmentedGroup
import com.occaecat.hush.ui.Space
import kotlinx.coroutines.launch

/**
 * An app-bar button opening a sheet of every registered device (connected or not), the one shown ticked, and a
 * way to add another. Picking one closes the sheet first, so the switch plays after it.
 */
@Composable
fun DeviceSwitcher() {
    val host = LocalDeviceHost.current ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Devices, "Switch device") }
    if (!open) return
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { open = false; then() }
    }
    ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.m).padding(bottom = Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Space.s, bottom = Space.m)) {
                Text("Your devices", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { close(host::add) }) {
                    Icon(Icons.Outlined.Add, null)
                    Text("Add", Modifier.padding(start = Space.xs))
                }
            }
            SegmentedGroup {
                host.all.forEachIndexed { i, d ->
                    val picked = d.key == host.current?.key
                    SegmentedListItem(
                        selected = picked,
                        onClick = { close { if (!picked) host.open(d) } },
                        shapes = segShapes(index = i, count = host.all.size),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = { host.Thumb(d) },
                        supportingContent = {
                            Text(
                                if (d.isDemo) "Demo" else if (d.active) "Connected" else "Not connected",
                                color = if (d.active || d.isDemo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = if (picked) ({ Icon(Icons.Outlined.CheckCircle, "Shown", tint = MaterialTheme.colorScheme.primary) }) else null,
                        content = { Text(d.name) },
                    )
                }
            }
        }
    }
}
