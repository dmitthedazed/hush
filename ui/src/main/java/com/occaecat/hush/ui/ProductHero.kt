@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.roundToInt

/** A small battery glyph (the fill tracks the level and springs on change) with the percentage beside it. */
@Composable
fun BatteryGauge(level: Int?, charging: Boolean) {
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
 * bottom), the bare product render inside the battery ring, and the name, level and chips under it. It stays
 * pinned; as the page scrolls it folds into a compact bar (small render on the left, name and level beside it,
 * chips at the end) so the cards get the room back.
 */
@Composable
fun ProductHero(
    name: String,
    collapse: HeroCollapse,
    connected: Boolean,
    /** The product render, filling the space it's given. */
    image: @Composable (Modifier) -> Unit,
    /** Under the name: a battery gauge, or a status line ("Wired", "Not connected"). */
    status: @Composable () -> Unit,
    chips: (@Composable RowScope.() -> Unit)? = null,
    /** A spinner over the render (connecting). */
    busy: Boolean = false,
    blobShape: RoundedPolygon = MaterialShapes.Cookie12Sided,
    blobColor: Color = if (connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    onTap: (() -> Unit)? = null,
    tapLabel: String? = null,
    /** A large text status (not a gauge) shrinks as the hero folds. */
    shrinkStatus: Boolean = !connected,
) {
    val layout = LocalLayout.current
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    // About a third of a phone screen for the render; landscape and short windows keep it smaller so the controls stay in view.
    val art = (windowHeight * if (layout.compactHeight) 0.38f else 0.28f).coerceIn(150.dp, 300.dp)
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))),
    ) {
        Layout(
            content = {
                // Tapping the headphones re-reads the battery (the app bar and its refresh button are gone).
                // Behind the render a blob takes the shape of the noise mode (sealed cookie, open flower, plain
                // circle) and jiggles into each new one; tapping squishes the whole thing and re-reads the battery.
                val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val blob by androidx.compose.animation.animateColorAsState(
                    blobColor, MaterialTheme.motionScheme.slowEffectsSpec(), label = "blob",
                )
                Box(
                    Modifier.layoutId(HeroPart.Art).height(art).aspectRatio(0.8f)
                        .squishy(press, 0.9f)
                        .clickable(
                            interactionSource = press, indication = null,
                            enabled = onTap != null, onClickLabel = tapLabel, onClick = { onTap?.invoke() },
                        )
                        .morphingBlob(blobShape, blob, spinMillis = if (connected) 40_000 else 90_000),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) LoadingIndicator(Modifier.align(Alignment.TopCenter).size(40.dp))
                    image(Modifier.fillMaxSize(0.94f))
                }
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.layoutId(HeroPart.Name),
                )
                Box(Modifier.layoutId(HeroPart.Status)) { status() }
                if (chips != null) Row(
                    Modifier.layoutId(HeroPart.Chips),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                    content = chips,
                )
            },
        ) { measurables, c ->
            val width = c.maxWidth
            val side = Space.l.roundToPx()
            val m = Space.m.roundToPx()
            val s = Space.s.roundToPx()
            val loose = Constraints(maxWidth = (width - side * 2).coerceAtLeast(0))
            fun part(p: HeroPart) = measurables.firstOrNull { it.layoutId == p }?.measure(loose)
            val artP = part(HeroPart.Art)!!
            val nameP = part(HeroPart.Name)!!
            val statusP = part(HeroPart.Status)
            val chipsP = part(HeroPart.Chips)
            val statusH = statusP?.height ?: 0
            val chipsH = chipsP?.height ?: 0

            // Full: everything in one centred column under the render.
            val fullArtY = s
            val fullNameY = fullArtY + artP.height + m
            val fullStatusY = fullNameY + nameP.height + s
            val fullChipsY = fullStatusY + statusH + s
            val fullH = (if (chipsP != null) fullChipsY + chipsH else fullStatusY + statusH) + Space.l.roundToPx()

            // Compact: a small render, the name and level stacked beside it, the chips at the end of the row
            // (or under the level when the row has no room for them).
            val artScale = CompactArt.toPx() / artP.height
            val smallStatus = if (shrinkStatus) 0.7f else 1f
            val textX = side + (artP.width * artScale).roundToInt() + m
            val textW = maxOf(nameP.width, ((statusP?.width ?: 0) * smallStatus).roundToInt())
            val chipsInline = chipsP == null || textX + textW + m + chipsP.width <= width - side
            val textH = nameP.height + (statusH * smallStatus).roundToInt() +
                if (chipsP != null && !chipsInline) s + chipsH else 0
            val rowH = maxOf(CompactArt.roundToPx(), textH)
            val compactH = s + rowH + m
            val rowTop = s
            val textTop = rowTop + (rowH - textH) / 2
            val compactNameY = textTop
            val compactStatusY = compactNameY + nameP.height
            val compactChipsY = if (chipsInline) rowTop + (rowH - chipsH) / 2
                else compactStatusY + (statusH * smallStatus).roundToInt() + s

            val range = (fullH - compactH).coerceAtLeast(0).toFloat()
            collapse.range = range
            if (collapse.offset > range) collapse.offset = range
            val f = collapse.fraction(range)
            fun mix(a: Int, b: Int) = lerp(a, b, f)
            fun centred(w: Int) = (width - w) / 2

            layout(width, mix(fullH, compactH)) {
                val sArt = 1f + (artScale - 1f) * f
                artP.placeWithLayer(
                    mix(centred(artP.width), side),
                    mix(fullArtY, rowTop + (rowH - CompactArt.roundToPx()) / 2),
                ) { scaleX = sArt; scaleY = sArt; transformOrigin = TransformOrigin(0f, 0f) }
                nameP.place(mix(centred(nameP.width), textX), mix(fullNameY, compactNameY))
                statusP?.let {
                    val sStatus = 1f + (smallStatus - 1f) * f
                    it.placeWithLayer(mix(centred(it.width), textX), mix(fullStatusY, compactStatusY)) {
                        scaleX = sStatus; scaleY = sStatus; transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
                chipsP?.place(
                    mix(centred(chipsP.width), if (chipsInline) width - side - chipsP.width else textX),
                    mix(fullChipsY, compactChipsY),
                )
            }
        }
        // The app bar's extras (the device switcher) in the corner while the hero is open; the folded bar has no room.
        val open = 1f - collapse.fraction() * 2f
        if (open > 0f) Row(
            Modifier.align(Alignment.TopEnd).padding(end = Space.s)
                .graphicsLayer { alpha = open.coerceIn(0f, 1f) },
            content = LocalAppBarExtras.current,
        )
    }
}

private enum class HeroPart { Art, Name, Status, Chips }

/** Height of the render in the folded hero. */
private val CompactArt = 56.dp

@Composable
fun LogoPill(@DrawableRes logo: Int, label: String, dimmed: Boolean = false, /** Marks cropped without margins. */ tight: Boolean = false) {
    val markHeight = if (tight) 14.dp else 16.dp
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
fun Pill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

