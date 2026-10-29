package com.occaecat.hush.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery2Bar
import androidx.compose.material.icons.outlined.Battery4Bar
import androidx.compose.material.icons.outlined.Battery6Bar
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.tv.R

/** Width of the icon-only drawer; in modal mode the content sits under it, so it is padded by this much. */
private val CollapsedDrawer = 80.dp

enum class Section(val label: String, val icon: ImageVector) {
    LISTEN("Listen", Icons.Outlined.Headphones),
    SOUND("Sound", Icons.Outlined.GraphicEq),
    DEVICE("Device", Icons.Outlined.Tune),
}

/**
 * TV home: the modal navigation drawer (collapsed to icons, expands over the content while focused), a status
 * pane with the headset render, and the section content. Back from the content returns to the drawer;
 * back from the drawer leaves the app, as the TV navigation guide asks.
 */
@Composable
fun HushTvShell(state: HeadphonesState, vm: HeadphonesController) {
    var section by rememberSaveable { mutableStateOf(Section.LISTEN) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val itemFocus = remember { Section.entries.associateWith { FocusRequester() } }
    val contentFocus = remember { FocusRequester() }
    var contentHasFocus by remember { mutableStateOf(false) }

    BackHandler(enabled = contentHasFocus) { runCatching { itemFocus.getValue(section).requestFocus() } }

    // Modal: the expanded drawer floats over a scrim instead of squeezing the content beside it.
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { value ->
            Column(
                Modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = Tv.TopMargin),
                verticalArrangement = Arrangement.spacedBy(Tv.Small, Alignment.CenterVertically),
            ) {
                Section.entries.forEach { s ->
                    NavigationDrawerItem(
                        selected = section == s,
                        onClick = {
                            section = s
                            runCatching { contentFocus.requestFocus() }
                        },
                        modifier = Modifier
                            .focusRequester(itemFocus.getValue(s))
                            // Moving through the drawer previews each section, like the TV catalog app.
                            .onFocusChanged { if (it.isFocused) section = s }
                            // ▶ enters the content through its restorer (last row in this section, else the
                            // first one), not by geometry, which would pick whatever row sits beside the item.
                            .onPreviewKeyEvent { e ->
                                if (e.key == Key.DirectionRight && e.type == KeyEventType.KeyDown) {
                                    contentFocus.requestFocus(); true
                                } else false
                            },
                        leadingContent = { Icon(s.icon, null) },
                    ) { Text(s.label) }
                }
            }
        },
    ) {
        Surface(
            Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            Row(Modifier.fillMaxSize().padding(start = CollapsedDrawer + Tv.Gap, end = Tv.SideMargin)) {
                StatusPane(state, Modifier.width(220.dp).fillMaxHeight().padding(vertical = Tv.TopMargin))
                Spacer(Modifier.width(Tv.Gutter))
                AnimatedContent(
                    targetState = section,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(contentFocus)
                        .focusRestorer()
                        .onFocusChanged { contentHasFocus = it.hasFocus },
                    label = "section",
                ) { s ->
                    when (s) {
                        Section.LISTEN -> ListenScreen(state, vm)
                        Section.SOUND -> SoundScreen(state, vm)
                        Section.DEVICE -> DeviceScreen(state, vm)
                    }
                }
            }
        }
    }
    // Land in the content on launch: the first thing the user sees is focused, the drawer is one ◀ away.
    LaunchedEffect(Unit) { runCatching { contentFocus.requestFocus() } }
}

@Composable
private fun StatusPane(state: HeadphonesState, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Image(
                painterResource(if (state.isJabra) R.drawable.earbuds else R.drawable.xm4_angle), null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alpha = if (state.phase == Phase.CONNECTED) 1f else 0.5f,
            )
        }
        Spacer(Modifier.height(Tv.Gap))
        Text(
            state.displayName,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            statusLine(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        state.battery?.let { level ->
            Spacer(Modifier.height(Tv.Gap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(batteryIcon(level, state.charging), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("$level%", style = MaterialTheme.typography.titleMedium)
                if (state.charging) {
                    Text(
                        "  Charging",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val chips = listOfNotNull(
            state.codec?.label,
            state.ncAsm?.let {
                when (it.mode) {
                    AmbientMode.NOISE_CANCELLING -> "Noise cancelling"
                    AmbientMode.AMBIENT -> "Ambient ${it.ambientLevel}"
                    AmbientMode.OFF -> null
                }
            },
            if (state.dseeActive) "DSEE" else null,
        )
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(Tv.Small))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips.forEach { Tag(it) } }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private fun batteryIcon(level: Int, charging: Boolean) = when {
    charging -> Icons.Outlined.BatteryChargingFull
    level >= 90 -> Icons.Outlined.BatteryFull
    level >= 60 -> Icons.Outlined.Battery6Bar
    level >= 30 -> Icons.Outlined.Battery4Bar
    else -> Icons.Outlined.Battery2Bar
}

fun statusLine(state: HeadphonesState) = when (state.phase) {
    Phase.CONNECTED -> if (state.audioSettling) "Applying change…" else "Connected"
    Phase.CONNECTING -> "Connecting…"
    Phase.RECONNECTING -> state.reconnectReason ?: "Reconnecting…"
    Phase.FAILED -> state.error ?: "Not connected"
    Phase.IDLE -> "Not connected"
    Phase.UPDATING -> "Firmware update in progress"
}
