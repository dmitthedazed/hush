package com.occaecat.hush.sony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HushTileService
import com.occaecat.hush.Phase
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceModule
import com.occaecat.hush.devices.Transport
import com.occaecat.hush.ui.DeepLink
import com.occaecat.hush.ui.DevicePickerScreen
import com.occaecat.hush.ui.HeadsetAlertDialog
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.ui.PermissionScreen
import com.occaecat.hush.ui.demoState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Sony headphones over MDR (WH-1000XM4). Always lists one entry: the paired headset, or a picker when none. */
object SonyModule : DeviceModule {
    override val id = "sony"
    override val brand = "Sony"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var _devices: StateFlow<List<Device>>
    override val devices: StateFlow<List<Device>> get() = _devices

    override fun init(context: Context) {
        val app = context.applicationContext as android.app.Application
        appContext = app
        com.occaecat.hush.PhoneIntegrations.install(app)
        com.occaecat.hush.devices.EqLibrary.init(app)
        com.occaecat.hush.sound.SoundStore.init(app)
        com.occaecat.hush.features.Insights.track(app, sonyInsights, HeadphonesController.get(app).state.map { s ->
            com.occaecat.hush.features.Insights.Sample(
                connected = s.phase == Phase.CONNECTED || s.phase == Phase.RECONNECTING,
                battery = s.battery?.takeIf { s.phase == Phase.CONNECTED }, charging = s.charging,
                nc = s.ncAsm?.mode == com.occaecat.hush.mdr.AmbientMode.NOISE_CANCELLING,
                volume = s.volume, volumeSteps = s.volumeSteps, playing = s.playing == true,
            )
        })
        com.occaecat.hush.features.FirmwareWatch.start(app)
        val controller = HeadphonesController.get(app)
        // The headset's buttons can start the sleep timer before the app is ever opened.
        if (com.occaecat.hush.features.SleepTimer.target == null) com.occaecat.hush.features.SleepTimer.target = sonySleepTarget(controller)
        fun entries(s: com.occaecat.hush.HeadphonesState) = listOf(
            Device(
                key = "$id:${s.address ?: "none"}", moduleId = id,
                name = s.deviceName ?: s.paired.firstOrNull { it.address == s.address }?.name ?: s.modelName ?: "Sony headphones",
                transport = Transport.BLUETOOTH, active = s.phase == Phase.CONNECTED,
            )
        )
        _devices = controller.state.map(::entries).distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, entries(controller.state.value))
    }

    override fun onIntent(context: Context, intent: Intent) = DeepLink.from(intent)

    private var controller: SonyController? = null

    override fun controller(device: Device): DeviceController =
        controller ?: SonyController(appContext, HeadphonesController.get(appContext)).also { controller = it }

    private lateinit var appContext: Context

    /** Debug builds' `--ez demo true`: a connected XM4 without hardware. */
    fun demo(context: Context): DeviceController = SonyController(
        context.applicationContext, HeadphonesController.get(context),
        kotlinx.coroutines.flow.MutableStateFlow(demoState), gated = false,
    )
}

/** Once the Bluetooth permission is there: connect, and the one-time asks (notifications, tile, battery). */
@Composable
internal fun SonyStartup(vm: HeadphonesController) {
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        vm.refreshPaired()
        vm.autoConnect()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Offer the Hush tile for the control center once (system dialog, the user decides).
        val prefs = context.getSharedPreferences("hush", 0)
        if (!prefs.getBoolean("tile_offered", false)) {
            prefs.edit().putBoolean("tile_offered", true).apply()
            context.getSystemService(android.app.StatusBarManager::class.java)?.requestAddTileService(
                android.content.ComponentName(context, HushTileService::class.java), "Hush",
                android.graphics.drawable.Icon.createWithResource(context, com.occaecat.hush.ui.R.drawable.ic_stat_headphones),
                context.mainExecutor,
            ) {}
        }
        // The tile, the My Devices card and Bluetooth settings start the session from the background; Android only
        // lets an app start its foreground service from there when it is exempt from battery optimization.
        val power = context.getSystemService(android.os.PowerManager::class.java)
        if (!power.isIgnoringBatteryOptimizations(context.packageName)) {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:" + context.packageName))
                )
            }
        }
    }
}

/** The XM4 in Insights: Sony's rated battery life, 30 h with noise canceling and 38 h without; data kept under "sony". */
internal val sonyInsights = com.occaecat.hush.features.Insights.Profile(
    key = "sony", noun = "headphones", ratedNcPerHour = 100.0 / 30, ratedOffPerHour = 100.0 / 38,
)
