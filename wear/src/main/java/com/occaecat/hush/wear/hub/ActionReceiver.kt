package com.occaecat.hush.wear.hub

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.occaecat.hush.link.Command
import com.occaecat.hush.mdr.AmbientMode

/** Taps from complications and the ongoing notification, routed through the hub. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hub = HushHub.get(context)
        when (intent.action) {
            ACTION_CYCLE -> hub.perform(Command.CycleNoise)
            ACTION_CONNECT -> hub.perform(Command.Connect)
            ACTION_DISCONNECT -> hub.perform(Command.Disconnect)
            ACTION_MODE -> intent.getStringExtra(EXTRA_MODE)
                ?.let { runCatching { AmbientMode.valueOf(it) }.getOrNull() }
                ?.let { hub.perform(Command.SetMode(it)) }
        }
    }

    companion object {
        const val ACTION_CYCLE = "com.occaecat.hush.wear.CYCLE"
        const val ACTION_CONNECT = "com.occaecat.hush.wear.CONNECT"
        const val ACTION_DISCONNECT = "com.occaecat.hush.wear.DISCONNECT"
        const val ACTION_MODE = "com.occaecat.hush.wear.MODE"
        const val EXTRA_MODE = "mode"

        fun pending(context: Context, action: String, mode: AmbientMode? = null): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode() + (mode?.ordinal ?: 0),
            Intent(context, ActionReceiver::class.java).setAction(action).apply { mode?.let { putExtra(EXTRA_MODE, it.name) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
