@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.asComposePath
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.occaecat.hush.devices.icon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.occaecat.hush.DeviceModules
import com.occaecat.hush.devices.Candidate
import com.occaecat.hush.devices.DeviceModule
import com.occaecat.hush.devices.DeviceRegistry
import com.occaecat.hush.devices.Model
import com.occaecat.hush.devices.Transport
import com.occaecat.hush.devices.demoCandidate
import com.occaecat.hush.devices.found
import kotlinx.coroutines.launch

/**
 * Adding a device. One screen answers "what can I add right now": the phone's Bluetooth (permission, off, looking),
 * the headphones a module can add (paired or plugged in, shown at once), nearby ones in pairing mode, and paired ones
 * Hush can't drive yet; then by hand, brand → model, each step its own destination. Adding a Bluetooth headset
 * connects it here first, so success or failure shows where the user tapped. [WelcomeScreen] is the same start for
 * the first run.
 */
@Composable
fun AddDeviceScreen(onClose: () -> Unit, onAdd: (Candidate) -> Unit, onBrand: (String) -> Unit) {
    val state = rememberAddDeviceState(onAdd)
    FullScreenPage("Add a device", onClose = onClose) { StartPage(state, onBrand) }
}

@Composable
fun AddBrandScreen(brand: String, onClose: () -> Unit, onModel: (String) -> Unit) {
    val module = DeviceModules.module(brand)
    LazyPage(module.brand, onClose) { brandItems(module, onModel) }
}

@Composable
fun AddModelScreen(brand: String, modelId: String, onClose: () -> Unit, onAdd: (Candidate) -> Unit) {
    val module = DeviceModules.module(brand)
    val model = module.models.firstOrNull { it.id == modelId }
    if (model == null) { LaunchedEffect(Unit) { onClose() }; return }
    val state = rememberAddDeviceState(onAdd)
    FullScreenPage(model.name, onClose = onClose) { ModelPage(state, module, model) }
}

/** Nothing added yet: a welcome over the same start as the add screen. Back from here leaves the app. */
@Composable
fun WelcomeScreen(onAdd: (Candidate) -> Unit, onBrand: (String) -> Unit) {
    val state = rememberAddDeviceState(onAdd)
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.m),
        ) {
            ShapeBadge(Icons.Outlined.Headphones, size = 72.dp, modifier = Modifier.padding(top = Space.xl, start = Space.s))
            Text(
                "Welcome to Hush", style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.padding(start = Space.s, top = Space.l, bottom = Space.xs),
            )
            Text(
                "Add your headphones to get started. Added devices stay in the device switcher even while they're off.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s),
            )
            StartPage(state, onBrand)
        }
    }
}

// --- Start ------------------------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.StartPage(state: AddDeviceState, onBrand: (String) -> Unit) {
    val found by DeviceModules.found.collectAsStateWithLifecycle()
    // Paired audio devices no module claims: Hush can see them but not drive them.
    val claimed = remember(found) { found.mapNotNull { it.address }.toSet() }
    val registered by DeviceRegistry.items.collectAsStateWithLifecycle()
    val others = remember(state.bonded, claimed, registered) {
        val known = claimed + registered.mapNotNull { it.address }
        state.bonded.filter { it.address !in known && !it.supported }
    }

    // Brand lists open with their pictures ready: the thumbnails decode in the background while this page is up.
    val context = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        DeviceModules.all.flatMap { it.models }.forEach { m ->
            if (m.image != null || m.imageUrl != null) ProductArtCache.load(context, m, large = false)
        }
    }
    Spacer(Modifier.height(Space.m))
    StatusCard(state, emptyHint = found.isEmpty() && state.nearby.isEmpty() && registered.isEmpty())

    Section("Ready to add", visible = found.isNotEmpty()) {
        found.forEachIndexed { i, c ->
            key(c.key) { CandidateRow(state, i, found.size, c) }
        }
    }
    Section("Nearby", visible = state.nearby.isNotEmpty()) {
        state.nearby.forEachIndexed { i, d ->
            key(d.address) { NearbyRow(state, i, state.nearby.size, d) }
        }
    }
    UnsupportedSection(others)

    SectionHeader("Add by brand")
    SegmentedGroup {
        val brands = remember { DeviceModules.all.filter { it.models.isNotEmpty() } }
        brands.forEachIndexed { i, m ->
            val summary = remember(m) { brandSummary(m) }
            ValueRow(i, brands.size, Icons.Outlined.Headphones, m.brand, summary) { onBrand(m.id) }
        }
    }
    Spacer(Modifier.height(Space.l))
}

private fun brandSummary(m: DeviceModule): String =
    if (m.models.size <= 3) m.models.joinToString(", ") { it.name }
    else m.models.groupBy { it.transport }.entries.joinToString(" · ") { (t, l) ->
        "${l.size} " + when (t) { Transport.BLUETOOTH -> "Bluetooth"; Transport.USB -> "USB"; Transport.WIRED -> "wired" }
    }

/** A titled group that grows in and out with the springs, instead of popping. */
@Composable
private fun ColumnScope.Section(title: String, visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Column {
            SectionHeader(title)
            SegmentedGroup(Modifier.animateContentSize(motion.defaultSpatialSpec()), content)
        }
    }
}

/** Grows a row in from 92% the first time it is shown (a headset just found), on the expressive spatial spring. */
@Composable
private fun Modifier.appear(): Modifier {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { scale.animateTo(1f, spatial) }
            launch { alpha.animateTo(1f, effects) }
        }
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
}

// --- Status -----------------------------------------------------------------------------------------------------------

private enum class Status { NONE, PERMISSION, OFF, MISSING, SCANNING, IDLE }

/**
 * The one thing about the phone's Bluetooth the user may have to act on: allow, turn on, or look for headphones in
 * pairing mode. Morphs between them in place.
 */
@Composable
private fun StatusCard(state: AddDeviceState, emptyHint: Boolean) {
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.onPermissionResult()
    }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { state.refresh() }
    val status = when (state.radio) {
        Radio.CHECKING -> Status.NONE
        Radio.NO_PERMISSION -> Status.PERMISSION
        Radio.OFF -> Status.OFF
        Radio.MISSING -> Status.MISSING
        Radio.READY -> if (state.scanning) Status.SCANNING else Status.IDLE
    }
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val attention = status == Status.PERMISSION || status == Status.OFF
    Surface(
        color = if (attention) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor = if (attention) colors.onSecondaryContainer else colors.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().animateContentSize(motion.defaultSpatialSpec()),
    ) {
        AnimatedContent(
            status,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            contentAlignment = Alignment.TopStart,
            label = "status",
        ) { s ->
            when (s) {
                Status.NONE -> Spacer(Modifier.fillMaxWidth().height(1.dp))
                Status.PERMISSION -> StatusBody(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Let Hush see your headphones",
                    text = "Allow Nearby devices so Hush can list the headphones paired with this phone and find new ones.",
                ) {
                    Button(onClick = {
                        if (state.askedPermission) openAppSettings(context)
                        else permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
                    }) { Text(if (state.askedPermission) "Open settings" else "Allow") }
                }
                Status.OFF -> StatusBody(
                    icon = Icons.Outlined.BluetoothDisabled,
                    title = "Bluetooth is off",
                    text = "Turn it on to see your headphones. Wired and USB devices work without it.",
                ) {
                    Button(onClick = {
                        runCatching { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                            .onFailure { openBluetoothSettings(context) }
                    }) { Text("Turn on") }
                }
                Status.MISSING -> StatusBody(
                    icon = Icons.Outlined.Usb,
                    title = "No Bluetooth here",
                    text = "This device has no Bluetooth. Wired and USB devices still work.",
                )
                Status.SCANNING -> Row(
                    Modifier.fillMaxWidth().padding(Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(horizontal = Space.m)) {
                        Text("Looking for headphones", style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(
                            "Put them in pairing mode: on most, hold the power button until the light flashes blue.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = state::stopScan) { Text("Stop") }
                }
                Status.IDLE -> StatusBody(
                    icon = Icons.Outlined.BluetoothSearching,
                    title = if (emptyHint) "No headphones yet" else "Pair new headphones",
                    text = if (emptyHint) "Put your headphones in pairing mode and look for them, or plug in wired ones."
                        else "Headphones in pairing mode show up under Nearby.",
                ) {
                    FilledTonalButton(onClick = {
                        if (state.canScan) state.scan() else permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN))
                    }) { Text("Look for headphones") }
                    TextButton(onClick = { openBluetoothSettings(context) }) { Text("Settings") }
                }
            }
        }
    }
}

@Composable
private fun StatusBody(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(Space.m)) {
        ShapeBadge(icon, size = 48.dp, polygon = MaterialShapes.Cookie7Sided)
        Column(Modifier.weight(1f).padding(start = Space.m)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.alpha(0.8f))
            if (actions != null) Row(
                Modifier.padding(top = Space.s),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
    }
}

// --- Device cards -----------------------------------------------------------------------------------------------------

/** A unit a module can add: picture, name, brand and model, and the add → connecting → connected (or why not) steps. */
@Composable
private fun CandidateRow(state: AddDeviceState, index: Int, count: Int, c: Candidate, added: Boolean = false) {
    val module = DeviceModules.module(c.moduleId)
    val model = remember(c.modelId) { module.models.firstOrNull { it.id == c.modelId } }
    DeviceCard(
        index, count, c.name,
        eyebrow = listOfNotNull(module.brand, model?.name?.takeIf { !c.name.contains(it, ignoreCase = true) }).joinToString(" · "),
        model = model,
        where = c.transport.icon to if (c.transport == Transport.BLUETOOTH) "Paired" else "Plugged in",
        support = model?.note?.let(::supportPill),
        step = state.step?.takeIf { it.id == c.key },
        enabled = !added && !(state.busy && state.step?.id != c.key),
        action = if (added) null else "Add",
        doneLabel = if (added) "Added" else null,
        onAction = { state.add(c) },
        onAnyway = { state.addAnyway(c) },
        onDismiss = state::dismissStep,
    )
}

/** A headset in pairing mode: one Hush can drive pairs, connects and is added in one go; others say so. */
@Composable
private fun NearbyRow(state: AddDeviceState, index: Int, count: Int, d: SeenDevice) {
    DeviceCard(
        index, count, d.name,
        eyebrow = d.moduleId?.let { DeviceModules.module(it).brand }.orEmpty(),
        model = d.model,
        where = Icons.Outlined.BluetoothSearching to "In pairing mode",
        support = if (d.supported) d.model?.note?.let(::supportPill) else "Not supported" to false,
        step = state.step?.takeIf { it.id == d.address },
        enabled = d.supported && !(state.busy && state.step?.id != d.address),
        action = if (d.supported) "Add" else null,
        onAction = { state.pair(d) },
        onAnyway = null,
        onDismiss = state::dismissStep,
    )
}

/**
 * A device as a card: the product on its disc, brand over the name, two quiet pills (how it's connected, how well
 * Hush knows it) and one action. Adding swaps the action for the expressive loading indicator, then a check; a
 * failure opens a line under the card with what to do.
 */
@Composable
private fun DeviceCard(
    index: Int,
    count: Int,
    title: String,
    eyebrow: String,
    model: Model?,
    where: Pair<androidx.compose.ui.graphics.vector.ImageVector, String>,
    /** The support note, and whether it's the good kind (tinted). */
    support: Pair<String, Boolean>?,
    step: AddStep?,
    enabled: Boolean,
    action: String?,
    doneLabel: String? = null,
    onAction: () -> Unit,
    onAnyway: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val phase = step?.phase
    val live = action != null || doneLabel != null
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = { if (phase == null) onAction() },
        enabled = (enabled && action != null) || phase != null,
        interactionSource = press,
        shape = cardShape(index, count),
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().appear().squishy(press, 0.98f),
    ) {
        Column(Modifier.padding(Space.m).animateContentSize(motion.defaultSpatialSpec())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModelThumb(model, enabled = live, size = 64.dp)
                Column(Modifier.weight(1f).padding(horizontal = Space.m)) {
                    if (eyebrow.isNotEmpty()) Text(
                        eyebrow, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (live) colors.onSurface else colors.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(top = Space.s),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Pill(where.first, where.second)
                        support?.let { (text, good) -> Pill(if (good) Icons.Filled.Check else null, text, tinted = good) }
                    }
                }
                AnimatedContent(
                    phase,
                    transitionSpec = {
                        (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f))
                            .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f))
                    },
                    contentAlignment = Alignment.Center,
                    label = "action",
                ) { p ->
                    when (p) {
                        AddStep.Phase.PAIRING, AddStep.Phase.CONNECTING -> LoadingIndicator(Modifier.size(48.dp))
                        AddStep.Phase.CONNECTED -> Box(
                            Modifier.size(48.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(colors.primary),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Check, "Connected", tint = colors.onPrimary) }
                        AddStep.Phase.FAILED -> Spacer(Modifier.size(0.dp))
                        null -> when {
                            doneLabel != null -> Pill(Icons.Filled.Check, doneLabel)
                            action != null -> Button(onClick = onAction, enabled = enabled) { Text(action) }
                            else -> Spacer(Modifier.size(0.dp))
                        }
                    }
                }
            }
            // What's happening, under the card, only while something is.
            if (phase != null && phase != AddStep.Phase.CONNECTED) {
                val failed = phase == AddStep.Phase.FAILED
                Text(
                    when (phase) {
                        AddStep.Phase.PAIRING -> "Pairing… confirm on the phone or the headphones if asked."
                        AddStep.Phase.CONNECTING -> "Connecting…"
                        else -> step.message ?: "Something went wrong."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) colors.error else colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.m),
                )
                if (failed) Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Button(onClick = onAction) { Text("Try again") }
                    if (onAnyway != null) OutlinedButton(onClick = onAnyway) { Text("Add anyway") }
                    else TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

/** A model's support note as a pill: short, and tinted only when Hush fully knows it. */
private fun supportPill(note: String): Pair<String, Boolean> = when {
    note.startsWith("Fully", ignoreCase = true) -> "Supported" to true
    note.startsWith("Not tested", ignoreCase = true) -> "Untested" to false
    else -> note to false
}

/** A small label: how a device is connected, how well Hush knows it. */
@Composable
private fun Pill(icon: androidx.compose.ui.graphics.vector.ImageVector?, text: String, tinted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (tinted) colors.secondaryContainer else colors.surfaceContainerHighest,
        contentColor = if (tinted) colors.onSecondaryContainer else colors.onSurfaceVariant,
    ) {
        Row(Modifier.padding(start = if (icon != null) 6.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Grouped cards read as one block: round outer corners, tight inner ones (the segmented-list look, card-sized). */
private fun cardShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 28.dp; val inner = 8.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return androidx.compose.foundation.shape.RoundedCornerShape(top, top, bottom, bottom)
}

/** Paired audio devices Hush can't drive: listed so the user knows they were seen, folded away past a few. */
@Composable
private fun ColumnScope.UnsupportedSection(others: List<SeenDevice>) {
    if (others.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val shown = if (open || others.size <= 2) others else emptyList()
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader("Paired, not supported yet", Modifier.weight(1f))
        if (others.size > 2) TextButton(onClick = { open = !open }, modifier = Modifier.padding(top = Space.m)) {
            Text(if (open) "Hide" else "Show ${others.size}")
            Spacer(Modifier.width(Space.xs))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
    }
    SegmentedGroup(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        shown.forEachIndexed { i, d ->
            key(d.address) {
                DeviceCard(
                    i, shown.size, d.name, eyebrow = "", model = null,
                    where = Icons.Outlined.Bluetooth to "Paired", support = "Not supported" to false,
                    step = null, enabled = false, action = null, onAction = {}, onAnyway = null, onDismiss = {},
                )
            }
        }
    }
}

// --- Brand and model --------------------------------------------------------------------------------------------------

/**
 * A brand's models, lazily: Sony lists fifty, and composing them all in the frame the page slid in took up to half a
 * second. Only the rows on screen are built; the rest come as the list scrolls.
 */
private fun LazyListScope.brandItems(module: DeviceModule, onModel: (String) -> Unit) {
    val groups = module.models.groupBy { it.group }
    groups.entries.forEachIndexed { gi, (group, models) ->
        if (group != null && groups.size > 1) item(key = "group:$group", contentType = "header") { SectionHeader(group) }
        else if (gi == 0) item(key = "top", contentType = "space") { Spacer(Modifier.height(Space.s)) }
        itemsIndexed(models, key = { _, m -> m.id }, contentType = { _, _ -> "model" }) { i, m ->
            ModelRow(i, models.size, m, onModel)
            if (i < models.lastIndex) Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
        }
    }
    item(key = "end", contentType = "space") { Spacer(Modifier.height(Space.l)) }
}

@Composable
private fun ModelRow(index: Int, count: Int, m: Model, onModel: (String) -> Unit) {
    // Every row's disc is shared (the list is lazy, so that's only the dozen on screen): the tapped one grows into the
    // model page's cookie, and shrinks back into its row on return.
    ValueRow(index, count, Icons.Outlined.Headphones, m.name, m.note,
        leading = {
            ModelThumb(m, size = 48.dp, modifier = Modifier.sharedThing("model:${m.id}"), morph = rememberDiscMorph(onPage = false))
        }) { onModel(m.id) }
}

/** [FullScreenPage]'s frame (back arrow, pinned app bar, readable width) around a lazy list, for long pages. */
@Composable
private fun LazyPage(title: String, onClose: () -> Unit, content: LazyListScope.() -> Unit) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                scrollBehavior = scroll,
            )
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = Space.m, end = Space.m),
                    content = content,
                )
            }
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun ColumnScope.ModelPage(state: AddDeviceState, module: DeviceModule, model: Model) {
    val context = LocalContext.current
    val all by module.candidates.collectAsStateWithLifecycle()
    val registered by DeviceRegistry.items.collectAsStateWithLifecycle()
    val keys = remember(registered) { registered.map { it.key }.toSet() }
    val units = remember(all, model) { all.filter { it.modelId == model.id } }
    // The brand list's disc, grown: a circle there, a cookie here, morphing on the way in and out.
    Box(Modifier.fillMaxWidth().padding(vertical = Space.m), contentAlignment = Alignment.Center) {
        ModelThumb(
            model, size = 232.dp, large = true,
            modifier = Modifier.sharedThing("model:${model.id}"), morph = rememberDiscMorph(onPage = true),
        )
    }
    Text(model.howTo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Space.s, vertical = Space.s))
    if (model.transport == Transport.BLUETOOTH && state.radio != Radio.READY && state.radio != Radio.CHECKING) {
        StatusCard(state, emptyHint = false)
    }
    if (units.isNotEmpty() || !model.addWithoutFinding) {
        SectionHeader(model.transport.found.replaceFirstChar { it.uppercase() })
        if (units.isEmpty()) Text(
            "None yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s),
        )
        else SegmentedGroup {
            units.forEachIndexed { i, c -> key(c.key) { CandidateRow(state, i, units.size, c, added = c.key in keys) } }
        }
    }
    Spacer(Modifier.height(Space.m))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        if (model.transport == Transport.BLUETOOTH) OutlinedButton(onClick = { openBluetoothSettings(context) }) {
            Icon(Icons.Outlined.BluetoothSearching, null, Modifier.size(18.dp)); Spacer(Modifier.width(Space.s)); Text("Bluetooth settings")
        }
        if (model.addWithoutFinding) {
            val c = Candidate(module.id, model.id, model.name, model.transport)
            val added = c.key in keys
            Button(onClick = { state.add(c) }, enabled = !added) { Text(if (added) "Added" else "Add ${model.name}") }
        }
    }
    if (module.hasDemo(model)) {
        // No headphones at hand: a stand-in that acts connected, so the whole app can be tried.
        val demo = demoCandidate(module.id, model)
        val added = demo.key in keys
        Spacer(Modifier.height(Space.s))
        TextButton(onClick = { state.add(demo) }, enabled = !added) { Text(if (added) "Demo added" else "Add as demo") }
    }
    Spacer(Modifier.height(Space.l))
}

/**
 * The model's picture on a disc (bundled, or Sony's render downloaded once); a headphones icon for models without one.
 * [morph] reshapes the disc (circle → cookie) while it travels between the brand list and the model page.
 */
@Composable
private fun ModelThumb(
    model: Model?,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    morph: DiscMorph? = null,
) {
    val hasPicture = model != null && (model.image != null || model.imageUrl != null)
    val art = if (hasPicture) rememberProductArt(model, large) else null
    // A dark product (most of Sony's) gets a light disc and a light one (white EarPods) a dark disc, in either
    // theme. Until the picture is decoded the disc is already there, so nothing shifts when it lands.
    val c = MaterialTheme.colorScheme
    val darkTheme = c.surface.luminance() < 0.5f
    val disc = when {
        art == null -> if (hasPicture) c.surfaceContainerHighest else c.primaryContainer
        art.light -> if (darkTheme) c.surfaceContainerHighest else c.inverseSurface
        else -> if (darkTheme) c.inverseSurface else c.surfaceContainerHighest
    }
    val shaped = if (morph != null) Modifier.graphicsLayer {
        shape = MorphShape(morph.morph, morph.progress.value)
        clip = true
    } else Modifier.clip(CircleShape)
    Box(modifier.size(size).then(shaped).background(disc), contentAlignment = Alignment.Center) {
        // Proportional to the disc as laid out, not to [size]: while the disc travels between the list and the model
        // page it is laid out at every size in between, and a fixed margin cropped or hid the picture.
        if (art != null) Image(
            art.image, model?.name, Modifier.fillMaxSize(0.72f).alpha(if (enabled) 1f else 0.38f),
            contentScale = ContentScale.Fit,
        )
        else if (!hasPicture) Icon(
            Icons.Outlined.Headphones, null, Modifier.fillMaxSize(0.45f),
            tint = if (enabled) c.onPrimaryContainer else c.onSurface.copy(alpha = 0.38f),
        )
    }
}

/** A disc's circle → cookie morph and how far along it is. */
private class DiscMorph(val morph: Morph, val progress: androidx.compose.runtime.State<Float>)

/**
 * Follows the page transition: on the model page ([onPage]) the disc is a cookie while the page is shown and a circle
 * while it comes and goes; on the brand list it is the other way round. Whichever side draws the shared disc, it
 * starts as one and lands as the other, on the expressive spatial spring (which overshoots a touch: a wobble).
 */
@Composable
private fun rememberDiscMorph(onPage: Boolean): DiscMorph? {
    val page = LocalPageScope.current ?: return null
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val progress = page.transition.animateFloat(transitionSpec = { spec }, label = "disc") { s ->
        val shown = s == EnterExitState.Visible
        if (shown == onPage) 1f else 0f
    }
    return remember(progress) { DiscMorph(morph, progress) }
}

/** [Morph] at [progress] as a shape (polygons are normalised to 0..1, so it's scaled to the box). */
private class MorphShape(private val morph: Morph, private val progress: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val path = android.graphics.Path()
        morph.toPath(progress, path)
        path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
        return androidx.compose.ui.graphics.Outline.Generic(path.asComposePath())
    }
}

private fun openBluetoothSettings(context: android.content.Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
}

private fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}

/** A device a module just found that isn't registered: offer to add it, once. */
@Composable
fun FoundDevicePrompt(c: Candidate, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val module = DeviceModules.module(c.moduleId)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.l).padding(bottom = Space.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ModelThumb(module.models.firstOrNull { it.id == c.modelId }, size = 64.dp)
            Spacer(Modifier.height(Space.m))
            Text("New device found", style = MaterialTheme.typography.headlineSmallEmphasized)
            Spacer(Modifier.height(Space.xs))
            Text(
                "${c.name} (${module.brand}) is ${c.transport.found}. " +
                    "Add it to Hush?",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedButton(onClick = onDismiss) { Text("Not now") }
                Button(onClick = onAdd) { Text("Add") }
            }
        }
    }
}
