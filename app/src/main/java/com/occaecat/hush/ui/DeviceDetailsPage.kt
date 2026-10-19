@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.occaecat.hush.devices.Detail
import com.occaecat.hush.devices.DetailGroup

/** A device's facts, grouped; plain values copy on tap, identifiers stay hidden until tapped. */
@Composable
fun DeviceDetailsPage(groups: List<DetailGroup>, onClose: () -> Unit) {
    FullScreenPage("Device details", onClose) {
        groups.forEach { g ->
            SectionHeader(g.title)
            SegmentedGroup {
                g.rows.forEachIndexed { i, r -> if (r.hidden) HiddenRow(i, g.rows.size, r) else CopyRow(i, g.rows.size, r) }
            }
        }
    }
}

/** A plain fact; tapping copies it. */
@Composable
private fun CopyRow(index: Int, count: Int, r: Detail) {
    val clipboard = LocalClipboardManager.current
    SegmentedListItem(
        onClick = { clipboard.setText(AnnotatedString(r.value)) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { LeadIcon(r.icon ?: Icons.Outlined.Info) },
        supportingContent = { Text(r.value) },
        content = { Text(r.title) },
    )
}

/** An identifier shown as dots; tap to reveal, the trailing button copies it. */
@Composable
private fun HiddenRow(index: Int, count: Int, r: Detail) {
    var shown by rememberSaveable(r.title) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val value = r.value
    SegmentedListItem(
        onClick = { shown = !shown },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { LeadIcon(r.icon ?: Icons.Outlined.Info) },
        supportingContent = {
            Row {
                Text(if (shown) value else "•".repeat(value.length.coerceAtMost(16)))
            }
        },
        trailingContent = {
            Row {
                Icon(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                    Icon(Icons.Outlined.ContentCopy, "Copy ${r.title.lowercase()}")
                }
            }
        },
        content = { Text(r.title) },
    )
}

