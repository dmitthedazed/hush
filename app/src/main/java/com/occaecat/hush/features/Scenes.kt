package com.occaecat.hush.features

import android.content.Context
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.Phase
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.sound.Scene
import com.occaecat.hush.sound.SoundAutomation
import com.occaecat.hush.sound.SoundStore
import com.occaecat.hush.sound.applyNoise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Applies a [Scene] in one go and remembers which one is on (for the chips and "next scene"). */
object Scenes {
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active

    /** What the headphones were doing before the first scene went on; tapping the active scene puts it back. */
    private data class Before(val address: String?, val layout: String?, val nc: NcAsmState?, val eq: EqState?, val speakToChat: Boolean?, val volume: Int?, val changedEq: Boolean = false)

    private var before: Before? = null
    private var observingDevice = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun observeDevice(controller: HeadphonesController) {
        if (observingDevice) return
        observingDevice = true
        scope.launch { controller.state.map { it.address }.distinctUntilChanged().collect(::forDevice) }
    }

    /** Tap on a scene chip: switches it on, or, if it is already on, switches it off and restores what was before. */
    fun toggle(context: Context, scene: Scene) {
        val controller = HeadphonesController.get(context)
        observeDevice(controller)
        forDevice(controller.state.value.address)
        if (_active.value == scene.id) restore(context) else apply(context, scene)
    }

    fun apply(context: Context, scene: Scene) {
        val c = HeadphonesController.get(context)
        if (c.state.value.phase != Phase.CONNECTED) return
        observeDevice(c)
        if (before?.address != c.state.value.address) clear()
        // Going from one scene straight to another keeps the original state, so turning off returns to it.
        if (_active.value == null) c.state.value.let { st -> before = Before(st.address, st.eqCaps?.layoutId, st.ncAsm, st.eq, st.speakToChat, st.volume) }
        val a = scene.action
        c.applyNoise(a)
        a.eq?.let {
            if (SoundAutomation.applyEq(c, SoundStore.config.value, it)) before = before?.copy(changedEq = true)
        }
        scene.speakToChat?.let { if (c.state.value.speakToChat != null) c.setSpeakToChat(it) }
        scene.volume?.takeIf { c.state.value.volume != null }?.let {
            c.setVolume((it.toDouble() / (scene.volumeSteps - 1).coerceAtLeast(1) * (c.state.value.volumeSteps - 1)).roundToInt())
        }
        _active.value = scene.id
    }

    /** The headphones' hold gesture can step through the scenes. */
    fun next(context: Context) {
        val scenes = SoundStore.config.value.scenes
        if (scenes.isEmpty()) return
        val i = scenes.indexOfFirst { it.id == _active.value }
        apply(context, scenes[(i + 1) % scenes.size])
    }

    private fun restore(context: Context) {
        val c = HeadphonesController.get(context)
        val b = before
        before = null
        _active.value = null
        b ?: return
        val now = c.state.value
        if (b.address != now.address) return
        b.nc?.takeIf { it != now.ncAsm }?.let { c.setNoiseControl(it.mode, it.ambientLevel, it.voiceFocus) }
        b.eq?.takeIf { b.changedEq && it != now.eq }?.let { eq ->
            b.layout?.let { c.restoreEq(eq, it, b.address) }
        }
        b.speakToChat?.takeIf { it != now.speakToChat }?.let(c::setSpeakToChat)
        b.volume?.takeIf { it != now.volume }?.let(c::setVolume)
    }

    /** A manual noise-mode change elsewhere means no scene is "on" any more (and there's nothing to go back to). */
    fun clear() { _active.value = null; before = null }

    fun forDevice(address: String?) { if (before != null && before?.address != address) clear() }
}
