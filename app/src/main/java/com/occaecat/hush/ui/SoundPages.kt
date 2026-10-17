@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.occaecat.hush.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.sound.Activity
import com.occaecat.hush.sound.AppRule
import com.occaecat.hush.sound.EqChoice
import com.occaecat.hush.sound.HearingTest
import com.occaecat.hush.sound.Place
import com.occaecat.hush.sound.SavedEq
import com.occaecat.hush.sound.SoundAction
import com.occaecat.hush.sound.SoundAutomation
import com.occaecat.hush.sound.SoundConfig
import com.occaecat.hush.sound.SoundStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------------------------

/** A full-screen page over the app (same frame as Find Your Equalizer): close button, title, scrolling body. */
@Composable
fun FullScreenPage(
    title: String,
    onClose: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().predictiveBack(onBack = onClose), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.m),
                    content = content,
                )
                if (bottomBar != null) {
                    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Space.m)) { bottomBar() }
                } else {
                    Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }
    }
}

/** Six tiny bars around a centre line: the shape of an EQ curve at a glance. */
@Composable
fun EqShape(levels: List<Int>, modifier: Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        val n = levels.size.coerceAtLeast(1)
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val mid = size.height / 2
        drawLine(color.copy(alpha = 0.3f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        levels.forEachIndexed { i, lv ->
            val db = lv - 10
            val h = (abs(db) / 10f * mid).coerceAtLeast(2.dp.toPx())
            drawRoundRect(color, Offset(i * (w + gap), if (db >= 0) mid - h else mid), Size(w, h), CornerRadius(w / 2, w / 2))
        }
    }
}

/** Re-reads permission-like state whenever the screen comes back (after a trip to Settings). */
@Composable
internal fun rememberOnResume(read: () -> Boolean): Boolean {
    var value by remember { mutableStateOf(read()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) value = read() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return value
}

@Composable
private fun NoticeCard(icon: ImageVector, title: String, text: String, button: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(Space.s))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(Space.xs))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(Space.s))
            Button(onClick = onClick) { Text(button) }
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

/** Splits the headset's preset list the way the Sound tab shows it: Sony's factory presets, then the user's slots. */
fun splitPresets(presets: List<Pair<Int, String>>) =
    presets.filter { it.first !in HeadphonesController.WRITABLE_PRESETS } to presets.filter { it.first in HeadphonesController.WRITABLE_PRESETS }

fun describe(action: SoundAction, config: SoundConfig, presets: List<Pair<Int, String>>): String {
    val nc = when (action.mode) {
        AmbientMode.NOISE_CANCELLING -> "Noise canceling"
        AmbientMode.AMBIENT -> "Ambient ${action.ambientLevel}" + if (action.voiceFocus) " · voice" else ""
        AmbientMode.OFF -> "Noise control off"
        null -> null
    }
    val eq = when (val e = action.eq) {
        is EqChoice.Preset -> presets.firstOrNull { it.first == e.id }?.second ?: Labels.presetNames[e.id]
        is EqChoice.Library -> config.library.firstOrNull { it.id == e.id }?.name
        null -> null
    }
    return listOfNotNull(nc, eq?.let { "EQ $it" }).joinToString(" · ").ifEmpty { "No change" }
}

/** Unchecked toggles need a visible container on a surfaceContainerLow sheet. */
@Composable
private fun sheetToggleColors() = ToggleButtonDefaults.toggleButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

// ---------------------------------------------------------------------------------------------------------------
// Action editor
// ---------------------------------------------------------------------------------------------------------------

/** Bottom sheet that edits one trigger's [SoundAction]: noise control (with ambient options) and the EQ. */
@Composable
fun ActionEditorSheet(
    title: String,
    initial: SoundAction,
    presets: List<Pair<Int, String>>,
    library: List<SavedEq>,
    onDelete: (() -> Unit)? = null,
    onDone: (SoundAction?) -> Unit,
) {
    var action by remember { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = { onDone(action) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.m).navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.s))
            SectionHeader("Noise control", top = Space.m)
            val modes = listOf<Pair<AmbientMode?, String>>(null to "Keep", AmbientMode.NOISE_CANCELLING to "ANC", AmbientMode.AMBIENT to "Ambient", AmbientMode.OFF to "Off")
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                modes.forEachIndexed { i, (m, label) ->
                    ToggleButton(
                        checked = action.mode == m,
                        onCheckedChange = { action = action.copy(mode = m) },
                        modifier = Modifier.weight(1f),
                        colors = sheetToggleColors(),
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) { Text(label, maxLines = 1) }
                }
            }
            AnimatedVisibility(action.mode == AmbientMode.AMBIENT) {
                Column(Modifier.padding(top = Space.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ambient level", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${action.ambientLevel}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = action.ambientLevel.toFloat(), onValueChange = { action = action.copy(ambientLevel = it.roundToInt()) },
                        valueRange = 1f..20f, steps = 18, colors = quietSliderColors(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Focus on voice", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(action.voiceFocus, { action = action.copy(voiceFocus = it) })
                    }
                }
            }
            SectionHeader("Equalizer", top = Space.l)
            val (sony, yours) = splitPresets(presets)
            @Composable
            fun Chip(choice: EqChoice?, label: String) {
                val checked = action.eq == choice
                ToggleButton(checked = checked, onCheckedChange = { action = action.copy(eq = choice) }, colors = sheetToggleColors()) {
                    if (checked) {
                        Icon(Icons.Filled.Check, null, Modifier.size(ToggleButtonDefaults.IconSize))
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    }
                    Text(label)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Chip(null, "Keep")
            }
            Text("Sony", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.s, top = Space.m, bottom = Space.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                sony.forEach { (id, name) -> Chip(EqChoice.Preset(id), name) }
            }
            Text("Yours", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.s, top = Space.m, bottom = Space.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                yours.forEach { (id, name) -> Chip(EqChoice.Preset(id), name) }
                library.forEach { Chip(EqChoice.Library(it.id), it.name) }
            }
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (onDelete != null) OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).height(56.dp)) { Text("Remove") }
                Button(onClick = { onDone(action) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Done") }
            }
            Spacer(Modifier.height(Space.m))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// EQ library
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun EqLibraryPage(eq: EqState?, presets: List<Pair<Int, String>>, vm: HeadphonesController, onClose: () -> Unit) {
    val context = LocalContext.current
    val config by SoundStore.config.collectAsState()
    var naming by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedEq?>(null) }
    var deleting by remember { mutableStateOf<SavedEq?>(null) }
    val bandCount = eq?.bands?.size ?: 0
    val currentName = eq?.let { e -> presets.firstOrNull { it.first == e.presetId }?.second } ?: "Current"

    FullScreenPage("EQ library", onClose) {
        Text(
            "Keep as many equalizer curves as you like. The headphones only store Manual and two Custom slots; " +
                "a library curve is written into one of them when you apply it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s),
        )
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            FilledTonalButton(onClick = { naming = true }, enabled = bandCount > 0, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Outlined.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Save current")
            }
            FilledTonalButton(onClick = { importing = true }, enabled = bandCount > 0, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Import")
            }
        }
        Spacer(Modifier.height(Space.m))
        if (config.library.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ShapeBadge(Icons.Outlined.Equalizer, size = 72.dp)
                Spacer(Modifier.height(Space.m))
                Text("No saved curves yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Save the current EQ, import a code, or keep results from AutoEq, Find Your Equalizer and the hearing test.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            config.library.forEachIndexed { i, item ->
                var menu by remember { mutableStateOf(false) }
                val usable = item.levels.size == bandCount
                SegmentedListItem(
                    onClick = { if (usable) vm.writeEqPreset(HeadphonesController.CUSTOM_PRESET, item.levels) },
                    enabled = usable,
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = config.library.size),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    leadingContent = { EqShape(item.levels, Modifier.size(width = 56.dp, height = 32.dp)) },
                    supportingContent = { Text(item.source ?: "Tap to apply to Manual", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Edit, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                listOf(0xA1, 0xA2).forEach { slot ->
                                    DropdownMenuItem(
                                        text = { Text("Save to ${Labels.presetNames[slot]}") },
                                        enabled = usable,
                                        onClick = { menu = false; vm.writeEqPreset(slot, item.levels) },
                                    )
                                }
                                DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick = { menu = false; renaming = item })
                                DropdownMenuItem(text = { Text("Share code") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                                    menu = false
                                    context.startActivity(Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SoundStore.shareCode(item)),
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

    if (naming && eq != null) NameDialog("Save current EQ", currentName, "Save") { name ->
        naming = false
        if (name != null) SoundStore.saveEq(name, eq.bands, "Saved from $currentName")
    }
    renaming?.let { item ->
        NameDialog("Rename", item.name, "Rename") { name -> renaming = null; if (name != null) SoundStore.renameEq(item.id, name) }
    }
    deleting?.let { item ->
        ConfirmDialog(
            "Triggers that use it will stop changing the EQ.",
            onConfirm = { SoundStore.deleteEq(item.id); deleting = null },
            onDismiss = { deleting = null },
            title = "Delete “${item.name}”?",
            confirm = "Delete",
        )
    }
    if (importing) {
        var text by remember { mutableStateOf("") }
        val parsed = SoundStore.parseCode(text, bandCount)
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("Import EQ code") },
            text = {
                Column {
                    Text("Paste a code shared from Hush (HUSHEQ1:…).", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Space.s))
                    OutlinedTextField(text, { text = it }, minLines = 2, isError = text.isNotBlank() && parsed == null)
                    if (text.isNotBlank() && parsed == null) {
                        Text("Not a valid code for these headphones", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { parsed?.let { (name, lv) -> SoundStore.saveEq(name, lv, "Imported") }; importing = false }, enabled = parsed != null) {
                    Text("Import")
                }
            },
            dismissButton = { TextButton(onClick = { importing = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Adaptive Sound Control
// ---------------------------------------------------------------------------------------------------------------

private val activityIcons = mapOf(
    Activity.STILL to Icons.Outlined.Weekend,
    Activity.WALKING to Icons.AutoMirrored.Filled.DirectionsWalk,
    Activity.RUNNING to Icons.AutoMirrored.Filled.DirectionsRun,
    Activity.VEHICLE to Icons.Outlined.DirectionsCar,
)

@Composable
fun AdaptiveSoundPage(presets: List<Pair<Int, String>>, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by SoundStore.config.collectAsState()
    val status by SoundAutomation.status.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val activityOk = rememberOnResume { SoundAutomation.hasActivityPermission(context) }.also { refresh }
    val locationOk = rememberOnResume { SoundAutomation.hasLocationPermission(context) }
    val backgroundOk = rememberOnResume { SoundAutomation.hasBackgroundLocation(context) }
    val askActivity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; SoundAutomation.refresh() }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++; SoundAutomation.refresh() }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; SoundAutomation.refresh() }
    var editing by remember { mutableStateOf<Activity?>(null) }
    var editingPlace by remember { mutableStateOf<Place?>(null) }
    var adding by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var locateError by remember { mutableStateOf<String?>(null) }

    FullScreenPage("Adaptive Sound Control", onClose) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Adaptive Sound Control", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Switches noise control and the EQ when you start walking, running or riding, and at your places.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Space.m))
                Switch(config.ascEnabled, { on ->
                    SoundStore.update { it.copy(ascEnabled = on) }
                    if (on && !activityOk) askActivity.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                })
            }
        }
        if (config.ascEnabled) {
            val now = listOfNotNull(
                status.inputs.activity?.label,
                config.places.filter { it.id in status.inputs.inside }.joinToString { it.name }.ifEmpty { null },
            ).joinToString(" · ")
            Text(
                if (now.isEmpty()) "Waiting for the first activity update…" else "Now: $now → ${describe(status.action, config, presets)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Space.s, top = Space.s),
            )
        }
        if (config.ascEnabled && !activityOk) {
            Spacer(Modifier.height(Space.m))
            NoticeCard(Icons.AutoMirrored.Filled.DirectionsWalk, "Physical activity access",
                "Needed to tell staying, walking, running and riding apart.", "Allow") {
                askActivity.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        SectionHeader("Activities")
        SegmentedGroup {
            Activity.entries.forEachIndexed { i, a ->
                val act = config.activityActions[a] ?: SoundAction()
                SegmentedListItem(
                    onClick = { editing = a },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = Activity.entries.size),
                    colors = ListItemDefaults.colors(
                        containerColor = if (status.inputs.activity == a && config.ascEnabled) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    leadingContent = { LeadIcon(activityIcons.getValue(a)) },
                    supportingContent = { Text(describe(act, config, presets)) },
                    content = { Text(a.label) },
                )
            }
        }

        SectionHeader("My Places")
        if (config.places.isNotEmpty() && (!locationOk || !backgroundOk)) {
            NoticeCard(Icons.Outlined.Place, "Location all the time",
                "Places only switch the sound if Hush may use your location in the background. Android asks for it in Settings.",
                "Allow") {
                if (!locationOk) askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            Spacer(Modifier.height(Space.s))
        }
        if (config.places.isNotEmpty()) SegmentedGroup {
            config.places.forEachIndexed { i, p ->
                SegmentedListItem(
                    onClick = { editingPlace = p },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = config.places.size),
                    colors = ListItemDefaults.colors(
                        containerColor = if (p.id in status.inputs.inside && config.ascEnabled) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    leadingContent = { LeadIcon(Icons.Outlined.Place) },
                    supportingContent = { Text(describe(p.action, config, presets) + " · ${p.radius.roundToInt()} m") },
                    content = { Text(p.name) },
                )
            }
        }
        Spacer(Modifier.height(Space.s))
        OutlinedButton(
            onClick = {
                if (!locationOk) askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else adding = true
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (locating) LoadingIndicator(Modifier.size(24.dp)) else Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(Space.s))
            Text("Add current location")
        }
        locateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(Space.s)) }
        Text(
            "Place settings win over activity settings; an app profile wins over both.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Space.s),
        )
        Spacer(Modifier.height(Space.l))
    }

    editing?.let { a ->
        ActionEditorSheet(a.label, config.activityActions[a] ?: SoundAction(), presets, config.library) { result ->
            if (result != null) SoundStore.update { it.copy(activityActions = it.activityActions + (a to result)) }
            editing = null
        }
    }
    editingPlace?.let { p ->
        ActionEditorSheet(p.name, p.action, presets, config.library, onDelete = {
            SoundStore.update { c -> c.copy(places = c.places.filterNot { it.id == p.id }) }
            editingPlace = null
        }) { result ->
            if (result != null) SoundStore.update { c -> c.copy(places = c.places.map { if (it.id == p.id) it.copy(action = result) else it }) }
            editingPlace = null
        }
    }
    if (adding) NameDialog("Name this place", "Home", "Add") { name ->
        adding = false
        if (name == null) return@NameDialog
        locating = true
        locateError = null
        scope.launch {
            val loc = runCatching {
                @Suppress("MissingPermission")
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
            }.getOrNull()
            locating = false
            if (loc == null) {
                locateError = "Couldn't get your location. Turn on location and try again."
            } else {
                val place = Place(UUID.randomUUID().toString(), name.trim(), loc.latitude, loc.longitude, 150f, SoundAction())
                SoundStore.update { it.copy(places = it.places + place) }
                editingPlace = place
                if (!backgroundOk) askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// App profiles
// ---------------------------------------------------------------------------------------------------------------

private data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

private suspend fun launchableApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != context.packageName }
        .map { ri ->
            AppInfo(
                ri.activityInfo.packageName,
                ri.loadLabel(pm).toString(),
                runCatching { ri.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun AppIcon(context: Context, pkg: String) {
    val icon = remember(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    if (icon != null) Image(icon, null, Modifier.size(40.dp)) else LeadIcon(Icons.Outlined.Apps)
}

@Composable
fun AppProfilesPage(presets: List<Pair<Int, String>>, onClose: () -> Unit) {
    val context = LocalContext.current
    val config by SoundStore.config.collectAsState()
    val status by SoundAutomation.status.collectAsState()
    val access = rememberOnResume { SoundAutomation.hasNotificationAccess(context) }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppRule?>(null) }

    FullScreenPage("App profiles", onClose) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("App profiles", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "While an app plays, use its own noise control and EQ. When it stops, the EQ goes back.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Space.m))
                Switch(config.appsEnabled, { on -> SoundStore.update { it.copy(appsEnabled = on) } })
            }
        }
        if (!access) {
            Spacer(Modifier.height(Space.m))
            NoticeCard(Icons.Outlined.Apps, "Notification access",
                "Android only shows which app is playing to apps with notification access. Hush reads media sessions only.",
                "Open settings") {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } else if (config.appsEnabled) {
            val playing = status.inputs.playing
            Text(
                playing?.let { p -> "Playing: " + (config.appRules.firstOrNull { it.pkg == p }?.label ?: p) } ?: "Nothing is playing",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Space.s, top = Space.s),
            )
        }
        SectionHeader("Apps")
        if (config.appRules.isEmpty()) {
            Text(
                "Add an app, for example a music app with Bright and noise canceling, or a podcast app with Speech.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s),
            )
        } else SegmentedGroup {
            config.appRules.forEachIndexed { i, r ->
                SegmentedListItem(
                    onClick = { editing = r },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = config.appRules.size),
                    colors = ListItemDefaults.colors(
                        containerColor = if (status.inputs.playing == r.pkg && config.appsEnabled) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    leadingContent = { AppIcon(context, r.pkg) },
                    supportingContent = { Text(describe(r.action, config, presets)) },
                    content = { Text(r.label) },
                )
            }
        }
        Spacer(Modifier.height(Space.s))
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(Space.s)); Text("Add app")
        }
        Spacer(Modifier.height(Space.l))
    }

    if (picking) AppPickerSheet(exclude = config.appRules.map { it.pkg }.toSet(), onDismiss = { picking = false }) { app ->
        picking = false
        val rule = AppRule(app.pkg, app.label, SoundAction())
        SoundStore.update { it.copy(appRules = it.appRules + rule) }
        editing = rule
    }
    editing?.let { r ->
        ActionEditorSheet(r.label, r.action, presets, config.library, onDelete = {
            SoundStore.update { c -> c.copy(appRules = c.appRules.filterNot { it.pkg == r.pkg }) }
            editing = null
        }) { result ->
            if (result != null) SoundStore.update { c -> c.copy(appRules = c.appRules.map { if (it.pkg == r.pkg) it.copy(action = result) else it }) }
            editing = null
        }
    }
}

@Composable
private fun AppPickerSheet(exclude: Set<String>, onDismiss: () -> Unit, onPick: (AppInfo) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = launchableApps(context) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, shape = CircleShape,
                placeholder = { Text("Search apps") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m),
            )
            Spacer(Modifier.height(Space.m))
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            } else {
                val shown = list.filter { it.pkg !in exclude && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }
                LazyColumn(Modifier.heightIn(max = 560.dp).padding(horizontal = Space.m), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    itemsIndexed(shown, key = { _, a -> a.pkg }) { i, a ->
                        SegmentedListItem(
                            onClick = { onPick(a) },
                            shapes = ListItemDefaults.segmentedShapes(index = i, count = shown.size),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            leadingContent = { a.icon?.let { Image(it, null, Modifier.size(40.dp)) } ?: LeadIcon(Icons.Outlined.Apps) },
                            content = { Text(a.label) },
                        )
                    }
                    item { Spacer(Modifier.height(Space.m)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Hearing test
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun HearingTestPage(vm: HeadphonesController, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val original = remember { vm.state.value.eq }
    var step by remember { mutableIntStateOf(-1) } // -1 intro, 0..5 tones, 6 result
    val heard = remember { mutableStateMapOf<Int, Double>() }
    var level by remember { mutableStateOf(HearingTest.START_DB) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val result = if (step >= HearingTest.tones.size) HearingTest.levels(heard) else null

    fun restoreEq() {
        val o = original ?: return
        if (o.presetId in HeadphonesController.WRITABLE_PRESETS && o.bands.isNotEmpty()) vm.writeEqPreset(o.presetId, o.bands)
        else vm.selectEqPreset(o.presetId)
    }

    fun playStep(i: Int) {
        job?.cancel()
        step = i
        if (i >= HearingTest.tones.size) return
        val tone = HearingTest.tones[i]
        job = scope.launch {
            HearingTest.play(tone) { level = it }
            // Reached the loudest burst without an answer: treat as not heard.
            if (step == i) playStep(i + 1)
        }
    }

    fun close(applied: Boolean) {
        job?.cancel()
        if (!applied) restoreEq()
        onClose()
    }

    FullScreenPage(
        "Hearing test",
        onClose = { close(false) },
        bottomBar = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                when {
                    step < 0 -> Button(onClick = {
                        // Test against a flat headset: EQ off, so the tones aren't shaped by a preset.
                        vm.selectEqPreset(0x00)
                        playStep(0)
                    }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Start") }
                    step < HearingTest.tones.size -> {
                        OutlinedButton(onClick = { playStep(step + 1) }, modifier = Modifier.weight(1f).height(72.dp)) { Text("Can't hear") }
                        Button(onClick = {
                            heard[HearingTest.tones[step].band] = level
                            playStep(step + 1)
                        }, modifier = Modifier.weight(2f).height(72.dp)) { Text("I hear it", style = MaterialTheme.typography.titleMedium) }
                    }
                    else -> {
                        OutlinedButton(onClick = { heard.clear(); playStep(0) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Retry") }
                        Button(onClick = {
                            result?.let {
                                vm.writeEqPreset(HeadphonesController.CUSTOM_PRESET, it)
                                SoundStore.saveEq("Hearing test", it, "Hearing test · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()))
                            }
                            close(true)
                        }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Apply") }
                    }
                }
            }
        },
    ) {
        if (step in 0 until HearingTest.tones.size) {
            LinearWavyProgressIndicator(
                progress = { (step + 1f) / HearingTest.tones.size },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Space.l))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                step < 0 -> {
                    ShapeBadge(Icons.Outlined.Hearing, size = 96.dp)
                    Spacer(Modifier.height(Space.l))
                    Text("Tune the sound to your ears", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Space.m))
                    Text(
                        "Find a quiet place, turn on noise canceling and set the volume to about a third. You'll hear six " +
                            "beeping tones that slowly get louder; press “I hear it” as soon as you can make one out.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.m))
                    Text(
                        "Bands where you hear less than usual get a gentle boost (at most +6 dB). This is a comfort setting, not a medical test.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                    )
                }
                step < HearingTest.tones.size -> {
                    val tone = HearingTest.tones[step]
                    val pulse = rememberInfiniteTransition(label = "pulse")
                    val s by pulse.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "s")
                    Spacer(Modifier.height(Space.xl))
                    Box(Modifier.size(180.dp).scale(s), contentAlignment = Alignment.Center) {
                        ShapeBadge(Icons.Outlined.Hearing, size = 180.dp, spinning = true)
                    }
                    Spacer(Modifier.height(Space.l))
                    Text(tone.label, style = MaterialTheme.typography.headlineMedium)
                    Text("Tone ${step + 1} of ${HearingTest.tones.size}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Space.m))
                    val frac = ((level - HearingTest.START_DB) / (HearingTest.MAX_DB - HearingTest.START_DB)).toFloat()
                    LinearWavyProgressIndicator(progress = { frac }, modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth())
                    Text("Getting louder…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> result?.let { lv ->
                    Text("Your hearing EQ", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(Space.m))
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.padding(Space.m)) {
                            lv.forEachIndexed { i, l ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (l > 10) "+${l - 10}" else "${l - 10}", style = MaterialTheme.typography.titleMedium,
                                        color = if (l == 10) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                                    Text(if (i == 0) "Bass" else Labels.sixBands[i], style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                    Text(
                        if (lv.all { it == 10 }) "Your hearing looks even across the bands, so no correction is needed."
                        else "Apply writes this to Manual and keeps a copy in the EQ library.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
