@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.mdr.EqState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sound Connect's "Find Your Equalizer" (feature/eqlisteningcomparison) for headsets with the standard six bands.
 * Two listening rounds of five candidates each, then Before/After and a save into Custom 2. The candidates are
 * Sound Connect's own tables (EqBandStepsStandard, y.java): a low offset moves Clear Bass, a high offset moves
 * 2.5k/6.3k/16k together, while 400 Hz and 1 kHz stay flat.
 */
object Fye {
    /** Clear Bass offset (StepValuesLow). */
    enum class Low(val clearBass: Int) { P9(9), P6(6), P3(3), ZERO(0), M3(-3), M6(-6), M9(-9) }

    /** 2.5k / 6.3k / 16k offsets (StepValuesHigh). */
    enum class High(val k25: Int, val k63: Int, val k16: Int) {
        ZERO(0, 0, 0), P2(2, 4, 6), P1(1, 2, 3), P0(0, 1, 2), M0(0, -1, -2), M1(-1, -2, -3), M2(-2, -4, -6)
    }

    data class Candidate(val low: Low, val high: High) {
        /** Headset levels 0..20 in band order: Clear Bass, 400, 1k, 2.5k, 6.3k, 16k. */
        val levels get() = listOf(low.clearBass, 0, 0, high.k25, high.k63, high.k16).map { it + 10 }
    }

    enum class Pick { ORG, A, B, C, D }

    val firstRound = mapOf(
        Pick.ORG to Candidate(Low.ZERO, High.ZERO),
        Pick.A to Candidate(Low.P6, High.M1),
        Pick.B to Candidate(Low.P6, High.P1),
        Pick.C to Candidate(Low.M6, High.P1),
        Pick.D to Candidate(Low.M6, High.M1),
    )

    /** The second round narrows in around whatever was picked first; its ORG is that first pick. */
    fun secondRound(first: Pick): Map<Pick, Candidate> = when (first) {
        Pick.ORG -> mapOf(
            Pick.ORG to Candidate(Low.ZERO, High.ZERO), Pick.A to Candidate(Low.P6, High.ZERO),
            Pick.B to Candidate(Low.ZERO, High.P1), Pick.C to Candidate(Low.M6, High.ZERO),
            Pick.D to Candidate(Low.ZERO, High.M1),
        )
        Pick.A -> mapOf(
            Pick.ORG to Candidate(Low.P6, High.M1), Pick.A to Candidate(Low.P9, High.M2),
            Pick.B to Candidate(Low.P9, High.M0), Pick.C to Candidate(Low.P3, High.M0),
            Pick.D to Candidate(Low.P3, High.M2),
        )
        Pick.B -> mapOf(
            Pick.ORG to Candidate(Low.P6, High.P1), Pick.A to Candidate(Low.P9, High.P0),
            Pick.B to Candidate(Low.P9, High.P2), Pick.C to Candidate(Low.P3, High.P2),
            Pick.D to Candidate(Low.P3, High.P0),
        )
        Pick.C -> mapOf(
            Pick.ORG to Candidate(Low.M6, High.P1), Pick.A to Candidate(Low.M3, High.P0),
            Pick.B to Candidate(Low.M3, High.P2), Pick.C to Candidate(Low.M9, High.P2),
            Pick.D to Candidate(Low.M9, High.P0),
        )
        Pick.D -> mapOf(
            Pick.ORG to Candidate(Low.M6, High.M1), Pick.A to Candidate(Low.M3, High.M2),
            Pick.B to Candidate(Low.M3, High.M0), Pick.C to Candidate(Low.M9, High.M0),
            Pick.D to Candidate(Low.M9, High.M2),
        )
    }

    /** How a candidate sounds next to the round's starting point, in words. */
    fun describe(c: Candidate, base: Candidate): String {
        val bass = c.low.clearBass - base.low.clearBass
        val treble = c.high.k16 - base.high.k16
        return listOfNotNull(
            when { bass > 0 -> "More bass"; bass < 0 -> "Less bass"; else -> null },
            when { treble > 0 -> "More treble"; treble < 0 -> "Less treble"; else -> null },
        ).joinToString(" · ").ifEmpty { "Unchanged" }
    }
}

private enum class FyeStage { INTRO, PREPARING, ROUND_1, ROUND_2, RESULT }

/**
 * Full-screen Find Your Equalizer flow. While auditioning, every candidate is written to Custom 2 and selected,
 * exactly like Sound Connect; leaving without saving puts Custom 2's old levels back and reselects the preset
 * that was active before.
 */
@Composable
fun FindYourEqDialog(vm: HeadphonesController, onDone: (saved: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val original = remember { vm.state.value.eq }
    val originalAddress = remember { vm.state.value.address }
    val originalLayout = remember { vm.state.value.eqCaps?.layoutId }
    fun sameDevice() = vm.state.value.isSony && vm.state.value.address == originalAddress && vm.state.value.eqCaps?.layoutId == originalLayout
    var stage by remember { mutableStateOf(FyeStage.INTRO) }
    var oldCustom2 by remember { mutableStateOf<List<Int>?>(null) }
    var first by remember { mutableStateOf<Fye.Pick?>(null) }
    var second by remember { mutableStateOf<Fye.Pick?>(null) }
    var after by remember { mutableStateOf(true) }
    val headset by vm.state.collectAsState()
    LaunchedEffect(headset.address, headset.eqCaps?.layoutId) { if (!sameDevice()) onDone(false) }

    val tuned = first?.let { f -> second?.let { Fye.secondRound(f)[it] } }

    fun audition(c: Fye.Candidate): Boolean = sameDevice() && vm.writeEqPreset(HeadphonesController.USER_PRESET_2, c.levels, originalLayout)

    fun restore(original: EqState?) {
        if (!sameDevice()) return
        oldCustom2?.let { vm.writeEqPreset(HeadphonesController.USER_PRESET_2, it, originalLayout) }
        val o = original ?: return
        originalLayout?.let { vm.restoreEq(o, it, originalAddress) }
    }

    fun cancel() {
        if (stage != FyeStage.INTRO) restore(original)
        onDone(false)
    }

    fun start() {
        if (!sameDevice()) { onDone(false); return }
        stage = FyeStage.PREPARING
        scope.launch {
            // Remember what Custom 2 holds now so a cancelled run can put it back.
            val before = vm.state.value.eq
            oldCustom2 = if (before?.presetId == HeadphonesController.USER_PRESET_2) before.bands else {
                vm.selectEqPreset(HeadphonesController.USER_PRESET_2)
                val got = withTimeoutOrNull(2_500) {
                    vm.state.first { s -> s.eq?.presetId == HeadphonesController.USER_PRESET_2 && s.eq?.bands != before?.bands }
                }
                (got?.eq ?: vm.state.value.eq)?.bands
            }
            first = Fye.Pick.ORG
            if (!sameDevice()) { onDone(false); return@launch }
            audition(Fye.firstRound.getValue(Fye.Pick.ORG))
            stage = FyeStage.ROUND_1
        }
    }

    val stepBack = stage == FyeStage.ROUND_2 || stage == FyeStage.RESULT
    fun back() {
        when (stage) {
            FyeStage.ROUND_2 -> { stage = FyeStage.ROUND_1; first?.let { audition(Fye.firstRound.getValue(it)) } }
            FyeStage.RESULT -> { stage = FyeStage.ROUND_2; after = true; tuned?.let(::audition) }
            else -> cancel()
        }
    }

    Dialog(
        onDismissRequest = ::cancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // A step back within the test springs the page back; from the first round back closes it.
        Surface(Modifier.fillMaxSize().predictiveBack(exits = !stepBack, onBack = ::back), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Find Your Equalizer") },
                    navigationIcon = { IconButton(onClick = ::cancel) { Icon(Icons.Filled.Close, "Close") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                val progress by animateFloatAsState(
                    when (stage) {
                        FyeStage.INTRO, FyeStage.PREPARING -> 0f
                        FyeStage.ROUND_1 -> 1 / 3f
                        FyeStage.ROUND_2 -> 2 / 3f
                        FyeStage.RESULT -> 1f
                    },
                    MaterialTheme.motionScheme.slowSpatialSpec(), label = "fye",
                )
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.m))
                MusicHint(context, Modifier.padding(horizontal = Space.m, vertical = Space.s))
                val motion = MaterialTheme.motionScheme
                AnimatedContent(
                    targetState = stage,
                    transitionSpec = {
                        (slideInHorizontally(motion.defaultSpatialSpec()) { it / 5 } + fadeIn(motion.defaultEffectsSpec()))
                            .togetherWith(fadeOut(motion.fastEffectsSpec()))
                    },
                    modifier = Modifier.weight(1f),
                    label = "fyeStage",
                ) { s ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.m),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (s) {
                            FyeStage.INTRO -> Intro()
                            FyeStage.PREPARING -> Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator(Modifier.size(64.dp))
                            }
                            FyeStage.ROUND_1 -> Round(
                                "Let's feel the difference in sound. Select the sound that suits your preference.",
                                Fye.firstRound, first,
                            ) { p -> first = p; audition(Fye.firstRound.getValue(p)) }
                            FyeStage.ROUND_2 -> {
                                val options = Fye.secondRound(first ?: Fye.Pick.ORG)
                                Round("Now, let's find a balance closer to your taste.", options, second) { p ->
                                    second = p; audition(options.getValue(p))
                                }
                            }
                            FyeStage.RESULT -> Result(tuned, original, after) { a ->
                                after = a
                                if (a) tuned?.let(::audition) else restore(original)
                            }
                        }
                    }
                }
                // Bottom actions.
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    when (stage) {
                        FyeStage.INTRO -> Button(onClick = ::start, modifier = Modifier.weight(1f).height(56.dp)) { Text("Start") }
                        FyeStage.PREPARING -> {}
                        FyeStage.ROUND_1 -> Button(
                            onClick = {
                                second = Fye.Pick.ORG
                                audition(Fye.secondRound(first ?: Fye.Pick.ORG).getValue(Fye.Pick.ORG))
                                stage = FyeStage.ROUND_2
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text("Next") }
                        FyeStage.ROUND_2 -> Button(
                            onClick = { after = true; stage = FyeStage.RESULT },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) { Text("Next") }
                        FyeStage.RESULT -> {
                            OutlinedButton(
                                onClick = {
                                    first = Fye.Pick.ORG; second = null; after = true
                                    audition(Fye.firstRound.getValue(Fye.Pick.ORG))
                                    stage = FyeStage.ROUND_1
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text("Retry") }
                            Button(
                                onClick = {
                                    tuned?.let {
                                        if (audition(it)) com.occaecat.hush.sound.SoundStore.saveEq(
                                            "Find Your Equalizer", it.levels,
                                            "Find Your Equalizer · " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date()),
                                            originalLayout ?: com.occaecat.hush.sound.legacySonyLayout(it.levels.size),
                                        )
                                    }
                                    onDone(true)
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) { Text("Save") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro() {
    Spacer(Modifier.height(Space.xl))
    ShapeBadge(Icons.Outlined.MusicNote, size = 96.dp)
    Spacer(Modifier.height(Space.l))
    Text("Find your favorite sound balance.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(Space.m))
    Text(
        "Play music and press Start. In two rounds you pick the sound you like best while it plays; " +
            "the headphones switch to each option as you tap it.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(Space.m))
    Text(
        "The completed sound balance will be saved to Custom 2 of the equalizer.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
}

/** Five candidates: the round's starting point full width, the four variations in a 2×2 grid. */
@Composable
private fun Round(title: String, options: Map<Fye.Pick, Fye.Candidate>, selected: Fye.Pick?, onPick: (Fye.Pick) -> Unit) {
    val base = options.getValue(Fye.Pick.ORG)
    Spacer(Modifier.height(Space.m))
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
    Spacer(Modifier.height(Space.l))
    Column(Modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        CandidateTile("Current", "As it sounds now", base, selected == Fye.Pick.ORG, Modifier.fillMaxWidth()) { onPick(Fye.Pick.ORG) }
        listOf(listOf(Fye.Pick.A, Fye.Pick.B), listOf(Fye.Pick.D, Fye.Pick.C)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { p ->
                    val c = options.getValue(p)
                    CandidateTile(p.name, Fye.describe(c, base), c, selected == p, Modifier.weight(1f)) { onPick(p) }
                }
            }
        }
    }
}

@Composable
private fun CandidateTile(
    title: String,
    subtitle: String,
    candidate: Fye.Candidate,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = modifier.height(132.dp).semantics { role = Role.RadioButton },
        shapes = ToggleButtonDefaults.shapes(
            shape = MaterialTheme.shapes.extraLarge,
            pressedShape = MaterialTheme.shapes.large,
            checkedShape = MaterialTheme.shapes.extraLargeIncreased,
        ),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AnimatedVisibility(selected) { Icon(Icons.Filled.Check, null, Modifier.size(20.dp)) }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            Spacer(Modifier.weight(1f))
            BandBars(candidate.levels, Modifier.fillMaxWidth().height(56.dp))
        }
    }
}

/** Six tiny bars around a centre line: the shape of a candidate at a glance. */
@Composable
private fun BandBars(levels: List<Int>, modifier: Modifier) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier) {
        val n = levels.size
        val gap = 8.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val mid = size.height / 2
        drawLine(color.copy(alpha = 0.3f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        levels.forEachIndexed { i, lv ->
            val db = lv - 10
            val h = (kotlin.math.abs(db) / 9f * mid).coerceAtLeast(3.dp.toPx())
            val top = if (db >= 0) mid - h else mid
            drawRoundRect(color, Offset(i * (w + gap), top), Size(w, h), CornerRadius(w / 2, w / 2))
        }
    }
}

@Composable
private fun Result(tuned: Fye.Candidate?, original: EqState?, after: Boolean, onSelect: (Boolean) -> Unit) {
    Spacer(Modifier.height(Space.m))
    Text(
        "You have found a sound balance that suits your preference.",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 480.dp),
    )
    Spacer(Modifier.height(Space.l))
    Row(
        Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        listOf(false to "Before", true to "After").forEachIndexed { i, (value, label) ->
            ToggleButton(
                checked = after == value,
                onCheckedChange = { onSelect(value) },
                modifier = Modifier.weight(1f).height(56.dp).semantics { role = Role.RadioButton },
                shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
            ) { Text(label) }
        }
    }
    Spacer(Modifier.height(Space.l))
    val shown = if (after) tuned?.levels else original?.bands
    if (shown != null && shown.size == Labels.sixBands.size) {
        Surface(
            Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(Space.m)) {
                shown.forEachIndexed { i, lv ->
                    val db = lv - 10
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (db > 0) "+$db" else "$db",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (db == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            if (i == 0) "Bass" else Labels.sixBands[i],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(Space.m))
    Text(
        if (after) "Save keeps this balance in Custom 2 and switches to it." else "Playing your previous equalizer setting.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Sound Connect warns when nothing is playing, since the comparison only makes sense with music on. */
@Composable
private fun MusicHint(context: Context, modifier: Modifier) {
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var playing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            playing = audio.isMusicActive
            delay(1_000)
        }
    }
    AnimatedVisibility(!playing, modifier) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.size(Space.s))
            Text(
                "Music is not being played back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
