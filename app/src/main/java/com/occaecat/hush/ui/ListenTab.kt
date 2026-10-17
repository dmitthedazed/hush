@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material3.FilledIconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.annotation.DrawableRes
import com.occaecat.hush.mdr.Codec
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.R
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import kotlin.math.roundToInt

@Composable
fun ListenTab(state: HeadphonesState, vm: HeadphonesController) {
    val layout = LocalLayout.current
    // On a fold lying flat the status pane above the hinge already plays the hero's part.
    val showHero = layout.tabletopTop == null
    TabScaffold(
        title = state.displayName,
        subtitle = subtitleFor(state),
        actions = {
            if (state.phase == Phase.CONNECTED) {
                IconButton(onClick = { vm.refreshBattery() }) { Icon(Icons.Outlined.Refresh, "Refresh battery") }
            }
        },
        hero = if (showHero) ({ ListenHero(state, vm::refreshBattery) }) else null,
    ) {
        when (state.phase) {
            Phase.CONNECTING -> if (!showHero) wideItem { ConnectingHero(state, ItemWidth) }
            Phase.FAILED -> state.elsewhere?.let { other -> wideItem { ElsewhereCard(other, vm::takeControl, ItemWidth) } } ?: wideItem {
                FailedHero(
                    state, ItemWidth,
                    onRetry = { state.address?.let(vm::connect) },
                    onChoose = vm::disconnect,
                    showImage = !showHero,
                )
            }
            else -> {}
        }
        if (state.phase == Phase.RECONNECTING || state.audioSettling) wideItem { ReconnectingBanner(state, ItemWidth) }
        if (state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING) {
            // On a fold lying flat the status pane above the hinge already shows the battery.
            state.ncAsm?.let { nc ->
                item { NoiseControlCard(nc.mode, nc.ambientLevel, nc.voiceFocus, vm, ItemWidth) }
                item { ScenesRow(state, ItemWidth) }
            }
            if (state.dseeAuto != null || state.speakToChat != null) {
                item {
                    Column(ItemWidth) {
                        SegmentedGroup {
                            val rows = listOfNotNull(state.dseeAuto?.let { "dsee" }, state.speakToChat?.let { "stc" })
                            rows.forEachIndexed { i, r ->
                                when (r) {
                                    "dsee" -> SwitchRow(
                                        i, rows.size, Icons.Outlined.AutoAwesome, "DSEE Extreme",
                                        if (state.dseeActive) "Upscaling now" else "Restores detail in compressed audio",
                                        state.dseeAuto == true, onCheckedChange = vm::setDsee,
                                    )
                                    "stc" -> SwitchRow(
                                        i, rows.size, Icons.Outlined.ChatBubbleOutline, "Speak-to-Chat",
                                        "Pause and let sound in when you talk",
                                        state.speakToChat == true, onCheckedChange = vm::setSpeakToChat,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (state.volume != null || state.track != null) item { NowPlayingCard(state, vm, ItemWidth) }
        }
    }
}

/** A small battery glyph (the fill tracks the level and springs on change) with the percentage beside it. */
@Composable
private fun BatteryGauge(level: Int?, charging: Boolean) {
    val colors = MaterialTheme.colorScheme
    val fraction by animateFloatAsState(
        ((level ?: 0) / 100f).coerceIn(0f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "battery",
    )
    val low = level != null && level <= 20 && !charging
    val fill = if (low) colors.error else colors.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 30.dp, height = 16.dp)
                .border(1.5.dp, colors.outline, RoundedCornerShape(5.dp)).padding(2.5.dp),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(2.5.dp)).background(fill))
        }
        Box(
            Modifier.padding(start = 1.5.dp).size(width = 2.5.dp, height = 7.dp)
                .clip(RoundedCornerShape(topEnd = 1.5.dp, bottomEnd = 1.5.dp)).background(colors.outline),
        )
        Spacer(Modifier.width(Space.s))
        if (charging) Icon(Icons.Filled.Bolt, null, Modifier.size(18.dp), tint = colors.primary)
        Text(level?.let { "$it%" } ?: "—", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
    }
}

/**
 * The hero: the top of the screen as one surfaceContainerHigh block (status bar included, rounded only at the
 * bottom), the bare product render inside the battery ring, and the name, level and chips under it.
 */
@Composable
private fun ListenHero(state: HeadphonesState, onRefresh: () -> Unit) {
    val level = state.battery
    val connected = state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING
    val layout = LocalLayout.current
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    // About a third of a phone screen for the render; landscape and short windows keep it smaller so the controls stay in view.
    val art = (windowHeight * if (layout.compactHeight) 0.38f else 0.28f).coerceIn(150.dp, 300.dp)
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top)))
            .padding(top = Space.s, bottom = Space.l),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Tapping the headphones re-reads the battery (the app bar and its refresh button are gone).
            Box(
                Modifier.height(art).aspectRatio(0.8f).clip(MaterialTheme.shapes.extraLarge)
                    .clickable(enabled = state.phase == Phase.CONNECTED, onClickLabel = "Refresh battery", onClick = onRefresh),
                contentAlignment = Alignment.Center,
            ) {
                if (state.phase == Phase.CONNECTING) LoadingIndicator(Modifier.align(Alignment.TopCenter).size(40.dp))
                Image(
                    painterResource(renderFor(state.modelColor)), contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.94f),
                    contentScale = ContentScale.Fit,
                    alpha = if (connected) 1f else 0.5f,
                    colorFilter = if (connected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                )
            }
            Spacer(Modifier.height(Space.m))
            Text(
                state.displayName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (connected) {
                Spacer(Modifier.height(Space.s))
                BatteryGauge(level, state.charging)
                val codec = state.codec?.takeIf { it != Codec.UNSETTLED }
                val dsee = state.dseeAuto == true
                if (codec != null || dsee) {
                    Spacer(Modifier.height(Space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        codec?.let { c -> codecLogo(c)?.let { LogoPill(it, c.label) } ?: Pill(c.label) }
                        // Shown while DSEE Extreme is switched on; it brightens while it is actually upscaling.
                        if (dsee) LogoPill(R.drawable.logo_dsee_extreme, "DSEE Extreme", dimmed = !state.dseeActive)
                    }
                }
            } else {
                Text(
                    subtitleFor(state) ?: "Not connected",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            }
        }
    }
}

/**
 * Codec marks recut as alpha masks so they take the theme colour: Sound Connect's a_mdr_codec_*, except LDAC,
 * which is the full-size mark the user supplied.
 */
@DrawableRes
private fun codecLogo(codec: Codec): Int? = when (codec) {
    Codec.SBC -> R.drawable.logo_codec_sbc
    Codec.AAC -> R.drawable.logo_codec_aac
    Codec.LDAC -> R.drawable.logo_codec_ldac
    Codec.APTX -> R.drawable.logo_codec_aptx
    Codec.APTX_HD -> R.drawable.logo_codec_aptxhd
    else -> null
}

@Composable
private fun LogoPill(@DrawableRes logo: Int, label: String, dimmed: Boolean = false) {
    // Sound Connect's marks carry their own margins; the LDAC mark (user-supplied, nodpi) is cropped tight.
    val markHeight = if (logo == R.drawable.logo_codec_ldac) 14.dp else 16.dp
    val painter = painterResource(logo)
    val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
    Image(
        painter, contentDescription = label,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
        alpha = if (dimmed) 0.55f else 1f,
        contentScale = ContentScale.Fit,
        modifier = Modifier.height(markHeight).aspectRatio(ratio),
    )
}

@Composable
private fun Pill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun ConnectingHero(state: HeadphonesState, modifier: Modifier) {
    Column(
        modifier.padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ProductDisk(R.drawable.xm4_front, size = 144.dp)
        LoadingIndicator(Modifier.size(48.dp))
        Text("Connecting", style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(
            state.deviceName ?: state.address.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailedHero(
    state: HeadphonesState, modifier: Modifier, onRetry: () -> Unit, onChoose: () -> Unit, showImage: Boolean = true,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showImage) {
                ProductDisk(R.drawable.xm4_front, size = 112.dp, dimmed = true)
                Spacer(Modifier.height(Space.m))
            }
            Text("Not connected", style = MaterialTheme.typography.headlineSmallEmphasized)
            Spacer(Modifier.height(Space.s))
            Text(
                "Make sure ${state.deviceName ?: "the headphones"} are on and connected in Bluetooth settings. " +
                    "Only one app can hold the control channel, so close Sound Connect.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Button(onClick = onRetry) { Text("Retry") }
                OutlinedButton(onClick = onChoose) { Text("Choose another") }
            }
        }
    }
}

private data class ModeOption(val mode: AmbientMode, val label: String, @DrawableRes val icon: Int)

// Sound Connect's shortcut glyphs: a person inside a solid ring (NC), a dotted ring (ambient), a bare ring (off).
private val modeOptions = listOf(
    ModeOption(AmbientMode.NOISE_CANCELLING, "ANC", R.drawable.ic_mode_nc),
    ModeOption(AmbientMode.AMBIENT, "Ambient", R.drawable.ic_mode_ambient),
    ModeOption(AmbientMode.OFF, "Off", R.drawable.ic_mode_off),
)

/**
 * Noise control: a connected button group (72dp, icon over label) with the ambient options in a surfaceContainerLow
 * block that springs open under it when Ambient is picked.
 */
@Composable
internal fun NoiseControlCard(mode: AmbientMode, level: Int, voiceFocus: Boolean, vm: HeadphonesController, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            modeOptions.forEachIndexed { i, o ->
                ToggleButton(
                    checked = mode == o.mode,
                    onCheckedChange = { vm.setAmbientMode(o.mode) },
                    modifier = Modifier.weight(1f).height(72.dp).semantics { role = Role.RadioButton },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    contentPadding = PaddingValues(horizontal = Space.s),
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modeOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(o.icon), null, Modifier.size(28.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            o.label, maxLines = 1, softWrap = false,
                            style = MaterialTheme.typography.labelLarge,
                            // Narrow columns (small phones, landscape halves) shrink the label rather than clip it.
                            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = mode == AmbientMode.AMBIENT,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(
                Modifier.padding(top = Space.s).fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(Space.m),
            ) {
                var drag by remember { mutableFloatStateOf(level.toFloat()) }
                var dragging by remember { mutableStateOf(false) }
                val shown = if (dragging) drag else level.toFloat()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ambient level", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${shown.roundToInt()}",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = shown,
                    onValueChange = {
                        dragging = true
                        drag = it
                        vm.setAmbientLevel(it.roundToInt(), dragging = true)
                    },
                    onValueChangeFinished = {
                        dragging = false
                        vm.setAmbientLevel(drag.roundToInt(), dragging = false)
                    },
                    valueRange = 1f..20f,
                    steps = 18,
                    colors = quietSliderColors(),
                )
                Spacer(Modifier.height(Space.s))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LeadIcon(Icons.Outlined.RecordVoiceOver)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text("Focus on voice", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Let voices through, keep the rest quieter",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = voiceFocus, onCheckedChange = vm::setVoiceFocus)
                }
            }
        }
    }
}

/**
 * What the source is playing and the headset's own volume, from the PLAYBACK_CONTROLLER function (Sound
 * Connect's mini player): track, artist and album as the headset reports them, previous / play-pause / next.
 */
/** Another multipoint source holds the control channel; Hush stepped back instead of fighting over it. */
@Composable
private fun ElsewhereCard(other: String, onTake: () -> Unit, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeBadge(Icons.Outlined.Devices, size = 64.dp)
            Spacer(Modifier.height(Space.m))
            Text("Controls are on $other", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.s))
            Text(
                "In multipoint the headphones let one device at a time change their settings. Music keeps playing; " +
                    "Hush takes the controls back when something plays on this phone.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(Space.m))
            Button(onClick = onTake) { Text("Take control") }
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val st = com.occaecat.hush.HeadphonesController.get(context).state.value
            androidx.compose.material3.TextButton(onClick = { openHelpArticle(context, st, com.occaecat.hush.features.Help.Article.MULTIPOINT, scope) }) {
                Text("How multipoint works")
            }
        }
    }
}
