@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

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
    val context = LocalContext.current
    val st by SleepTimer.state.collectAsState()
    var powerOff by remember { mutableStateOf(SleepTimer.powerOffWhenDone(context)) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = Space.m).navigationBarsPadding()) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.s))
            val t = SleepTimer.target
            val noun = t?.noun ?: "headphones"
            val canOff = t?.canPowerOff == true
            Text("Fades the volume, pauses, then ${if (canOff && powerOff) "turns the $noun off" else "puts the volume back"}.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.s, top = Space.xs))
            Spacer(Modifier.height(Space.m))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                    ToggleButton(checked = false, onCheckedChange = { SleepTimer.start(context, m); onClose() },
                        colors = ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Text(if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60}")
                    }
                }
                ToggleButton(checked = st == SleepTimer.State.EndOfTrack, onCheckedChange = { SleepTimer.startEndOfTrack(context); onClose() },
                    colors = ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Text("End of track")
                }
            }
            if (canOff) Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text("Turn off the $noun", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(powerOff, { powerOff = it; SleepTimer.setPowerOffWhenDone(context, it) })
            }
            if (st != SleepTimer.State.Off) Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (st is SleepTimer.State.Counting) OutlinedButton(onClick = { SleepTimer.extend(context, 15) }, modifier = Modifier.weight(1f)) { Text("+15 min") }
                OutlinedButton(onClick = { SleepTimer.cancel(); onClose() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Turn off timer")
                }
            }
            Spacer(Modifier.height(Space.l))
        }
    }
}
