@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/** 8dp spacing scale. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

/** Icon inside an expressive MaterialShapes container; optionally spins slowly (charging, connecting). */
/**
 * A product render on a light disc. The XM4 renders are black on transparent, so the disc stays light in both
 * themes; [dimmed] desaturates it for "not connected".
 */
@Composable
fun ProductDisk(@DrawableRes image: Int, size: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false) =
    ProductDisk(painterResource(image), size, modifier, dimmed)

@Composable
fun ProductDisk(image: androidx.compose.ui.graphics.painter.Painter, size: Dp, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val backdrop = if (colors.surface.luminance() < 0.5f) colors.onSurface.copy(alpha = 0.92f) else colors.surfaceContainerLowest
    Box(modifier.size(size).clip(CircleShape).background(backdrop), contentAlignment = Alignment.Center) {
        Image(
            image, contentDescription = null,
            modifier = Modifier.fillMaxSize(0.8f),
            contentScale = ContentScale.Fit,
            alpha = if (dimmed) 0.55f else 1f,
            colorFilter = if (dimmed) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
        )
    }
}

@Composable
fun ShapeBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    spinning: Boolean = false,
) {
    val angle = if (spinning) {
        val t = rememberInfiniteTransition(label = "spin")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart), label = "a")
        a
    } else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Surface(
            shape = polygon.toShape(),
            color = container,
            modifier = Modifier.size(size).rotate(angle),
        ) {}
        Icon(icon, null, tint = content, modifier = Modifier.size(size * 0.46f))
    }
}

/** Widens a grid item by [side] on both ends, past the grid's content padding (edge-to-edge rows). */
fun Modifier.bleed(side: Dp): Modifier = layout { m, c ->
    val extra = (side * 2).roundToPx()
    val p = m.measure(c.copy(minWidth = c.maxWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, top: Dp = Space.l) {
    Text(
        text,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Space.m, top = top, bottom = Space.s),
    )
}

/** A group of segmented list items: rounded ends, tight gaps (M3 Expressive lists). */
@Composable
fun SegmentedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content)
}

@Composable
private fun segmentColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    // A switched-on row is not a "selected" row; the switch already carries the state.
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
)

/** A list row's leading icon on a 40dp primaryContainer circle. */
@Composable
fun LeadIcon(icon: ImageVector, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (enabled) colors.primaryContainer else colors.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null, Modifier.size(24.dp),
            tint = if (enabled) colors.onPrimaryContainer else colors.onSurface.copy(alpha = 0.38f),
        )
    }
}

/** Toggle buttons sitting on a surfaceContainer card need a visible resting container. */
@Composable
fun onCardToggleColors() = androidx.compose.material3.ToggleButtonDefaults.toggleButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/** Track only: step dots are noise on 20-step sliders. */
@Composable
fun quietSliderColors() = androidx.compose.material3.SliderDefaults.colors(
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
fun SwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val press = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        interactionSource = press,
        modifier = Modifier.squishy(press, 0.97f),
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentColors(),
        leadingContent = { LeadIcon(icon, enabled) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        },
        content = { Text(title) },
    )
}

@Composable
fun ValueRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    value: String?,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    /** Instead of [icon] on its circle (a device picture). */
    leading: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val press = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    SegmentedListItem(
        onClick = onClick,
        interactionSource = press,
        modifier = modifier.squishy(press, 0.97f),
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentColors(),
        leadingContent = leading ?: { LeadIcon(icon, enabled) },
        supportingContent = value?.let { { Text(it, color = MaterialTheme.colorScheme.primary) } },
        trailingContent = trailing ?: {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        content = { Text(title) },
    )
}

@Composable
fun InfoRow(index: Int, count: Int, icon: ImageVector, title: String, value: String) {
    SegmentedListItem(
        onClick = {},
        enabled = false,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            // A read-only row is still part of the group; without this it renders with no container at all.
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
            disabledLeadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        leadingContent = { LeadIcon(icon) },
        supportingContent = { Text(value) },
        content = { Text(title) },
    )
}

/** Choice from a list, shown as a bottom sheet of segmented items. */
data class Choice(val title: String, val options: List<Pair<Int, String>>, val selected: Int?, val onPick: (Int) -> Unit)

@Composable
fun ChoiceSheet(choice: Choice, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = Space.m).padding(bottom = Space.l).navigationBarsPadding()) {
            Text(
                choice.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(start = Space.s, bottom = Space.m),
            )
            SegmentedGroup {
                choice.options.forEachIndexed { i, (id, label) ->
                    val selected = choice.selected == id
                    SegmentedListItem(
                        selected = selected,
                        onClick = { choice.onPick(id); onDismiss() },
                        shapes = ListItemDefaults.segmentedShapes(index = i, count = choice.options.size),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        leadingContent = { RadioButton(selected = selected, onClick = null) },
                        content = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Reconnection is necessary",
    confirm: String = "Change",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
