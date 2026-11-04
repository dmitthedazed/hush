package com.occaecat.hush.wear.hub

import com.occaecat.hush.Phase
import com.occaecat.hush.link.NoiseMode
import com.occaecat.hush.link.Snapshot
import com.occaecat.hush.wear.R

/** Wording and icons shared by the app, tiles, complications and the ongoing activity. */
object WearLabels {
    fun mode(m: NoiseMode?) = when (m) {
        NoiseMode.NOISE_CANCELLING -> "Noise cancelling"
        NoiseMode.AMBIENT -> "Ambient sound"
        NoiseMode.OFF -> "Off"
        null -> "—"
    }

    fun modeShort(m: NoiseMode?) = when (m) {
        NoiseMode.NOISE_CANCELLING -> "ANC"
        NoiseMode.AMBIENT -> "AMB"
        NoiseMode.OFF -> "OFF"
        null -> "—"
    }

    fun modeIcon(m: NoiseMode?) = when (m) {
        NoiseMode.NOISE_CANCELLING -> R.drawable.ic_w_nc
        NoiseMode.AMBIENT -> R.drawable.ic_w_ambient
        NoiseMode.OFF -> R.drawable.ic_w_off
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
        Phase.IDLE -> if (route == Route.PHONE && !phoneReachable) "Phone not reachable" else "Disconnected"
    }
}
