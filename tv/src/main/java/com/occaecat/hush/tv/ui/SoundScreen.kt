@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.occaecat.hush.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.ui.Labels
import com.occaecat.hush.mdr.EqBandSpec

@Composable
fun SoundScreen(state: HeadphonesState, vm: HeadphonesController) {
    val dialogs = rememberDialogState()
    SectionScaffold("Sound") {
        if (!sessionBanner(state, vm)) return@SectionScaffold
        state.eq?.let { eq ->
            val caps = state.eqCaps ?: return@let
            val presets = state.eqCaps?.presets?.takeIf { it.isNotEmpty() }
                ?.map { it.id to it.name.ifBlank { Labels.presetNames[it.id] ?: "Preset" } }
                ?: if (state.isSony) Labels.presetNames.filterKeys { it <= 0x17 || it == 0xA0 }.toList() else emptyList()
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    SectionHeader("Equalizer", Modifier.weight(1f))
                    Text(
                        presets.firstOrNull { it.first == eq.presetId }?.second ?: "Custom",
                        Modifier.padding(bottom = Tv.Small, end = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (presets.isNotEmpty()) item {
                val selectedIndex = presets.indexOfFirst { it.first == eq.presetId }.coerceAtLeast(0)
                LazyRow(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex),
                    horizontalArrangement = Arrangement.spacedBy(Tv.Small),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                ) {
                    items(presets, key = { it.first }) { (id, name) ->
                        val selected = eq.presetId == id
                        FilterChip(
                            selected = selected,
                            onClick = { vm.selectEqPreset(id, caps.namespace) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                        ) { Text(name) }
                    }
                }
            }
            if (eq.bands.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tv.Small)) {
                    eq.bands.forEachIndexed { i, raw ->
                        val band = caps.bandSpecs.getOrNull(i) ?: return@forEachIndexed
                        EqBand(band, raw, Modifier.weight(1f)) { vm.setEqBand(i, it) }
                    }
                }
            }
            if (caps.supportsEnable) item { SwitchRow(Icons.Outlined.AutoAwesome, "Equalizer enabled", null, eq.enabled, onCheckedChange = vm::setEqEnabled) }
            if (eq.bands.isNotEmpty()) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::setEqFlat) {
                        Icon(Icons.Outlined.RestartAlt, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Flat")
                    }
                    Spacer(Modifier.width(Tv.Gap))
                    Text(
                        "Select a band and press OK, then ▲ ▼ to adjust",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val preferConn = state.soundQualityPreferConnection
        if (state.dseeAuto != null || preferConn != null) {
            item { SectionHeader("Sound quality") }
            state.dseeAuto?.let { on ->
                item {
                    SwitchRow(
                        Icons.Outlined.AutoAwesome, "DSEE Extreme",
                        if (state.dseeActive) "Upscaling compressed audio now" else "Restores detail in compressed audio",
                        on, onCheckedChange = vm::setDsee,
                    )
                }
            }
            preferConn?.let { pc ->
                item {
                    ValueRow(
                        Icons.Outlined.Compress, "Bluetooth connection quality",
                        if (pc) "Stable connection" else "Sound quality",
                        enabled = !state.audioSettling,
                    ) {
                        dialogs.choice = Choice(
                            "Bluetooth connection quality",
                            listOf(0 to "Prioritize sound quality", 1 to "Prioritize stable connection"),
                            if (pc) 1 else 0,
                        ) { v -> if ((v == 1) != pc) dialogs.confirmReconnect { vm.setSoundQualityMode(v == 1) } }
                    }
                }
            }
        }
    }
    DialogHost(dialogs)
}

/**
 * One EQ band, 0..20 with 10 as flat. A D-pad can't drag, so OK enters edit mode (▲ ▼ change the
 * level, OK or Back leave it); outside edit mode ▲ ▼ move focus normally.
 */
@Composable
private fun EqBand(band: EqBandSpec, raw: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val db = band.toDb(raw)
    val label = band.label
    val accent = MaterialTheme.colorScheme.primary
    // With targetSdk 36 Back arrives through the predictive-back dispatcher, not as a key event, so leaving
    // edit mode needs its own (innermost, so it wins over the shell's) BackHandler.
    BackHandler(enabled = editing) { editing = false }
    Surface(
        onClick = { editing = !editing },
        modifier = modifier
            .height(184.dp)
            .onFocusChanged { focused = it.isFocused; if (!it.isFocused) editing = false }
            .onPreviewKeyEvent { e ->
                if (!editing) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { if (e.type == KeyEventType.KeyDown && raw < band.max) onChange((raw + band.uiStep).coerceAtMost(band.max)); true }
                    Key.DirectionDown -> { if (e.type == KeyEventType.KeyDown && raw > band.min) onChange((raw - band.uiStep).coerceAtLeast(band.min)); true }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = if (editing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = if (editing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.inverseOnSurface,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (editing) Border(BorderStroke(2.dp, accent)) else Border.None,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                (if (editing) "▲ " else "") + (if (db > 0) "+$db" else "$db") + (if (editing) " ▼" else ""),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            val onFocusSurface = focused && !editing
            val track = if (onFocusSurface) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            val fill = if (onFocusSurface) MaterialTheme.colorScheme.inversePrimary else accent
            BoxWithConstraints(Modifier.weight(1f).width(8.dp).clip(CircleShape).background(track)) {
                // Fill from the 0 dB centre line towards the level.
                val half = maxHeight / 2
                val extent = maxOf(kotlin.math.abs(band.toDb(band.min)), kotlin.math.abs(band.toDb(band.max))).coerceAtLeast(1.0)
                val len = half * (kotlin.math.abs(db) / extent).toFloat()
                Box(Modifier.align(Alignment.Center).width(8.dp).height(2.dp).background(fill.copy(alpha = 0.5f)))
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = if (db >= 0) half - len else half)
                        .width(8.dp)
                        .height(len)
                        .background(fill),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}
