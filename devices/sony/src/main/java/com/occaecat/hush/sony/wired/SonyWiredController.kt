package com.occaecat.hush.sony.wired

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import com.occaecat.hush.devices.Connection
import com.occaecat.hush.devices.Detail
import com.occaecat.hush.devices.DetailGroup
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceState
import com.occaecat.hush.devices.HeroChip
import com.occaecat.hush.devices.Microphone
import com.occaecat.hush.devices.Transport
import com.occaecat.hush.features.Insights
import com.occaecat.hush.features.PhoneSleepTarget
import com.occaecat.hush.wired.PhoneEqualizer
import com.occaecat.hush.wired.RemoteRender
import com.occaecat.hush.wired.WiredAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One of Sony's passive wired headphones in the shared UI. Nothing to talk to: it's "connected" while it's the model
 * taken to be on the wired output ([SonyWired.current]), the equalizer runs on the phone, the volume is the phone's.
 */
class SonyWiredController(
    context: Context,
    private val model: SonyWired.WiredModel,
    current: StateFlow<String?>,
) : DeviceController {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val plugged: StateFlow<Boolean> = current.map { it == model.id }.distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, current.value == model.id)

    override val equalizer = PhoneEqualizer(
        app, "sony:${model.id}", model.name, "wired_eq_${model.id}", plugged, "Sony ${model.name}",
    )

    override val state: StateFlow<DeviceState> = combine(plugged, WiredAudio.free, equalizer.settings) { on, out, _ ->
        deviceState(on, out.takeIf { on })
    }.stateIn(scope, SharingStarted.Eagerly, deviceState(plugged.value, WiredAudio.free.value.takeIf { plugged.value }))

    private fun deviceState(on: Boolean, out: WiredAudio.Output?): DeviceState {
        val eqLabel = equalizer.label
        val route = out?.kind
        return DeviceState(
            name = model.name,
            connection = if (on) Connection.CONNECTED else Connection.DISCONNECTED,
            subtitle = listOfNotNull(if (on) "Connected" else "Not plugged in", route, eqLabel).joinToString(" · "),
            statusText = if (on) null else "Not plugged in",
            statusLabel = Icons.Outlined.Cable to "Wired",
            blobShape = if (model.kind == SonyWired.Kind.OVER_EAR) MaterialShapes.Cookie9Sided else MaterialShapes.Pill,
            cardFacts = listOfNotNull(model.kind.label, route),
            model = model.name,
            heroChips = listOfNotNull(route?.let { HeroChip(it) }, eqLabel?.let { HeroChip(it) }),
            disconnectedHelp = "Plug them into the headphone jack or a USB-C adapter. If several wired models are " +
                "added, open this one in the device switcher so Hush knows it's the one on the cable.",
            details = listOf(
                DetailGroup("Headphones", listOf(
                    Detail("Model", model.name, Icons.Outlined.Headphones, copy = true),
                    Detail("Type", "${model.kind.label}, wired (passive)", Icons.Outlined.Cable),
                )),
                DetailGroup("Output", listOfNotNull(
                    Detail("Connected through", out?.let { o -> listOf(o.kind, o.product).filter { it.isNotBlank() }.distinct().joinToString(" · ") } ?: "—",
                        Icons.Outlined.SettingsInputComponent),
                    out?.rates?.maxOrNull()?.let { Detail("Playback", "up to ${it / 1000} kHz", Icons.Outlined.SettingsInputComponent) },
                    out?.takeIf { it.mic }?.let { Detail("Microphone", "Inline mic on the cable", Icons.Outlined.Mic) },
                )),
            ),
        )
    }

    @Composable
    override fun Render(modifier: Modifier, connected: Boolean) {
        val context = LocalContext.current
        val bitmap by produceState(RemoteRender.cached(context, model.id, trim = true)) {
            if (value == null) value = RemoteRender.load(context, model.id, model.renderUrl, trim = true)
        }
        val b = bitmap
        if (b != null) Image(
            b.asImageBitmap(), model.name, modifier, contentScale = ContentScale.Fit,
            alpha = if (connected) 1f else 0.5f,
            colorFilter = if (connected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        )
        else Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Headphones, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }

    override fun renderBitmap(context: Context) = RemoteRender.cached(context, model.id, trim = true)

    private val insightsProfile = Insights.Profile(key = "sony:${model.id}", noun = model.name)
    override val insights = insightsProfile

    init {
        Insights.track(app, insightsProfile, WiredAudio.volumeSamples(app, plugged))
    }

    /** Only when the cable or adapter has a mic line (a headset plug, a USB headset). */
    override val microphone: Microphone?
        get() = if (WiredAudio.free.value?.mic == true && plugged.value)
            Microphone(Transport.WIRED, note = "Records from the mic on the cable, through the phone's wired input.")
        else null

    override val sleepTarget = PhoneSleepTarget(app, model.name)
    override val quick get() = equalizer.quick

    override fun soundSections(scope: LazyStaggeredGridScope) = equalizer.soundSections(scope)
}
