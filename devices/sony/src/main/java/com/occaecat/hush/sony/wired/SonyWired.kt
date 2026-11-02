package com.occaecat.hush.sony.wired

import android.content.Context
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceRegistry
import com.occaecat.hush.devices.Model
import com.occaecat.hush.devices.Transport
import com.occaecat.hush.wired.WiredAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Sony's passive wired headphones: the models Sound Connect lists as "wired headphones" (its 360 Reality Audio
 * device list, `api.iac.meta.ndmdhs.com/v1/device/list`, type `passive`). They have no electronics to talk to, so
 * Hush can't tell which one is plugged in: the user adds their models, and the one opened last is taken to be on
 * the phone's wired output while something wired (and not a self-identifying accessory) is there.
 */
object SonyWired {
    enum class Kind(val label: String) { OVER_EAR("Over-ear"), IN_EAR("In-ear") }

    data class WiredModel(val name: String, val kind: Kind) {
        val id get() = "wired-" + name.lowercase()

        /** Sony's render from the same service Sound Connect shows it from. */
        val renderUrl get() = "https://image.iac.meta.ndmdhs.com/device/$name/icon.png"
    }

    /** In Sound Connect's order. */
    val catalogue = listOf(
        "MDR-Z1R", "MDR-Z7", "MDR-Z7M2", "MDR-1A", "MDR-1AM2", "MDR-7506", "MDR-CD900ST", "MDR-H600A", "MDR-M1ST",
        "MDR-M1", "MDR-MV1",
    ).map { WiredModel(it, Kind.OVER_EAR) } + listOf(
        "IER-H500A", "IER-M500", "IER-M7", "IER-M9", "IER-NW510N", "IER-Z1R", "XBA-N1", "XBA-N3", "XBA-Z5", "STH50C",
        "SBH90C",
    ).map { WiredModel(it, Kind.IN_EAR) }

    fun byId(id: String?) = catalogue.firstOrNull { it.id == id }

    const val HOW_TO = "Plug them into the headphone jack or a USB-C adapter. Wired headphones can't say who they " +
        "are, so Hush takes the wired output to be the model you opened last. It adds a phone-side equalizer with " +
        "AutoEq, listening insights and the sleep timer."

    fun models(): List<Model> = catalogue.map { m ->
        Model(m.id, m.name, Transport.WIRED, HOW_TO, addWithoutFinding = true, imageUrl = m.renderUrl,
            group = "Wired · ${m.kind.label.lowercase()}")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: Context
    private val controllers = mutableMapOf<String, SonyWiredController>()

    /** The model the user opened last: the one on the wired output. */
    private val selected = MutableStateFlow<String?>(null)

    private lateinit var _devices: StateFlow<List<Device>>
    val devices: StateFlow<List<Device>> get() = _devices

    /** The registered model taken to be plugged in, or null. */
    private lateinit var _current: StateFlow<String?>
    val current: StateFlow<String?> get() = _current

    fun init(context: Context, moduleId: String) {
        app = context.applicationContext
        WiredAudio.init(app)
        val prefs = app.getSharedPreferences("sony_wired", Context.MODE_PRIVATE)
        selected.value = prefs.getString("selected", null)
        selected.onEach { prefs.edit().putString("selected", it).apply() }.launchIn(scope)

        val registered = DeviceRegistry.items
        _current = combine(registered, selected, WiredAudio.free) { items, sel, out ->
            val mine = items.filter { it.moduleId == moduleId && byId(it.modelId) != null }.map { it.modelId }
            if (out == null) null else sel?.takeIf { it in mine } ?: mine.firstOrNull()
        }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)
        _devices = _current.combine(registered) { id, _ ->
            listOfNotNull(byId(id)?.let { Device("$moduleId:${it.id}", moduleId, it.name, Transport.WIRED, active = true, modelId = it.id) })
        }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, emptyList())
        // Registered models get their controller (and with it their insights) from the start.
        registered.onEach { items ->
            items.filter { it.moduleId == moduleId }.mapNotNull { byId(it.modelId) }.forEach(::controller)
        }.launchIn(scope)
    }

    fun isWired(device: Device) = byId(device.modelId) != null

    fun controller(model: WiredModel): SonyWiredController =
        controllers.getOrPut(model.id) { SonyWiredController(app, model, current) }

    /** Opened in the switcher: this is the pair on the cable now. */
    fun open(device: Device) { selected.value = device.modelId }

    fun remove(device: Device) {
        if (selected.value == device.modelId) selected.value = null
    }
}
