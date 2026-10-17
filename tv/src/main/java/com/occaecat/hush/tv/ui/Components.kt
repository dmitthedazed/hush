package com.occaecat.hush.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.occaecat.hush.ui.Labels

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 4.dp, top = Tv.Gap, bottom = Tv.Small),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** List rows rest on a tonal card colour and invert on focus, the TV focus system's colour indication. */
@Composable
private fun rowColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
)

/** A whole-row toggle: OK flips it. The Switch is only an indicator, so focus never lands on it separately. */
@Composable
fun SwitchRow(
    icon: ImageVector,
    title: String,
    supporting: String?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        selected = false,
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        modifier = modifier,
        colors = rowColors(),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        leadingContent = { Icon(icon, null, Modifier.size(ListItemDefaults.IconSize)) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(it, maxLines = 2) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** A row that opens a choice or runs an action; shows the current value on the right. */
@Composable
fun ValueRow(
    icon: ImageVector,
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = rowColors(),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        leadingContent = { Icon(icon, null, Modifier.size(ListItemDefaults.IconSize)) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(it, maxLines = 2) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp))
            }
        },
    )
}

/** Read-only fact (firmware, address). Still focusable so the list scrolls with the D-pad. */
@Composable
fun InfoRow(icon: ImageVector, title: String, value: String, modifier: Modifier = Modifier) {
    ListItem(
        selected = false,
        onClick = {},
        modifier = modifier,
        colors = rowColors(),
        scale = ListItemDefaults.scale(focusedScale = 1f),
        leadingContent = { Icon(icon, null, Modifier.size(ListItemDefaults.IconSize)) },
        headlineContent = { Text(title) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
    )
}

/**
 * The TV stand-in for a slider: tv-material has none, and a D-pad can't drag. Focus the row, then
 * ◀ ▶ step the value; the headset gets every step (the controller's outbox is conflated).
 */
@Composable
fun StepperRow(
    icon: ImageVector,
    title: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { "$it" },
    onChange: (Int) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { if (value > range.first) onChange(value - 1); true }
                    Key.DirectionRight -> { if (value < range.last) onChange(value + 1); true }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(ListItemDefaults.IconSize))
                Spacer(Modifier.width(16.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (focused) {
                    Text("◀  ", style = MaterialTheme.typography.labelLarge)
                }
                Text(format(value), style = MaterialTheme.typography.titleMedium)
                if (focused) {
                    Text("  ▶", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(10.dp))
            val track = if (focused) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            val fill = if (focused) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.primary
            val frac = (value - range.first).toFloat() / (range.last - range.first).coerceAtLeast(1)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(track)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(fill))
            }
        }
    }
}

data class Confirm(val message: String, val button: String, val action: () -> Unit)

data class Choice(val title: String, val options: List<Pair<Int, String>>, val selected: Int, val onPick: (Int) -> Unit)

/** Holds the one dialog a screen may show at a time. */
class DialogState {
    var choice by mutableStateOf<Choice?>(null)
    var confirm by mutableStateOf<Confirm?>(null)

    fun confirmReconnect(extra: String? = null, action: () -> Unit) {
        confirm = Confirm(listOfNotNull(extra, Labels.RECONNECT + " Change?").joinToString(" "), "Change", action)
    }
}

@Composable
fun rememberDialogState() = remember { DialogState() }

@Composable
fun DialogHost(d: DialogState) {
    d.choice?.let { c -> ChoiceDialog(c) { d.choice = null } }
    d.confirm?.let { c ->
        ConfirmDialog("Headphones", c.message, confirm = c.button, onConfirm = c.action) { d.confirm = null }
    }
}

@Composable
private fun DialogSurface(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 360.dp, max = 480.dp),
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) { Column(Modifier.padding(24.dp)) { content() } }
    }
}

@Composable
fun ChoiceDialog(c: Choice, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    DialogSurface(onDismiss) {
        Text(c.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(c.options) { _, (id, label) ->
                val selected = id == c.selected
                ListItem(
                    selected = selected,
                    onClick = { c.onPick(id); onDismiss() },
                    modifier = if (selected || (c.options.none { it.first == c.selected } && id == c.options.first().first))
                        Modifier.focusRequester(first) else Modifier,
                    headlineContent = { Text(label) },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String,
    dismiss: String? = "Cancel",
    onConfirm: () -> Unit,
    onDismissed: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    DialogSurface({ onDismissed(); onDismiss() }) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tv.Gap, Alignment.End)) {
            if (dismiss != null) OutlinedButton(onClick = { onDismissed(); onDismiss() }) { Text(dismiss) }
            Button(onClick = { onConfirm(); onDismiss() }, modifier = Modifier.focusRequester(focus)) { Text(confirm) }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** The headset's own prompts (ALERT_NTFY_PARAM), answered the same way the phone app does. */
@Composable
fun HeadsetAlertDialog(type: Int, actionType: Int, onAnswer: (Int, Boolean) -> Unit) {
    val askYesNo = actionType == 1
    ConfirmDialog(
        title = "Headphones",
        message = Labels.headsetAlerts[type] ?: "The headphones need your confirmation (message $type).",
        confirm = if (askYesNo) "Change" else "OK",
        dismiss = if (askYesNo) "Cancel" else null,
        onConfirm = { onAnswer(type, true) },
        onDismissed = { onAnswer(type, false) },
        onDismiss = {},
    )
}
