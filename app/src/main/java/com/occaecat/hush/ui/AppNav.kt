@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.occaecat.hush.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    data class Features(val device: String) : Route
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
        is Features -> "features\t$device"
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
        is Features -> device
        is MicTest -> device
        is Insight -> device
        is EqLibrary -> device
        is Automation -> device
        else -> null
    }

    /** The row and page of a container transform share this ([sharedPage]); null for destinations without one. */
    val pageKey: String? get() = when (this) {
        is Updates -> "updates"
        is Features -> "features"
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
                "features" -> Features(p[1])
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
 * Hush's springs: pages slide in over the last one, rows grow into the pages they open, devices fade through, and
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
    val scope = rememberCoroutineScope()
    val back = remember(nav) { BackGesture(nav, scope) }
    back.motion = motion
    val system = LocalNavigationEventDispatcherOwner.current
    DisposableEffect(back, system) {
        system?.navigationEventDispatcher?.addHandler(back)
        onDispose { back.remove() }
    }
    LaunchedEffect(back) { snapshotFlow { nav.stack.size > 1 }.collect { back.isBackEnabled = it } }
    val corner = rememberScreenCorner()
    CompositionLocalProvider(LocalNavigator provides nav) {
        SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val entries = rememberDecoratedNavEntries(nav.stack, listOf(rememberSaveableStateHolderNavEntryDecorator())) { route ->
                NavEntry(route, metadata = metadataFor(route, motion)) {
                    DisposableEffect(route) { onDispose { back.gone(route) } }
                    CompositionLocalProvider(
                        LocalSharedScope provides this@SharedTransitionLayout,
                        LocalPageScope provides LocalNavAnimatedContentScope.current,
                        LocalShareSpace provides (route.deviceKey ?: ""),
                        LocalBackGesture provides back.held,
                    ) {
                        // Slid pages are opaque, so the parallax page underneath never shows through; pages that
                        // grow out of a row draw their own surface inside the container transform.
                        Box(
                            Modifier.fillMaxSize().predictivePage(back, route, corner)
                                .then(if (route.pageKey == null) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier),
                        ) {
                            Destination(route, nav, devices, ::controllerOf, tab, ::add)
                        }
                    }
                }
            }
            val scenes = rememberSceneState(entries, listOf(SinglePaneSceneStrategy()), emptyList(), this, nav::back)
            val events = rememberNavigationEventState(scenes)
            // The display only hears of a back gesture from [back], at its start and its end: it then holds the page
            // underneath while [back] draws the gesture itself.
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back.display) {
                NavigationBackHandler(scenes, events, {}) { if (nav.top == back.page) nav.back() }
            }
            NavDisplay(
                scenes, events,
                transitionSpec = { sharedAxisIn(motion) },
                popTransitionSpec = { sharedAxisOut(motion) },
                predictivePopTransitionSpec = { held() },
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

    @Composable
    override fun Thumb(device: Device) {
        val model = DeviceModules.all.firstOrNull { it.id == device.moduleId }?.models?.firstOrNull { it.id == device.modelId }
        if (model?.image != null || model?.imageUrl != null) ModelThumb(model) else super.Thumb(device)
    }
}

/** A pushed page: [FullScreenPage] draws in place and grows out of the row that opened it. */
@Composable
private fun InPage(route: Route, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInNav provides true, LocalPageKey provides route.pageKey) { content() }
}

// --- Motion ----------------------------------------------------------------------------------------------------------

/*
 * Page motion follows Android 16+'s own: a pushed page slides in across the whole width on top while the one under it
 * drifts a quarter of the way back (parallax), and back reverses it with the leaving page on top. Full-width slides run
 * on the expressive scheme's spatial stiffness with no bounce: an overshoot would open a gap at the window's edge.
 * Fades and in-page details keep the expressive scheme as it is.
 */
private fun <T> pageSpring() = spring<T>(Spring.DampingRatioNoBouncy, PageStiffness)
private const val PageStiffness = 380f // MotionScheme.expressive()'s default spatial spring

private fun AnimatedContentTransitionScope<Scene<Route>>.sharedAxisIn(m: MotionScheme): ContentTransform =
    slideInHorizontally(pageSpring()) { it }
        .togetherWith(slideOutHorizontally(pageSpring()) { -it / 4 } + fadeOut(m.slowEffectsSpec(), targetAlpha = 0.5f))

private fun AnimatedContentTransitionScope<Scene<Route>>.sharedAxisOut(m: MotionScheme): ContentTransform =
    (slideInHorizontally(pageSpring()) { -it / 4 } + fadeIn(m.defaultEffectsSpec(), initialAlpha = 0.5f))
        .togetherWith(slideOutHorizontally(pageSpring()) { it })
        .apply { targetContentZIndex = -1f }

/** While [BackGesture] draws a back gesture the display only holds both pages, the leaving one on top. */
private fun held(): ContentTransform =
    (EnterTransition.None togetherWith ExitTransition.None).apply { targetContentZIndex = -1f }

/**
 * Predictive back as in Telegram for Android (ActionBarLayout): the dragged page shrinks to 90% towards the side away
 * from the finger, pivoting at the finger's height, with the screen's own corners; nothing else moves, and the page
 * underneath stays put under a scrim that clears as the page leaves. Letting go flings the page off, cancelling springs
 * it back (both on the expressive spatial spring), and only then is the stack popped or the page underneath let go.
 *
 * The page follows the gesture directly, in its graphics layer: no transition seeks, nothing recomposes per frame.
 * NavDisplay is fed the gesture's start and end through [display], a dispatcher of its own, so it composes the page
 * underneath and pops at the end without animating anything itself.
 */
@Stable
private class BackGesture(private val nav: Navigator, private val scope: CoroutineScope) :
    NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, false) {
    lateinit var motion: MotionScheme
    private val input = DirectNavigationEventInput()
    val display = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher().also { it.addInput(input) }
    }

    /** The page being dragged, and the one under it; both stay set until one of them leaves the tree. */
    var page by mutableStateOf<Route?>(null); private set
    private var under: Route? = null
    var fromLeft by mutableStateOf(true); private set
    var touchY by mutableFloatStateOf(Float.NaN); private set
    /** 0..1: how far the page has shrunk (the drag). */
    var shrink by mutableFloatStateOf(0f); private set
    /** 0..1: how far it has slid off (after letting go). */
    val leave = Animatable(0f).apply { updateBounds(0f, 1f) }
    val held = derivedStateOf { page != null }
    /** Letting go or cancelling is still playing out; a back that comes meanwhile waits for it ([queued]). */
    private var settling = false
    private var queued = false

    override fun onBackStarted(event: NavigationEvent) {
        if (settling) return
        fromLeft = event.swipeEdge != NavigationEvent.EDGE_RIGHT
        touchY = if (event.touchY > 0f) event.touchY else Float.NaN
        shrink = 0f
        under = nav.stack.getOrNull(nav.stack.lastIndex - 1)
        page = nav.top
        scope.launch { leave.snapTo(0f) }
        input.backStarted(NavigationEvent(event.swipeEdge, 0f, event.touchX, event.touchY))
    }

    override fun onBackProgressed(event: NavigationEvent) {
        if (settling) return
        // Telegram's curve: most of the shrink comes in the first part of the drag (standard decelerate).
        val t = 1f - event.progress.coerceIn(0f, 1f)
        shrink = 1f - t * t * t
    }

    override fun onBackCompleted() {
        if (settling) { queued = true; return }
        if (page == null) { nav.back(); return } // a back without a gesture (button, three-button navigation)
        settle {
            leave.animateTo(1f, motion.defaultSpatialSpec(), initialVelocity = 4f)
            input.backCompleted()
        }
    }

    override fun onBackCancelled() {
        if (settling || page == null) return
        settle {
            animate(shrink, 0f, animationSpec = motion.defaultSpatialSpec()) { v, _ -> shrink = v }
            input.backCancelled()
        }
    }

    private fun settle(block: suspend () -> Unit) {
        settling = true
        scope.launch {
            try { block() } finally {
                settling = false
                if (queued) { queued = false; nav.back() }
            }
        }
    }

    /** [route] left the tree: the popped page after a back, or the page underneath after a cancel. */
    fun gone(route: Route) {
        if (page != null && (route == page || route == under)) {
            page = null
            under = null
            shrink = 0f
        }
    }
}

private val ScrimAlpha = 120f / 255f * 0.8f

/** [BackGesture]'s drawing: the scrim over the page underneath, and the dragged page scaled, rounded and slid. */
private fun Modifier.predictivePage(g: BackGesture, route: Route, corner: Shape): Modifier = this
    .drawBehind {
        if (g.page == route) {
            val a = ScrimAlpha * g.shrink.coerceIn(0f, 1f) * (1f - g.leave.value)
            if (a > 0f) drawRect(Color.Black, alpha = a)
        }
    }
    .graphicsLayer {
        if (g.page != route) {
            scaleX = 1f; scaleY = 1f; translationX = 0f; clip = false
            return@graphicsLayer
        }
        val s = 1f - 0.1f * g.shrink
        scaleX = s; scaleY = s
        val inset = 82.dp.toPx() / size.width
        val y = if (g.touchY.isNaN() || size.height <= 0f) 0.5f else (g.touchY / size.height).coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(if (g.fromLeft) 1f - inset else inset, y)
        translationX = (if (g.fromLeft) 1f else -1f) * g.leave.value * size.width
        shape = corner
        clip = true
    }

/** The display's corner radius (Android 12+ reports it), so a page shrinking off it keeps the screen's shape. */
@Composable
private fun rememberScreenCorner(): Shape {
    val view = LocalView.current
    return remember(view) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val r = view.rootWindowInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
                return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(r.toFloat())))
            }
        }
    }
}

/** Fade through (M3, for unrelated destinations): the old one fades out quickly, the new one fades in growing from 92%. */
private fun fadeThrough(m: MotionScheme): ContentTransform =
    (fadeIn(m.defaultEffectsSpec()) + scaleIn(m.defaultSpatialSpec(), initialScale = 0.92f)) togetherWith fadeOut(m.fastEffectsSpec())

private fun metadataFor(route: Route, m: MotionScheme): Map<String, Any> = when {
    // Device to device (and the first one replacing Welcome): fade through, nothing slides.
    route is Route.Home || route == Route.Welcome ->
        NavDisplay.transitionSpec { fadeThrough(m) } + NavDisplay.popTransitionSpec { fadeThrough(m) }
    // Pages that grow out of a row: the row's container transform carries them, the tabs stay put underneath.
    route.pageKey != null -> NavDisplay.transitionSpec {
        fadeIn(m.fastEffectsSpec()) togetherWith ExitTransition.KeepUntilTransitionsFinished
    } + NavDisplay.popTransitionSpec {
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
        is Route.Features -> FeaturesPage(c, s.name, nav::back)
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
