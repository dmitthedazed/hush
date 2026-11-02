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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
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
import com.occaecat.hush.sony.R
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp

/** Sony's cards on the shared Listen page: connection trouble and scenes. */
internal fun LazyStaggeredGridScope.sonyListen(state: HeadphonesState, vm: HeadphonesController) {
    when (state.phase) {
        Phase.FAILED, Phase.IDLE -> state.elsewhere?.takeIf { state.phase == Phase.FAILED }
            ?.let { other -> wideItem { ElsewhereCard(other, vm::takeControl, ItemWidth) } } ?: wideItem {
            FailedHero(
                state, ItemWidth,
                onRetry = { state.address?.let(vm::connect) },
                showImage = false,
            )
        }
        else -> {}
    }
    // Noise control and the DSEE / Speak-to-Chat switches are shared (SonyNoise, sonySettings); scenes stay Sony's.
    if ((state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING) && state.ncAsm != null) {
    }
}

/**
 * Codec marks recut as alpha masks so they take the theme colour: Sound Connect's a_mdr_codec_*, except LDAC,
 * which is the full-size mark the user supplied.
 */
@DrawableRes
internal fun codecLogo(codec: Codec): Int? = when (codec) {
    Codec.SBC -> R.drawable.logo_codec_sbc
    Codec.AAC -> R.drawable.logo_codec_aac
    Codec.LDAC -> R.drawable.logo_codec_ldac
    Codec.APTX -> R.drawable.logo_codec_aptx
    Codec.APTX_HD -> R.drawable.logo_codec_aptxhd
    else -> null
}

@Composable
private fun ConnectingHero(state: HeadphonesState, modifier: Modifier) {
    Column(
        modifier.padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        ModelDisk(state, size = 144.dp)
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
    state: HeadphonesState, modifier: Modifier, onRetry: () -> Unit, showImage: Boolean = true,
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
                ModelDisk(state, size = 112.dp, dimmed = true)
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
            Button(onClick = onRetry) { Text(if (state.phase == Phase.IDLE) "Connect" else "Retry") }
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
