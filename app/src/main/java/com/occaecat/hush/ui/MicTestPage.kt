@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.material3.toShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialShapes
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.Microphone
import com.occaecat.hush.features.MicTest
import kotlin.math.roundToInt

/** The Device tab's entry to the mic test, for any device with a [Microphone]. */
@Composable
fun MicTestRow(modifier: Modifier, open: () -> Unit) {
    Column(modifier) {
        SectionHeader("Microphone", top = Space.s)
        SegmentedGroup {
            ValueRow(0, 1, Icons.Outlined.Mic, "Microphone test", null,
                modifier = Modifier.sharedPage("mic"), onClick = open)
        }
    }
}

/** Record a few seconds from the device's mic (or the phone's, to compare) and hear it back. Shared by every device. */
@Composable
fun MicTestPage(c: DeviceController, name: String, mic: Microphone, onClose: () -> Unit) {
    val context = LocalContext.current
    val test = remember(mic) { MicTest(context, mic) }
    DisposableEffect(test) { onDispose { test.close() } }
    // The level and progress tick ~20 times a second: only what reads them (the meter, the seconds left) follows.
    val state = test.state.collectAsState()
    val st by remember(state) { derivedStateOf(structuralEqualityPolicy()) { state.value.copy(level = 0f, progress = 0f) } }
    val secondsLeft by remember(state) { derivedStateOf { (5 * (1 - state.value.progress)).roundToInt().coerceAtLeast(1) } }
    var source by remember { mutableStateOf(MicTest.Source.DEVICE) }
    var permTick by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permTick++
        if (ok) test.record(source)
    }
    val granted = remember(permTick) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val busy = st.phase != MicTest.Phase.IDLE
    val motion = MaterialTheme.motionScheme
    val level = remember(test) { Animatable(0f) }
    LaunchedEffect(test) { test.state.map { it.level }.distinctUntilChanged().collectLatest { level.animateTo(it, motion.fastSpatialSpec()) } }

    FullScreenPage("Microphone test", onClose) {
        val colors = MaterialTheme.colorScheme
        // The card's colour is drawn, not composed; the text's colour and the meter's follow on the same spring.
        val container by animateColorAsState(
            when (st.phase) {
                MicTest.Phase.RECORDING -> colors.primaryContainer
                MicTest.Phase.PLAYING -> colors.tertiaryContainer
                MicTest.Phase.IDLE -> colors.surfaceContainerHigh
            }, motion.defaultEffectsSpec(), label = "mic hero",
        )
        val onContainer by animateColorAsState(
            when (st.phase) {
                MicTest.Phase.RECORDING -> colors.onPrimaryContainer
                MicTest.Phase.PLAYING -> colors.onTertiaryContainer
                MicTest.Phase.IDLE -> colors.onSurface
            }, motion.defaultEffectsSpec(), label = "mic hero content",
        )
        val meter by animateColorAsState(
            if (st.phase == MicTest.Phase.PLAYING) colors.tertiary else colors.primary, motion.defaultEffectsSpec(), label = "mic meter",
        )
        val hero = MaterialTheme.shapes.extraLarge
        Surface(
            Modifier.fillMaxWidth().clip(hero).drawBehind { drawRect(container) },
            shape = hero, color = Color.Transparent, contentColor = onContainer,
        ) {
            Column(Modifier.padding(horizontal = Space.l, vertical = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = colors.surface.copy(alpha = 0.55f), modifier = Modifier.size(128.dp)) {
                    Box(contentAlignment = Alignment.Center) { c.Render(Modifier.size(88.dp), true) }
                }
                Spacer(Modifier.height(Space.l))
                Text(
                    when (st.phase) {
                        MicTest.Phase.RECORDING -> "Recording… speak now"
                        MicTest.Phase.PLAYING -> "Playing back"
                        MicTest.Phase.IDLE -> if (st.result != null) "How you sound" else "Ready"
                    },
                    style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    when (st.phase) {
                        MicTest.Phase.RECORDING -> "$secondsLeft s left"
                        MicTest.Phase.PLAYING -> "Through $name"
                        MicTest.Phase.IDLE -> if (source == MicTest.Source.DEVICE) name else "Phone"
                    },
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = onContainer.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(Space.l))
                // The level while recording or playing; flat when idle.
                LinearWavyProgressIndicator(
                    progress = { if (busy) level.value.coerceAtLeast(0.02f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(16.dp),
                    color = meter,
                    trackColor = onContainer.copy(alpha = 0.15f),
                )
            }
        }
        Spacer(Modifier.height(Space.m))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(MicTest.Source.DEVICE to name, MicTest.Source.PHONE to "Phone").forEachIndexed { i, (s, label) ->
                SegmentedButton(
                    selected = source == s, onClick = { source = s }, enabled = !busy,
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(Space.m))
        Button(
            onClick = {
                when {
                    busy -> test.stop()
                    !granted -> ask.launch(Manifest.permission.RECORD_AUDIO)
                    else -> test.record(source)
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = if (busy) androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = colors.errorContainer, contentColor = colors.onErrorContainer,
            ) else androidx.compose.material3.ButtonDefaults.buttonColors(),
        ) {
            Icon(if (busy) Icons.Outlined.Stop else Icons.Outlined.Mic, null)
            Spacer(Modifier.width(Space.s))
            Text(when {
                busy -> "Stop"
                st.result != null -> "Record again"
                else -> "Record 5 seconds"
            }, style = MaterialTheme.typography.titleMedium)
        }
        st.error?.let {
            Spacer(Modifier.height(Space.m))
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(Space.m))
            }
        }
        st.result?.let { r ->
            SectionHeader("Last recording")
            SegmentedGroup {
                InfoRow(0, 3, Icons.Outlined.Mic, "Recorded from", "${r.from} · ${r.sampleRate / 1000} kHz")
                InfoRow(1, 3, Icons.Outlined.GraphicEq, "Loudest",
                    "%.0f dBFS".format(r.peakDb) + when {
                        r.clipped -> " · too loud, it clipped"
                        r.peakDb > -3 -> " · close to clipping"
                        r.peakDb < -30 -> " · quiet, speak closer"
                        else -> " · good"
                    })
                InfoRow(2, 3, Icons.Outlined.VolumeUp, "Background", "%.0f dBFS".format(r.floorDb) + if (r.floorDb > -40) " · noisy" else " · quiet")
            }
        }
        mic.note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m))
        }
    }
}
