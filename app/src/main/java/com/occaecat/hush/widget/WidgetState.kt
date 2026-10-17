package com.occaecat.hush.widget

import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode

/** What the home screen widgets show; a slice of [HeadphonesState] so widget refreshes only follow real changes. */
data class WidgetState(
    val paired: Boolean,
    val connected: Boolean,
    val connecting: Boolean,
    val name: String,
    val battery: Int?,
    val charging: Boolean,
    val mode: AmbientMode?,
    val codec: String?,
    val dsee: Boolean?,
    val speakToChat: Boolean?,
) {
    val status: String
        get() = when {
            !paired -> "Pick headphones in Hush"
            connecting -> "Connecting…"
            !connected -> "Not connected"
            else -> listOfNotNull("Connected", codec).joinToString(" · ")
        }

    companion object {
        fun from(s: HeadphonesState) = WidgetState(
            paired = s.address != null,
            connected = s.phase == Phase.CONNECTED,
            connecting = s.phase == Phase.CONNECTING || s.phase == Phase.RECONNECTING,
            name = s.displayName,
            battery = s.battery,
            charging = s.charging,
            mode = s.ncAsm?.mode,
            codec = s.codec?.label?.takeIf { it != "—" },
            dsee = s.dseeAuto,
            speakToChat = s.speakToChat,
        )

        /** Used by the widget picker and Android Studio previews. */
        val sample = WidgetState(
            paired = true, connected = true, connecting = false, name = "WH-1000XM4", battery = 72,
            charging = false, mode = AmbientMode.NOISE_CANCELLING, codec = "LDAC", dsee = true, speakToChat = false,
        )

        val sampleDisconnected = sample.copy(connected = false, battery = null, mode = null, codec = null)
    }
}
