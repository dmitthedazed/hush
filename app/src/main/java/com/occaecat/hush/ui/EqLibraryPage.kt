@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.occaecat.hush.devices.EqLibrary
import com.occaecat.hush.devices.Equalizer
import kotlin.math.abs

/** The EQ library for the current equalizer: save, apply, store on the device, rename, share, import, delete. */
@Composable
fun EqLibraryPage(eq: Equalizer, onClose: () -> Unit) {
    val context = LocalContext.current
    val all by EqLibrary.items.collectAsState()
    val ui by eq.state.collectAsState()
    val items = all.filter { it.layout == eq.layout }
    var naming by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<EqLibrary.Curve?>(null) }
    var deleting by remember { mutableStateOf<EqLibrary.Curve?>(null) }
    val current = ui
    val range = current?.range ?: -10..10

    FullScreenPage("EQ library", onClose) {
        Text(
            listOfNotNull("Keep as many equalizer curves as you like.", eq.libraryNote).joinToString(" "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s),
        )
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            FilledTonalButton(onClick = { naming = true }, enabled = current?.levels?.isNotEmpty() == true, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Outlined.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Save current")
            }
            FilledTonalButton(onClick = { importing = true }, enabled = current != null, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Import")
            }
        }
        Spacer(Modifier.height(Space.m))
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShapeBadge(Icons.Outlined.Equalizer, size = 72.dp)
                Spacer(Modifier.height(Space.m))
                Text("No saved curves yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Save the current EQ, import a code, or keep results from AutoEq and the sound tools.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            items.forEachIndexed { i, item ->
                var menu by remember { mutableStateOf(false) }
                SegmentedListItem(
                    onClick = { eq.applyLevels(item.levels) },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = items.size),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    leadingContent = { CurveShape(item.levels, range, Modifier.size(width = 56.dp, height = 32.dp)) },
                    supportingContent = { Text(item.source ?: "Tap to apply", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Edit, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                eq.slots.forEach { (slot, name) ->
                                    DropdownMenuItem(text = { Text("Save to $name") }, onClick = { menu = false; eq.saveToSlot(slot, item.levels) })
                                }
                                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick = { menu = false; renaming = item })
                                DropdownMenuItem(text = { Text("Share code") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                                    menu = false
                                    context.startActivity(Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, EqLibrary.shareCode(item)),
                                        item.name,
                                    ))
                                })
                                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                    onClick = { menu = false; deleting = item })
                            }
                        }
                    },
                    content = { Text(item.name) },
                )
            }
        }
        Spacer(Modifier.height(Space.l))
    }

    if (naming && current != null) NameDialog("Save current EQ", current.preset, "Save") { name ->
        naming = false
        if (name != null) EqLibrary.save(name, eq.layout, current.levels, "Saved from ${current.preset}")
    }
    renaming?.let { item ->
        NameDialog("Rename", item.name, "Rename") { name -> renaming = null; if (name != null) EqLibrary.rename(item.id, name) }
    }
    deleting?.let { item ->
        ConfirmDialog(
            "Automations that use it will stop changing the EQ.",
            onConfirm = { EqLibrary.delete(item.id); deleting = null },
            onDismiss = { deleting = null },
            title = "Delete “${item.name}”?",
            confirm = "Delete",
        )
    }
    if (importing && current != null) {
        var text by remember { mutableStateOf("") }
        val parsed = EqLibrary.parseCode(text, eq.layout, current.levels.size, range)
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("Import EQ code") },
            text = {
                Column {
                    Text("Paste a code shared from Hush (HUSHEQ…).", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Space.s))
                    OutlinedTextField(text, { text = it }, minLines = 2, isError = text.isNotBlank() && parsed == null)
                    if (text.isNotBlank() && parsed == null) {
                        Text("Not a valid code for this equalizer", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { parsed?.let { (name, lv) -> EqLibrary.save(name, eq.layout, lv, "Imported") }; importing = false }, enabled = parsed != null) {
                    Text("Import")
                }
            },
            dismissButton = { TextButton(onClick = { importing = false }) { Text("Cancel") } },
        )
    }
}

/** Tiny bars around a centre line: the shape of a curve at a glance. */
@Composable
fun CurveShape(levels: List<Int>, range: IntRange, modifier: Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val top = maxOf(abs(range.first), abs(range.last)).coerceAtLeast(1).toFloat()
    Canvas(modifier) {
        val n = levels.size.coerceAtLeast(1)
        val gap = (if (n > 8) 2 else 4).dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val mid = size.height / 2
        drawLine(color.copy(alpha = 0.3f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        levels.forEachIndexed { i, db ->
            val h = (abs(db) / top * mid).coerceAtLeast(2.dp.toPx())
            drawRoundRect(color, Offset(i * (w + gap), if (db >= 0) mid - h else mid), Size(w, h), CornerRadius(w / 2, w / 2))
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it.take(40) }, singleLine = true, label = { Text("Name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
        },
        confirmButton = { TextButton(onClick = { onDone(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}
