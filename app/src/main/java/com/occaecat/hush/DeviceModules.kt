package com.occaecat.hush

import com.occaecat.hush.apple.AppleModule
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceModule
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.occaecat.hush.sony.SonyModule

/** Every brand Hush supports, in the order the device switcher lists them. Adding a brand = one line here. */
object DeviceModules {
    val all: List<DeviceModule> = listOf(SonyModule, AppleModule)

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private val controllers = mutableMapOf<String, com.occaecat.hush.devices.DeviceController>()

    fun controller(device: Device) = controllers.getOrPut(device.key) { all.first { it.id == device.moduleId }.controller(device) }

    /** Every device of every module, in module order. */
    val devices: kotlinx.coroutines.flow.StateFlow<List<Device>> by lazy {
        kotlinx.coroutines.flow.combine(all.map { it.devices }) { lists -> lists.flatMap { it } }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, all.flatMap { it.devices.value })
    }

    /** What Hush shows without a user choice: a wired device in use, then any device in use, then the first one. */
    fun pick(devices: List<Device>): Device? =
        devices.firstOrNull { it.active && it.transport == com.occaecat.hush.devices.Transport.USB }
            ?: devices.firstOrNull { it.active }
            ?: devices.firstOrNull()

    /** The device widgets show. */
    val current: kotlinx.coroutines.flow.StateFlow<Device?> by lazy {
        devices.map(::pick).distinctUntilChanged().stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, pick(devices.value))
    }
}
