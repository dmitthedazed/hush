@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

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

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    LISTEN("Listen", Icons.Outlined.Headphones, Icons.Filled.Headphones),
    SOUND("Sound", Icons.Outlined.GraphicEq, Icons.Filled.GraphicEq),
    DEVICE("Device", Icons.Outlined.Settings, Icons.Filled.Settings),
}


/**
 * Every device's frame: Listen, Sound and Device tabs on a bottom bar (a rail on wide windows), the same motion and
 * predictive back. Device modules fill the tabs from the shared pieces (ProductHero, EqualizerHero, NowPlayingCard,
 * rows), so all brands look and move the same.
 */
@Composable
fun DeviceShell(
    listen: @Composable () -> Unit,
    sound: @Composable () -> Unit,
    device: @Composable () -> Unit,
    /** Changing this to a non-null value jumps to the Device tab (deep links from notifications). */
    openDevice: Any? = null,
    /** Shown above the hinge of a fold lying flat; without it the tabs just use the whole window. */
    statusPane: (@Composable (Modifier) -> Unit)? = null,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.LISTEN) }
    LaunchedEffect(openDevice) { if (openDevice != null) tab = Tab.DEVICE }
    val layout = LocalLayout.current

    val content: @Composable (Modifier) -> Unit = { modifier ->
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            // Back from Sound or Device peeks at Listen: the page shrinks with the gesture, then Listen springs in.
            modifier = modifier.predictiveBack(enabled = tab != Tab.LISTEN, exits = false) { tab = Tab.LISTEN },
            label = "tab",
        ) { t ->
            when (t) {
                Tab.LISTEN -> listen()
                Tab.SOUND -> sound()
                Tab.DEVICE -> device()
            }
        }
    }

    val top = layout.tabletopTop?.takeIf { statusPane != null }
    when {
        top != null -> Column(Modifier.fillMaxSize()) {
            // Lying flat: a glanceable status above the hinge, the controls on the lower half.
            statusPane!!(Modifier.fillMaxWidth().height(top))
            BottomBarLayout(tab, { tab = it }, content, Modifier.weight(1f))
        }
        layout.rail -> Row(Modifier.fillMaxSize()) {
            NavigationRail(
                header = {
                    if (!layout.compactHeight) {
                        ShapeBadge(Icons.Filled.Headphones, size = 56.dp, polygon = MaterialShapes.Cookie7Sided,
                            modifier = Modifier.padding(vertical = Space.m))
                    }
                },
                modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)),
            ) {
                if (layout.compactHeight) Spacer(Modifier.weight(1f))
                Tab.entries.forEach { t ->
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null, Modifier.hopOnSelect(tab == t)) },
                        label = { Text(t.label) },
                    )
                }
                if (layout.compactHeight) Spacer(Modifier.weight(1f))
            }
            // The bottom inset goes into the list's content padding so cards scroll behind the gesture bar.
            content(
                Modifier.weight(1f)
                    // The rail already keeps clear of a start-side cutout; don't let the app bar pad for it again.
                    .consumeWindowInsets(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.End)
                    )
            )
        }
        else -> BottomBarLayout(tab, { tab = it }, content, Modifier.fillMaxSize())
    }
}

@Composable
private fun BottomBarLayout(tab: Tab, onTab: (Tab) -> Unit, content: @Composable (Modifier) -> Unit, modifier: Modifier) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // 80dp bar on surfaceContainer; it pads itself for the gesture area so the colour runs to the edge.
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTab(t) },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null, Modifier.hopOnSelect(tab == t)) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        content(
            Modifier.padding(bottom = pad.calculateBottomPadding()).consumeWindowInsets(pad)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        )
    }
}
