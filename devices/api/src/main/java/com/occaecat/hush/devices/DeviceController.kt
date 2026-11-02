package com.occaecat.hush.devices

import androidx.annotation.DrawableRes
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import com.occaecat.hush.autoeq.AutoEq
import com.occaecat.hush.ui.DeviceTrack
import com.occaecat.hush.ui.EqUi
import kotlinx.coroutines.flow.StateFlow

/**
 * One device as the shared UI sees it: live [state], optional capabilities (null = the device can't do it, and the
 * UI leaves that part out), and slots for sections only this device has. Every slot has an empty default.
 */
interface DeviceController {
    val state: StateFlow<DeviceState>

    /** The equalizer, hardware or phone-side; null when there is none. */
    val equalizer: Equalizer? get() = null

    /** Noise cancelling / ambient modes; null when the device has none. */
    val noise: NoiseControl? get() = null

    /**
     * The device's settings as rows the shared pages draw (each group says which page). Composed while the UI shows,
     * so it can read Compose state and remember things.
     */
    @Composable
    fun settings(): List<SettingsGroup> = emptyList()

    /** Firmware version and update check; null when the device has no updatable firmware. */
    val firmware: Firmware? get() = null

    /** The product render for the hero and the device card. */
    @Composable
    fun Render(modifier: Modifier, connected: Boolean)

    /** Battery, listening and last-seen history; null when the device isn't tracked. */
    val insights: com.occaecat.hush.features.Insights.Profile? get() = null

    /** Extra switches scenes and automation can set; null when there are none. */
    val sceneSwitches: SceneSwitches? get() = null

    /** The microphone, for the shared mic test; null when the device has none (or the phone can't reach it). */
    val microphone: Microphone? get() = null

    /** What the sleep timer fades and switches off; null = the phone's music volume. */
    val sleepTarget: com.occaecat.hush.features.SleepTarget? get() = null

    /** Tapping the render in the hero (e.g. re-read the battery). */
    fun onHeroTap() {}

    /** The device's own volume (0 until [DeviceState.volumeSteps]). */
    fun setVolume(level: Int) {}

    /**
     * Screens before the device UI (permissions, choosing which headset); call [content] when the shared UI should
     * show. Also the place for prompts the device raises at any time.
     */
    @Composable
    fun Gate(content: @Composable () -> Unit) = content()

    /** Device-only cards on Listen, between the status banners and the player (noise control, scenes…). */
    fun listenSections(scope: LazyStaggeredGridScope) {}

    /** Device-only cards on Sound, after presets and AutoEq (sound tools, automation…). */
    fun soundSections(scope: LazyStaggeredGridScope) {}

    /** Device-only cards on Device, between the device card and About (buttons, authenticity, remote…). */
    fun deviceSections(scope: LazyStaggeredGridScope) {}

    /** Buttons in the device card's action row, before the shared "Remove" (connect, turn off). */
    @Composable
    fun androidx.compose.foundation.layout.RowScope.CardActions() {}

    /** More rows in the About group (help, backup, the maker's documentation). */
    val aboutRows: List<AboutRow> get() = emptyList()

    /** Pages and dialogs the device's own sections open; always composed while its UI is shown. */
    @Composable
    fun Overlays() {}

    /** Shown above the hinge of a fold lying flat. */
    val statusPane: (@Composable (Modifier) -> Unit)? get() = null

    /** Modes and switches for widgets; null when the device has none. */
    val quick: QuickControls? get() = null

    /** Try to connect again (a widget tapped while disconnected). */
    fun reconnect() {}

    /** A picture of one earbud or the case, shown by its battery gauge; null = the part's letter. */
    @androidx.compose.runtime.Composable
    fun partImage(part: BatteryPart): androidx.compose.ui.graphics.painter.Painter? = null

    /** The render as a bitmap, for widgets; null = a generic glyph. */
    fun renderBitmap(context: android.content.Context): android.graphics.Bitmap? = null

    /** What a tap on a widget opens; null = the app. */
    fun panelIntent(context: android.content.Context): android.content.Intent? = null

    /** A page id from a deep link the shared UI should open ("updates"); consumed once read. */
    val openPage: StateFlow<String?>? get() = null
    fun consumeOpenPage() {}
}

enum class Connection { CONNECTED, CONNECTING, RECONNECTING, DISCONNECTED }

data class DeviceState(
    val name: String,
    val connection: Connection,
    /** App-bar subtitle, e.g. "Connected · 80% · LDAC". */
    val subtitle: String?,
    /** In the hero instead of the battery when not connected ("Not connected", "Controls on laptop"). */
    val statusText: String? = null,
    /** A banner over the pages while something settles ("Applying sound quality…"). */
    val banner: String? = null,
    /** In the hero next to the name when there is no battery ("Wired"). */
    val statusLabel: Pair<ImageVector, String>? = null,
    /** Every battery it reports (one, or buds and case); empty for none. */
    val batteries: List<BatteryLevel> = emptyList(),
    /** Noise canceling is on (battery drain is measured separately for it). */
    val noiseCancelling: Boolean = false,
    /** The hero blob: shape and container colour role. */
    val blobShape: RoundedPolygon? = null,
    val blobRole: BlobRole = BlobRole.PRIMARY,
    /** What the device itself says is playing, for when the phone's media session isn't readable. */
    val track: DeviceTrack? = null,
    val volume: Int? = null,
    val volumeSteps: Int = 0,
    /** Where the sound comes from, in the player ("This phone", a laptop). */
    val source: Pair<ImageVector, String>? = null,
    /** The device card's facts, short ("Firmware 3.0.1", "LDAC"). */
    val cardFacts: List<String> = emptyList(),
    /** Device details page, grouped. */
    val details: List<DetailGroup> = emptyList(),
    /** Under the device card while disconnected: what to do about it. */
    val disconnectedHelp: String? = null,
    /** Model name: AutoEq's search and the device card title. */
    val model: String? = null,
    /** Small marks under the name in the hero (codec, DSEE…). */
    val heroChips: List<HeroChip> = emptyList(),
) {
    val connected get() = connection == Connection.CONNECTED || connection == Connection.RECONNECTING

    /** The level that says how long listening lasts: the only battery, or the lower bud (never the case). */
    val battery: Int? get() = batteries.filter { it.part != BatteryPart.CASE }.minOfOrNull { it.level }
    val charging: Boolean get() = batteries.any { it.part != BatteryPart.CASE && it.charging }
}

/** A hero mark: a logo (an alpha mask tinted by the theme) or a text pill. */
data class HeroChip(
    val label: String,
    @DrawableRes val logo: Int? = null,
    val dimmed: Boolean = false,
    /** The logo is cropped without margins. */
    val tight: Boolean = false,
)

enum class BlobRole { PRIMARY, SECONDARY, TERTIARY }

data class DetailGroup(val title: String, val rows: List<Detail>)

data class Detail(
    val title: String,
    val value: String,
    val icon: ImageVector? = null,
    /** Tap to copy. */
    val copy: Boolean = false,
    /** Shown only after a tap (serial numbers). */
    val hidden: Boolean = false,
)

data class AboutRow(val icon: ImageVector, val title: String, val value: String?, val onClick: () -> Unit)

/** An equalizer: the hero's bands, presets in groups, and how AutoEq corrections are applied to it. */
interface Equalizer : Capability {
    val state: StateFlow<EqUi?>

    /** Preset chips by group ("Sony presets", "Your presets"); ids are the device's own. */
    val presets: StateFlow<List<PresetGroup>>

    /** The band layout curves are stored under in the EQ library ("sony-6", "phone-10"). */
    val layout: String

    fun setBand(index: Int, level: Int)
    fun selectPreset(id: String)

    /** Plays back a whole curve in dB (a library entry). */
    fun applyLevels(levels: List<Int>)

    /** Places a library curve can also be stored on the device ("Custom 1"), as id to name. */
    val slots: List<Pair<String, String>> get() = emptyList()
    fun saveToSlot(slot: String, levels: List<Int>) {}

    /** One sentence on where an applied library curve goes, for the library page. */
    val libraryNote: String? get() = null
    fun reset()
    fun compare(pressed: Boolean)

    /** A phone-side EQ can be switched off; null when the device's EQ is always there. */
    val toggle: ((Boolean) -> Unit)? get() = null

    /** Under the header in the hero (a response curve). */
    @Composable
    fun HeroExtra() {}

    /** How AutoEq results land on this equalizer. */
    val autoEq: AutoEqTarget

    /** The AutoEq profile currently in effect, for the AutoEq row. */
    val autoEqApplied: StateFlow<String?>

    /**
     * What the EQ is now, to put back when a scene or an automation rule lets go. The default keeps the active preset
     * (or the curve) and the on/off switch; a device whose presets hold their own bands can keep more.
     */
    fun snapshot(): Any? = EqSnapshot(
        presets.value.flatMap { it.presets }.firstOrNull { it.active }?.id, state.value?.levels, state.value?.enabled,
    )

    fun restore(snapshot: Any?) {
        val s = snapshot as? EqSnapshot ?: return
        if (s.enabled == false) { toggle?.invoke(false); return }
        if (s.enabled == true) toggle?.invoke(true)
        if (s.preset != null) selectPreset(s.preset) else s.levels?.let(::applyLevels)
    }
}

data class EqSnapshot(val preset: String?, val levels: List<Int>?, val enabled: Boolean?)

data class PresetGroup(val title: String, val presets: List<Preset>)
data class Preset(val id: String, val name: String, val active: Boolean)

/** Where an AutoEq correction goes. */
interface AutoEqTarget {
    /** The slot it will land in, for the preview ("Manual", "Phone EQ"). */
    val label: String

    /** Fit [curve] onto the device; null when it can't be. [AutoEq.Fit.levels] are in dB, empty for curve EQs. */
    fun fit(curve: List<AutoEq.Point>): AutoEq.Fit?

    fun apply(entry: AutoEq.Entry, curve: List<AutoEq.Point>, fit: AutoEq.Fit)

    /** Pre-filled AutoEq search when it differs from [DeviceState.model] ("Apple EarPods"). */
    val exactName: String? get() = null

    /** Names of the fitted levels, for the preview. */
    val bandLabels: List<String> get() = emptyList()

    /** One sentence on the limits of this equalizer, for the preview. */
    val note: String? get() = null

    /** Keeps the result in the EQ library; null when the device has none. */
    val saveToLibrary: ((AutoEq.Entry, AutoEq.Fit) -> Unit)? get() = null
}

/** Firmware, for the Updates page. */
interface Firmware : Capability {
    /** Composed while the Device page shows: checks when it can, returns what's pending ("firmware 3.1") or null. */
    @Composable
    fun pending(): String?

    /** True while a check runs. */
    @Composable
    fun checking(): Boolean

    /** The device part of the Updates page. */
    @Composable
    fun Section()

    suspend fun recheck(context: android.content.Context)
}

/** Glanceable controls: a few modes (one active) and on/off switches, for widgets. */
interface QuickControls {
    val modes: StateFlow<List<QuickMode>>
    fun setMode(id: String)
    /** Next mode, as a single-button widget does it. */
    fun cycleMode()
    val toggles: StateFlow<List<QuickToggle>>
    fun toggle(id: String)
}

data class QuickMode(
    val id: String,
    val label: String,
    val short: String,
    /** A 24dp glyph tinted by the widget. */
    @DrawableRes val icon: Int,
    val active: Boolean,
)

data class QuickToggle(val id: String, val label: String, val checked: Boolean, val enabled: Boolean)
