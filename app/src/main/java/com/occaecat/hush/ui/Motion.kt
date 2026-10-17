@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.launch

/*
 * Hush's motion vocabulary on top of MotionScheme.expressive(): things squish when pressed and spring back past
 * their size, selected icons hop, and every back gesture is predictive. Every effect reads from the
 * same two springs so the app moves like one object.
 */

/** Under-damped: visibly overshoots and settles (presses, hops, morphs). */
private fun <T> wobbly(stiffness: Float = Spring.StiffnessMediumLow) = spring<T>(dampingRatio = 0.45f, stiffness = stiffness)

/** Shrinks while [interaction] is pressed and wobbles back on release. */
fun Modifier.squishy(interaction: InteractionSource, pressedScale: Float = 0.94f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        if (pressed) spring(stiffness = Spring.StiffnessHigh) else wobbly(Spring.StiffnessMedium),
        label = "squish",
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

/** A hop each time [selected] turns on: squashed down, then springs up past full size. */
fun Modifier.hopOnSelect(selected: Boolean): Modifier = composed {
    val s = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (first) { first = false; return@LaunchedEffect }
        if (selected) {
            s.snapTo(0.7f)
            s.animateTo(1f, wobbly(Spring.StiffnessMedium))
        }
    }
    graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/**
 * A big MaterialShapes blob that morphs from shape to shape with an overshooting spring, turns slowly and
 * breathes. [shape] picks the target; the morph starts from wherever the last one was.
 */
fun Modifier.morphingBlob(shape: RoundedPolygon, color: Color, spinMillis: Int = 36_000, breathe: Boolean = true): Modifier =
    composed {
        var from by remember { mutableStateOf(shape) }
        var to by remember { mutableStateOf(shape) }
        val progress = remember { Animatable(1f) }
        val kick = remember { Animatable(1f) }
        LaunchedEffect(shape) {
            if (shape === to) return@LaunchedEffect
            from = to
            to = shape
            launch { kick.snapTo(0.86f); kick.animateTo(1f, wobbly(Spring.StiffnessLow)) }
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
        }
        val morph = remember(from, to) { Morph(from, to) }
        val loop = rememberInfiniteTransition(label = "blob")
        val angle by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(spinMillis, easing = LinearEasing)), label = "spin")
        val breath by loop.animateFloat(
            0.96f, if (breathe) 1.02f else 0.96f,
            infiniteRepeatable(tween(2_600, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
        val androidPath = remember { android.graphics.Path() }
        drawBehind {
            val side = minOf(size.width, size.height)
            androidPath.rewind()
            // Morph tolerates progress past 1 (it extrapolates the cubics), which is what makes it jiggle.
            morph.toPath(progress.value, androidPath)
            val path = androidPath.asComposePath()
            val c = androidx.compose.ui.geometry.Offset(side / 2f, side / 2f)
            translate((size.width - side) / 2f, (size.height - side) / 2f) {
                rotate(angle, pivot = c) {
                    scale(1.14f * breath * kick.value, pivot = c) {
                        // Polygons are normalised to 0..1.
                        scale(side, side, pivot = androidx.compose.ui.geometry.Offset.Zero) { drawPath(path, color) }
                    }
                }
            }
        }
    }

/** The blob for each noise-control mode: sealed and dense, open petals, a calm circle. */
@Composable
fun blobFor(mode: com.occaecat.hush.mdr.AmbientMode?): RoundedPolygon = when (mode) {
    com.occaecat.hush.mdr.AmbientMode.NOISE_CANCELLING -> MaterialShapes.Cookie12Sided
    com.occaecat.hush.mdr.AmbientMode.AMBIENT -> MaterialShapes.Flower
    com.occaecat.hush.mdr.AmbientMode.OFF -> MaterialShapes.Circle
    null -> MaterialShapes.SoftBurst
}


/**
 * Predictive back with springs: while the user drags, the page shrinks towards the gesture's edge and rounds its
 * corners (M3 predictive-back spec: 90% scale, shift by width/20 − 8dp). Letting go cancels with an overshooting
 * bounce back; committing either flings the page away ([exits]) and then calls [onBack], or calls [onBack] at once
 * and springs the page back (for in-page steps where the content itself changes).
 */
@Composable
fun Modifier.predictiveBack(enabled: Boolean = true, exits: Boolean = true, onBack: () -> Unit): Modifier {
    val progress = remember { Animatable(0f) }
    val gone = remember { Animatable(0f) }
    var fromLeft by remember { mutableStateOf(true) }
    val current by androidx.compose.runtime.rememberUpdatedState(onBack)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.activity.compose.PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { e ->
                fromLeft = e.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT
                progress.snapTo(e.progress)
            }
            if (exits) {
                scope.launch { progress.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                gone.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.01f))
                current()
            } else {
                current()
                progress.animateTo(0f, wobbly(Spring.StiffnessMedium))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Gesture cancelled: bounce back into place.
            scope.launch { progress.animateTo(0f, wobbly(Spring.StiffnessMediumLow)) }
            throw e
        }
    }
    val margin = with(LocalDensity.current) { 8.dp.toPx() }
    val corner = with(LocalDensity.current) { 32.dp.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        val g = gone.value
        val s = 1f - 0.1f * p - 0.08f * g
        scaleX = s; scaleY = s
        val shift = (size.width / 20f - margin).coerceAtLeast(0f) * p + size.width * 0.25f * g
        translationX = if (fromLeft) shift else -shift
        alpha = 1f - g
        val r = corner * (p + g).coerceAtMost(1f)
        if (r > 0f) {
            shape = androidx.compose.foundation.shape.RoundedCornerShape(r)
            clip = true
        }
    }
}
