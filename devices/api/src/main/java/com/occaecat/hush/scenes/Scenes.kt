package com.occaecat.hush.scenes

import com.occaecat.hush.devices.DeviceController
import com.occaecat.hush.devices.EqLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A connected device scenes and automation can act on. */
data class SceneTarget(val key: String, val moduleId: String, val controller: DeviceController)

/**
 * Applies scenes to any device through the shared capabilities (noise control, equalizer, volume, scene switches)
 * and remembers which one is on per device. Tapping the active scene turns it off and puts back what was before.
 */
object Scenes {
    private val _active = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Device key → active scene id. */
    val active: StateFlow<Map<String, String>> = _active

    /** Connected devices, kept by the app (modules can't see the device list). */
    var targets: StateFlow<List<SceneTarget>> = MutableStateFlow(emptyList())

    /** What a device was doing before its first scene went on. */
    private data class Before(val noise: com.occaecat.hush.devices.NoiseUi?, val eq: Any?, val volume: Int?, val switches: Map<String, Boolean>)

    private val before = mutableMapOf<String, Before>()

    fun toggle(key: String, c: DeviceController, scene: Scene) {
        if (_active.value[key] == scene.id) restore(key, c) else apply(key, c, scene)
    }

    fun apply(key: String, c: DeviceController, scene: Scene) {
        // Going from one scene straight to another keeps the original state, so turning off returns to it.
        if (key !in _active.value) before[key] = snapshot(c)
        perform(c, scene.action)
        _active.update { it + (key to scene.id) }
    }

    /** The next scene on the connected device of [moduleId] (a headset button stepping through them). */
    fun next(moduleId: String) {
        val t = targets.value.firstOrNull { it.moduleId == moduleId } ?: return
        val scenes = SceneStore.device(SceneStore.config.value, t.key, t.controller).scenes
        if (scenes.isEmpty()) return
        val i = scenes.indexOfFirst { it.id == _active.value[t.key] }
        apply(t.key, t.controller, scenes[(i + 1) % scenes.size])
    }

    /** A manual change elsewhere means no scene is "on" any more (and there's nothing to go back to). */
    fun clear(key: String) {
        _active.update { it - key }
        before.remove(key)
    }

    private fun restore(key: String, c: DeviceController) {
        val b = before.remove(key)
        _active.update { it - key }
        b ?: return
        b.noise?.let { n -> if (n != c.noise?.state?.value) c.noise?.apply(n.active, n.level?.value, n.voiceFocus) }
        c.equalizer?.restore(b.eq)
        b.volume?.takeIf { it != c.state.value.volume }?.let(c::setVolume)
        c.sceneSwitches?.let { sw -> b.switches.forEach { (id, v) -> if (sw.get(id) != v) sw.set(id, v) } }
    }

    private fun snapshot(c: DeviceController) = Before(
        noise = c.noise?.state?.value,
        eq = c.equalizer?.snapshot(),
        volume = c.state.value.volume,
        switches = c.sceneSwitches?.let { sw -> sw.list.mapNotNull { s -> sw.get(s.id)?.let { s.id to it } }.toMap() }.orEmpty(),
    )

    /** Sends [a] to the device; parts it can't do are skipped. */
    fun perform(c: DeviceController, a: SceneAction) {
        a.noise?.let { id -> c.noise?.takeIf { n -> n.modes.any { it.id == id } }?.apply(id, a.level, a.voiceFocus) }
        performEq(c, a.eq)
        a.volume?.takeIf { c.state.value.volumeSteps > 0 }?.let { c.setVolume(it.coerceIn(0, c.state.value.volumeSteps - 1)) }
        c.sceneSwitches?.let { sw -> a.switches.forEach { (id, v) -> if (sw.get(id) != null && sw.get(id) != v) sw.set(id, v) } }
    }

    fun performEq(c: DeviceController, pick: EqPick?) {
        val eq = c.equalizer ?: return
        when (pick) {
            is EqPick.Preset -> { eq.toggle?.invoke(true); eq.selectPreset(pick.id) }
            is EqPick.Library -> EqLibrary.items.value.firstOrNull { it.id == pick.id && it.layout == eq.layout }?.let {
                eq.toggle?.invoke(true); eq.applyLevels(it.levels)
            }
            EqPick.Off -> eq.toggle?.invoke(false)
            null -> {}
        }
    }
}
