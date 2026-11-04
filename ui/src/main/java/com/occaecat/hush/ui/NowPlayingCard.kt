@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.occaecat.hush.sound.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Track info a device reports itself, plus its transport controls. */
data class DeviceTrack(
    val title: String?,
    val artist: String?,
    val album: String?,
    val playing: Boolean?,
    val previous: () -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
)

/**
 * The player. Artwork, position and seeking come from the phone's media session (notification access); without it
 * only the headset's PLAYBACK_CONTROLLER text is known and the artwork slot asks for access. All colours are
 * theme roles; the artwork only shows through as a heavily blurred wash behind the tonal surface.
 */
@Composable
fun NowPlayingCard(
    modifier: Modifier,
    /** Where the sound comes from, shown above the title. */
    source: Pair<ImageVector, String> = Icons.Outlined.Smartphone to "This phone",
    /** What the device itself reports, for when the phone's media session isn't readable. */
    fallback: DeviceTrack? = null,
    /** Top-right of the text block (a sleep timer). */
    badge: @Composable () -> Unit = {},
    /** Under the transport buttons (the device's own volume). */
    volume: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val media by NowPlaying.media.collectAsState()
    val access = rememberOnResume { NowPlaying.hasAccess(context) }
    val m = media
    val playing = m?.playing ?: (fallback?.playing == true)
    val title = m?.title ?: fallback?.title
    val subtitle = (m?.artist ?: fallback?.artist) ?: (m?.album ?: fallback?.album)
    val colors = MaterialTheme.colorScheme

    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh) {
        Box {
            // A soft wash of the artwork's colours behind everything.
            Crossfade(m?.art, label = "wash", animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(), modifier = Modifier.matchParentSize()) { a ->
                if (a != null) Box(Modifier.fillMaxSize()) {
                    Image(a.asImageBitmap(), null, Modifier.matchParentSize().blur(48.dp), contentScale = ContentScale.Crop, alpha = 0.55f)
                    Box(Modifier.matchParentSize().background(colors.surfaceContainerHigh.copy(alpha = 0.72f)))
                }
            }
            Column(Modifier.padding(Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(m?.art, m?.appIcon, access) { NowPlaying.askAccess(context) }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        val (srcIcon, srcName) = source
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(srcIcon, null, Modifier.size(14.dp), tint = colors.primary)
                            Spacer(Modifier.width(Space.xs))
                            Text(srcName, style = MaterialTheme.typography.labelMedium, color = colors.primary, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            // Taller than the label line; let it overhang instead of pushing the title down.
                            Box(Modifier.height(16.dp), contentAlignment = Alignment.CenterEnd) {
                                Box(Modifier.wrapContentHeight(unbounded = true)) { badge() }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(title ?: "Nothing playing", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (m != null && m.durationMs > 0) {
                    Spacer(Modifier.height(Space.s))
                    SeekBar(m)
                } else Spacer(Modifier.height(Space.m))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { m?.previous() ?: fallback?.previous?.invoke() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "Previous")
                    }
                    Spacer(Modifier.width(Space.m))
                    // Expressive morph: a soft square while playing, a full pill when paused.
                    val corner by animateDpAsState(if (playing) 18.dp else 28.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "play")
                    FilledIconButton(
                        onClick = { m?.playPause() ?: fallback?.playPause?.invoke() },
                        modifier = Modifier.size(width = 80.dp, height = 56.dp),
                        shape = RoundedCornerShape(corner),
                    ) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(Space.m))
                    FilledTonalIconButton(onClick = { m?.next() ?: fallback?.next?.invoke() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.SkipNext, "Next")
                    }
                }
                if (volume != null) {
                    Spacer(Modifier.height(Space.s))
                    volume()
                }
            }
        }
    }
}

/** Square artwork with the playing app's icon on its corner; without access it is the button that asks for it. */
@Composable
private fun Artwork(art: Bitmap?, appIcon: Bitmap?, access: Boolean, askAccess: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Box {
        Box(
            Modifier.size(72.dp).clip(shape).background(colors.primaryContainer)
                .then(if (!access) Modifier.clickable(onClickLabel = "Show artwork", onClick = askAccess) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(art, label = "art", animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()) { a ->
                if (a != null) Image(a.asImageBitmap(), "Artwork", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(if (access) Icons.Outlined.MusicNote else Icons.Outlined.AddPhotoAlternate,
                        if (access) null else "Show artwork", tint = colors.onPrimaryContainer)
                }
            }
        }
        if (appIcon != null) Image(
            appIcon.asImageBitmap(), null,
            Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(22.dp)
                .border(2.dp, colors.surfaceContainerHigh, CircleShape).clip(CircleShape),
        )
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
)

private fun time(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Position from the media session, ticking while it plays; seeking goes back to the session. */
@Composable
private fun SeekBar(m: NowPlaying.Media) {
    var position by remember { mutableLongStateOf(m.positionNow()) }
    LaunchedEffect(m) {
        while (true) {
            position = m.positionNow()
            if (!m.playing) break
            delay(500)
        }
    }
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: position.toFloat()
    Column {
        Slider(
            value = shown,
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { m.seekTo(it.toLong()); position = it.toLong() }
                drag = null
            },
            enabled = m.canSeek,
            valueRange = 0f..m.durationMs.toFloat(),
            colors = sliderColors(),
            thumb = { ThinThumb() },
            track = { ThinTrack(it) },
            modifier = Modifier.height(28.dp),
        )
        Row {
            val style = MaterialTheme.typography.labelSmall
            Text(time(shown.toLong()), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(time(m.durationMs), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A device's own volume in [steps] steps, slim like the seek bar; [onChange] fires while dragging. */
@Composable
fun VolumeRow(v: Int, steps: Int, onChange: (Int) -> Unit, label: String = "Headphone volume") {
    var drag by remember { mutableFloatStateOf(v.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val shown = if (dragging) drag else v.toFloat()
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Outlined.VolumeDown, label, Modifier.size(20.dp), tint = tint)
        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                if (it.roundToInt() != drag.roundToInt()) onChange(it.roundToInt())
                drag = it
            },
            onValueChangeFinished = { dragging = false; onChange(drag.roundToInt()) },
            valueRange = 0f..(steps - 1).toFloat(),
            colors = sliderColors(),
            thumb = { ThinThumb() },
            track = { ThinTrack(it) },
            modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = Space.s),
        )
        Text("${shown.roundToInt()}", style = MaterialTheme.typography.labelMedium, color = tint,
            modifier = Modifier.width(20.dp), textAlign = TextAlign.End)
    }
}

/** A slim track and thumb: the default expressive slider is too heavy for a card this size. */
@Composable
private fun ThinThumb() {
    Box(Modifier.size(width = 4.dp, height = 18.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
}

@Composable
private fun ThinTrack(st: SliderState) {
    SliderDefaults.Track(
        sliderState = st,
        modifier = Modifier.height(6.dp),
        colors = sliderColors(),
        drawStopIndicator = null,
        thumbTrackGapSize = 4.dp,
        trackInsideCornerSize = 2.dp,
    )
}
