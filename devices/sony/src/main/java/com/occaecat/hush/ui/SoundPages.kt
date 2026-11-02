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
import com.occaecat.hush.sound.HearingTest
import com.occaecat.hush.sound.SavedEq
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

/** Splits the headset's preset list the way the Sound tab shows it: Sony's factory presets, then the user's slots. */
fun splitPresets(presets: List<Pair<Int, String>>) =
    presets.filter { it.first !in HeadphonesController.WRITABLE_PRESETS } to presets.filter { it.first in HeadphonesController.WRITABLE_PRESETS }

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
