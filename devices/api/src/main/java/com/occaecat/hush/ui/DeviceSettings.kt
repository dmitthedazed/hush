@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.occaecat.hush.devices.BatteryLevel
import com.occaecat.hush.devices.BatteryPart
import com.occaecat.hush.devices.Confirm
import com.occaecat.hush.devices.NoiseControl
import com.occaecat.hush.devices.NoiseUi
import com.occaecat.hush.devices.Setting
import com.occaecat.hush.devices.SettingsGroup
import kotlin.math.roundToInt

/**
 * Noise control: a connected button group (72dp, icon over label); a mode with options opens a surfaceContainerLow
 * block under it with the level and Focus on voice.
 */
@Composable
fun NoiseControlCard(noise: NoiseControl, ui: NoiseUi, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    val modes = noise.modes
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            modes.forEachIndexed { i, m ->
                val press = remember { MutableInteractionSource() }
                val on = ui.active == m.id
                ToggleButton(
                    checked = on,
                    onCheckedChange = { noise.setMode(m.id) },
                    interactionSource = press,
                    modifier = Modifier.weight(1f).height(72.dp).squishy(press, 0.92f).semantics { role = Role.RadioButton },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = Space.s),
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(m.icon), m.title, Modifier.size(28.dp).hopOnSelect(on))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            m.label, maxLines = 1, softWrap = false,
                            style = MaterialTheme.typography.labelLarge,
                            // Narrow columns (small phones, landscape halves) shrink the label rather than clip it.
                            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                        )
                    }
                }
            }
        }
        val options = modes.firstOrNull { it.id == ui.active }?.hasOptions == true && (ui.level != null || ui.voiceFocus != null)
        AnimatedVisibility(
            visible = options,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(
                Modifier.padding(top = Space.s).fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(Space.m),
            ) {
                ui.level?.let { level ->
                    var drag by remember { mutableFloatStateOf(level.value.toFloat()) }
                    var dragging by remember { mutableStateOf(false) }
                    val shown = if (dragging) drag else level.value.toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(level.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${shown.roundToInt()}", style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                    }
                    val r = level.range
                    Slider(
                        value = shown,
                        onValueChange = { dragging = true; drag = it; noise.setLevel(it.roundToInt(), dragging = true) },
                        onValueChangeFinished = { dragging = false; noise.setLevel(drag.roundToInt(), dragging = false) },
                        valueRange = r.first.toFloat()..r.last.toFloat(),
                        steps = (r.last - r.first - 1).coerceAtLeast(0),
                        colors = quietSliderColors(),
                    )
                }
                ui.voiceFocus?.let { focus ->
                    if (ui.level != null) Spacer(Modifier.height(Space.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LeadIcon(Icons.Outlined.RecordVoiceOver)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text("Focus on voice", style = MaterialTheme.typography.bodyLarge)
                            Text("Let voices through, keep the rest quieter", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = focus, onCheckedChange = noise::setVoiceFocus)
                    }
                }
            }
        }
    }
}

/** A device's settings group: header, segmented rows, an optional note; choices open a sheet, changes may ask first. */
@Composable
fun SettingsGroupView(group: SettingsGroup, modifier: Modifier) {
    var choice by remember { mutableStateOf<Choice?>(null) }
    var confirm by remember { mutableStateOf<Pair<Confirm, () -> Unit>?>(null) }
    fun guarded(question: Confirm?, apply: () -> Unit) { if (question == null) apply() else confirm = question to apply }
    Column(modifier) {
        group.title?.let { SectionHeader(it, top = Space.s) }
        SegmentedGroup {
            val n = group.items.size
            group.items.forEachIndexed { i, item ->
                when (item) {
                    is Setting.Toggle -> SwitchRow(i, n, item.icon, item.title, item.summary, item.checked, item.enabled) { v ->
                        guarded(item.confirm?.invoke(v)) { item.onChange(v) }
                    }
                    is Setting.Choice -> ValueRow(i, n, item.icon, item.title, item.value, item.enabled) {
                        choice = Choice(item.title, item.options, item.selected) { v ->
                            if (v != item.selected) guarded(item.confirm?.invoke(v)) { item.onSelect(v) }
                        }
                    }
                    is Setting.Action -> ActionRow(i, n, item)
                }
            }
        }
        group.note?.let {
            Text(
                it, style = MaterialTheme.typography.bodySmall,
                color = if (group.noteIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s),
            )
        }
    }
    choice?.let { ChoiceSheet(it) { choice = null } }
    confirm?.let { (q, apply) ->
        ConfirmDialog(q.message, onConfirm = apply, onDismiss = { confirm = null }, title = q.title, confirm = q.button)
    }
}

@Composable
private fun ActionRow(index: Int, count: Int, a: Setting.Action) {
    if (a.button == null && !a.busy) return ValueRow(index, count, a.icon, a.title, a.summary, a.enabled, onClick = a.onClick)
    SegmentedListItem(
        onClick = a.onClick,
        enabled = a.enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.colors(
            containerColor = if (a.busy) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        leadingContent = { if (a.busy) LoadingIndicator(Modifier.size(40.dp)) else LeadIcon(a.icon, a.enabled) },
        supportingContent = a.summary?.let { { Text(it) } },
        trailingContent = a.button?.let { label ->
            {
                if (a.busy) TextButton(onClick = a.onClick) { Text(label) }
                else FilledTonalButton(onClick = a.onClick, enabled = a.enabled) { Text(label) }
            }
        },
        content = { Text(a.title) },
    )
}

/** The hero's battery: one gauge, or one per bud and the case. */
@Composable
fun BatteryStatus(batteries: List<BatteryLevel>) {
    if (batteries.size == 1 && batteries[0].part == BatteryPart.MAIN) return BatteryGauge(batteries[0].level, batteries[0].charging)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
        batteries.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.part.label.take(1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Space.xs))
                BatteryGauge(b.level, b.charging)
            }
        }
    }
}

/** Short battery facts for the device card ("72% · charging", "L 80%"). */
fun batteryFacts(batteries: List<BatteryLevel>): List<String> = batteries.map { b ->
    val head = if (b.part == BatteryPart.MAIN) "" else b.part.label.take(1) + " "
    head + "${b.level}%" + if (b.charging) " · charging" else ""
}
