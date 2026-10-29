package com.occaecat.hush

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.occaecat.hush.ui.HushShell
import com.occaecat.hush.ui.LocalLayout
import com.occaecat.hush.ui.demoState
import com.occaecat.hush.ui.rememberHushLayout
import androidx.compose.runtime.CompositionLocalProvider
import com.occaecat.hush.ui.DevicePickerScreen
import com.occaecat.hush.ui.HeadsetAlertDialog
import com.occaecat.hush.ui.PermissionScreen
import com.occaecat.hush.ui.theme.HushTheme

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        com.occaecat.hush.ui.DeepLink.from(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.occaecat.hush.ui.DeepLink.from(intent)
        // Debug builds: `am start -n com.occaecat.hush/.MainActivity --ez demo true` shows a connected XM4 without
        // hardware, for checking layouts on emulators.
        val demo = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("demo", false)
        setContent {
            HushTheme {
                CompositionLocalProvider(LocalLayout provides rememberHushLayout()) {
                    if (demo) DemoApp() else HushApp()
                }
            }
        }
    }
}

@Composable
private fun HushApp() {
    val context = LocalContext.current
    val vm = remember { HeadphonesController.get(context) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    if (!granted) {
        PermissionScreen(onGrant = { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) })
        return
    }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        vm.refreshPaired()
        vm.autoConnect()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // The tile, the My Devices card and Bluetooth settings start the session from the background; Android only
        // lets an app start its foreground service from there when it is exempt from battery optimization.
        // Generated widget picker previews (Android 15+), once per app version.
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        com.occaecat.hush.widget.HushWidgets.publishPreviews(context, version)
        // Offer the Hush tile for the control center once (system dialog, the user decides).
        val prefs = context.getSharedPreferences("hush", 0)
        if (!prefs.getBoolean("tile_offered", false)) {
            prefs.edit().putBoolean("tile_offered", true).apply()
            context.getSystemService(android.app.StatusBarManager::class.java)?.requestAddTileService(
                android.content.ComponentName(context, HushTileService::class.java), context.getString(R.string.hush_app_label),
                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_stat_headphones),
                context.mainExecutor,
            ) {}
        }
        val power = context.getSystemService(android.os.PowerManager::class.java)
        if (!power.isIgnoringBatteryOptimizations(context.packageName)) {
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:" + context.packageName))
                )
            }
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()

    state.alert?.let { (type, actionType) -> HeadsetAlertDialog(type, actionType, vm::answerAlert) }

    if (state.address == null || state.phase == Phase.IDLE) {
        DevicePickerScreen(state = state, onRefresh = vm::refreshPaired, onPick = vm::pick)
    } else {
        HushShell(state = state, vm = vm)
    }
}

@Composable
private fun DemoApp() {
    val context = LocalContext.current
    HushShell(state = demoState, vm = remember { HeadphonesController.get(context) })
}
