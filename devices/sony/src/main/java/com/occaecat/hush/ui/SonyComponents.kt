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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.occaecat.hush.HeadphonesState
import androidx.core.graphics.drawable.toBitmap
import com.occaecat.hush.sony.SonyArt

/**
 * Sony's official WH-1000XM4 render for the colour the headset reports (DeviceInfo SERIES_AND_COLOR → ModelColor);
 * the URLs come from Sound Connect's cloud model list (sca_image_image_url) and the files are bundled. Other models
 * get a neutral silhouette here; [rememberRender] swaps in their downloaded render ([com.occaecat.hush.sony.SonyArt]).
 */
@androidx.annotation.DrawableRes
fun renderFor(state: com.occaecat.hush.HeadphonesState): Int =
    if (!isXm4(state)) com.occaecat.hush.sony.R.drawable.render_generic
    else renderFor(state.modelColor)

private fun isXm4(state: HeadphonesState) = state.modelName?.contains("WH-1000XM4", ignoreCase = true) != false

/** The headset's render: the bundled XM4 one, else Sony's picture for the model and colour once it is downloaded. */
@Composable
fun rememberRender(state: HeadphonesState): androidx.compose.ui.graphics.painter.Painter =
    (if (isXm4(state)) null else rememberArt(state, SonyArt.Part.RENDER)) ?: painterResource(renderFor(state))

/** One of Sony's pictures for the headset ([SonyArt]): cached, else downloaded; null until then or if there is none. */
@Composable
fun rememberArt(state: HeadphonesState, part: SonyArt.Part): androidx.compose.ui.graphics.painter.Painter? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by androidx.compose.runtime.produceState(SonyArt.cached(context, state.modelName, state.modelColor, part), state.modelName, state.modelColor, part) {
        if (value == null) value = SonyArt.load(context, state.modelName, state.modelColor, part)
    }
    return bitmap?.let { remember(it) { androidx.compose.ui.graphics.painter.BitmapPainter(it.asImageBitmap()) } }
}

/** [rememberRender] outside composition (widgets, the card panel): the downloaded render if there is one. */
fun renderBitmap(context: android.content.Context, state: HeadphonesState): android.graphics.Bitmap? =
    SonyArt.cached(context, state.modelName, state.modelColor).takeUnless { isXm4(state) }
        // The generic render is a vector, which BitmapFactory can't decode.
        ?: androidx.core.content.ContextCompat.getDrawable(context, renderFor(state))?.toBitmap()

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

/** Whether the bundled XM4 photos fit: the model the headset reported, else the phone's name for it; unknown counts as the XM4. */
private fun looksXm4(state: HeadphonesState) = (state.modelName ?: state.deviceName)?.contains("WH-1000XM4", ignoreCase = true) != false

/** The headset in a disk (connecting / not connected): the XM4 photo, else the model's render once Hush knows the model. */
@Composable
fun ModelDisk(state: HeadphonesState, size: androidx.compose.ui.unit.Dp, dimmed: Boolean = false) = when {
    looksXm4(state) -> ProductDisk(com.occaecat.hush.sony.R.drawable.xm4_front, size, dimmed = dimmed)
    state.modelName != null -> ProductDisk(rememberRender(state), size, dimmed = dimmed)
    else -> ProductDisk(com.occaecat.hush.sony.R.drawable.render_generic, size, dimmed = dimmed)
}

/** Notifications' large icon: the XM4 card, else the model's downloaded render. */
fun notificationIcon(context: android.content.Context): android.graphics.drawable.Icon {
    val state = com.occaecat.hush.HeadphonesController.get(context).state.value
    if (looksXm4(state)) return android.graphics.drawable.Icon.createWithResource(context, com.occaecat.hush.sony.R.drawable.card_xm4)
    return SonyArt.cached(context, state.modelName, state.modelColor)?.let(android.graphics.drawable.Icon::createWithBitmap)
        ?: android.graphics.drawable.Icon.createWithResource(context, com.occaecat.hush.ui.R.drawable.ic_stat_headphones)
}
