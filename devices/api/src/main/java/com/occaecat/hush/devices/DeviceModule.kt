package com.occaecat.hush.devices

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * One brand's device support, living in its own Gradle module under `devices/` (devices/sony, devices/apple, …).
 * Hush is one app with one UI: the shared pages (Listen, Sound, Device, About, Updates, AutoEq, details) are drawn
 * by the app from what a [DeviceController] reports and can do. A module only finds its devices, exposes their
 * state and capabilities, and adds the sections nobody else has.
 *
 * Adding a brand: create `devices/<brand>` depending on `:devices:api` (which brings `:ui`), implement this
 * interface and [DeviceController], declare its manifest entries in the module's own manifest, include it in
 * `settings.gradle.kts`, depend on it from `:app` and add it to `DeviceModules`. A new model of an existing brand is
 * more [Device]s (and maybe another controller) from that brand's module.
 */
interface DeviceModule {
    /** Stable id, also the prefix of every [Device.key]: "sony", "apple". */
    val id: String
    val brand: String

    /** Once, from `Application.onCreate`: start watching for devices, restore settings. */
    fun init(context: Context) {}

    /**
     * Live devices: what this module is talking to or holding right now ([Device.active] = connected). The shell
     * lists registered devices ([DeviceRegistry]) and takes "active" from here.
     */
    val devices: StateFlow<List<Device>>

    /** Models that can be added by hand, brand → model. */
    val models: List<Model> get() = emptyList()

    /** Units this module can see right now (paired headsets, plugged-in accessories), registered or not. */
    val candidates: StateFlow<List<Candidate>>

    /** Look again for [candidates] (the add screen opened, the app came back). */
    fun scan(context: Context) {}

    /** What an older Hush remembered before the registry, registered once on upgrade. */
    fun remembered(context: Context): List<Candidate> = emptyList()

    fun controller(device: Device): DeviceController

    /** The user opened [device]: connect to it if it isn't the one in use. */
    fun onOpen(device: Device) {}

    /** The user removed [device] from Hush: forget its connection settings. */
    fun onRemove(device: Device) {}

    /** An intent that reached the app's activity (deep links, notification extras). */
    fun onIntent(context: Context, intent: Intent) {}
}

/** Something the user can pick in Hush. */
data class Device(
    /** Unique across modules: "<moduleId>:<model or address>". */
    val key: String,
    val moduleId: String,
    val name: String,
    val transport: Transport,
    /** Connected and playing through it, or ready to: active devices win over remembered ones. */
    val active: Boolean,
    val modelId: String? = null,
    /** Bluetooth address; null for devices identified by model (wired EarPods). */
    val address: String? = null,
)

enum class Transport { BLUETOOTH, USB }

/** What a device page needs from the shell: the other devices, and a way to switch to one. */
interface DeviceHost {
    val current: Device?
    val all: List<Device>
    fun open(device: Device)
    /** The add-device screen. */
    fun add()
    fun remove(device: Device)
}

val LocalDeviceHost = compositionLocalOf<DeviceHost?> { null }

/** The shell's launcher activity, for notifications and widgets in device modules (they can't see :app's classes). */
fun appIntent(context: Context): Intent =
    Intent().setClassName(context.packageName, "com.occaecat.hush.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
