package com.occaecat.hush.wear.hub

import com.occaecat.hush.Phase
import com.occaecat.hush.link.Snapshot
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.wear.R

/** Wording and icons shared by the app, tiles, complications and the ongoing activity. */
object WearLabels {
    fun mode(m: AmbientMode?) = when (m) {
        AmbientMode.NOISE_CANCELLING -> "Noise cancelling"
        AmbientMode.AMBIENT -> "Ambient sound"
        AmbientMode.OFF -> "Off"
        null -> "—"
    }

    fun modeShort(m: AmbientMode?) = when (m) {
        AmbientMode.NOISE_CANCELLING -> "ANC"
        AmbientMode.AMBIENT -> "AMB"
        AmbientMode.OFF -> "OFF"
        null -> "—"
    }

    fun modeIcon(m: AmbientMode?) = when (m) {
        AmbientMode.NOISE_CANCELLING -> R.drawable.ic_w_nc
        AmbientMode.AMBIENT -> R.drawable.ic_w_ambient
        AmbientMode.OFF -> R.drawable.ic_w_off
        null -> R.drawable.ic_w_disconnected
    }

    fun name(s: Snapshot) = s.name ?: "Headphones"

    fun status(s: Snapshot, route: Route, phoneReachable: Boolean) = when (s.phase) {
        Phase.CONNECTED -> listOfNotNull(
            s.battery?.let { "$it%" + if (s.charging) " ⚡" else "" },
            if (route == Route.PHONE) "via phone" else "on watch",
        ).joinToString(" · ")
        Phase.CONNECTING -> "Connecting…"
        Phase.RECONNECTING -> s.reconnectReason ?: "Reconnecting…"
        Phase.FAILED -> "Couldn't connect"
        Phase.UPDATING -> "Firmware update in progress"
        Phase.IDLE -> if (route == Route.PHONE && !phoneReachable) "Phone not reachable" else "Disconnected"
    }
}
