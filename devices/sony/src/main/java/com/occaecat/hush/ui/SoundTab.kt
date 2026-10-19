@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.occaecat.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.autoeq.AutoEq
import com.occaecat.hush.sound.SavedEq
import com.occaecat.hush.sound.SoundAutomation
import com.occaecat.hush.sound.SoundStore
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase
import kotlin.math.roundToInt

/** What Sony's sound pages need besides the headset state: which page or sheet is open. */
class SonySoundUi {
    val dialogs = DialogState()
    var page by mutableStateOf<String?>(null)
    var fyeOpen by mutableStateOf(false)
}

/** Sony's cards on the shared Sound page: personal tuning, automation, sound quality. */
internal fun LazyStaggeredGridScope.sonySound(state: HeadphonesState, vm: HeadphonesController, ui: SonySoundUi) = with(ui) {
    val connected = state.phase == Phase.CONNECTED || state.phase == Phase.RECONNECTING
    if (!connected) return@with
    val eq = state.eq
    if (eq != null && eq.bands.size == Labels.sixBands.size) item {
        // Find Your Equalizer uses Sound Connect's tables for the standard six bands only.
        val fye = eq.bands.size == Labels.sixBands.size
        Column(ItemWidth) {
            SectionHeader("Personalize", top = Space.s)
            SegmentedGroup {
                val rows = listOfNotNull("fye".takeIf { fye }, "hearing".takeIf { fye })
                rows.forEachIndexed { i, r ->
                    when (r) {
                        "fye" -> ValueRow(
                            i, rows.size, Icons.Outlined.Hearing, "Find Your Equalizer",
                            "Pick the sound you like by ear; saved to Custom 2",
                            onClick = { fyeOpen = true },
                        )
                        "hearing" -> ValueRow(
                            i, rows.size, Icons.Outlined.GraphicEq, "Hearing test",
                            "Even out the bands you hear less",
                            onClick = { page = "hearing" },
                        )
                    }
                }
            }
        }
    }
    item {
        val sound by SoundStore.config.collectAsState()
        val status by SoundAutomation.status.collectAsState()
        Column(ItemWidth) {
            SectionHeader("Automation", top = Space.s)
            SegmentedGroup {
                ValueRow(
                    0, 2, Icons.AutoMirrored.Outlined.DirectionsWalk, "Adaptive Sound Control",
                    if (!sound.ascEnabled) "Off" else status.inputs.activity?.let { "On · ${it.label}" } ?: "On",
                    onClick = { page = "asc" },
                )
                ValueRow(
                    1, 2, Icons.Outlined.Apps, "App profiles",
                    when {
                        !sound.appsEnabled -> "Off"
                        sound.appRules.isEmpty() -> "On · no apps yet"
                        else -> "On · ${sound.appRules.size} app" + if (sound.appRules.size > 1) "s" else ""
                    },
                    onClick = { page = "apps" },
                )
            }
        }
    }
    val rows = listOfNotNull(
        state.dseeAuto?.let { "dsee" },
        state.soundQualityPreferConnection?.let { "mode" },
    )
    if (rows.isNotEmpty()) item {
        Column(ItemWidth) {
            SectionHeader("Sound quality", top = Space.s)
            SegmentedGroup {
                rows.forEachIndexed { i, r ->
                    when (r) {
                        "dsee" -> SwitchRow(
                            i, rows.size, Icons.Outlined.AutoAwesome, "DSEE Extreme",
                            if (state.dseeActive) "Upscaling compressed audio now" else "Restores detail in compressed audio",
                            state.dseeAuto == true, onCheckedChange = vm::setDsee,
                        )
                        "mode" -> {
                            val preferConn = state.soundQualityPreferConnection == true
                            ValueRow(
                                i, rows.size, Icons.Outlined.Compress, "Bluetooth connection quality",
                                if (preferConn) "Prioritize stable connection" else "Prioritize sound quality",
                                enabled = !state.audioSettling,
                            ) {
                                dialogs.choice = Choice(
                                    "Bluetooth connection quality",
                                    listOf(0 to "Prioritize sound quality", 1 to "Prioritize stable connection"),
                                    if (preferConn) 1 else 0,
                                ) { v -> dialogs.confirmReconnect { vm.setSoundQualityMode(v == 1) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sony's sound pages and sheets, composed while the device UI shows. */
@Composable
internal fun SonySoundOverlays(state: HeadphonesState, vm: HeadphonesController, ui: SonySoundUi, onEqChanged: () -> Unit) = with(ui) {
    val presets = state.eq?.let { presetsFor(state) }
    DialogHost(dialogs)
    when (page) {
        "asc" -> AdaptiveSoundPage(presets.orEmpty()) { page = null }
        "apps" -> AppProfilesPage(presets.orEmpty()) { page = null }
        "hearing" -> HearingTestPage(vm) { page = null }
    }
    if (fyeOpen) {
        FindYourEqDialog(vm) { saved ->
            fyeOpen = false
            if (saved) onEqChanged()
        }
    }
}

internal fun presetsFor(state: HeadphonesState): List<Pair<Int, String>> =
    state.eqCaps?.presets?.takeIf { it.isNotEmpty() }
        ?.map { it.id to it.name.ifBlank { Labels.presetNames[it.id] ?: "Preset" } }
        ?: Labels.presetNames.filterKeys { it <= 0x17 || it == 0xA0 }.toList()

