package com.occaecat.hush.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.layout.androidImageResource
import androidx.wear.protolayout.layout.box
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.row
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.types.dp
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.layout.imageResource
import androidx.wear.protolayout.material3.ButtonDefaults.filledButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.PrimaryLayoutMargins.Companion.DEFAULT_PRIMARY_LAYOUT_MARGIN
import androidx.wear.protolayout.material3.PrimaryLayoutMargins.Companion.MAX_PRIMARY_LAYOUT_MARGIN
import androidx.wear.protolayout.material3.ProgressIndicatorColors
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.circularProgressIndicator
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.modifiers.padding
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import androidx.wear.tiles.timeline
import androidx.wear.tiles.timelineEntry
import com.occaecat.hush.link.Command
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.wear.R
import com.occaecat.hush.wear.hub.HubState
import com.occaecat.hush.wear.hub.HushHub
import com.occaecat.hush.wear.hub.WearLabels
import com.occaecat.hush.wear.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CLICK_CONNECT = "connect"
private const val CLICK_MODE = "mode:"

/**
 * Taps come back as a tile request whose state names the clicked id; the hub acts on it optimistically, so the
 * returned layout already shows the new mode.
 */
private suspend fun handleClick(context: Context, request: TileRequest): HubState = withContext(Dispatchers.Main) {
    val hub = HushHub.get(context)
    val id = request.currentState.lastClickableId
    when {
        id == CLICK_CONNECT -> hub.perform(Command.Connect)
        id.startsWith(CLICK_MODE) ->
            runCatching { AmbientMode.valueOf(id.removePrefix(CLICK_MODE)) }.getOrNull()?.let { hub.perform(Command.SetMode(it)) }
    }
    if (id.isEmpty()) hub.refreshPhone()
    hub.state.value
}

private fun MaterialScope.openApp(context: Context) =
    clickable(launchAction(ComponentName(context, MainActivity::class.java)))

/**
 * The one Hush tile: the three noise modes as an expressive button group in the middle, the headset battery as
 * an arc hugging the screen edge (open at the bottom for the edge button), and the level on the edge button.
 */
class HushTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val s = handleClick(this@HushTileService, requestParams)
        return tile(timeline(timelineEntry(layout(s))))
    }

    private fun MaterialScope.layout(s: HubState): LayoutElement {
        val snap = s.snapshot
        val ctx = this@HushTileService
        val level = snap.battery.takeIf { snap.connected }
        val content = primaryLayout(
            titleSlot = {
                val name = text(WearLabels.name(snap).layoutString)
                // Connected: the XM4 silhouette with the battery level above the name.
                if (snap.connected) column(
                    row(
                        icon(
                            imageResource(androidImageResource(R.drawable.ic_xm4)),
                            width = 16f.dp, height = 16f.dp,
                            tintColor = colorScheme.primary,
                        ),
                        spacer(width = 4f.dp),
                        text(
                            (level?.let { (if (snap.charging) "⚡" else "") + "$it%" } ?: WearLabels.modeShort(snap.mode))
                                .layoutString,
                            typography = Typography.LABEL_MEDIUM,
                            color = colorScheme.primary,
                        ),
                        verticalAlignment = VERTICAL_ALIGN_CENTER,
                    ),
                    spacer(height = 2f.dp),
                    name,
                    horizontalAlignment = HORIZONTAL_ALIGN_CENTER,
                ) else name
            },
            mainSlot = {
                if (snap.connected) box(
                    buttonGroup { for (m in AmbientMode.entries) buttonGroupItem { modeButton(m, snap.mode == m) } },
                    width = expand(),
                    height = expand(),
                    // Air between the round-cornered buttons and the battery arc.
                    modifier = if (level != null) LayoutModifier.padding(GROUP_INSET) else null,
                ) else column(
                    icon(
                        imageResource(androidImageResource(R.drawable.ic_xm4)),
                        width = 36f.dp, height = 36f.dp,
                        tintColor = colorScheme.onSurfaceVariant,
                    ),
                    spacer(height = 6f.dp),
                    text(
                        WearLabels.status(snap, s.route, s.phoneReachable).layoutString,
                        typography = Typography.BODY_MEDIUM, maxLines = 2,
                    ),
                    horizontalAlignment = HORIZONTAL_ALIGN_CENTER,
                )
            },
            bottomSlot = {
                when {
                    snap.connected -> textEdgeButton(onClick = openApp(ctx), colors = filledTonalButtonColors()) {
                        text("Open".layoutString)
                    }
                    snap.busy -> textEdgeButton(onClick = openApp(ctx), colors = filledTonalButtonColors()) {
                        text("Wait…".layoutString)
                    }
                    else -> textEdgeButton(onClick = clickable(id = CLICK_CONNECT)) { text("Connect".layoutString) }
                }
            },
            // Pull the buttons in so they clear the battery arc.
            margins = if (level != null) MAX_PRIMARY_LAYOUT_MARGIN else DEFAULT_PRIMARY_LAYOUT_MARGIN,
        )
        if (level == null) return content
        // 0° is 12 o'clock, clockwise: from 7:30 over the top to 4:30, leaving the bottom to the edge button.
        val ring = circularProgressIndicator(
            staticProgress = level / 100f,
            startAngleDegrees = ARC_START,
            endAngleDegrees = ARC_START + ARC_SWEEP,
            strokeWidth = 6f,
            colors = ProgressIndicatorColors(
                indicatorColor = if (level <= LOW_BATTERY) colorScheme.error else colorScheme.primary,
                trackColor = colorScheme.surfaceContainerHigh,
            ),
            modifier = LayoutModifier.padding(RING_INSET),
        )
        return box(ring, content, width = expand(), height = expand())
    }

    private fun MaterialScope.modeButton(m: AmbientMode, selected: Boolean) = iconButton(
        onClick = clickable(id = CLICK_MODE + m.name),
        iconContent = { icon(imageResource(androidImageResource(WearLabels.modeIcon(m)))) },
        modifier = LayoutModifier.contentDescription(WearLabels.mode(m)),
        width = expand(),
        height = expand(),
        // Expressive: the active mode is a squircle in the strong color, the others stay round and tonal.
        shape = if (selected) shapes.large else shapes.full,
        colors = if (selected) filledButtonColors() else filledTonalButtonColors(),
    )

    private companion object {
        const val ARC_START = 225f
        const val ARC_SWEEP = 270f
        const val LOW_BATTERY = 15
        const val RING_INSET = 3f
        const val GROUP_INSET = 6f
    }
}
