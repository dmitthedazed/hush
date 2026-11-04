package com.occaecat.hush.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractTest {
    private class Fake(override val noise: NoiseControl?) : DeviceController {
        override val state: StateFlow<DeviceState> = MutableStateFlow(DeviceState("Buds", Connection.CONNECTED, subtitle = null))
        @Composable override fun Render(modifier: Modifier, connected: Boolean) {}
    }

    private fun noise(active: String, vararg ids: String, source: Source = Source.DEVICE) = object : NoiseControl {
        override val providedBy get() = source
        override val modes = ids.map { NoiseMode(it, it, NoiseKind.OFF, 0) }
        override val state: StateFlow<NoiseUi?> = MutableStateFlow(NoiseUi(active))
        override fun setMode(id: String) {}
    }

    @Test fun aWellBehavedControllerPasses() = assertEquals(emptyList<String>(), Contract.check(Fake(noise("nc", "nc", "off"))))

    @Test fun unknownActiveModeIsCaught() = assertTrue(Contract.check(Fake(noise("asm", "nc", "off"))).single().contains("asm"))

    @Test fun duplicateModeIdsAreCaught() = assertTrue(Contract.check(Fake(noise("nc", "nc", "nc"))).any { "twice" in it })

    @Test fun phoneNoiseCancellingIsRefused() =
        assertTrue(Contract.check(Fake(noise("nc", "nc", source = Source.PHONE))).any { "can't cancel noise" in it })
}
