package com.occaecat.hush.wear.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.StepperLevelIndicator
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.occaecat.hush.link.Command
import com.occaecat.hush.link.EqNames
import com.occaecat.hush.wear.hub.HushHub
import com.occaecat.hush.wear.hub.RoutePref
import kotlin.math.roundToInt

/** Ambient level 1–20 on a Stepper; the bezel / crown turns it too, one detent per step with a haptic tick. */
@Composable
fun AmbientScreen(hub: HushHub) {
    val s by hub.state.collectAsStateWithLifecycle()
    val range = 1..20
    var level by remember { mutableIntStateOf(s.snapshot.ambientLevel.coerceIn(range)) }
    var rotary by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }

    fun set(v: Int) {
        val next = v.coerceIn(range)
        if (next == level) return
        level = next
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        hub.perform(Command.SetAmbientLevel(next))
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    // No time text here: the Stepper's + button owns the top of the screen.
    ScreenScaffold(timeText = {}) {
      Box(Modifier.fillMaxSize()) {
        Stepper(
            value = level,
            onValueChange = ::set,
            valueProgression = range,
            decreaseIcon = { Icon(Icons.Rounded.Remove, "Less ambient sound") },
            increaseIcon = { Icon(Icons.Rounded.Add, "More ambient sound") },
            modifier = Modifier
                .onRotaryScrollEvent {
                    rotary += it.verticalScrollPixels
                    val steps = (rotary / ROTARY_STEP_PX).roundToInt()
                    if (steps != 0) {
                        rotary -= steps * ROTARY_STEP_PX
                        set(level + steps)
                    }
                    true
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$level", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text("Ambient level", style = MaterialTheme.typography.labelMedium)
            }
        }
        // Drawn over the Stepper on the left edge, always visible (the scaffold's scroll-indicator slot auto-hides).
        StepperLevelIndicator(
            value = { level },
            valueProgression = range,
            modifier = Modifier.align(Alignment.CenterStart),
        )
      }
    }
}

private const val ROTARY_STEP_PX = 48f

@Composable
fun EqualizerScreen(hub: HushHub) {
    val s by hub.state.collectAsStateWithLifecycle()
    val snap = s.snapshot
    val presets = snap.eqPresets.ifEmpty { listOfNotNull(snap.eqPreset) }
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Equalizer") } }
            items(presets.size) { i ->
                val id = presets[i]
                RadioButton(
                    selected = snap.eqPreset == id,
                    onSelect = { hub.perform(Command.SetEqPreset(id)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    label = { Text(EqNames.of(id), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/**
 * Where the headset is controlled from. Auto keeps the phone in charge when it is near and falls back to the
 * watch's own Bluetooth link; the headset list is what is bonded to the watch for that standalone route.
 */
@Composable
fun ConnectionScreen(hub: HushHub) {
    val s by hub.state.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) hub.refreshPaired()
    }
    LaunchedEffect(Unit) {
        if (hub.hasBluetoothPermission()) hub.refreshPaired()
        hub.refreshPhone()
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            if (s.snapshot.connected || s.snapshot.busy) EdgeButton(
                onClick = { hub.perform(Command.Disconnect) },
                buttonSize = EdgeButtonSize.Small,
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) { Icon(Icons.Rounded.LinkOff, "Disconnect") }
            else EdgeButton(onClick = { hub.perform(Command.Connect) }, buttonSize = EdgeButtonSize.Small) { Text("Connect") }
        },
    ) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Control from") } }
            for ((pref, label, secondary) in listOf(
                Triple(RoutePref.AUTO, "Auto", "Phone when near, else watch"),
                Triple(RoutePref.PHONE, "Phone", s.phoneName ?: if (s.phoneReachable) "Connected" else "Not reachable"),
                Triple(RoutePref.WATCH, "Watch", "Direct Bluetooth"),
            )) item {
                RadioButton(
                    selected = s.pref == pref,
                    onSelect = { hub.setPref(pref) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    secondaryLabel = { Text(secondary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    label = { Text(label) },
                )
            }

            item { ListHeader { Text("On this watch") } }
            if (!hub.hasBluetoothPermission()) item {
                Button(
                    onClick = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    secondaryLabel = { Text("To use headphones without the phone") },
                    label = { Text("Allow Bluetooth") },
                )
            } else {
                val sony = s.watchPaired.filter { it.looksSony }.ifEmpty { s.watchPaired }
                if (sony.isEmpty()) item {
                    Text(
                        "Pair your headphones in the watch's Bluetooth settings to use them without the phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                for (d in sony) item {
                    RadioButton(
                        selected = s.watchHeadset == d.address,
                        onSelect = { hub.pickWatchHeadset(d.address) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        icon = { Icon(Icons.Rounded.Headphones, null) },
                        label = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
                if (s.watchHeadset != null) item {
                    Button(
                        onClick = { hub.forgetWatchHeadset() },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        colors = ButtonDefaults.outlinedButtonColors(),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        label = { Text("Forget on watch") },
                    )
                }
            }
        }
    }
}
