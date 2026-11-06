package com.occaecat.hush

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.occaecat.hush.devices.Connection

/**
 * Quick settings tile for the device the widgets show, whatever its brand: tap cycles its noise modes, or
 * reconnects it when it is not connected.
 */
class HushTileService : TileService() {

    private val device get() = DeviceModules.current.value
    private val controller get() = device?.let(DeviceModules::controller)

    override fun onStartListening() = render()

    override fun onClick() {
        val c = controller ?: return
        if (c.state.value.connected) c.quick?.cycleMode() else c.reconnect()
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val s = controller?.state?.value
        val mode = controller?.quick?.modes?.value?.firstOrNull { it.active }
        tile.label = mode?.takeIf { s?.connected == true }?.label ?: "Hush"
        tile.subtitle = when {
            s == null -> "No headphones"
            s.connection == Connection.CONNECTING || s.connection == Connection.RECONNECTING -> "Connecting…"
            !s.connected -> "Tap to connect"
            else -> s.battery?.let { "$it%" } ?: s.name
        }
        tile.state = when {
            s == null -> Tile.STATE_UNAVAILABLE
            s.connected -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, com.occaecat.hush.ui.R.drawable.ic_stat_headphones)
        tile.updateTile()
    }

    companion object {
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, HushTileService::class.java)) }
        }

        /** Offers the tile for the control center once, after the first device is added (system dialog). */
        fun offer(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val prefs = context.getSharedPreferences("hush", 0)
            if (prefs.getBoolean("tile_offered", false)) return
            prefs.edit().putBoolean("tile_offered", true).apply()
            context.getSystemService(android.app.StatusBarManager::class.java)?.requestAddTileService(
                ComponentName(context, HushTileService::class.java), "Hush",
                Icon.createWithResource(context, com.occaecat.hush.ui.R.drawable.ic_stat_headphones),
                context.mainExecutor,
            ) {}
        }
    }
}
