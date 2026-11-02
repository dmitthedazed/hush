@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * The app's navigation shares its animation scopes through these, so pieces in :ui and the device modules can take
 * part in container transforms without knowing about the navigation library. Outside the app's navigation (Wear,
 * previews, a page in its own window) they are null and every modifier here does nothing.
 */

/** The scope shared elements move in: the whole navigation display. */
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit animation of the destination being drawn. */
val LocalPageScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** True inside a destination of the app's navigation: [FullScreenPage] draws in place and back is the navigation's. */
val LocalInNav = compositionLocalOf { false }

/**
 * What the destination being drawn is about (a device's key): shared keys are scoped to it, so two devices' tabs
 * crossing during a switch don't trade rows.
 */
val LocalShareSpace = compositionLocalOf { "" }

/** The [sharedPage] key of the destination being drawn, so its page grows out of the row that opened it. */
val LocalPageKey = compositionLocalOf<String?> { null }

/**
 * Container transform: a row and the page it opens carry the same [key]; opening the page grows it out of the row,
 * and back (predictive back too) shrinks it into the row again.
 */
@Composable
fun Modifier.sharedPage(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val page = LocalPageScope.current ?: return this
    val motion = MaterialTheme.motionScheme
    return with(shared) {
        this@sharedPage.sharedBounds(
            rememberSharedContentState("page:${LocalShareSpace.current}:$key"),
            page,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
            boundsTransform = { _, _ -> motion.defaultSpatialSpec() },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(28.dp)),
        )
    }
}

/** One thing (a product picture) that moves from one destination to the next. */
@Composable
fun Modifier.sharedThing(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val page = LocalPageScope.current ?: return this
    val motion = MaterialTheme.motionScheme
    return with(shared) {
        this@sharedThing.sharedElement(
            rememberSharedContentState("thing:${LocalShareSpace.current}:$key"),
            page,
            boundsTransform = { _, _ -> motion.defaultSpatialSpec() },
        )
    }
}
