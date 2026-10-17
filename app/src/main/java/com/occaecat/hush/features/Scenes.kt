package com.occaecat.hush.features

import android.content.Context
import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.sound.Scene
import com.occaecat.hush.sound.SoundAutomation
import com.occaecat.hush.sound.SoundStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Applies a [Scene] in one go and remembers which one is on (for the chips and "next scene"). */
object Scenes {
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active

    fun apply(context: Context, scene: Scene) {
        val c = HeadphonesController.get(context)
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

    /** A manual noise-mode change elsewhere means no scene is "on" any more. */
    fun clear() { _active.value = null }
}
