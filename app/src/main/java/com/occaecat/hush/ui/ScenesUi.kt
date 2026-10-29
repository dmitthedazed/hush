@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.occaecat.hush.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.features.Scenes
import com.occaecat.hush.features.SleepTimer
import com.occaecat.hush.sound.Scene
import com.occaecat.hush.sound.SoundAction
import com.occaecat.hush.sound.SoundStore
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.roundToInt

val SceneIcons: Map<String, ImageVector> = linkedMapOf(
    "commute" to Icons.Outlined.DirectionsSubway,
    "office" to Icons.Outlined.Work,
    "walk" to Icons.AutoMirrored.Outlined.DirectionsWalk,
    "sleep" to Icons.Outlined.Bedtime,
    "focus" to Icons.Outlined.SelfImprovement,
    "call" to Icons.Outlined.Call,
    "star" to Icons.Outlined.Star,
)

/** One-tap scenes under the noise control group; long press edits, "+" adds. */
@Composable
fun ScenesRow(state: HeadphonesState, modifier: Modifier) {
    val context = LocalContext.current
    val config by SoundStore.config.collectAsState()
    val active by Scenes.active.collectAsState()
    var editing by remember { mutableStateOf<Scene?>(null) }
    // A noise mode changed elsewhere (button on the headset, notification) no longer matches the scene.
    val mode = state.ncAsm?.mode
    LaunchedEffect(state.address) { Scenes.forDevice(state.address) }
    LaunchedEffect(mode) {
        val s = config.scenes.firstOrNull { it.id == active } ?: return@LaunchedEffect
        if (s.action.mode != null && mode != null && s.action.mode != mode) Scenes.clear()
    }
    Column(modifier) {
        SectionHeader("Scenes", top = Space.s)
        // Scrolls under the grid's side padding to the screen edge; the first and last chips still line up with the cards.
        Row(
            Modifier.bleed(Space.m).horizontalScroll(rememberScrollState()).padding(horizontal = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            config.scenes.forEach { scene ->
                val on = active == scene.id
                // The active scene rounds out into a pill and takes the primary colour; presses squish it.
                val motion = MaterialTheme.motionScheme
                val corner by androidx.compose.animation.core.animateDpAsState(if (on) 28.dp else 16.dp, motion.fastSpatialSpec(), label = "corner")
                val fill by androidx.compose.animation.animateColorAsState(
                    if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, motion.defaultEffectsSpec(), label = "fill",
                )
                val ink by androidx.compose.animation.animateColorAsState(
                    if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, motion.defaultEffectsSpec(), label = "ink",
                )
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
                val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Surface(
                    shape = shape,
                    color = fill,
                    contentColor = ink,
                    modifier = Modifier.squishy(press, 0.9f).clip(shape).combinedClickable(
                        interactionSource = press, indication = androidx.compose.material3.ripple(),
                        onClickLabel = if (on) "Turn off ${scene.name}" else "Apply ${scene.name}", onLongClickLabel = "Edit ${scene.name}",
                        onLongClick = { editing = scene },
                        onClick = { Scenes.toggle(context, scene) },
                    ),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(SceneIcons[scene.icon] ?: Icons.Outlined.Star, null, Modifier.size(20.dp).hopOnSelect(on))
                        Spacer(Modifier.width(Space.s))
                        Text(scene.name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Surface(
                onClick = { editing = Scene(UUID.randomUUID().toString(), "", "star", SoundAction()) },
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Icon(Icons.Outlined.Add, "Add scene", Modifier.padding(12.dp).size(20.dp))
            }
        }
    }
    editing?.let { s -> SceneEditorSheet(s, state, isNew = config.scenes.none { it.id == s.id }) { editing = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneEditorSheet(initial: Scene, state: HeadphonesState, isNew: Boolean, onClose: () -> Unit) {
    var scene by remember { mutableStateOf(initial.copy(
        volume = initial.volume?.let { (it.toDouble() / (initial.volumeSteps - 1).coerceAtLeast(1) * (state.volumeSteps - 1)).roundToInt() },
        volumeSteps = state.volumeSteps,
    )) }
    var editingSound by remember { mutableStateOf(false) }
    val config by SoundStore.config.collectAsState()
    val presets = presetsFor(state)
    fun save() {
        val s = scene.copy(name = scene.name.trim().ifEmpty { "Scene" })
        SoundStore.update { c -> c.copy(scenes = if (isNew) c.scenes + s else c.scenes.map { if (it.id == s.id) s else it }) }
        onClose()
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.m).navigationBarsPadding()) {
            Text(if (isNew) "New scene" else "Edit scene", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.s))
            Spacer(Modifier.height(Space.m))
            OutlinedTextField(scene.name, { scene = scene.copy(name = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionHeader("Icon", top = Space.m)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                SceneIcons.forEach { (key, icon) ->
                    ToggleButton(checked = scene.icon == key, onCheckedChange = { scene = scene.copy(icon = key) },
                        colors = ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Icon(icon, key, Modifier.size(20.dp))
                    }
                }
            }
            SectionHeader("Sound", top = Space.m)
            SegmentedGroup {
                ValueRow(0, 1, Icons.Outlined.Star, "Noise control & EQ", describe(scene.action, config, presets)) { editingSound = true }
            }
            if (state.speakToChat != null) {
            SectionHeader("Speak-to-Chat", top = Space.m)
            val stc = listOf<Pair<Boolean?, String>>(null to "Keep", true to "On", false to "Off")
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                stc.forEachIndexed { i, (v, label) ->
                    ToggleButton(
                        checked = scene.speakToChat == v, onCheckedChange = { scene = scene.copy(speakToChat = v) },
                        modifier = Modifier.weight(1f),
                        colors = ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            stc.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) { Text(label) }
                }
            }
            }
            if (state.volume != null) Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text("Set headphone volume", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(scene.volume != null, { scene = scene.copy(volume = if (it) state.volume ?: 10 else null, volumeSteps = state.volumeSteps) })
            }
            scene.volume?.takeIf { state.volume != null }?.let { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(v.toFloat(), { scene = scene.copy(volume = it.roundToInt(), volumeSteps = state.volumeSteps) }, valueRange = 0f..(state.volumeSteps - 1).toFloat(),
                        colors = quietSliderColors(), modifier = Modifier.weight(1f))
                    Text("$v", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Space.m))
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (!isNew) OutlinedButton(onClick = {
                    SoundStore.update { c -> c.copy(scenes = c.scenes.filterNot { it.id == scene.id }) }
                    onClose()
                }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Delete") }
                Button(onClick = ::save, modifier = Modifier.weight(1f).height(56.dp)) { Text("Save") }
            }
            Spacer(Modifier.height(Space.m))
        }
    }
    if (editingSound) ActionEditorSheet("Sound for ${scene.name.ifBlank { "this scene" }}", scene.action, presets, config.library) { a ->
        if (a != null) scene = scene.copy(action = a)
        editingSound = false
    }
}

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
    val headset by com.occaecat.hush.HeadphonesController.get(context).state.collectAsState()
    val canPowerOff = headset.capabilities.powerOff
    var powerOff by remember { mutableStateOf(SleepTimer.powerOffWhenDone(context)) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = Space.m).navigationBarsPadding()) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.s))
            Text("Fades the headphone volume, pauses, then ${if (powerOff && canPowerOff) "turns the headphones off" else "puts the volume back"}.",
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
            if (canPowerOff) Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text("Turn off the headphones", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
