package com.occaecat.hush.wear.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.occaecat.hush.Phase
import com.occaecat.hush.link.Command
import com.occaecat.hush.link.EqNames
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.wear.hub.HubState
import com.occaecat.hush.wear.hub.HushHub
import com.occaecat.hush.wear.hub.Route
import com.occaecat.hush.wear.hub.RoutePref
import com.occaecat.hush.wear.hub.WearLabels

@Composable
fun HomeScreen(
    hub: HushHub,
    onAmbient: () -> Unit,
    onEqualizer: () -> Unit,
    onConnection: () -> Unit,
    onOpenOnPhone: () -> Unit,
) {
    val s by hub.state.collectAsStateWithLifecycle()
    val snap = s.snapshot
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            when {
                snap.connected -> EdgeButton(
                    onClick = onConnection,
                    buttonSize = EdgeButtonSize.ExtraSmall,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) { Icon(Icons.Rounded.SettingsBluetooth, contentDescription = "Connection") }
                s.needsSetup -> EdgeButton(onClick = onConnection, buttonSize = EdgeButtonSize.Medium) { Text("Set up") }
                else -> EdgeButton(
                    onClick = { hub.perform(Command.Connect) },
                    buttonSize = EdgeButtonSize.Medium,
                    enabled = !snap.busy,
                ) { Text(if (snap.busy) "Connecting" else "Connect") }
            }
        },
    ) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding) {
            item { Hero(s, Modifier.fillMaxWidth().scrollFade(this, spec)) }

            if (snap.connected) {
                item {
                    ModeGroup(
                        snap.mode,
                        Modifier.fillMaxWidth().transformedHeight(this, spec),
                        SurfaceTransformation(spec),
                    ) { hub.perform(Command.SetMode(it)) }
                }
                item {
                    Text(
                        WearLabels.mode(snap.mode),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().scrollFade(this, spec),
                    )
                }
                if (snap.mode == AmbientMode.AMBIENT) {
                    item {
                        ListButton(
                            spec, label = "Ambient level", secondary = "${snap.ambientLevel} of 20",
                            icon = { Icon(painterResource(com.occaecat.hush.wear.R.drawable.ic_w_ambient), null) },
                            onClick = onAmbient,
                        )
                    }
                    item {
                        ListSwitch(spec, "Voice focus", snap.voiceFocus, Icons.Rounded.RecordVoiceOver) {
                            hub.perform(Command.SetVoiceFocus(it))
                        }
                    }
                }
                snap.speakToChat?.let { on ->
                    item { ListSwitch(spec, "Speak-to-Chat", on, Icons.Rounded.Forum) { hub.perform(Command.SetSpeakToChat(it)) } }
                }
                if (snap.eqPresets.isNotEmpty() || snap.eqPreset != null) item {
                    ListButton(
                        spec, label = "Equalizer", secondary = snap.eqPreset?.let(EqNames::of) ?: "—",
                        icon = { Icon(Icons.Rounded.Equalizer, null) }, onClick = onEqualizer,
                    )
                }
                snap.dseeAuto?.let { on ->
                    item { ListSwitch(spec, "DSEE Extreme", on, Icons.Rounded.GraphicEq) { hub.perform(Command.SetDsee(it)) } }
                }
            } else if (s.needsSetup) {
                item {
                    Text(
                        "Open Hush on your phone, or pick headphones paired with this watch.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }

            if (!s.needsSetup) item { ListHeader { Text("More") } }
            if (!s.needsSetup) item {
                ListButton(
                    spec, label = "Connection",
                    secondary = routeSummary(s),
                    icon = { Icon(Icons.Rounded.Tune, null) }, onClick = onConnection,
                )
            }
            if (s.phoneReachable) item {
                ListButton(
                    spec, label = "All settings", secondary = "Open on phone",
                    icon = { Icon(Icons.Rounded.SmartDisplay, null) }, onClick = onOpenOnPhone,
                )
            }
        }
    }
}

fun routeSummary(s: HubState): String {
    val pref = when (s.pref) {
        RoutePref.AUTO -> "Auto"
        RoutePref.WATCH -> "Watch only"
        RoutePref.PHONE -> "Phone only"
    }
    val via = if (s.route == Route.PHONE) "via phone" else "on watch"
    return "$pref · $via"
}

/**
 * For items that are not Material surfaces (the hero, plain labels): keep the measured height so nothing below
 * slides under them, and scale/fade the whole item as it reaches the curved edge instead.
 */
private fun Modifier.scrollFade(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
    with(scope) {
        graphicsLayer {
            with(spec) {
                applyContainerTransformation(scrollProgress)
                applyContentTransformation(scrollProgress)
            }
        }
    }

/** Battery ring around the headphones glyph, name and status underneath. */
@Composable
private fun Hero(s: HubState, modifier: Modifier) {
    val snap = s.snapshot
    Column(modifier.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            when {
                snap.busy -> CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 4.dp)
                snap.connected && snap.battery != null -> CircularProgressIndicator(
                    progress = { snap.battery!! / 100f },
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 5.dp,
                    colors = if (snap.battery!! <= 15) ProgressIndicatorDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.error,
                    ) else ProgressIndicatorDefaults.colors(),
                )
            }
            Icon(
                painterResource(com.occaecat.hush.wear.R.drawable.ic_xm4),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (snap.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            WearLabels.name(snap),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            if (snap.phase == Phase.FAILED && s.route == Route.WATCH) s.watchError ?: "Couldn't connect"
            else WearLabels.status(snap, s.route, s.phoneReachable),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** NC / Ambient / Off: an expressive group whose pressed button grows and whose active button morphs its shape. */
@Composable
private fun ModeGroup(
    mode: AmbientMode?,
    modifier: Modifier,
    transformation: androidx.wear.compose.material3.SurfaceTransformation,
    onSelect: (AmbientMode) -> Unit,
) {
    // The group carries the list transformation; its buttons stay untransformed (no double scaling).
    ButtonGroup(modifier, transformation = transformation) {
        for (m in AmbientMode.entries) {
            val source = remember { MutableInteractionSource() }
            IconToggleButton(
                checked = mode == m,
                onCheckedChange = { onSelect(m) },
                interactionSource = source,
                shapes = IconToggleButtonDefaults.animatedShapes(),
                modifier = Modifier.weight(1f).animateWidth(source),
            ) {
                Icon(painterResource(WearLabels.modeIcon(m)), contentDescription = WearLabels.mode(m))
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.ListButton(
    spec: TransformationSpec,
    label: String,
    secondary: String?,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        colors = ButtonDefaults.filledTonalButtonColors(),
        icon = { icon() },
        secondaryLabel = secondary?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun TransformingLazyColumnItemScope.ListSwitch(
    spec: TransformationSpec,
    label: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onChange: (Boolean) -> Unit,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
        icon = { Icon(icon, null) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}
