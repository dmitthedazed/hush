package com.occaecat.hush

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.Transport
import com.occaecat.hush.sony.SonyModule
import com.occaecat.hush.ui.HushApp
import com.occaecat.hush.ui.LocalLayout
import com.occaecat.hush.ui.rememberHushLayout
import com.occaecat.hush.ui.theme.HushTheme

/** The shell: [HushApp] draws every device with one UI ([com.occaecat.hush.ui.DeviceUi]) and owns the navigation. */
class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent) = DeviceModules.all.forEach { it.onIntent(this, intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        route(intent)
        if (com.occaecat.hush.devices.DeviceRegistry.items.value.isNotEmpty()) HushTileService.offer(this)
        // Debug builds: `am start -n com.occaecat.hush/.MainActivity --ez demo true` shows a connected XM4 without
        // hardware, for checking layouts on emulators; `--es demo WH-1000XM3` (or WF-1000XM3, WF-SP800N, WI-C100)
        // shows another MDR v1 model.
        val demoModel = intent.getStringExtra("demo")
        val demo = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            (demoModel != null || intent.getBooleanExtra("demo", false))
        setContent {
            HushTheme {
                CompositionLocalProvider(LocalLayout provides rememberHushLayout()) {
                    val demoDevice = if (demo) remember {
                        val c = SonyModule.demo(this@MainActivity, demoModel)
                        Device("sony:demo:intent", "sony", c.state.value.name, Transport.BLUETOOTH, active = true, address = "demo:intent") to c
                    } else null
                    HushApp(demoDevice)
                }
            }
        }
    }
}
