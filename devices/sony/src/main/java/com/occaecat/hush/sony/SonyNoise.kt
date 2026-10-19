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

/** NC / Ambient / Off over MDR's NCASM function; the ambient level (1–20) and Focus on voice sit under Ambient. */
class SonyNoise(private val vm: HeadphonesController, source: StateFlow<HeadphonesState>, scope: CoroutineScope) : NoiseControl {
    // Sound Connect's shortcut glyphs: a person inside a solid ring (NC), a dotted ring (ambient), a bare ring (off).
    override val modes = listOf(
        NoiseMode(AmbientMode.NOISE_CANCELLING.name, "ANC", NoiseKind.CANCELLING, R.drawable.ic_mode_nc, R.drawable.ic_w_nc, "Noise cancelling"),
        NoiseMode(AmbientMode.AMBIENT.name, "Ambient", NoiseKind.AMBIENT, R.drawable.ic_mode_ambient, R.drawable.ic_w_ambient, "Ambient sound",
            hasOptions = true),
        NoiseMode(AmbientMode.OFF.name, "Off", NoiseKind.OFF, R.drawable.ic_mode_off, R.drawable.ic_w_off),
    )

    override val state: StateFlow<NoiseUi?> = source.map(::ui).stateIn(scope, SharingStarted.Eagerly, ui(source.value))

    private fun ui(s: HeadphonesState): NoiseUi? {
        if (s.phase != Phase.CONNECTED && s.phase != Phase.RECONNECTING) return null
        val nc = s.ncAsm ?: return null
        return NoiseUi(nc.mode.name, NoiseLevel("Ambient level", nc.ambientLevel, 1..20), nc.voiceFocus)
    }

    override fun setMode(id: String) { runCatching { AmbientMode.valueOf(id) }.getOrNull()?.let(vm::setAmbientMode) }
    override fun setLevel(level: Int, dragging: Boolean) = vm.setAmbientLevel(level, dragging = dragging)
    override fun setVoiceFocus(on: Boolean) = vm.setVoiceFocus(on)
}
