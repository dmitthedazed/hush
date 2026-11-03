package com.occaecat.hush.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.occaecat.hush.Phase
import com.occaecat.hush.link.EqNames
import com.occaecat.hush.link.NoiseMode
import com.occaecat.hush.link.Snapshot
import com.occaecat.hush.link.TOGGLE_DSEE
import com.occaecat.hush.link.TOGGLE_STC
import com.occaecat.hush.wear.hub.HushHub

/** Debug builds: fakes a connected WH-1000XM4 so the UI, tiles and complications can be checked on an emulator. */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hub = HushHub.get(context)
        if (intent.getStringExtra("demo") == "off") return hub.setDemo(null)
        hub.setDemo(
            Snapshot(
                phase = Phase.CONNECTED,
                name = "WH-1000XM4",
                battery = intent.getIntExtra("battery", 80),
                mode = runCatching { NoiseMode.valueOf(intent.getStringExtra("mode") ?: "") }
                    .getOrDefault(NoiseMode.NOISE_CANCELLING),
                ambientLevel = 14,
                voiceFocus = false,
                toggles = listOf(Snapshot.Toggle(TOGGLE_STC, "Speak-to-Chat", false), Snapshot.Toggle(TOGGLE_DSEE, "DSEE Extreme", true)),
                eqPreset = "${0x16}",
                eqPresets = listOf(0x00, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0xA0).map { Snapshot.Preset("$it", EqNames.of(it)) },
                hasAddress = true,
            )
        )
    }
}
