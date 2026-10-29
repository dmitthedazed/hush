@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.occaecat.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.autoeq.AutoEq
import com.occaecat.hush.sound.SavedEq
import com.occaecat.hush.sound.applySavedEq
import com.occaecat.hush.sound.compatibleLayout
import com.occaecat.hush.mdr.EqCapability
import com.occaecat.hush.mdr.EqBandSpec
import com.occaecat.hush.sound.SoundAutomation
import com.occaecat.hush.sound.SoundStore
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase
import kotlin.math.roundToInt

@Composable
fun SoundTab(state: HeadphonesState, vm: HeadphonesController) {
    val dialogs = rememberDialogState()
    val context = LocalContext.current
    var autoEqOpen by rememberSaveable { mutableStateOf(false) }
    var fyeOpen by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val sound by SoundStore.config.collectAsState()
    // The last AutoEq profile only describes the sound while the custom preset it was written to is active.
    var autoEqApplied by remember { mutableStateOf(AutoEq.lastApplied(context)) }
    val connected = state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING
    val caps = state.eqCaps
    val eq = state.eq.takeIf { caps != null }
    val error = state.error
    val presets = eq?.let { presetsFor(state) }
    TabScaffold(
        title = "Sound",
        subtitle = subtitleFor(state),
        // Same frame as Listen: the equalizer is the page's hero, so there is no app bar while it is shown.
        hero = if (connected && eq != null && presets != null) ({
            EqualizerHero(eq, caps!!, presets, vm, onManualEdit = {
                // A hand-tuned band means the curve is no longer the AutoEq one.
                if (autoEqApplied != null) { AutoEq.forgetApplied(context); autoEqApplied = null }
            })
        }) else null,
    ) {
        if (state.phase == Phase.RECONNECTING || state.audioSettling) wideItem { ReconnectingBanner(state, ItemWidth) }
        if (connected && error != null) wideItem {
            Surface(ItemWidth.padding(top = Space.s), shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                Text(error, Modifier.padding(Space.m), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!connected) {
            wideItem { NotConnectedPlaceholder(state, ItemWidth) }
            return@TabScaffold
        }
        if (eq != null && presets != null) item { PresetPicker(eq, caps!!, presets, sound.library, vm, ItemWidth) }
        if (eq != null && eq.bands.isNotEmpty()) item {
            val current = autoEqApplied?.takeIf { it.matches(state) }
            // Find Your Equalizer uses Sound Connect's tables for the standard six bands only.
            val fye = state.isSony && eq.bands.size == Labels.sixBands.size && caps?.bandSpecs?.all { it.neutral == 10 && it.min == 0 && it.max == 20 } == true
            Column(ItemWidth) {
                SectionHeader("Personalize", top = Space.s)
                SegmentedGroup {
                    val rows = listOfNotNull("fye".takeIf { fye }, "hearing".takeIf { fye }, "autoeq", "library")
                    rows.forEachIndexed { i, r ->
                        when (r) {
                            "fye" -> ValueRow(
                                i, rows.size, Icons.Outlined.Hearing, "Find Your Equalizer",
                                "Pick the sound you like by ear; saved to Custom 2",
                                onClick = { fyeOpen = true },
                            )
                            "hearing" -> ValueRow(
                                i, rows.size, Icons.Outlined.GraphicEq, "Hearing test",
                                "Even out the bands you hear less",
                                onClick = { page = "hearing" },
                            )
                            "autoeq" -> ValueRow(
                                i, rows.size, Icons.Outlined.AutoGraph,
                                current?.name?.let { "AutoEq · $it" } ?: "AutoEq",
                                current?.detail ?: "Fit a measured correction to these bands",
                                onClick = { autoEqOpen = true },
                            )
                            "library" -> ValueRow(
                                i, rows.size, Icons.Outlined.LibraryMusic, "EQ library",
                                when (val n = sound.library.size) { 0 -> "Save, share and import curves"; 1 -> "1 saved curve"; else -> "$n saved curves" },
                                onClick = { page = "library" },
                            )
                        }
                    }
                }
            }
        }
        item {
            val status by SoundAutomation.status.collectAsState()
            Column(ItemWidth) {
                SectionHeader("Automation", top = Space.s)
                SegmentedGroup {
                    ValueRow(
                        0, 2, Icons.AutoMirrored.Outlined.DirectionsWalk, "Adaptive Sound Control",
                        if (!sound.ascEnabled) "Off" else status.inputs.activity?.let { "On · ${it.label}" } ?: "On",
                        onClick = { page = "asc" },
                    )
                    ValueRow(
                        1, 2, Icons.Outlined.Apps, "App profiles",
                        when {
                            !sound.appsEnabled -> "Off"
                            sound.appRules.isEmpty() -> "On · no apps yet"
                            else -> "On · ${sound.appRules.size} app" + if (sound.appRules.size > 1) "s" else ""
                        },
                        onClick = { page = "apps" },
                    )
                }
            }
        }
        val rows = listOfNotNull(
            state.dseeAuto?.let { "dsee" },
            state.soundQualityPreferConnection?.let { "mode" },
        )
        if (rows.isNotEmpty()) item {
            Column(ItemWidth) {
                SectionHeader("Sound quality", top = Space.s)
                SegmentedGroup {
                    rows.forEachIndexed { i, r ->
                        when (r) {
                            "dsee" -> SwitchRow(
                                i, rows.size, Icons.Outlined.AutoAwesome, "DSEE Extreme",
                                if (state.dseeActive) "Upscaling compressed audio now" else "Restores detail in compressed audio",
                                state.dseeAuto == true, onCheckedChange = vm::setDsee,
                            )
                            "mode" -> {
                                val preferConn = state.soundQualityPreferConnection == true
                                ValueRow(
                                    i, rows.size, Icons.Outlined.Compress, "Bluetooth connection quality",
                                    if (preferConn) "Prioritize stable connection" else "Prioritize sound quality",
                                    enabled = !state.audioSettling,
                                ) {
                                    dialogs.choice = Choice(
                                        "Bluetooth connection quality",
                                        listOf(0 to "Prioritize sound quality", 1 to "Prioritize stable connection"),
                                        if (preferConn) 1 else 0,
                                    ) { v -> dialogs.confirmReconnect { vm.setSoundQualityMode(v == 1) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    DialogHost(dialogs)
    if (autoEqOpen && eq != null && caps != null) {
        AutoEqSheet(
            modelName = state.modelName,
            capability = caps,
            targetPreset = if (!state.isSony) -1 else if (eq.presetId in HeadphonesController.WRITABLE_PRESETS) eq.presetId else HeadphonesController.CUSTOM_PRESET,
            vm = vm,
            onApplied = { autoEqApplied = AutoEq.lastApplied(context) },
            onDismiss = { autoEqOpen = false },
        )
    }
    when (page) {
        "library" -> EqLibraryPage(eq, presets.orEmpty(), vm) { page = null }
        "asc" -> AdaptiveSoundPage(presets.orEmpty()) { page = null }
        "apps" -> AppProfilesPage(presets.orEmpty()) { page = null }
        "hearing" -> HearingTestPage(vm) { page = null }
    }
    if (fyeOpen) {
        FindYourEqDialog(vm) { saved ->
            fyeOpen = false
            // Custom 2 now holds the found balance; an AutoEq profile stored there no longer describes it.
            if (saved && autoEqApplied?.presetId == HeadphonesController.USER_PRESET_2) {
                AutoEq.forgetApplied(context); autoEqApplied = null
            }
        }
    }
}

internal fun presetsFor(state: HeadphonesState): List<Pair<Int, String>> =
    state.eqCaps?.presets?.takeIf { it.isNotEmpty() }
        ?.map { it.id to it.name.ifBlank { if (state.isSony) Labels.presetNames[it.id] ?: "Preset" else "Preset" } }
        ?: if (state.isSony) Labels.presetNames.filterKeys { it <= 0x17 || it == 0xA0 }.toList() else emptyList()

/**
 * The top of the page as one surfaceContainerHigh block (status bar included, rounded at the bottom): the
 * equalizer title, the active preset and a reset button, over the band sliders.
 */
@Composable
private fun EqualizerHero(eq: EqState, caps: EqCapability, presets: List<Pair<Int, String>>, vm: HeadphonesController, onManualEdit: () -> Unit) {
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
                Text(
                    "Equalizer",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    if (!eq.enabled) "Bypassed" else presets.firstOrNull { it.first == eq.presetId }?.second ?: "Custom",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // Always composed: it must stay alive while it holds the headset on Off.
            if (caps.supportsEnable) androidx.compose.material3.Switch(eq.enabled, vm::setEqEnabled)
            if (eq.bands.isNotEmpty()) CompareButton(eq, caps, vm)
            if (eq.bands.isNotEmpty()) {
                FilledTonalIconButton(onClick = { vm.setEqFlat(); onManualEdit() }) {
                    Icon(Icons.Outlined.RestartAlt, "Reset to flat")
                }
            }
        }
        if (eq.bands.isNotEmpty()) {
            Spacer(Modifier.height(Space.l))
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.m)) {
                eq.bands.forEachIndexed { i, raw ->
                    caps.bandSpecs.getOrNull(i)?.let { band ->
                        EqBand(band, raw, Modifier.weight(1f)) { vm.setEqBand(i, it); onManualEdit() }
                    }
                }
            }
        } else {
            Text(
                "This preset has no adjustable bands.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
            )
        }
    }
}

/**
 * Hold to compare against bypass, Sony's Off preset, or a flat curve when bypass is unavailable.
 * Release restores the captured curve only on the original device and layout.
 */
@Composable
private fun CompareButton(eq: EqState, caps: EqCapability, vm: HeadphonesController) {
    var holding by remember { mutableStateOf(false) }
    val headset by vm.state.collectAsState()
    // The press handler outlives recompositions (selecting Off changes `eq`), so read the latest value through this.
    val current by rememberUpdatedState(eq)
    val compareWithFlat = !caps.supportsEnable && caps.namespace != "sony-mdr"
    val scale by animateFloatAsState(if (holding) 1.15f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ab")
    Surface(
        shape = CircleShape,
        color = if (holding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (holding) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(end = Space.s).size(40.dp).scale(scale)
            .semantics { contentDescription = if (compareWithFlat) "Hold to compare with flat EQ" else "Hold to hear without equalizer" }
            .pointerInput(caps.layoutId, caps.supportsEnable, headset.address, headset.phase) {
                detectTapGestures(onPress = {
                    val before = current
                    if (!before.enabled || (caps.namespace == "sony-mdr" && before.presetId == 0x00)) return@detectTapGestures
                    val address = vm.state.value.address
                    holding = true
                    when {
                        caps.supportsEnable -> vm.setEqEnabled(false)
                        caps.namespace == "sony-mdr" -> vm.selectEqPreset(0x00, caps.namespace)
                        else -> {
                            if (!vm.applyEqBands(caps.bandSpecs.map { it.neutral }, caps.layoutId)) {
                                holding = false
                                return@detectTapGestures
                            }
                        }
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        holding = false
                        vm.restoreEq(before, caps.layoutId, address)
                    }
                })
            },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Compare, null, Modifier.size(20.dp)) }
    }
}

/** Every preset at once, wrapped into rows of pill toggles; the active one fills with primary. */
/**
 * Presets in two groups: Sony's factory curves, and the user's own — the headset's writable slots (Manual,
 * Custom 1/2) plus the phone-side EQ library, which is written into Manual when picked.
 */
@Composable
private fun PresetPicker(eq: EqState, caps: EqCapability, presets: List<Pair<Int, String>>, library: List<SavedEq>, vm: HeadphonesController, modifier: Modifier) {
    val (sony, yours) = splitPresets(presets)
    Column(modifier) {
        if (sony.isNotEmpty()) {
            SectionHeader("Factory presets", top = Space.s)
            PresetChips(sony.map { (id, name) -> Triple(name, eq.presetId == id && eq.enabled) { vm.selectEqPreset(id, caps.namespace) } })
        }
        if (caps.namespace != "sony-mdr") {
            SectionHeader("Equalizer", top = Space.s)
            PresetChips(listOf(Triple("Flat", eq.enabled && eq.bands == caps.bandSpecs.map { it.neutral }) { vm.setEqFlat() }))
        }
        SectionHeader("Your presets")
        val usable = library.filter { it.compatibleLayout(caps) != null }
        PresetChips(
            yours.map { (id, name) -> Triple(name, eq.presetId == id && eq.enabled) { vm.selectEqPreset(id, caps.namespace) } } +
                usable.map { item ->
                    // A library curve counts as active while Manual holds exactly its levels.
                    val active = eq.enabled && eq.bands == item.levels
                    Triple(item.name, active) { vm.applySavedEq(item); Unit }
                },
        )
    }
}

@Composable
private fun PresetChips(items: List<Triple<String, Boolean, () -> Unit>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        items.forEach { (name, checked, onClick) ->
            val press = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ToggleButton(
                checked = checked,
                onCheckedChange = { onClick() },
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

/** One band: 0..20 with 10 as flat, shown as -10..+10 dB-ish steps. */
@Composable
private fun EqBand(band: EqBandSpec, raw: Int, modifier: Modifier, onCommit: (Int) -> Unit) {
    val state = remember(band) { SliderState(value = raw.toFloat(), steps = ((band.max - band.min) / band.uiStep - 1).coerceAtLeast(0), valueRange = band.min.toFloat()..band.max.toFloat()) }
    state.onValueChangeFinished = { onCommit((band.neutral + ((state.value - band.neutral) / band.uiStep).roundToInt() * band.uiStep).coerceIn(band.min, band.max)) }
    LaunchedEffect(raw) { if (!state.isDragging) state.value = raw.toFloat() }
    val db = band.toDb(state.value.roundToInt())
    val layout = LocalLayout.current
    val height = when {
        layout.compactHeight -> 140.dp
        layout.shortHeight -> 170.dp
        else -> 200.dp
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatEqDb(db),
            style = MaterialTheme.typography.labelLarge,
            color = if (db == 0.0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Space.s))
        VerticalSlider(state = state, modifier = Modifier.height(height), reverseDirection = true, colors = quietSliderColors())
        Spacer(Modifier.height(Space.s))
        Text(
            band.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatEqDb(db: Double): String {
    val value = if (kotlin.math.abs(db - db.roundToInt()) < 0.001) db.roundToInt().toString() else "%.2f".format(java.util.Locale.ROOT, db).trimEnd('0').trimEnd('.')
    return if (db > 0) "+$value" else value
}
