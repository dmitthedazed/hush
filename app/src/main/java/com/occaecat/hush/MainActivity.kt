package com.occaecat.hush

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceHost
import com.occaecat.hush.devices.DeviceSwitcher
import com.occaecat.hush.devices.LocalDeviceHost
import com.occaecat.hush.sony.SonyModule
import com.occaecat.hush.ui.LocalAppBarExtras
import com.occaecat.hush.ui.LocalLayout
import com.occaecat.hush.ui.rememberHushLayout
import com.occaecat.hush.ui.theme.HushTheme

/** The shell: one UI for every device ([com.occaecat.hush.ui.DeviceUi]), showing the picked device; the switcher changes it. */
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
        // Debug builds: `am start -n com.occaecat.hush/.MainActivity --ez demo true` shows a connected XM4 without
        // hardware, for checking layouts on emulators.
        val demo = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("demo", false)
        setContent {
            HushTheme {
                CompositionLocalProvider(LocalLayout provides rememberHushLayout()) {
                    if (demo) com.occaecat.hush.ui.DeviceUi(remember { SonyModule.demo(this@MainActivity) }) else Shell()
                }
            }
        }
    }
}

@Composable
private fun Shell() {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // Generated widget picker previews (Android 15+), once per app version.
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        com.occaecat.hush.widget.HushWidgets.publishPreviews(context, version)
    }
    val lists = DeviceModules.all.map { it.devices.collectAsState() }
    val devices = lists.flatMap { it.value }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    // A device that becomes active (EarPods plugged in, headset connected) takes the screen, like the audio route does.
    var seenActive by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(devices) {
        val active = devices.filter { it.active }.map { it.key }.toSet()
        (active - seenActive).firstOrNull()?.let { picked = it }
        seenActive = active
    }
    val current = devices.firstOrNull { it.key == picked } ?: DeviceModules.pick(devices) ?: return
    val host = object : DeviceHost {
        override val current: Device = current
        override val all: List<Device> = devices
        override fun open(device: Device) { picked = device.key }
    }
    CompositionLocalProvider(
        LocalDeviceHost provides host,
        LocalAppBarExtras provides { DeviceSwitcher() },
    ) {
        val controller = remember(current.key) { DeviceModules.controller(current) }
        key(current.key) { com.occaecat.hush.ui.DeviceUi(controller) }
    }
}
