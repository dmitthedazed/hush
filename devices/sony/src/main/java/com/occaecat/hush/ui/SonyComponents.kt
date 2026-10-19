@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.occaecat.hush.HeadphonesState

/**
 * Sony's official render for the colour the headset reports (DeviceInfo SERIES_AND_COLOR → ModelColor); the URLs
 * come from Sound Connect's cloud model list (sca_image_image_url) and the files are bundled. Unknown colour: the
 * generic render.
 */
@androidx.annotation.DrawableRes
fun renderFor(color: Int?): Int = when (color) {
    0x00, 0x01 -> com.occaecat.hush.sony.R.drawable.xm4_sony_black
    0x02 -> com.occaecat.hush.sony.R.drawable.xm4_sony_white
    0x03 -> com.occaecat.hush.sony.R.drawable.xm4_sony_silver
    0x05 -> com.occaecat.hush.sony.R.drawable.xm4_sony_blue
    else -> com.occaecat.hush.sony.R.drawable.xm4_angle
}

/** Prompts the headset raises on its own (ALERT_NTFY_PARAM). */
@Composable
fun HeadsetAlertDialog(type: Int, actionType: Int, onAnswer: (Int, Boolean) -> Unit) {
    val askYesNo = actionType == 1
    AlertDialog(
        onDismissRequest = { onAnswer(type, false) },
        title = { Text("Headphones") },
        text = { Text(Labels.headsetAlerts[type] ?: "The headphones need your confirmation (message $type).") },
        confirmButton = { TextButton(onClick = { onAnswer(type, true) }) { Text(if (askYesNo) "Change" else "OK") } },
        dismissButton = if (askYesNo) {
            { TextButton(onClick = { onAnswer(type, false) }) { Text("Cancel") } }
        } else null,
    )
}

@Composable
fun ReconnectingBanner(state: HeadphonesState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(48.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(Space.m))
            Column {
                Text("Reconnecting", style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    (state.reconnectReason ?: "Connection lost") + ". This takes a few seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Holds the one sheet or dialog a tab may show at a time. */
class DialogState {
    var choice by androidx.compose.runtime.mutableStateOf<Choice?>(null)
    var confirm by androidx.compose.runtime.mutableStateOf<Pair<String, () -> Unit>?>(null)

    fun confirmReconnect(extra: String? = null, action: () -> Unit) {
        confirm = listOfNotNull(extra, Labels.RECONNECT + " Change?").joinToString(" ") to action
    }
}

@Composable
fun rememberDialogState() = androidx.compose.runtime.remember { DialogState() }

@Composable
fun DialogHost(d: DialogState) {
    d.choice?.let { ChoiceSheet(it) { d.choice = null } }
    d.confirm?.let { (msg, action) -> ConfirmDialog(msg, action, onDismiss = { d.confirm = null }) }
}

/** The blob for each noise-control mode: sealed and dense, open petals, a calm circle. */
@Composable
fun blobFor(mode: com.occaecat.hush.mdr.AmbientMode?): RoundedPolygon = when (mode) {
    com.occaecat.hush.mdr.AmbientMode.NOISE_CANCELLING -> MaterialShapes.Cookie12Sided
    com.occaecat.hush.mdr.AmbientMode.AMBIENT -> MaterialShapes.Flower
    com.occaecat.hush.mdr.AmbientMode.OFF -> MaterialShapes.Circle
    null -> MaterialShapes.SoftBurst
}
