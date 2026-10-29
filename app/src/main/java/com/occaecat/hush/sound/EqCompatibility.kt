package com.occaecat.hush.sound

import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.mdr.EqCapability

/** Old files carry Sony raw levels; never interpret their band count as a Jabra layout. */
fun SavedEq.compatibleLayout(caps: EqCapability?): String? {
    caps ?: return null
    if (!caps.supports(levels)) return null
    if (layoutId == caps.layoutId) return caps.layoutId
    if (layoutId == legacySonyLayout(levels.size) && caps.namespace == "sony-mdr" &&
        caps.bandSpecs.all { it.min == 0 && it.max == 20 && it.neutral == 10 && it.unitsPerDb == 1.0 }) {
        return caps.layoutId
    }
    return null
}

fun HeadphonesController.applySavedEq(eq: SavedEq): Boolean {
    val layout = eq.compatibleLayout(state.value.eqCaps) ?: return false
    return applyEqBands(eq.levels, layout)
}

/** Check before recording an automation restore point: a skipped choice must have no later side effects. */
internal fun canApplyEq(choice: EqChoice, config: SoundConfig, caps: EqCapability?): Boolean {
    caps ?: return false
    return when (choice) {
        is EqChoice.Preset -> caps.namespace == "sony-mdr" && choice.namespace == caps.namespace &&
            (choice.id == 0 || caps.presets.any { it.id == choice.id })
        is EqChoice.Library -> config.library.firstOrNull { it.id == choice.id }?.compatibleLayout(caps) != null
    }
}

val HeadphonesState.noiseNamespace: String get() = if (isJabra) "jabra-gnp" else "sony-mdr"

/** Shared modes cross brands, but vendor-specific ambient scales and voice focus do not. */
fun HeadphonesController.applyNoise(action: SoundAction) {
    val mode = action.mode ?: return
    val st = state.value
    val matchingRange = if (action.ambientMin == null && action.ambientMax == null) st.isSony
        else action.ambientMin == st.capabilities.ambientMin && action.ambientMax == st.capabilities.ambientMax
    val matchingScale = action.noiseNamespace == st.noiseNamespace && matchingRange
    setNoiseControl(mode, action.ambientLevel.takeIf { matchingScale }, action.voiceFocus.takeIf { matchingScale && st.capabilities.voiceFocus })
}
