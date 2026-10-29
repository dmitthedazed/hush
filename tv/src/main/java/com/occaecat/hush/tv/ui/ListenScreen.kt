package com.occaecat.hush.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.HearingDisabled
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode

/** Title plus a vertically scrolling list, with the overscan-safe top/bottom margins. */
@Composable
fun SectionScaffold(title: String, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            title,
            Modifier.padding(top = Tv.TopMargin, start = 4.dp, bottom = Tv.Small),
            style = MaterialTheme.typography.headlineSmall,
        )
        // Entering a section lands on its first item (a fresh restorer falls back to it); coming back from
        // the drawer to the same section restores the last focused row.
        val first = remember { FocusRequester() }
        Box(Modifier.fillMaxSize().focusRestorer(first)) {
        LazyColumn(
            Modifier.fillMaxSize().focusRequester(first),
            // Room for the 1.02x focus scale so the focused row is never clipped at the edges.
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = Tv.TopMargin),
            verticalArrangement = Arrangement.spacedBy(Tv.Small),
            content = content,
        )
        }
    }
}

/** Connecting / failed / reconnecting states shared by every section. Returns true if the section has a session. */
fun LazyListScope.sessionBanner(state: HeadphonesState, vm: HeadphonesController): Boolean {
    if (state.phase == Phase.CONNECTED) state.error?.let { error ->
        item { Banner(Icons.Outlined.Sync, "Could not apply setting", error) }
    }
    if (state.phase == Phase.RECONNECTING || state.audioSettling) {
        item { Banner(Icons.Outlined.Sync, "Reconnecting", (state.reconnectReason ?: "Connection lost") + ". This takes a few seconds.") }
    }
    when (state.phase) {
        Phase.CONNECTING -> item { Banner(Icons.Outlined.Sync, "Connecting", "Opening the headphone channel…") }
        Phase.FAILED, Phase.IDLE -> item {
            Column {
                Banner(Icons.Outlined.HearingDisabled, "Not connected", state.error ?: "The headphones are not connected.")
                Spacer(Modifier.height(Tv.Gap))
                Row(horizontalArrangement = Arrangement.spacedBy(Tv.Gap)) {
                    Button(onClick = { state.address?.let(vm::connect) }) { Text("Try again") }
                    OutlinedButton(onClick = vm::disconnect) { Text("Choose headphones") }
                }
            }
        }
        else -> {}
    }
    return state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING
}

@Composable
private fun Banner(icon: ImageVector, title: String, text: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ListenScreen(state: HeadphonesState, vm: HeadphonesController) {
    SectionScaffold("Listen") {
        if (!sessionBanner(state, vm)) return@SectionScaffold
        state.ncAsm?.let { nc ->
            item { SectionHeader("Noise control") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Tv.Gap)) {
                    if (AmbientMode.NOISE_CANCELLING in state.capabilities.noiseModes) ModeTile(Icons.Outlined.HearingDisabled, "Noise cancelling", nc.mode == AmbientMode.NOISE_CANCELLING, Modifier.weight(1f)) {
                        vm.setAmbientMode(AmbientMode.NOISE_CANCELLING)
                    }
                    if (AmbientMode.AMBIENT in state.capabilities.noiseModes) ModeTile(Icons.Outlined.Hearing, if (state.isJabra) "HearThrough" else "Ambient sound", nc.mode == AmbientMode.AMBIENT, Modifier.weight(1f)) {
                        vm.setAmbientMode(AmbientMode.AMBIENT)
                    }
                    if (AmbientMode.OFF in state.capabilities.noiseModes) ModeTile(Icons.AutoMirrored.Outlined.VolumeOff, "Off", nc.mode == AmbientMode.OFF, Modifier.weight(1f)) {
                        vm.setAmbientMode(AmbientMode.OFF)
                    }
                }
            }
            if (nc.mode == AmbientMode.NOISE_CANCELLING && state.capabilities.ancMax > 0) state.ancLevel?.let { level ->
                item { StepperRow(Icons.Outlined.HearingDisabled, "Noise cancelling strength", level, 0..state.capabilities.ancMax, onChange = vm::setAncLevel) }
            }
            if (nc.mode == AmbientMode.AMBIENT) {
                if (state.capabilities.ambientLevels) item(key = "level") {
                    StepperRow(Icons.Outlined.Hearing, if (state.isJabra) "HearThrough level" else "Ambient sound level", nc.ambientLevel, state.capabilities.ambientMin..state.capabilities.ambientMax) {
                        vm.setAmbientLevel(it, dragging = false)
                    }
                }
                if (state.capabilities.voiceFocus) item(key = "focus") {
                    SwitchRow(
                        Icons.Outlined.RecordVoiceOver, "Focus on voice", "Let voices through, keep the noise down",
                        nc.voiceFocus, onCheckedChange = vm::setVoiceFocus,
                    )
                }
            }
        }
        if (state.dseeAuto != null || state.speakToChat != null) {
            item { SectionHeader("Quick settings") }
            state.dseeAuto?.let { on ->
                item {
                    SwitchRow(
                        Icons.Outlined.AutoAwesome, "DSEE Extreme",
                        if (state.dseeActive) "Upscaling now" else "Restores detail in compressed audio",
                        on, onCheckedChange = vm::setDsee,
                    )
                }
            }
            state.speakToChat?.let { on ->
                item {
                    SwitchRow(
                        Icons.Outlined.ChatBubbleOutline, "Speak-to-Chat", "Pause and let sound in when you talk",
                        on, onCheckedChange = vm::setSpeakToChat,
                    )
                }
            }
        }
    }
}

/** One of the three NC/ASM modes. The selected one keeps a primary tint even while another tile has focus. */
@Composable
private fun ModeTile(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = SelectableSurfaceDefaults.shape(MaterialTheme.shapes.large),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primary,
            focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, null, Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}
