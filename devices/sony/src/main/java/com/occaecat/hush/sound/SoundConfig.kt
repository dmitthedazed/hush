package com.occaecat.hush.sound

import com.occaecat.hush.devices.EqLibrary

/** A named EQ curve kept on the phone, in the headset's raw levels (0..20); stored in the shared [EqLibrary]. */
data class SavedEq(val id: String, val name: String, val levels: List<Int>, val source: String? = null)

/** The XM4's equalizer layout in the shared EQ library: Clear Bass + five bands, dB. */
const val SONY_LAYOUT = "sony-6"

/**
 * The XM4's view of the shared EQ library, in raw levels. Scenes, Adaptive Sound Control and app profiles are shared
 * by every device now (`com.occaecat.hush.scenes`); Hush 0.17's `sound.json` migrates there.
 */
object SoundStore {
    /** The library curves this headset can play, in raw levels. */
    val library: List<SavedEq>
        get() = EqLibrary.forLayout(SONY_LAYOUT).map { SavedEq(it.id, it.name, it.levels.map { l -> l + 10 }, it.source) }

    fun saveEq(name: String, levels: List<Int>, source: String? = null): SavedEq {
        val c = EqLibrary.save(name, SONY_LAYOUT, levels.map { it - 10 }, source)
        return SavedEq(c.id, c.name, levels, source)
    }

    fun renameEq(id: String, name: String) = EqLibrary.rename(id, name)

    fun deleteEq(id: String) = EqLibrary.delete(id)
}
