package com.occaecat.hush

import android.content.Context
import com.occaecat.hush.apple.AppleModule
import com.occaecat.hush.devices.Candidate
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceModule
import com.occaecat.hush.devices.DeviceRegistry
import com.occaecat.hush.devices.Transport
import com.occaecat.hush.sony.SonyModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Every brand Hush supports, in the order the add screen lists them. Adding a brand = one line here. */
object DeviceModules {
    val all: List<DeviceModule> = listOf(SonyModule, AppleModule)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllers = mutableMapOf<String, DeviceController>()

    fun module(id: String) = all.first { it.id == id }

    fun controller(device: Device) = controllers.getOrPut(device.key) { module(device.moduleId).controller(device) }

    /** After every module's init: register what older versions remembered (once). */
    fun init(context: Context) = DeviceRegistry.migrate(all.flatMap { it.remembered(context) })

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
        module(device.moduleId).onRemove(device)
        controllers.remove(device.key)
        DeviceRegistry.remove(device.key)
    }

    /** What Hush shows without a user choice: a wired device in use, then any device in use, then the first one. */
    fun pick(devices: List<Device>): Device? =
        devices.firstOrNull { it.active && it.transport == Transport.USB }
            ?: devices.firstOrNull { it.active }
            ?: devices.firstOrNull()

    /** The device widgets show. */
    val current: StateFlow<Device?> by lazy {
        devices.map(::pick).distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, pick(devices.value))
    }
}
