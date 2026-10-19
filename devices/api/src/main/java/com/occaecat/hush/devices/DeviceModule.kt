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

    /** The devices this module can show right now, connected or not (e.g. a remembered headset). */
    val devices: StateFlow<List<Device>>

    fun controller(device: Device): DeviceController

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
)

enum class Transport { BLUETOOTH, USB }

/** What a device page needs from the shell: the other devices, and a way to switch to one. */
interface DeviceHost {
    val current: Device?
    val all: List<Device>
    fun open(device: Device)
}

val LocalDeviceHost = compositionLocalOf<DeviceHost?> { null }

/** The shell's launcher activity, for notifications and widgets in device modules (they can't see :app's classes). */
fun appIntent(context: Context): Intent =
    Intent().setClassName(context.packageName, "com.occaecat.hush.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
