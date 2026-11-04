package com.occaecat.hush.sony

import com.occaecat.hush.HeadphonesController
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.Phase
import com.occaecat.hush.devices.NoiseControl
import com.occaecat.hush.devices.NoiseKind
import com.occaecat.hush.devices.NoiseLevel
import com.occaecat.hush.devices.NoiseMode
import com.occaecat.hush.devices.NoiseUi
import com.occaecat.hush.mdr.AmbientMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * NC / Ambient / Off over MDR's NCASM function, shaped by what the headset reported (NCASM_RET_CAPABILITY): NC-only
 * models get NC and Off, ambient-only models Ambient and Off; the ambient level and Focus on voice sit under
 * Ambient when the headset has them.
 */
class SonyNoise(private val vm: HeadphonesController, private val source: StateFlow<HeadphonesState>, scope: CoroutineScope) : NoiseControl {
    // Sound Connect's shortcut glyphs: a person inside a solid ring (NC), a dotted ring (ambient), a bare ring (off).
    private val nc = NoiseMode(AmbientMode.NOISE_CANCELLING.name, "ANC", NoiseKind.CANCELLING, R.drawable.ic_mode_nc, R.drawable.ic_w_nc, "Noise cancelling")
    private val off = NoiseMode(AmbientMode.OFF.name, "Off", NoiseKind.OFF, R.drawable.ic_mode_off, R.drawable.ic_w_off)
    private fun ambient(levels: IntRange?, options: Boolean) = NoiseMode(
        AmbientMode.AMBIENT.name, "Ambient", NoiseKind.AMBIENT, R.drawable.ic_mode_ambient, R.drawable.ic_w_ambient, "Ambient sound",
        hasOptions = options, levelRange = levels,
    )

    private fun levels(s: HeadphonesState): IntRange? {
        val caps = s.ncAsmCaps ?: return 1..20
        return if (caps.asmLevels && caps.asmSteps > 1) 1..caps.asmSteps else null
    }

    override val modes: List<NoiseMode>
        get() {
            val s = source.value
            val levels = levels(s)
            val voice = s.ncAsmCaps?.voice ?: true
            return s.noiseModes().map {
                when (it) {
                    AmbientMode.NOISE_CANCELLING -> nc
                    AmbientMode.AMBIENT -> ambient(levels, levels != null || voice)
                    AmbientMode.OFF -> off
                }
            }
        }

    override val state: StateFlow<NoiseUi?> = source.map(::ui).stateIn(scope, SharingStarted.Eagerly, ui(source.value))

    private fun ui(s: HeadphonesState): NoiseUi? {
        if (s.phase != Phase.CONNECTED && s.phase != Phase.RECONNECTING) return null
        val nc = s.ncAsm ?: return null
        val levels = levels(s)
        return NoiseUi(
            nc.mode.name,
            levels?.let { NoiseLevel("Ambient level", nc.ambientLevel.coerceIn(it), it) },
            nc.voiceFocus.takeIf { s.ncAsmCaps?.voice ?: true },
        )
    }

    override fun setMode(id: String) { runCatching { AmbientMode.valueOf(id) }.getOrNull()?.let(vm::setAmbientMode) }
    override fun setLevel(level: Int, dragging: Boolean) = vm.setAmbientLevel(level, dragging = dragging)
    override fun setVoiceFocus(on: Boolean) = vm.setVoiceFocus(on)

    /** One NCASM write for the mode, level and voice together. */
    override fun apply(id: String, level: Int?, voiceFocus: Boolean?) {
        val mode = runCatching { AmbientMode.valueOf(id) }.getOrNull() ?: return
        if (mode == AmbientMode.AMBIENT) vm.setNoiseControl(mode, level, voiceFocus) else vm.setNoiseControl(mode)
    }
}
