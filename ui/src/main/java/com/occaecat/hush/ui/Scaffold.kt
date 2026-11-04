@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Buttons the app shell adds to every app bar (the device switcher), after the page's own actions. */
val LocalAppBarExtras = androidx.compose.runtime.compositionLocalOf<@Composable RowScope.() -> Unit> { {} }

/** Content items fill their column; the grid itself caps the column width. */
val ItemWidth = Modifier.fillMaxWidth()

/**
 * A tab page: large flexible app bar that collapses into a small one, over a grid of cards. One column on
 * phones, two on wide windows and on a fold held like a book (split at the hinge).
 */
@Composable
fun TabScaffold(
    title: String,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit = {},
    /** When set, replaces the app bar: drawn edge to edge from the very top of the window, behind the status bar. */
    hero: (@Composable () -> Unit)? = null,
    /** Like [hero], but pinned to the top: scrolling folds it into a compact bar instead of carrying it away. */
    pinnedHero: (@Composable (HeroCollapse) -> Unit)? = null,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    val layout = LocalLayout.current
    // A short window (a phone on its side) can't spare a pinned hero most of its height: it scrolls away instead.
    val unpin = layout.compactHeight && pinnedHero != null
    val hero = hero ?: if (unpin) ({ pinnedHero!!(remember { HeroCollapse() }) }) else null
    val pinnedHero = pinnedHero.takeUnless { unpin }
    val heroless = hero == null && pinnedHero == null
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hinge = layout.bookHinge
    val density = LocalDensity.current
    val gap = if (hinge != null) with(density) { (hinge.last - hinge.first).toDp() } + Space.xl else Space.m
    // On very wide windows (desktop, maximised) the app bar lines up with the centred grid instead of the window edge.
    val maxWidth = if (hinge != null) Dp.Infinity
        else ColumnMaxWidth * layout.columns + gap * (layout.columns - 1) + Space.m * 2
    var consumed by remember { mutableStateOf(WindowInsets(0)) }
    Box(
        Modifier.fillMaxSize().onConsumedWindowInsetsChanged { consumed = it }.background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
    Scaffold(
        // Without an app bar the collapse behaviour has no height limit and would swallow every scroll.
        modifier = Modifier.widthIn(max = maxWidth)
            .then(if (heroless) Modifier.nestedScroll(scroll.nestedScrollConnection) else Modifier),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (!heroless) {
                // No app bar: the hero carries the title.
            } else if (layout.compactHeight) {
                // Short windows can't spare the large bar's height.
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            subtitle?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    },
                    actions = { actions(); LocalAppBarExtras.current(this) },
                    scrollBehavior = scroll,
                    windowInsets = if (layout.tabletopTop != null) WindowInsets(0) else TopAppBarDefaults.windowInsets,
                )
            } else if (layout.shortHeight) {
                MediumFlexibleTopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                    actions = { actions(); LocalAppBarExtras.current(this) },
                    scrollBehavior = scroll,
                )
            } else {
                LargeFlexibleTopAppBar(
                    title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                    actions = { actions(); LocalAppBarExtras.current(this) },
                    scrollBehavior = scroll,
                )
            }
        },
    ) { pad ->
        // Where the grid sits in the window, so book-posture columns can be cut at the hinge.
        var gridLeft by remember { mutableIntStateOf(0) }
        val cells = if (hinge != null) {
            val pad = with(density) { Space.m.roundToPx() }
            HingeCells(hinge.first - gridLeft - pad, hinge.last - gridLeft - pad)
        } else StaggeredGridCells.Fixed(layout.columns)
        val gridState = rememberLazyStaggeredGridState()
        val grid = @Composable {
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = cells,
                contentPadding = PaddingValues(
                    start = Space.m, end = Space.m, top = when {
                        pinnedHero != null -> HeroOverlap + Space.m
                        hero != null -> 0.dp
                        else -> pad.calculateTopPadding() + Space.s
                    },
                    // Only what a navigation bar above us hasn't already taken.
                    bottom = Space.xl + WindowInsets.navigationBars.exclude(consumed).asPaddingValues().calculateBottomPadding(),
                ),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalItemSpacing = Space.m,
                modifier = Modifier.fillMaxSize().onGloballyPositioned { gridLeft = it.positionInWindow().x.toInt() },
            ) {
                if (hero != null) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "hero") {
                        // Cancel the grid's side padding so the hero runs from edge to edge.
                        Box(Modifier.bleed(Space.m)) { hero() }
                    }
                }
                content()
            }
        }
        if (pinnedHero != null) {
            val collapse = remember { HeroCollapse() }
            val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            val connection = remember(collapse, settle) { collapse.connection(settle) }
            val overlap = with(density) { HeroOverlap.roundToPx() }
            Layout(
                content = {
                    grid()
                    Box(Modifier.zIndex(1f)) { pinnedHero(collapse) }
                },
                modifier = Modifier.fillMaxSize().nestedScroll(connection),
            ) { (gridM, heroM), c ->
                val heroP = heroM.measure(c.copy(minHeight = 0))
                // The cards start under the hero's rounded bottom so they show through its corners.
                val top = (heroP.height - overlap).coerceIn(0, c.maxHeight)
                val gridP = gridM.measure(c.copy(minHeight = 0, maxHeight = c.maxHeight - top))
                layout(c.maxWidth, c.maxHeight) {
                    gridP.place(0, top)
                    heroP.place(0, 0)
                }
            }
        } else {
            grid()
        }
        if (hero != null) {
            // Once the hero scrolls away, keep the status bar readable over the cards passing under it.
            val scrolled by remember { derivedStateOf { gridState.canScrollBackward } }
            val scrim by animateFloatAsState(if (scrolled) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "scrim")
            Box(
                Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                    .graphicsLayer { alpha = scrim }.background(MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
    }
}

/** How far a pinned hero sits under the grid's first row: its bottom corner radius. */
private val HeroOverlap = 28.dp

/**
 * How far a pinned hero has folded towards its compact bar. The hero reports [range] (the pixels between its
 * full and compact heights) as it measures; scrolling moves [offset] within it before the grid itself scrolls.
 */
@Stable
class HeroCollapse {
    var range by mutableFloatStateOf(0f)
    var offset by mutableFloatStateOf(0f)

    /** 0 = full, 1 = compact. */
    fun fraction(range: Float = this.range): Float = if (range > 0f) (offset / range).coerceIn(0f, 1f) else 0f

    private fun consume(dy: Float): Float {
        val old = offset
        offset = (old - dy).coerceIn(0f, range)
        return old - offset
    }

    internal fun connection(settle: androidx.compose.animation.core.FiniteAnimationSpec<Float>) =
        object : NestedScrollConnection {
            // Scrolling up folds the hero first; scrolling down unfolds it only once the grid is back at its top.
            override fun onPreScroll(available: Offset, source: NestedScrollSource) =
                if (available.y < 0f) Offset(0f, consume(available.y)) else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
                if (available.y > 0f) Offset(0f, consume(available.y)) else Offset.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Never leave it half folded: settle on the nearer end.
                val f = fraction()
                if (f > 0f && f < 1f) animate(offset, if (f < 0.5f) 0f else range, animationSpec = settle) { v, _ -> offset = v }
                return Velocity.Zero
            }
        }
}

/** An item that spans every column (banners, placeholders). */
fun LazyStaggeredGridScope.wideItem(content: @Composable () -> Unit) =
    item(span = StaggeredGridItemSpan.FullLine) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = ColumnMaxWidth)) { content() }
        }
    }
