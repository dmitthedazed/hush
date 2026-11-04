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
import com.occaecat.hush.mdr.AmbientMode
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
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.sony.R
import com.occaecat.hush.Phase

fun subtitleFor(state: HeadphonesState) = when (state.phase) {
    Phase.CONNECTED -> listOfNotNull(
        "Connected",
        if (state.trueWireless) listOfNotNull(state.leftBattery?.let { "L ${it.level}%" }, state.rightBattery?.let { "R ${it.level}%" })
            .joinToString(" ").ifEmpty { null }
        else state.battery?.let { "$it%" },
        state.codec?.label?.takeIf { it != "—" },
    ).joinToString(" · ")
    Phase.CONNECTING -> "Connecting…"
    Phase.RECONNECTING -> "Reconnecting…"
    Phase.FAILED -> state.elsewhere?.let { "Controls on $it" } ?: "Not connected"
    Phase.IDLE -> null
}

/** Shown on Sound and Device while there is no control session. */
@Composable
fun NotConnectedPlaceholder(state: HeadphonesState, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        if (state.phase == Phase.FAILED) {
            ModelDisk(state, size = 112.dp, dimmed = true)
        } else {
            ContainedLoadingIndicator(Modifier.size(96.dp))
        }
        Text(
            when {
                state.elsewhere != null -> "Controls are on ${state.elsewhere}"
                state.phase == Phase.FAILED -> "Headphones not connected"
                else -> "Waiting for the headphones"
            },
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            if (state.elsewhere != null) "In multipoint only one device can change these settings. Hush takes over when music plays on this phone, or from the Listen tab."
            else "Settings show up here once the control channel is open.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Sony's own article for what's going on: multipoint when another source holds the controls, otherwise connecting.
        if (state.phase == Phase.FAILED) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            androidx.compose.material3.TextButton(onClick = {
                openHelpArticle(context, state,
                    if (state.elsewhere != null) com.occaecat.hush.features.Help.Article.MULTIPOINT else com.occaecat.hush.features.Help.Article.CONNECTION, scope)
            }) { Text(if (state.elsewhere != null) "How multipoint works" else "Connection troubleshooting") }
        }
        Spacer(Modifier.height(Space.s))
    }
}

/** Top half of a fold lying flat: what the headphones are doing, readable from a distance. */
@Composable
internal fun StatusPane(state: HeadphonesState, vm: HeadphonesController, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(Space.l),
            contentAlignment = Alignment.Center,
        ) {
            val disk = minOf(maxHeight, 200.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                ProductDisk(rememberRender(state), size = disk, dimmed = state.phase != Phase.CONNECTED)
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        state.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.battery?.let { "$it%" } ?: subtitleFor(state) ?: "—",
                        style = MaterialTheme.typography.displaySmallEmphasized,
                    )
                    state.ncAsm?.let { nc ->
                        Text(
                            when (nc.mode) {
                                AmbientMode.NOISE_CANCELLING -> "Noise canceling"
                                AmbientMode.AMBIENT -> "Ambient sound · ${nc.ambientLevel}"
                                AmbientMode.OFF -> "Noise control off"
                            },
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (state.phase == Phase.CONNECTED) {
                        // The one control worth reaching across the table for.
                        FilledTonalButton(onClick = vm::cycleNoiseMode, modifier = Modifier.padding(top = Space.s)) {
                            Text("Switch mode")
                        }
                    }
                }
            }
        }
    }
}
