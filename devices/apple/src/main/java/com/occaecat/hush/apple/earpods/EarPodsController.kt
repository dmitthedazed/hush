package com.occaecat.hush.apple.earpods

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.occaecat.hush.apple.usb.EarPodsCheck
import com.occaecat.hush.autoeq.AutoEq
import com.occaecat.hush.devices.AutoEqTarget
import com.occaecat.hush.devices.Connection
import com.occaecat.hush.devices.Detail
import com.occaecat.hush.devices.DetailGroup
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceState
import com.occaecat.hush.devices.Equalizer
import com.occaecat.hush.devices.HeroChip
import com.occaecat.hush.devices.Preset
import com.occaecat.hush.devices.PresetGroup
import com.occaecat.hush.ui.EqUi
import com.occaecat.hush.ui.ItemWidth
import com.occaecat.hush.ui.SegmentedGroup
import com.occaecat.hush.ui.Space
import com.occaecat.hush.ui.SwitchRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Wired EarPods (USB-C) for the shared UI. They have no control channel: the state is what Android and the USB
 * descriptors say, the equalizer runs on the phone, and their own sections are the authenticity check, the remote
 * and Apple's documentation.
 */
object EarPodsController : DeviceController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val state: StateFlow<DeviceState> = kotlinx.coroutines.flow.combine(EarPods.state, EarPods.eq.settings) { s, _ -> deviceState(s) }
        .stateIn(scope, SharingStarted.Eagerly, deviceState(EarPods.state.value))

    private fun deviceState(s: EarPods.State): DeviceState {
        // Android lists every rate its resampler accepts; the descriptors say what the hardware runs (48 kHz only).
        val native = s.report?.items?.any { it.title == "Audio formats" && it.result == EarPodsCheck.Result.PASS } == true
        val eqLabel = EarPods.eq.label
        return DeviceState(
            name = "EarPods",
            connection = if (s.attached) Connection.CONNECTED else Connection.DISCONNECTED,
            subtitle = listOfNotNull("Connected", "USB-C", eqLabel).joinToString(" · "),
            statusText = if (s.attached) null else "Not plugged in",
            statusLabel = Icons.Outlined.Usb to "Wired",
            blobShape = MaterialShapes.Cookie9Sided,
            cardFacts = listOfNotNull("USB-C", "48 kHz", s.firmware?.let { "v$it" }),
            model = "EarPods (USB-C)",
            heroChips = listOfNotNull(HeroChip("48 kHz"), eqLabel?.let { HeroChip(it) }),
            details = listOf(
                DetailGroup("EarPods", listOfNotNull(
                    Detail("Model", "EarPods (USB-C)", Icons.Outlined.Headphones, copy = true),
                    s.serial?.let { Detail("Serial number", it, Icons.Outlined.Numbers, hidden = true) },
                    s.firmware?.let { Detail("Firmware", it, Icons.Outlined.Memory, copy = true) },
                )),
                DetailGroup("Audio", listOfNotNull(
                    Detail("Playback", if (native) "48 kHz · 16 / 24-bit · stereo" else s.output?.let(::audioLine) ?: "—", Icons.Outlined.Usb),
                    Detail("Microphone", if (native) "48 kHz · 16 / 24-bit · mono" else s.mic?.let(::audioLine) ?: "—", Icons.Outlined.Mic),
                    Detail("Connection", "USB Audio, full speed", Icons.Outlined.Cable),
                )),
            ),
        )
    }

    private fun audioLine(a: EarPods.Audio) = listOfNotNull(
        a.rates.maxOrNull()?.let { "up to ${it / 1000} kHz" },
        a.encodings.filter { it.endsWith("bit") }.takeIf { it.isNotEmpty() }?.joinToString(" / "),
    ).joinToString(" · ")

    /** Apple's product render, fetched once from Apple's CDN; a plain glyph until it's there or offline. */
    @Composable
    override fun Render(modifier: Modifier, connected: Boolean) {
        val context = LocalContext.current
        val bitmap by produceState<android.graphics.Bitmap?>(null) { value = AppleRender.earPods(context) }
        val b = bitmap
        if (b != null) Image(
            b.asImageBitmap(), "EarPods", modifier, contentScale = ContentScale.Fit,
            alpha = if (connected) 1f else 0.5f,
            colorFilter = if (connected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        )
        else Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Headphones, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }

    /** No battery; the phone's volume scale is taken to reach about 100 dB(A) at the top, like most wired buds. */
    val insightsProfile = com.occaecat.hush.features.Insights.Profile(key = "apple:earpods-usb-c", noun = "EarPods")
    override val insights = insightsProfile
    override val microphone = com.occaecat.hush.devices.Microphone(
        com.occaecat.hush.devices.Transport.USB, note = "The inline mic records at 48 kHz over USB, the same as in calls.",
    )
    override val sleepTarget = com.occaecat.hush.features.PhoneSleepTarget(EarPods.appContext, "EarPods")

    override fun renderBitmap(context: Context): android.graphics.Bitmap? = AppleRender.cached(context)

    /** Widgets: the phone-side equalizer as a switch. */
    override val quick get() = EarPods.eq.quick

    override fun soundSections(scope: LazyStaggeredGridScope) = EarPods.eq.soundSections(scope)

    override fun deviceSections(scope: LazyStaggeredGridScope) {
        scope.item { val s by EarPods.state.collectAsState(); Authenticity(s) }
        scope.item { Remote() }
        scope.item { val s by EarPods.state.collectAsState(); AppleDocs(s) }
    }

    override val equalizer get() = EarPods.eq
}
