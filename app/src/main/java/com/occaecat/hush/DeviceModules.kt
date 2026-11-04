package com.occaecat.hush

import android.content.Context
import com.occaecat.hush.apple.AppleModule
import com.occaecat.hush.devices.Candidate
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceModule
import com.occaecat.hush.devices.DeviceRegistry
import com.occaecat.hush.devices.Transport
import com.occaecat.hush.devices.isDemo
import com.occaecat.hush.fallback.withPhoneFallbacks
import com.occaecat.hush.jabra.JabraModule
import com.occaecat.hush.sony.SonyModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Every brand Hush supports, in the order the add screen lists them. Adding a brand = one line here. */
object Brands {
    val all: List<DeviceModule> = listOf(SonyModule, AppleModule, JabraModule)
}

/** The brands' devices as one list for the shell: registered, live, found, and their controllers. */
object DeviceModules {
    val all: List<DeviceModule> get() = Brands.all

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllers = mutableMapOf<String, DeviceController>()
    private val selectedKey = MutableStateFlow<String?>(null)

    fun module(id: String) = all.first { it.id == id }

    private lateinit var app: Context
    private var debuggable = false

    /** The device's controller, with the phone filling in what its hardware lacks (marked as such). */
    fun controller(device: Device) = controllers.getOrPut(device.key) {
        module(device.moduleId).let { if (device.isDemo) it.demoController(device) else it.controller(device) }
            .withPhoneFallbacks(app, device)
            .also { c -> if (debuggable) com.occaecat.hush.devices.Contract.check(c).forEach { android.util.Log.w("Contract", "${device.key}: $it") } }
    }

    /** Opening a device connects to it when it isn't the one in use; demos have nothing to connect to. */
    fun open(device: Device) {
        if (device.isDemo) return
        selectedKey.value = device.key
        app.getSharedPreferences("device-selection", Context.MODE_PRIVATE).edit().putString("key", device.key).apply()
        all.filter { it.id != device.moduleId }.forEach(DeviceModule::onDeactivated)
        module(device.moduleId).onOpen(device)
    }

    /** After every module's init: register what older versions remembered (once). */
    fun init(context: Context) {
        app = context.applicationContext
        debuggable = app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Debug builds stop at a brand that breaks the module contract (unit tests check the same).
        if (debuggable) all.flatMap(com.occaecat.hush.devices.Contract::check).let { check(it.isEmpty()) { it.joinToString("\n") } }
        DeviceRegistry.migrate(all.flatMap { it.remembered(context) })
        val saved = app.getSharedPreferences("device-selection", Context.MODE_PRIVATE).getString("key", null)
        val legacyAddress = app.getSharedPreferences("hush", Context.MODE_PRIVATE).getString("address", null)
        val entry = DeviceRegistry.items.value.firstOrNull { it.key == saved }
            ?: DeviceRegistry.items.value.firstOrNull { it.address == legacyAddress && legacyAddress != null }
        entry?.let {
            val device = Device(it.key, it.moduleId, it.name, it.transport, false, it.modelId, it.address)
            open(device)
        }
    }

    private val live: StateFlow<List<Device>> by lazy {
        combine(all.map { it.devices }) { lists -> lists.flatMap { it } }
            .stateIn(scope, SharingStarted.Eagerly, all.flatMap { it.devices.value })
    }

    private fun merge(registered: List<DeviceRegistry.Entry>, live: List<Device>) = registered.map { r ->
        val l = live.firstOrNull { it.key == r.key }
        Device(r.key, r.moduleId, r.name, r.transport, active = l?.active == true, modelId = r.modelId, address = r.address)
    }

    /** The registered devices, connected or not, in the order they were added. */
    val devices: StateFlow<List<Device>> by lazy {
        combine(DeviceRegistry.items, live, ::merge).stateIn(scope, SharingStarted.Eagerly, merge(DeviceRegistry.items.value, live.value))
    }

    /** Units the modules can see that aren't registered yet. */
    val found: StateFlow<List<Candidate>> by lazy {
        combine(listOf(DeviceRegistry.items.map { r -> r.map { it.key } }) + all.map { it.candidates }) { parts ->
            @Suppress("UNCHECKED_CAST")
            val keys = parts[0] as List<String>
            parts.drop(1).flatMap { it as List<Candidate> }.filter { it.key !in keys }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    fun scan(context: Context) = all.forEach { it.scan(context) }

    fun remove(device: Device) {
        if (!device.isDemo) module(device.moduleId).onRemove(device)
        controllers.remove(device.key)
        DeviceRegistry.remove(device.key)
        if (selectedKey.value == device.key) {
            selectedKey.value = null
            app.getSharedPreferences("device-selection", Context.MODE_PRIVATE).edit().remove("key").apply()
        }
    }

    /** What Hush shows without a user choice: a wired device in use, then any device in use, then the first one. */
    fun pick(devices: List<Device>): Device? =
        devices.firstOrNull { it.active && it.transport != Transport.BLUETOOTH }
            ?: devices.firstOrNull { it.active }
            ?: devices.firstOrNull()

    /**
     * Connected devices that are ready to take settings (noise control reported, if they have one), for scenes and
     * automation.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val targets: StateFlow<List<com.occaecat.hush.scenes.SceneTarget>> by lazy {
        devices.flatMapLatest { list ->
            val ready = list.filter { it.active }.map { d ->
                val c = controller(d)
                combine(c.state, c.noise?.state ?: kotlinx.coroutines.flow.flowOf(null)) { s, n ->
                    com.occaecat.hush.scenes.SceneTarget(d.key, d.moduleId, c).takeIf { s.connected && (c.noise == null || n != null) }
                }
            }
            if (ready.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList()) else combine(ready) { it.filterNotNull() }
        }.distinctUntilChanged { a, b -> a.map { it.key } == b.map { it.key } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    /**
     * A demo of the model called [name] (any brand that has demos), else of the first model that has one, for the
     * debug launch intent.
     */
    fun demo(name: String?): Pair<Device, DeviceController>? {
        val demos = all.flatMap { m -> m.models.filter(m::hasDemo).map { m to it } }
        val (module, model) = name?.let { n -> demos.firstOrNull { it.second.name.equals(n, ignoreCase = true) } }
            ?: demos.firstOrNull() ?: return null
        val c = com.occaecat.hush.devices.demoCandidate(module.id, model)
        val device = Device(c.key, module.id, c.name, c.transport, active = true, modelId = model.id, address = c.address)
        return device to controller(device)
    }

    /** The device widgets show. */
    val current: StateFlow<Device?> by lazy {
        combine(devices, selectedKey) { list, key ->
            list.firstOrNull { it.active && it.transport != Transport.BLUETOOTH }
                ?: list.firstOrNull { it.key == key }
                ?: pick(list)
        }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, pick(devices.value))
    }
}
