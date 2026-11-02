@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Podcasts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.EqLibrary
import com.occaecat.hush.scenes.EqPick
import com.occaecat.hush.scenes.Scene
import com.occaecat.hush.scenes.SceneAction
import com.occaecat.hush.scenes.SceneStore
import com.occaecat.hush.scenes.Scenes
import java.util.UUID
import kotlin.math.roundToInt

val SceneIcons: Map<String, ImageVector> = linkedMapOf(
    "commute" to Icons.Outlined.DirectionsSubway,
    "office" to Icons.Outlined.Work,
    "walk" to Icons.AutoMirrored.Outlined.DirectionsWalk,
    "sleep" to Icons.Outlined.Bedtime,
    "focus" to Icons.Outlined.SelfImprovement,
    "call" to Icons.Outlined.Call,
    "music" to Icons.Outlined.MusicNote,
    "podcast" to Icons.Outlined.Podcasts,
    "gym" to Icons.Outlined.FitnessCenter,
    "listen" to Icons.Outlined.Headphones,
    "star" to Icons.Outlined.Star,
)

/** What [a] does on [c], in a few words ("ANC · EQ Bright · volume 8"). */
@Composable
fun describe(a: SceneAction, c: DeviceController): String {
    val presets = c.equalizer?.presets?.collectAsState()?.value.orEmpty().flatMap { it.presets }
    val library by EqLibrary.items.collectAsState()
    val mode = c.noise?.modes?.firstOrNull { it.id == a.noise }
    val nc = mode?.let { m ->
        listOfNotNull(m.title, a.level?.takeIf { m.levelRange != null }?.toString()).joinToString(" ") + if (a.voiceFocus == true) " · voice" else ""
    }
    val eq = when (val e = a.eq) {
        is EqPick.Preset -> presets.firstOrNull { it.id == e.id }?.name?.let { "EQ $it" }
        is EqPick.Library -> library.firstOrNull { it.id == e.id }?.name?.let { "EQ $it" }
        EqPick.Off -> "EQ off"
        null -> null
    }
    val switches = c.sceneSwitches?.list.orEmpty().mapNotNull { s -> a.switches[s.id]?.let { "${s.title} ${if (it) "on" else "off"}" } }
    return (listOfNotNull(nc, eq, a.volume?.let { "volume $it" }) + switches).joinToString(" · ").ifEmpty { "No change" }
}

/** One-tap scenes for any device: tap applies (or turns off the active one), long press edits, "+" adds. */
@Composable
fun ScenesRow(key: String, c: DeviceController, modifier: Modifier) {
    val config by SceneStore.config.collectAsState()
    val activeAll by Scenes.active.collectAsState()
    val active = activeAll[key]
    val scenes = SceneStore.device(config, key, c).scenes
    var editing by remember { mutableStateOf<Scene?>(null) }
    // A noise mode changed elsewhere (a button on the device, the notification) no longer matches the scene.
    val noise = c.noise?.state?.collectAsState()?.value?.active
    LaunchedEffect(noise) {
        val s = scenes.firstOrNull { it.id == active } ?: return@LaunchedEffect
        if (s.action.noise != null && noise != null && s.action.noise != noise) Scenes.clear(key)
    }
    Column(modifier) {
        SectionHeader("Scenes", top = Space.s)
        // Scrolls under the grid's side padding to the screen edge; the first and last chips still line up with the cards.
        Row(
            Modifier.bleed(Space.m).horizontalScroll(rememberScrollState()).padding(horizontal = Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            scenes.forEach { scene ->
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
                    shape = shape, color = fill, contentColor = ink,
                    modifier = Modifier.squishy(press, 0.9f).clip(shape).combinedClickable(
                        interactionSource = press, indication = androidx.compose.material3.ripple(),
                        onClickLabel = if (on) "Turn off ${scene.name}" else "Apply ${scene.name}", onLongClickLabel = "Edit ${scene.name}",
                        onLongClick = { editing = scene },
                        onClick = { Scenes.toggle(key, c, scene) },
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
                onClick = { editing = Scene(UUID.randomUUID().toString(), "", "star", SceneAction()) },
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Icon(Icons.Outlined.Add, "Add scene", Modifier.padding(12.dp).size(20.dp))
            }
        }
    }
    editing?.let { s ->
        val isNew = scenes.none { it.id == s.id }
        var name by remember(s.id) { mutableStateOf(s.name) }
        var icon by remember(s.id) { mutableStateOf(s.icon) }
        SceneSheet(
            title = if (isNew) "New scene" else "Edit scene", c = c, initial = s.action,
            header = {
                OutlinedTextField(name, { name = it.take(24) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SectionHeader("Icon", top = Space.m)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    SceneIcons.forEach { (k, v) ->
                        ToggleButton(checked = icon == k, onCheckedChange = { icon = k }, colors = sheetToggleColors()) { Icon(v, k, Modifier.size(20.dp)) }
                    }
                }
            },
            onDelete = if (isNew) null else ({
                SceneStore.updateDevice(key, c) { d -> d.copy(scenes = d.scenes.filterNot { it.id == s.id }) }
                if (active == s.id) Scenes.clear(key)
                editing = null
            }),
        ) { action ->
            if (action != null) {
                val scene = s.copy(name = name.trim().ifEmpty { "Scene" }, icon = icon, action = action)
                SceneStore.updateDevice(key, c) { d ->
                    d.copy(scenes = if (isNew) d.scenes + scene else d.scenes.map { if (it.id == scene.id) scene else it })
                }
            }
            editing = null
        }
    }
}

/** Unchecked toggles need a visible container on a surfaceContainerLow sheet. */
@Composable
fun sheetToggleColors() = ToggleButtonDefaults.toggleButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * Bottom sheet that edits a [SceneAction] for one device: only what the device has — noise modes (with level and
 * voice), its EQ presets and library curves, volume, and its own switches. [header] goes on top (a scene's name).
 */
@Composable
fun SceneSheet(
    title: String,
    c: DeviceController,
    initial: SceneAction,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDone: (SceneAction?) -> Unit,
) {
    var action by remember { mutableStateOf(initial) }
    val state by c.state.collectAsState()
    ModalBottomSheet(
        onDismissRequest = { onDone(null) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.m).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.s, bottom = Space.m))
            header?.invoke(this)

            c.noise?.let { noise ->
                SectionHeader("Noise control", top = Space.m)
                val modes = listOf<Pair<String?, String>>(null to "Keep") + noise.modes.map { it.id to it.label }
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    modes.forEachIndexed { i, (id, label) ->
                        ToggleButton(
                            checked = action.noise == id,
                            onCheckedChange = {
                                val m = noise.modes.firstOrNull { it.id == id }
                                action = action.copy(
                                    noise = id,
                                    level = m?.levelRange?.let { r -> (action.level ?: r.last / 2).coerceIn(r) },
                                    voiceFocus = if (m?.hasOptions == true) action.voiceFocus ?: false else null,
                                )
                            },
                            modifier = Modifier.weight(1f), colors = sheetToggleColors(),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) { Text(label, maxLines = 1) }
                    }
                }
                val mode = noise.modes.firstOrNull { it.id == action.noise }
                AnimatedVisibility(mode?.levelRange != null || mode?.hasOptions == true) {
                    Column(Modifier.padding(top = Space.m)) {
                        mode?.levelRange?.let { r ->
                            val v = action.level ?: r.first
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${mode.title} level", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text("$v", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(v.toFloat(), { action = action.copy(level = it.roundToInt()) },
                                valueRange = r.first.toFloat()..r.last.toFloat(), steps = (r.last - r.first - 1).coerceAtLeast(0),
                                colors = quietSliderColors())
                        }
                        if (mode?.hasOptions == true) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Focus on voice", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(action.voiceFocus == true, { action = action.copy(voiceFocus = it) })
                        }
                    }
                }
            }

            c.equalizer?.let { eq ->
                val groups by eq.presets.collectAsState()
                val library by EqLibrary.items.collectAsState()
                val curves = library.filter { it.layout == eq.layout }
                SectionHeader("Equalizer", top = Space.l)
                @Composable
                fun Chip(pick: EqPick?, label: String) {
                    val checked = action.eq == pick
                    ToggleButton(checked = checked, onCheckedChange = { action = action.copy(eq = pick) }, colors = sheetToggleColors()) {
                        if (checked) {
                            Icon(Icons.Filled.Check, null, Modifier.size(ToggleButtonDefaults.IconSize))
                            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        }
                        Text(label)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Chip(null, "Keep")
                    if (eq.toggle != null) Chip(EqPick.Off, "Off")
                }
                groups.forEach { g ->
                    Text(g.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.s, top = Space.m, bottom = Space.s))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        g.presets.forEach { Chip(EqPick.Preset(it.id), it.name) }
                    }
                }
                if (curves.isNotEmpty()) {
                    Text("Library", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.s, top = Space.m, bottom = Space.s))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        curves.forEach { Chip(EqPick.Library(it.id), it.name) }
                    }
                }
            }

            c.sceneSwitches?.list?.forEach { sw ->
                SectionHeader(sw.title, top = Space.l)
                val opts = listOf<Pair<Boolean?, String>>(null to "Keep", true to "On", false to "Off")
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    opts.forEachIndexed { i, (v, label) ->
                        ToggleButton(
                            checked = action.switches[sw.id] == v,
                            onCheckedChange = { action = action.copy(switches = if (v == null) action.switches - sw.id else action.switches + (sw.id to v)) },
                            modifier = Modifier.weight(1f), colors = sheetToggleColors(),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                opts.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) { Text(label) }
                    }
                }
            }

            if (state.volumeSteps > 0) {
                Row(Modifier.padding(top = Space.l), verticalAlignment = Alignment.CenterVertically) {
                    Text("Set volume", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(action.volume != null, { action = action.copy(volume = if (it) state.volume ?: (state.volumeSteps / 3) else null) })
                }
                action.volume?.let { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(v.toFloat(), { action = action.copy(volume = it.roundToInt()) }, valueRange = 0f..(state.volumeSteps - 1).toFloat(),
                            colors = quietSliderColors(), modifier = Modifier.weight(1f))
                        Text("$v", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Space.m))
                    }
                }
            }

            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (onDelete != null) OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).height(56.dp)) { Text("Delete") }
                Button(onClick = { onDone(action) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Save") }
            }
            Spacer(Modifier.height(Space.m))
        }
    }
}
