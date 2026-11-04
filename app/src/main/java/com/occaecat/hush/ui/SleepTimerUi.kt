@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.occaecat.hush.features.SleepTimer

// ---------------------------------------------------------------------------------------------------------------
// Sleep timer
// ---------------------------------------------------------------------------------------------------------------

/** Moon chip for the player: shows the time left while a timer runs, opens the timer sheet. */
@Composable
fun SleepTimerChip(modifier: Modifier = Modifier) {
    val st by SleepTimer.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(st) { while (st is SleepTimer.State.Counting) { now = System.currentTimeMillis(); delay(15_000) } }
    val label = when (val s = st) {
        is SleepTimer.State.Counting -> "${((s.endsAt - now + 59_999) / 60_000).coerceAtLeast(1)} min"
        SleepTimer.State.EndOfTrack -> "End of track"
        SleepTimer.State.Fading -> "Fading…"
        SleepTimer.State.Off -> null
    }
    Surface(
        onClick = { open = true }, modifier = modifier, shape = MaterialTheme.shapes.extraLarge,
        color = if (label != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (label != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bedtime, "Sleep timer", Modifier.size(14.dp))
            if (label != null) {
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (open) SleepTimerSheet { open = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerSheet(onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Slide the sheet away first; dropping it from the tree would just cut it off.
    val live by SleepTimer.state.collectAsState()
    // What the sheet shows freezes as it slides away: the timer just started would otherwise grow its rows mid-slide.
    var leaving by remember { mutableStateOf<SleepTimer.State?>(null) }
    val st = leaving ?: live
    val dismiss: () -> Unit = {
        if (leaving == null) {
            leaving = live
            scope.launch { sheet.hide() }.invokeOnCompletion { onClose() }
        }
    }
    val context = LocalContext.current
    var powerOff by remember { mutableStateOf(SleepTimer.powerOffWhenDone(context)) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = Space.m).navigationBarsPadding()) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.s))
            val t = SleepTimer.target
            val noun = t?.noun ?: "headphones"
            val canOff = t?.canPowerOff == true
            Spacer(Modifier.height(Space.s))
            var hours by remember { mutableIntStateOf(0) }
            var minutes by remember { mutableIntStateOf(30) }
            Row(Modifier.fillMaxWidth().height(WheelItemHeight * 3), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxSize()) {
                    Surface(
                        Modifier.fillMaxWidth().height(WheelItemHeight).align(Alignment.Center),
                        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {}
                    Row(Modifier.fillMaxSize()) {
                        WheelColumn(24, hours, "hours", { hours = it }, Modifier.weight(1f))
                        WheelColumn(60, minutes, "min", { minutes = it }, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                FilledTonalButton(
                    onClick = { SleepTimer.startEndOfTrack(context); dismiss() },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(if (st == SleepTimer.State.EndOfTrack) "End of track ✓" else "End of track") }
                Button(
                    onClick = { SleepTimer.start(context, hours * 60 + minutes); dismiss() },
                    enabled = hours * 60 + minutes > 0,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Start") }
            }
            if (canOff) Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text("Turn off the $noun", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(powerOff, { powerOff = it; SleepTimer.setPowerOffWhenDone(context, it) })
            }
            if (st != SleepTimer.State.Off) Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (st is SleepTimer.State.Counting) OutlinedButton(onClick = { SleepTimer.extend(context, 15) }, modifier = Modifier.weight(1f)) { Text("+15 min") }
                OutlinedButton(onClick = { SleepTimer.cancel(); dismiss() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Turn off timer")
                }
            }
            Spacer(Modifier.height(Space.m))
        }
    }
}

private val WheelItemHeight = 44.dp

/**
 * One iOS-style wheel: [count] numbers, the centred one is picked and [label] stands beside the pick. Each number's
 * size and fade follow its distance from the centre as the wheel turns (in its graphics layer, nothing recomposes per
 * frame), and a fling settles onto a number on the expressive spatial spring.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(count: Int, selected: Int, label: String, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val motion = MaterialTheme.motionScheme
    val decay = androidx.compose.animation.rememberSplineBasedDecay<Float>()
    val fling = remember(state, decay, motion) {
        androidx.compose.foundation.gestures.snapping.snapFlingBehavior(
            androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider(state, androidx.compose.foundation.gestures.snapping.SnapPosition.Center),
            decay, motion.defaultSpatialSpec(),
        )
    }
    val centered by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - mid) }?.index ?: selected
        }
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val select by androidx.compose.runtime.rememberUpdatedState(onSelect)
    LaunchedEffect(state) {
        var last = centered
        androidx.compose.runtime.snapshotFlow { centered }.collect {
            if (it != last && state.isScrollInProgress) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
            last = it
        }
    }
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.isScrollInProgress to centered }.collect { (moving, i) -> if (!moving) select(i) }
    }
    Box(modifier) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(), state = state, flingBehavior = fling,
            contentPadding = PaddingValues(vertical = WheelItemHeight),
        ) {
            items(count) { i ->
                Box(Modifier.fillMaxWidth().height(WheelItemHeight), contentAlignment = Alignment.Center) {
                    Text("$i", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.graphicsLayer {
                            // 0 at the centre, 1 a whole row away or further.
                            val info = state.layoutInfo
                            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                            var d = 1f
                            val items = info.visibleItemsInfo
                            for (k in items.indices) {
                                val it = items[k]
                                if (it.index == i) { d = (kotlin.math.abs(it.offset + it.size / 2f - mid) / it.size).coerceAtMost(1f); break }
                            }
                            val s = 1.25f - 0.35f * d
                            scaleX = s; scaleY = s
                            alpha = 1f - 0.6f * d
                        })
                }
            }
        }
        // Beside the centre row, clear of the widest number at its picked size.
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center).layout { m, c ->
                val p = m.measure(c.copy(minWidth = 0))
                layout(p.width, p.height) { p.place(p.width / 2 + 26.dp.roundToPx(), 0) }
            })
    }
}
