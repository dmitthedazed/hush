@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
            ValueRow(0, 1, Icons.Outlined.Mic, "Microphone test", "Record yourself and hear how you sound on calls",
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
    val st by test.state.collectAsState()
    var source by remember { mutableStateOf(MicTest.Source.DEVICE) }
    var permTick by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permTick++
        if (ok) test.record(source)
    }
    val granted = remember(permTick) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val busy = st.phase != MicTest.Phase.IDLE
    val level by animateFloatAsState(st.level, label = "mic level")

    FullScreenPage("Microphone test", onClose) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    c.Render(Modifier.size(64.dp), true)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (st.phase) {
                                MicTest.Phase.RECORDING -> "Recording… speak now"
                                MicTest.Phase.PLAYING -> "Playing back"
                                MicTest.Phase.IDLE -> if (st.result != null) "How you sound" else "Ready"
                            },
                            style = MaterialTheme.typography.titleLargeEmphasized,
                        )
                        Text(
                            when (st.phase) {
                                MicTest.Phase.RECORDING -> "${(5 * (1 - st.progress)).roundToInt().coerceAtLeast(1)} s left"
                                MicTest.Phase.PLAYING -> "Through $name"
                                MicTest.Phase.IDLE -> if (source == MicTest.Source.DEVICE) "From $name's microphone" else "From the phone's microphone"
                            },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(Space.l))
                // The level while recording or playing; flat when idle.
                LinearWavyProgressIndicator(
                    progress = { if (busy) level.coerceAtLeast(0.02f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(16.dp),
                    color = if (st.phase == MicTest.Phase.PLAYING) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
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
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(if (busy) Icons.Outlined.Stop else Icons.Outlined.Mic, null)
            Spacer(Modifier.width(Space.s))
            Text(when {
                busy -> "Stop"
                st.result != null -> "Record again"
                else -> "Record 5 seconds"
            })
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
        Text(
            listOfNotNull(mic.note, "Recordings stay in memory and are gone when you leave this page.").joinToString(" "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.m),
        )
    }
}
