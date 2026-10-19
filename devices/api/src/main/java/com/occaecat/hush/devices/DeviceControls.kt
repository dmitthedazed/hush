package com.occaecat.hush.devices

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// ---- noise control ----------------------------------------------------------------------------------------------

/** Noise cancelling and its neighbours, as the shared Listen card and the widgets draw them. */
interface NoiseControl {
    /** What the device can switch between, in display order. */
    val modes: List<NoiseMode>

    /** The live setting; null until the device has reported it (no card then). */
    val state: StateFlow<NoiseUi?>

    fun setMode(id: String)

    /** [dragging] while the finger is still on the slider: a device may send cheaper, unconfirmed writes. */
    fun setLevel(level: Int, dragging: Boolean) {}

    fun setVoiceFocus(on: Boolean) {}
}

enum class NoiseKind { CANCELLING, AMBIENT, ADAPTIVE, OFF }

data class NoiseMode(
    val id: String,
    /** On the button ("ANC"). */
    val label: String,
    val kind: NoiseKind,
    /** The button glyph. */
    @DrawableRes val icon: Int,
    /** A plain 24dp vector for widgets; Glance can't draw every drawable. */
    @DrawableRes val widgetIcon: Int = icon,
    /** Spelled out, for widgets and accessibility ("Noise cancelling"). */
    val title: String = label,
    /** The level and Focus on voice open under this mode while it is active. */
    val hasOptions: Boolean = false,
)

data class NoiseUi(
    val active: String,
    /** The ambient (or cancelling) strength; null when the device has none. */
    val level: NoiseLevel? = null,
    /** Let voices through; null when the device has no such option. */
    val voiceFocus: Boolean? = null,
)

data class NoiseLevel(val title: String, val value: Int, val range: IntRange)

/** Widget modes straight from the noise control, so a device with one gets them for free. */
fun NoiseControl.quickModes(scope: CoroutineScope): StateFlow<List<QuickMode>> {
    fun list(s: NoiseUi?) = modes.map { QuickMode(it.id, it.title, it.label, it.widgetIcon, s?.active == it.id) }
    return state.map(::list).stateIn(scope, SharingStarted.Eagerly, list(state.value))
}

// ---- battery ----------------------------------------------------------------------------------------------------

/** One battery the device reports: its only one, or a bud, or the case. */
data class BatteryLevel(val level: Int, val charging: Boolean = false, val part: BatteryPart = BatteryPart.MAIN)

enum class BatteryPart(val label: String) { MAIN("Battery"), LEFT("Left"), RIGHT("Right"), CASE("Case") }

// ---- settings ---------------------------------------------------------------------------------------------------

/** Which shared page a settings group goes on. */
enum class Tab { LISTEN, SOUND, DEVICE }

/**
 * A titled group of settings rows the shared pages draw the same way for every device. A device lists what it has;
 * pages, sheets and confirmations are the shared UI's.
 */
data class SettingsGroup(
    /** Section header; null for a group without one. */
    val title: String?,
    val items: List<Setting>,
    val tab: Tab = Tab.DEVICE,
    /** A line under the group (a hint, or what is wrong). */
    val note: String? = null,
    val noteIsError: Boolean = false,
)

/** Asked before a change goes through ("the headphones reconnect"). */
data class Confirm(val title: String, val message: String, val button: String = "Change")

sealed interface Setting {
    val icon: ImageVector
    val title: String
    val enabled: Boolean

    data class Toggle(
        override val icon: ImageVector,
        override val title: String,
        val summary: String?,
        val checked: Boolean,
        override val enabled: Boolean = true,
        /** Asked first for the new value; null = no question. */
        val confirm: ((Boolean) -> Confirm?)? = null,
        val onChange: (Boolean) -> Unit,
    ) : Setting

    /** One of a few values, picked in a sheet. */
    data class Choice(
        override val icon: ImageVector,
        override val title: String,
        val options: List<Pair<Int, String>>,
        val selected: Int?,
        override val enabled: Boolean = true,
        /** Under the title; by default the selected option's label. */
        val value: String? = options.firstOrNull { it.first == selected }?.second,
        val confirm: ((Int) -> Confirm?)? = null,
        val onSelect: (Int) -> Unit,
    ) : Setting

    /** Opens something or runs something (a test, a page). */
    data class Action(
        override val icon: ImageVector,
        override val title: String,
        val summary: String?,
        override val enabled: Boolean = true,
        /** Running: a spinner instead of the icon. */
        val busy: Boolean = false,
        /** A button at the end instead of the chevron ("Optimize", "Cancel"). */
        val button: String? = null,
        val onClick: () -> Unit,
    ) : Setting
}
