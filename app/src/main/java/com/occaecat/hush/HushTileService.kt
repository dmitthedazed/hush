package com.occaecat.hush

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick settings tile: tap cycles NC → Ambient → Off, or reconnects the saved headset when it is not connected. */
class HushTileService : TileService() {

    private val controller get() = HeadphonesController.get(this)

    override fun onStartListening() = render()

    override fun onClick() {
        val s = CardSnapshot(controller.state.value)
        if (s.connected) controller.cycleNoiseMode() else controller.connectSaved()
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val s = CardSnapshot(controller.state.value)
        tile.label = s.mode?.takeIf { s.connected }?.let(HushService::modeLabel) ?: "Hush"
        tile.subtitle = when {
            s.address == null -> "No headphones"
            s.connecting -> "Connecting…"
            !s.connected -> "Tap to connect"
            else -> s.battery?.let { "$it%" } ?: s.name
        }
        tile.state = when {
            s.address == null -> Tile.STATE_UNAVAILABLE
            s.connected -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_headphones)
        tile.updateTile()
    }

    companion object {
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, HushTileService::class.java)) }
        }
    }
}
