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
    val devices by DeviceModules.devices.collectAsState()
    val found by DeviceModules.found.collectAsState()
    val dismissed by com.occaecat.hush.devices.DeviceRegistry.dismissed.collectAsState()
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    fun add(c: com.occaecat.hush.devices.Candidate) {
        com.occaecat.hush.devices.DeviceRegistry.add(c)
        picked = c.key
        adding = false
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        DeviceModules.scan(context)
        onPauseOrDispose {}
    }
    // Nothing registered: the add screen is the app.
    if (devices.isEmpty()) {
        com.occaecat.hush.ui.AddDeviceScreen(onClose = null, onAdd = ::add)
        return
    }
    // A device that becomes active (EarPods plugged in, headset connected) takes the screen, like the audio route does.
    var seenActive by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(devices) {
        val active = devices.filter { it.active }.map { it.key }.toSet()
        (active - seenActive).firstOrNull()?.let { picked = it }
        seenActive = active
    }
    val current = devices.firstOrNull { it.key == picked } ?: DeviceModules.pick(devices) ?: return
    // Opening a device connects to it when it isn't the one in use (one Sony session at a time).
    LaunchedEffect(current.key) { DeviceModules.module(current.moduleId).onOpen(current) }
    val host = object : DeviceHost {
        override val current: Device = current
        override val all: List<Device> = devices
        override fun open(device: Device) { picked = device.key }
        override fun add() { adding = true }
        override fun remove(device: Device) {
            DeviceModules.remove(device)
            if (picked == device.key) picked = null
        }
    }
    CompositionLocalProvider(
        LocalDeviceHost provides host,
        LocalAppBarExtras provides { DeviceSwitcher() },
    ) {
        val controller = remember(current.key) { DeviceModules.controller(current) }
        key(current.key) { com.occaecat.hush.ui.DeviceUi(controller) }
    }
    if (adding) com.occaecat.hush.ui.AddDeviceScreen(onClose = { adding = false }, onAdd = ::add)
    // Auto-discovery: a unit the modules found that isn't registered and wasn't waved off gets one prompt.
    else found.firstOrNull { it.key !in dismissed }?.let { c ->
        com.occaecat.hush.ui.FoundDevicePrompt(c, onAdd = { add(c) }, onDismiss = { com.occaecat.hush.devices.DeviceRegistry.dismiss(c.key) })
    }
}
