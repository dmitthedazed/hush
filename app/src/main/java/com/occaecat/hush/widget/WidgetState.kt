package com.occaecat.hush.widget

import android.graphics.Bitmap
import com.occaecat.hush.devices.DeviceState
import com.occaecat.hush.devices.QuickMode
import com.occaecat.hush.devices.QuickToggle

/** What the home screen widgets show: a slice of the current device's state, so refreshes follow real changes. */
data class WidgetState(
    val paired: Boolean,
    val connected: Boolean,
    val connecting: Boolean,
    val name: String,
    val battery: Int?,
    val charging: Boolean,
    /** Shown instead of the battery when the device has none ("Wired"). */
    val label: String?,
    val detail: String?,
    val modes: List<QuickMode>,
    val toggles: List<QuickToggle>,
    val render: Bitmap?,
) {
    val active get() = modes.firstOrNull { it.active }

    val status: String
        get() = when {
            !paired -> "Pick headphones in Hush"
            connecting -> "Connecting…"
            !connected -> "Not connected"
            else -> listOfNotNull("Connected", detail).joinToString(" · ")
        }

    /** The big number or word: battery, else the status label. */
    val headline: String
        get() = when {
            !connected -> "—"
            battery != null -> "$battery%"
            else -> label ?: "On"
        }

    companion object {
        fun from(s: DeviceState?, modes: List<QuickMode>, toggles: List<QuickToggle>, render: Bitmap?) = WidgetState(
            paired = s != null,
            connected = s?.connected == true,
            connecting = s?.connection == com.occaecat.hush.devices.Connection.CONNECTING ||
                s?.connection == com.occaecat.hush.devices.Connection.RECONNECTING,
            name = s?.name ?: "Hush",
            battery = s?.battery,
            charging = s?.charging == true,
            label = s?.statusLabel?.second,
            detail = s?.heroChips?.firstOrNull()?.label,
            modes = modes,
            toggles = toggles,
            render = render,
        )

        /** Used by the widget picker and Android Studio previews. */
        val sample = WidgetState(
            paired = true, connected = true, connecting = false, name = "WH-1000XM4", battery = 72, charging = false,
            label = null, detail = "LDAC",
            modes = listOf(
                QuickMode("nc", "Noise cancelling", "NC", com.occaecat.hush.R.drawable.ic_w_headphones, true),
                QuickMode("ambient", "Ambient sound", "Ambient", com.occaecat.hush.R.drawable.ic_w_headphones, false),
                QuickMode("off", "Off", "Off", com.occaecat.hush.R.drawable.ic_w_headphones, false),
            ),
            toggles = listOf(QuickToggle("dsee", "DSEE Extreme", true, true), QuickToggle("stc", "Speak-to-Chat", false, true)),
            render = null,
        )

        val sampleDisconnected = sample.copy(connected = false, battery = null)
    }
}
