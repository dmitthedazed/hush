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
import com.occaecat.hush.HeadphonesState
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
import com.occaecat.hush.ui.demoStates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Sony headphones over MDR v1 (WH-1000XM4 and the models of its generation, docs/MDR_V1.md) and v2 (WH-1000XM5,
 * WF-1000XM5, LinkBuds and later, docs/MDR_V2.md; untested on hardware). Finds paired Sony
 * headsets as candidates; what each one offers comes from the headset itself. One session at a time, so
 * opening a registered headset connects to it. Plus Sony's passive wired headphones ([com.occaecat.hush.sony.wired.SonyWired]).
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
        com.occaecat.hush.features.Insights.track(app, sonyInsights, HeadphonesController.get(app).state.map { s ->
            com.occaecat.hush.features.Insights.Sample(
                connected = s.phase == Phase.CONNECTED || s.phase == Phase.RECONNECTING,
                battery = (s.battery ?: listOfNotNull(s.leftBattery, s.rightBattery).filter { it.level > 0 }.minOfOrNull { it.level })?.takeIf { s.phase == Phase.CONNECTED },
                charging = s.charging || listOfNotNull(s.leftBattery, s.rightBattery).any { it.charging },
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
        com.occaecat.hush.sony.wired.SonyWired.init(app, id)
        _devices = kotlinx.coroutines.flow.combine(
            controller.state.map(::entries).distinctUntilChanged(), com.occaecat.hush.sony.wired.SonyWired.devices,
        ) { bt, wired -> bt + wired }
            .stateIn(scope, SharingStarted.Eagerly, entries(controller.state.value) + com.occaecat.hush.sony.wired.SonyWired.devices.value)
        _candidates = controller.state.map { s ->
            s.paired.filter { it.looksSony }.map { Candidate(id, modelFor(it.name), it.name, Transport.BLUETOOTH, it.address) }
        }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, emptyList())
        scan(app)
    }

    private lateinit var _candidates: StateFlow<List<Candidate>>
    override val candidates: StateFlow<List<Candidate>> get() = _candidates

    /**
     * Other MDR v1 models: confirmed by their protocol reply (WH-1000XM3, WF-SP800N, WI-SP600N), driven by
     * Gadgetbridge's v1 code (WH-1000XM2, WF-1000XM3, WI-C100), or of the same generation.
     */
    private val V1_MODELS = listOf(
        "WH-1000XM3", "WH-1000XM2", "WF-1000XM3", "WF-SP800N", "WI-SP600N", "WI-C100", "WI-1000XM2", "WH-H910N", "WH-XB900N", "WH-CH710N",
    )

    /**
     * MDR v2 models (Sound Connect's TABLE_SET_2 service; research/v2/SUMMARY.md §7), longest names first so
     * "LinkBuds S" is not taken for "LinkBuds". Hush drives them from captures and Sound Connect's classes only.
     */
    private val V2_MODELS = listOf(
        "WH-1000XM6", "WH-1000XM5", "WF-1000XM6", "WF-1000XM5", "WF-1000XM4", "LinkBuds Fit", "LinkBuds Open", "LinkBuds Clip",
        "LinkBuds S", "LinkBuds", "ULT WEAR", "WH-CH720N", "WH-CH520", "WH-XB910N", "WF-C710N", "WF-C700N", "WF-C510",
    )

    override val models = listOf(
        Model(
            XM4, "WH-1000XM4", Transport.BLUETOOTH,
            "Pair the headphones in the phone's Bluetooth settings first (hold the power button for 7 seconds to " +
                "start pairing), then pick them below. Close Sound Connect: only one app can hold the control channel.",
            permission = Manifest.permission.BLUETOOTH_CONNECT, image = R.drawable.xm4_front, note = "Fully supported",
            group = "Bluetooth",
        ),
    ) + V1_MODELS.map { name ->
        Model(
            name.lowercase(), name, Transport.BLUETOOTH,
            "Pair them in the phone's Bluetooth settings first, then pick them below. Close Sound Connect: only one app " +
                "can hold the control channel. Hush shows what the headphones say they can do.",
            permission = Manifest.permission.BLUETOOTH_CONNECT, note = "Not tested on hardware", group = "Bluetooth",
            imageUrl = SonyArt.urlFor(name),
        )
    } + V2_MODELS.map { name ->
        Model(
            name.lowercase().replace(' ', '-'), name, Transport.BLUETOOTH,
            "Pair them in the phone's Bluetooth settings first, then pick them below. Close Sound Connect: only one app " +
                "can hold the control channel. These use Sony's newer protocol (v2): Hush shows what the headphones say " +
                "they can do, and some settings are not there yet.",
            permission = Manifest.permission.BLUETOOTH_CONNECT, note = "Not tested on hardware", group = "Bluetooth",
            imageUrl = SonyArt.urlFor(name),
        )
    } + Model(
        OTHER, "Other Sony headphones", Transport.BLUETOOTH,
        "Hush speaks both generations of Sony's headphone protocol (MDR v1 and v2) and reads what each model can do. " +
            "Pair them in Bluetooth settings, then pick them below.",
        permission = Manifest.permission.BLUETOOTH_CONNECT, note = "Untested", group = "Bluetooth",
    ) + com.occaecat.hush.sony.wired.SonyWired.models()

    private const val XM4 = "wh-1000xm4"
    private const val OTHER = "sony-mdr"

    private fun modelFor(name: String): String = when {
        name.contains("WH-1000XM4", ignoreCase = true) -> XM4
        else -> V1_MODELS.firstOrNull { name.contains(it, ignoreCase = true) }?.lowercase()
            ?: V2_MODELS.firstOrNull { name.contains(it, ignoreCase = true) }?.lowercase()?.replace(' ', '-') ?: OTHER
    }

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
        if (com.occaecat.hush.sony.wired.SonyWired.isWired(device)) return com.occaecat.hush.sony.wired.SonyWired.open(device)
        val vm = HeadphonesController.get(appContext)
        val addr = device.address ?: return
        val s = vm.state.value
        if (s.address != addr || s.phase == Phase.IDLE || s.phase == Phase.FAILED) {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) vm.pick(addr)
        }
    }

    override fun onRemove(device: Device) {
        if (com.occaecat.hush.sony.wired.SonyWired.isWired(device)) return com.occaecat.hush.sony.wired.SonyWired.remove(device)
        val vm = HeadphonesController.get(appContext)
        if (vm.state.value.address == device.address) vm.forget()
    }

    override fun onIntent(context: Context, intent: Intent) = DeepLink.from(intent)

    private var controller: SonyController? = null

    override fun controller(device: Device): DeviceController =
        com.occaecat.hush.sony.wired.SonyWired.byId(device.modelId)?.let(com.occaecat.hush.sony.wired.SonyWired::controller) ?: controller ?: SonyController(appContext, HeadphonesController.get(appContext)).also { controller = it }

    private lateinit var appContext: Context

    /** Debug builds' demo: a connected headset without hardware; [model] picks one of [demoStates] (the XM4 by default). */
    fun demo(context: Context, model: String? = null): DeviceController =
        demoController(context, model?.let { demoStates[it.uppercase()] } ?: demoState)

    private fun demoController(context: Context, state: HeadphonesState) =
        SonyController(context.applicationContext, HeadphonesController.demo(context, state), gated = false)

    /** Every Bluetooth model; wired ones have nothing to pretend (their settings are the phone's own). */
    override fun hasDemo(model: Model) = model.transport == Transport.BLUETOOTH

    override fun demoController(device: Device): DeviceController {
        val name = models.firstOrNull { it.id == device.modelId && it.id != OTHER }?.name ?: "WH-1000XM4"
        return demoController(appContext, demoStateFor(name))
    }

    /**
     * The canned state closest to [name]: its own when there is one ([demoStates]), else one of the same kind
     * (earbuds with a case, a neckband, headband headphones), named after the model so its picture shows.
     */
    private fun demoStateFor(name: String): HeadphonesState {
        demoStates[name.uppercase()]?.let { return it }
        val kind = when {
            name.startsWith("WF-") || name.startsWith("LinkBuds") -> "WF-1000XM3"
            name.startsWith("WI-") -> "WI-C100"
            else -> "WH-1000XM4"
        }
        return demoStates.getValue(kind).copy(modelName = name, deviceName = name)
    }
}

/** Once the Bluetooth permission is there: connect, and the one-time asks (notifications, battery). */
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

/**
 * Sony's rated battery life, hours with noise canceling on and off (earbuds: per charge, without the case).
 * Every Sony model keeps its Insights data under "sony"; only the rating used before 2 h are measured differs.
 */
private val ratedHours = mapOf(
    "WH-1000XM4" to (30 to 38), "WH-1000XM3" to (30 to 38), "WH-1000XM2" to (30 to 38),
    "WF-1000XM3" to (6 to 8), "WF-SP800N" to (9 to 13), "WI-SP600N" to (6 to 6), "WI-1000XM2" to (10 to 10),
    "WH-H910N" to (35 to 50), "WH-XB900N" to (30 to 35), "WH-CH710N" to (35 to 45), "WI-C100" to (25 to 25),
)

internal fun sonyInsights(model: String?): com.occaecat.hush.features.Insights.Profile {
    val (nc, off) = model?.let { m -> ratedHours.entries.firstOrNull { m.contains(it.key, ignoreCase = true) }?.value } ?: (30 to 38)
    return com.occaecat.hush.features.Insights.Profile(key = "sony", noun = "headphones", ratedNcPerHour = 100.0 / nc, ratedOffPerHour = 100.0 / off)
}

internal val sonyInsights = sonyInsights("WH-1000XM4")
