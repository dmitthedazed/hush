package com.occaecat.hush.devices

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
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

    /** Trademarks and where the module's images and data come from, for About; null when there is nothing to say. */
    val notice: String? get() = null

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

    /** Whether [model] can be added as a demo: a device that acts connected, with no hardware behind it. */
    fun hasDemo(model: Model): Boolean = false

    /**
     * The controller of a demo [device] ([Device.isDemo], model [Device.modelId]). It must never reach real hardware:
     * changes only update its own state. Opening and removing a demo never calls [onOpen] / [onRemove].
     */
    fun demoController(device: Device): DeviceController = controller(device)

    /** The user opened [device]: connect to it if it isn't the one in use. */
    fun onOpen(device: Device) {}

    /** Another brand was opened; release this module's current control session, if any. */
    fun onDeactivated() {}

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

/** A device added with "Add as demo": its address is "demo:<model id>", so the key is "<module>:demo:<model id>". */
val Device.isDemo: Boolean get() = address?.startsWith(DEMO_PREFIX) == true

/** The registry entry for a demo of [model]. */
fun demoCandidate(moduleId: String, model: Model) =
    Candidate(moduleId, model.id, "${model.name} (demo)", model.transport, address = DEMO_PREFIX + model.id)

private const val DEMO_PREFIX = "demo:"

/** How a device reaches the phone. WIRED = a passive headphone on the jack or a USB-C adapter: nothing to talk to. */
enum class Transport { BLUETOOTH, USB, WIRED }

val Transport.icon: ImageVector get() = when (this) {
    Transport.BLUETOOTH -> Icons.Outlined.Bluetooth
    Transport.USB -> Icons.Outlined.Usb
    Transport.WIRED -> Icons.Outlined.Cable
}

/** "plugged in" / "paired with this phone", for the add screens. */
val Transport.found: String get() = if (this == Transport.BLUETOOTH) "paired with this phone" else "plugged in"

/** What a device page needs from the shell: the other devices, and a way to switch to one. */
interface DeviceHost {
    val current: Device?
    val all: List<Device>
    fun open(device: Device)
    /** The add-device screen. */
    fun add()
    fun remove(device: Device)
    /** [device]'s picture in lists (the device switcher): how it connects, unless the shell has the model's render. */
    @androidx.compose.runtime.Composable
    fun Thumb(device: Device) = com.occaecat.hush.ui.LeadIcon(device.transport.icon)
}

val LocalDeviceHost = compositionLocalOf<DeviceHost?> { null }

/** The shell's launcher activity, for notifications and widgets in device modules (they can't see :app's classes). */
fun appIntent(context: Context): Intent =
    Intent().setClassName(context.packageName, "com.occaecat.hush.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
