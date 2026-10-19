package com.occaecat.hush.features

import android.content.Context
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.EqState
import com.occaecat.hush.mdr.NcAsmState
import com.occaecat.hush.sound.Scene
import com.occaecat.hush.sound.SoundAutomation
import com.occaecat.hush.sound.SoundStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Applies a [Scene] in one go and remembers which one is on (for the chips and "next scene"). */
object Scenes {
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active

    /** What the headphones were doing before the first scene went on; tapping the active scene puts it back. */
    private data class Before(val nc: NcAsmState?, val eq: EqState?, val speakToChat: Boolean?, val volume: Int?)

    private var before: Before? = null

    /** Tap on a scene chip: switches it on, or, if it is already on, switches it off and restores what was before. */
    fun toggle(context: Context, scene: Scene) {
        if (_active.value == scene.id) restore(context) else apply(context, scene)
    }

    fun apply(context: Context, scene: Scene) {
        val c = HeadphonesController.get(context)
        // Going from one scene straight to another keeps the original state, so turning off returns to it.
        if (_active.value == null) c.state.value.let { st -> before = Before(st.ncAsm, st.eq, st.speakToChat, st.volume) }
        val a = scene.action
        a.mode?.let { mode ->
            if (mode == AmbientMode.AMBIENT) c.setNoiseControl(mode, a.ambientLevel, a.voiceFocus) else c.setNoiseControl(mode)
        }
        a.eq?.let { SoundAutomation.applyEq(c, SoundStore.config.value, it) }
        scene.speakToChat?.let { if (c.state.value.speakToChat != null) c.setSpeakToChat(it) }
        scene.volume?.let { c.setVolume(it) }
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
        b.nc?.takeIf { it != now.ncAsm }?.let { c.setNoiseControl(it.mode, it.ambientLevel, it.voiceFocus) }
        b.eq?.takeIf { it != now.eq }?.let { eq ->
            // A library curve is written into Custom; if Custom was the active preset, put its old bands back too.
            if (eq.presetId == HeadphonesController.CUSTOM_PRESET && eq.bands.isNotEmpty()) c.writeEqPreset(eq.presetId, eq.bands)
            else c.selectEqPreset(eq.presetId)
        }
        b.speakToChat?.takeIf { it != now.speakToChat }?.let(c::setSpeakToChat)
        b.volume?.takeIf { it != now.volume }?.let(c::setVolume)
    }

    /** A manual noise-mode change elsewhere means no scene is "on" any more (and there's nothing to go back to). */
    fun clear() { _active.value = null; before = null }
}
