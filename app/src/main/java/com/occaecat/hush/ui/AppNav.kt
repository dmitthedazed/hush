@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.occaecat.hush.ui

import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.occaecat.hush.DeviceModules
import com.occaecat.hush.devices.Candidate
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.DeviceHost
import com.occaecat.hush.devices.DeviceRegistry
import com.occaecat.hush.devices.DeviceSwitcher
import com.occaecat.hush.devices.LocalDeviceHost
import com.occaecat.hush.devices.isDemo

/**
 * Where the app can be. The stack's root is [Welcome] (nothing added yet) or a device's [Home]; everything else is
 * pushed on top and leaves with (predictive) back. A destination that belongs to a device names it, so switching
 * devices never shows one device's page over another.
 */
sealed interface Route {
    /** Nothing added yet: adding the first device is the app. */
    data object Welcome : Route
    /** A device's Listen, Sound and Device tabs. */
    data class Home(val device: String) : Route

    data object AddDevice : Route
    data class AddBrand(val brand: String) : Route
    data class AddModel(val brand: String, val model: String) : Route

    data class Updates(val device: String) : Route
    data class Details(val device: String) : Route
    data class About(val device: String) : Route
    data class MicTest(val device: String) : Route
    data class Insight(val device: String, val which: String) : Route
    data class EqLibrary(val device: String) : Route
    data class Automation(val device: String, val which: String) : Route

    /** For the saved stack: the route as one string. Keys and ids never contain a tab. */
    fun encode(): String = when (this) {
        Welcome -> "welcome"
        AddDevice -> "add"
        is Home -> "home\t$device"
        is AddBrand -> "brand\t$brand"
        is AddModel -> "model\t$brand\t$model"
        is About -> "about\t$device"
        is Updates -> "updates\t$device"
        is Details -> "details\t$device"
        is MicTest -> "mic\t$device"
        is Insight -> "insight\t$device\t$which"
        is EqLibrary -> "library\t$device"
        is Automation -> "automation\t$device\t$which"
    }

    /** The device the destination belongs to, if any. */
    val deviceKey: String? get() = when (this) {
        is Home -> device
        is Updates -> device
        is About -> device
        is Details -> device
        is MicTest -> device
        is Insight -> device
        is EqLibrary -> device
        is Automation -> device
        else -> null
    }

    /** The row and page of a container transform share this ([sharedPage]); null for destinations without one. */
    val pageKey: String? get() = when (this) {
        is Updates -> "updates"
        is Details -> "details"
        is About -> "about"
        is MicTest -> "mic"
        is Insight -> "insight:$which"
        is EqLibrary -> "library"
        is Automation -> "automation:$which"
        else -> null
    }

    val isAdding: Boolean get() = this is AddDevice || this is AddBrand || this is AddModel

    companion object {
        fun decode(s: String): Route? {
            val p = s.split('\t')
            return when (p[0]) {
                "welcome" -> Welcome
                "add" -> AddDevice
                "about" -> About(p[1])
                "home" -> Home(p[1])
                "brand" -> AddBrand(p[1])
                "model" -> AddModel(p[1], p[2])
                "updates" -> Updates(p[1])
                "details" -> Details(p[1])
                "mic" -> MicTest(p[1])
                "insight" -> Insight(p[1], p[2])
                "library" -> EqLibrary(p[1])
                "automation" -> Automation(p[1], p[2])
                else -> null
            }
        }
    }
}

/** Moves around the app's stack. Pages reach it through [LocalNavigator]. */
class Navigator(val stack: SnapshotStateList<Route>) {
    val top: Route get() = stack.last()

    fun push(route: Route) { if (stack.lastOrNull() != route) stack.add(route) }

    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    /** Back to the bottom of the stack, which becomes [root]. */
    fun reset(root: Route) {
        stack.clear()
        stack.add(root)
    }

    /** Another device's tabs at the bottom, keeping only what isn't about the old one (an add flow in progress). */
    fun switchTo(root: Route) {
        val keep = stack.drop(1).filter { it.deviceKey == null }
        stack.clear()
        stack.add(root)
        stack.addAll(keep)
    }

    companion object {
        val Saver: Saver<Navigator, Any> = listSaver(
            save = { n -> n.stack.map { it.encode() } },
            restore = { saved -> Navigator(saved.mapNotNull { runCatching { Route.decode(it) }.getOrNull() }.ifEmpty { listOf(Route.Welcome) }.toMutableStateList()) },
        )
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

/**
 * The app: every registered device ([DeviceModules.devices]), or [demo] alone. Owns the navigation stack, keeps its
 * root on the right device (one that becomes active takes the screen, like the audio route does), and draws it with
 * Hush's springs: pages slide along the shared axis, rows grow into the pages they open, devices fade through, and
 * every back is predictive.
 */
@Composable
fun HushApp(demo: Pair<Device, DeviceController>? = null) {
    val context = LocalContext.current
    val registered by DeviceModules.devices.collectAsState()
    val devices = demo?.let { listOf(it.first) } ?: registered
    val found by DeviceModules.found.collectAsState()
    val dismissed by DeviceRegistry.dismissed.collectAsState()
    fun controllerOf(d: Device) = if (demo != null && d.key == demo.first.key) demo.second else DeviceModules.controller(d)
    fun rootFor(list: List<Device>, prefer: String? = null): Route =
        (list.firstOrNull { it.key == prefer } ?: DeviceModules.pick(list))?.let { Route.Home(it.key) } ?: Route.Welcome

    val nav = rememberSaveable(saver = Navigator.Saver) { Navigator(mutableListOf(rootFor(devices)).toMutableStateList()) }
    // Switching devices stays on the tab the user was on.
    val tab = rememberSaveable { mutableStateOf(Tab.LISTEN) }

    if (demo == null) {
        LaunchedEffect(Unit) {
            // Generated widget picker previews (Android 15+), once per app version.
            val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            com.occaecat.hush.widget.HushWidgets.publishPreviews(context, version)
        }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            DeviceModules.scan(context)
            onPauseOrDispose {}
        }
    }

    // The root follows the registry: the first device added replaces Welcome, removing the last one brings it back,
    // and a removed device's pages go with it.
    LaunchedEffect(devices) {
        val keys = devices.map { it.key }.toSet()
        val root = nav.stack.first()
        when {
            devices.isEmpty() -> if (root != Route.Welcome) nav.reset(Route.Welcome)
            root == Route.Welcome -> nav.reset(rootFor(devices))
            root.deviceKey !in keys -> nav.reset(rootFor(devices))
            else -> nav.stack.removeAll { it.deviceKey != null && it.deviceKey !in keys }
        }
    }
    // A device that becomes active (EarPods plugged in, headset connected) takes the screen.
    var seenActive by remember { mutableStateOf(devices.filter { it.active }.map { it.key }.toSet()) }
    LaunchedEffect(devices) {
        val active = devices.filter { it.active }.map { it.key }.toSet()
        (active - seenActive).firstOrNull()?.let { key ->
            if (nav.stack.first() != Route.Home(key)) nav.switchTo(Route.Home(key))
        }
        seenActive = active
    }

    fun add(c: Candidate) {
        DeviceRegistry.add(c)
        tab.value = Tab.LISTEN
        nav.reset(Route.Home(c.key))
    }

    val motion = MaterialTheme.motionScheme
    CompositionLocalProvider(LocalNavigator provides nav) {
        SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            NavDisplay(
                backStack = nav.stack,
                onBack = nav::back,
                sharedTransitionScope = this,
                transitionSpec = { sharedAxisIn(motion) },
                popTransitionSpec = { sharedAxisOut(motion) },
                predictivePopTransitionSpec = { edge -> predictiveOut(motion, edge) },
                entryProvider = { route ->
                    NavEntry(route, metadata = metadataFor(route, motion)) {
                        CompositionLocalProvider(
                            LocalSharedScope provides this@SharedTransitionLayout,
                            LocalPageScope provides LocalNavAnimatedContentScope.current,
                            LocalShareSpace provides (route.deviceKey ?: ""),
                        ) {
                            Destination(route, nav, devices, ::controllerOf, tab, ::add)
                        }
                    }
                },
            )
        }
    }

    // Auto-discovery: a unit the modules found that isn't registered and wasn't waved off gets one prompt, on a
    // device's tabs only (the add screens list it anyway).
    if (demo == null && nav.top is Route.Home) found.firstOrNull { it.key !in dismissed }?.let { c ->
        FoundDevicePrompt(c, onAdd = { add(c) }, onDismiss = { DeviceRegistry.dismiss(c.key) })
    }
}

@Composable
private fun Destination(
    route: Route,
    nav: Navigator,
    devices: List<Device>,
    controllerOf: (Device) -> DeviceController,
    tab: MutableState<Tab>,
    add: (Candidate) -> Unit,
) {
    when (route) {
        Route.Welcome -> WelcomeScreen(onAdd = add, onBrand = { nav.push(Route.AddBrand(it)) })
        Route.AddDevice -> InPage(route) { AddDeviceScreen(onClose = nav::back, onAdd = add, onBrand = { nav.push(Route.AddBrand(it)) }) }
        is Route.AddBrand -> InPage(route) { AddBrandScreen(route.brand, onClose = nav::back) { nav.push(Route.AddModel(route.brand, it)) } }
        is Route.AddModel -> InPage(route) { AddModelScreen(route.brand, route.model, onClose = nav::back, onAdd = add) }
        else -> {
            // Everything else belongs to a device; one that was just removed draws nothing while the stack drops it.
            val device = devices.firstOrNull { it.key == route.deviceKey } ?: return
            val c = remember(device.key) { controllerOf(device) }
            if (route is Route.Home) {
                val host = remember(device, devices) { hostFor(device, devices, nav) }
                CompositionLocalProvider(LocalDeviceHost provides host, LocalAppBarExtras provides { DeviceSwitcher() }) {
                    // Opening a device connects to it when it isn't the one in use (one Sony session at a time).
                    LaunchedEffect(device.key) { if (!device.isDemo) DeviceModules.open(device) }
                    DeviceUi(c, device.key, tab)
                }
            } else InPage(route) { DevicePageFor(route, c) }
        }
    }
}

private fun hostFor(device: Device, devices: List<Device>, nav: Navigator) = object : DeviceHost {
    override val current: Device = device
    override val all: List<Device> = devices
    override fun open(device: Device) = nav.switchTo(Route.Home(device.key))
    override fun add() = nav.push(Route.AddDevice)
    override fun remove(device: Device) = DeviceModules.remove(device)
}

/** A pushed page: [FullScreenPage] draws in place and grows out of the row that opened it. */
@Composable
private fun InPage(route: Route, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInNav provides true, LocalPageKey provides route.pageKey) { content() }
}

// --- Motion ----------------------------------------------------------------------------------------------------------

/** Forward: the new page slides in a fifth of the way along the shared axis while the old one drifts back. */
private fun AnimatedContentTransitionScope<Scene<Route>>.sharedAxisIn(m: MotionScheme): ContentTransform =
    (slideInHorizontally(m.defaultSpatialSpec()) { it / 5 } + fadeIn(m.defaultEffectsSpec()))
        .togetherWith(slideOutHorizontally(m.defaultSpatialSpec()) { -it / 10 } + fadeOut(m.fastEffectsSpec()))

private fun AnimatedContentTransitionScope<Scene<Route>>.sharedAxisOut(m: MotionScheme): ContentTransform =
    (slideInHorizontally(m.defaultSpatialSpec()) { -it / 10 } + fadeIn(m.defaultEffectsSpec()))
        .togetherWith(slideOutHorizontally(m.defaultSpatialSpec()) { it / 5 } + fadeOut(m.fastEffectsSpec()))

/**
 * Predictive back (M3 spec): while the finger drags, the page shrinks to 90% and shifts away from the edge the gesture
 * started at, and the page underneath comes up from 95%.
 */
private fun AnimatedContentTransitionScope<Scene<Route>>.predictiveOut(m: MotionScheme, edge: Int): ContentTransform {
    val sign = if (edge == BackEventCompat.EDGE_LEFT) 1 else -1
    return (fadeIn(m.defaultEffectsSpec()) + scaleIn(m.defaultSpatialSpec(), initialScale = 0.95f))
        .togetherWith(
            scaleOut(m.defaultSpatialSpec(), targetScale = 0.9f) +
                slideOutHorizontally(m.defaultSpatialSpec()) { sign * it / 12 } + fadeOut(m.defaultEffectsSpec())
        )
}

private fun metadataFor(route: Route, m: MotionScheme): Map<String, Any> = when {
    // Device to device (and the first one replacing Welcome): fade through, nothing slides.
    route is Route.Home || route == Route.Welcome -> NavDisplay.transitionSpec {
        (fadeIn(m.defaultEffectsSpec()) + scaleIn(m.defaultSpatialSpec(), initialScale = 0.92f))
            .togetherWith(fadeOut(m.fastEffectsSpec()) + scaleOut(m.defaultSpatialSpec(), targetScale = 1.04f))
    }
    // Pages that grow out of a row: the row's container transform carries them, the tabs stay put underneath.
    route.pageKey != null -> NavDisplay.transitionSpec {
        fadeIn(m.fastEffectsSpec()) togetherWith ExitTransition.KeepUntilTransitionsFinished
    } + NavDisplay.popTransitionSpec {
        EnterTransition.None togetherWith fadeOut(m.defaultEffectsSpec())
    } + NavDisplay.predictivePopTransitionSpec {
        EnterTransition.None togetherWith fadeOut(m.defaultEffectsSpec())
    }
    else -> emptyMap()
}

// --- Device pages ----------------------------------------------------------------------------------------------------

@Composable
private fun DevicePageFor(route: Route, c: DeviceController) {
    val nav = LocalNavigator.current
    val s by c.state.collectAsState()
    when (route) {
        is Route.About -> AboutPage(nav::back)
        is Route.Updates -> UpdatesPage(rememberAppUpdate(), c.firmware, s.model ?: s.name, nav::back)
        is Route.Details -> DeviceDetailsPage(s.details, nav::back)
        is Route.MicTest -> c.microphone?.let { MicTestPage(c, s.name, it, nav::back) } ?: GoneBack()
        is Route.Insight -> c.insights?.let { InsightsPage(route.which, it, live(s), nav::back) } ?: GoneBack()
        is Route.EqLibrary -> c.equalizer?.let { EqLibraryPage(it, nav::back) } ?: GoneBack()
        is Route.Automation -> when (route.which) {
            "asc" -> AdaptiveSoundPage(route.device, s.name, c, nav::back)
            "apps" -> AppProfilesPage(route.device, s.name, c, nav::back)
            else -> GoneBack()
        }
        else -> GoneBack()
    }
}

/** A page whose subject went away (the device lost the feature, a stale saved stack): leave it. */
@Composable
private fun GoneBack() {
    val nav = LocalNavigator.current
    Box(Modifier.fillMaxSize())
    LaunchedEffect(Unit) { nav.back() }
}
