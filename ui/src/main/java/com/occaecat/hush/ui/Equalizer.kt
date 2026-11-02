@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** An equalizer as the shared UI draws it: band levels in dB steps within [range]. */
data class EqUi(
    /** Under the title: the active preset ("Custom 1", "Flat + AutoEq", "Off"). */
    val preset: String,
    val levels: List<Int>,
    val labels: List<String>,
    val range: IntRange,
    val enabled: Boolean = true,
    /** False on a device whose presets can be picked but whose bands are fixed: the sliders only show the curve. */
    val editable: Boolean = true,
)

/**
 * The top of the Sound page as one surfaceContainerHigh block (status bar included, rounded at the bottom): the
 * equalizer title and preset, hold-to-compare and reset, then the band sliders. [extra] sits between the header
 * and the sliders (a response curve, a notice).
 */
@Composable
fun EqualizerHero(
    eq: EqUi,
    onBand: (index: Int, level: Int) -> Unit,
    onReset: (() -> Unit)? = null,
    /** Pressed: play without the equalizer; released: back. */
    onCompare: ((pressed: Boolean) -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    extra: (@Composable () -> Unit)? = null,
    emptyText: String = "This preset has no adjustable bands.",
) {
    val top = if (LocalLayout.current.tabletopTop == null) {
        WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
    } else WindowInsets(0)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .windowInsetsPadding(top)
            .padding(top = Space.m, bottom = Space.l),
    ) {
        Row(Modifier.padding(start = Space.l, end = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(eq.preset, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            // Always composed while bands exist: it must stay alive while it holds the sound bypassed.
            if (onCompare != null && eq.levels.isNotEmpty() && eq.enabled) CompareButton(onCompare)
            if (onReset != null && eq.levels.isNotEmpty() && eq.enabled && eq.editable) {
                FilledTonalIconButton(onClick = onReset) { Icon(Icons.Outlined.RestartAlt, "Reset to flat") }
            }
            actions()
        }
        extra?.invoke()
        if (eq.levels.isNotEmpty()) {
            Spacer(Modifier.height(Space.l))
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.m)) {
                eq.levels.forEachIndexed { i, level ->
                    EqBand(eq.labels.getOrElse(i) { "${i + 1}" }, level, eq.range, eq.enabled && eq.editable, Modifier.weight(1f)) { onBand(i, it) }
                }
            }
        } else {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
            )
        }
    }
}

/** Hold to hear the sound without EQ, release to go back: a quick A/B of the current curve. */
@Composable
private fun CompareButton(onCompare: (Boolean) -> Unit) {
    var holding by remember { mutableStateOf(false) }
    // The press handler outlives recompositions (bypassing changes the state), so read the latest callback.
    val compare by rememberUpdatedState(onCompare)
    val scale by animateFloatAsState(if (holding) 1.15f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ab")
    Surface(
        shape = CircleShape,
        color = if (holding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (holding) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(end = Space.s).size(40.dp).scale(scale)
            .semantics { contentDescription = "Hold to hear without equalizer" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    holding = true
                    compare(true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        holding = false
                        compare(false)
                    }
                })
            },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Compare, null, Modifier.size(20.dp)) }
    }
}

/** One band: a vertical slider in whole steps over [range], its level above and its frequency below. */
@Composable
private fun EqBand(label: String, level: Int, range: IntRange, enabled: Boolean, modifier: Modifier, onCommit: (Int) -> Unit) {
    val state = remember(range) {
        SliderState(value = level.toFloat(), steps = range.last - range.first - 1, valueRange = range.first.toFloat()..range.last.toFloat())
    }
    state.onValueChangeFinished = { onCommit(state.value.roundToInt()) }
    LaunchedEffect(level) { if (!state.isDragging) state.value = level.toFloat() }
    val db = state.value.roundToInt()
    val layout = LocalLayout.current
    val height = when {
        layout.compactHeight -> 140.dp
        layout.shortHeight -> 170.dp
        else -> 200.dp
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (db > 0) "+$db" else "$db",
            style = MaterialTheme.typography.labelLarge,
            color = if (db == 0 || !enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Space.s))
        VerticalSlider(state = state, modifier = Modifier.height(height), enabled = enabled, reverseDirection = true, colors = quietSliderColors())
        Spacer(Modifier.height(Space.s))
        Text(
            label, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Presets as wrapped rows of pill toggles; the active one fills with primary. */
@Composable
fun PresetChips(items: List<Triple<String, Boolean, () -> Unit>>, enabled: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        items.forEach { (name, checked, onClick) ->
            val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ToggleButton(
                checked = checked,
                onCheckedChange = { onClick() },
                enabled = enabled,
                interactionSource = press,
                modifier = Modifier.squishy(press, 0.9f),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                if (checked) {
                    Icon(Icons.Filled.Check, null, Modifier.size(ToggleButtonDefaults.IconSize).hopOnSelect(checked))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(name)
            }
        }
    }
}
