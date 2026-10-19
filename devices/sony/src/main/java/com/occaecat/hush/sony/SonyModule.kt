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
import com.occaecat.hush.devices.Candidate
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.Model
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

/**
 * Sony headphones over MDR (WH-1000XM4). Finds paired Sony headsets as candidates; one session at a time, so
 * opening a registered headset connects to it.
 */
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
        fun entries(s: com.occaecat.hush.HeadphonesState) = listOfNotNull(s.address?.let { addr ->
            Device(
                key = "$id:$addr", moduleId = id,
                name = s.deviceName ?: s.paired.firstOrNull { it.address == addr }?.name ?: s.modelName ?: "Sony headphones",
                transport = Transport.BLUETOOTH, active = s.phase == Phase.CONNECTED,
                modelId = modelFor(s.deviceName.orEmpty()), address = addr,
            )
        })
        _devices = controller.state.map(::entries).distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, entries(controller.state.value))
        _candidates = controller.state.map { s ->
            s.paired.filter { it.looksSony }.map { Candidate(id, modelFor(it.name), it.name, Transport.BLUETOOTH, it.address) }
        }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, emptyList())
        scan(app)
    }

    private lateinit var _candidates: StateFlow<List<Candidate>>
    override val candidates: StateFlow<List<Candidate>> get() = _candidates

    override val models = listOf(
        Model(
            XM4, "WH-1000XM4", Transport.BLUETOOTH,
            "Pair the headphones in the phone's Bluetooth settings first (hold the power button for 7 seconds to " +
                "start pairing), then pick them below. Close Sound Connect: only one app can hold the control channel.",
            permission = Manifest.permission.BLUETOOTH_CONNECT, image = R.drawable.xm4_front, note = "Fully supported",
        ),
        Model(
            OTHER, "Other Sony headphones", Transport.BLUETOOTH,
            "Hush speaks Sony's headphone protocol (MDR), so other WH- and WF- models may work. What shows up depends " +
                "on what the headset reports. Pair it in Bluetooth settings, then pick it below.",
            permission = Manifest.permission.BLUETOOTH_CONNECT, note = "Untested",
        ),
    )

    private const val XM4 = "wh-1000xm4"
    private const val OTHER = "sony-mdr"
    private fun modelFor(name: String) = if (name.contains("WH-1000XM4", ignoreCase = true)) XM4 else OTHER

    override fun scan(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            HeadphonesController.get(context).refreshPaired()
        }
    }

    /** The headset an older Hush connected to. */
    override fun remembered(context: Context): List<Candidate> {
        val s = HeadphonesController.get(context).state.value
        val addr = s.address ?: return emptyList()
        val name = s.paired.firstOrNull { it.address == addr }?.name ?: s.deviceName ?: "WH-1000XM4"
        return listOf(Candidate(id, modelFor(name), name, Transport.BLUETOOTH, addr))
    }

    override fun onOpen(device: Device) {
        val vm = HeadphonesController.get(appContext)
        val addr = device.address ?: return
        val s = vm.state.value
        if (s.address != addr || s.phase == Phase.IDLE || s.phase == Phase.FAILED) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) vm.pick(addr)
        }
    }

    override fun onRemove(device: Device) {
        val vm = HeadphonesController.get(appContext)
        if (vm.state.value.address == device.address) vm.forget()
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
