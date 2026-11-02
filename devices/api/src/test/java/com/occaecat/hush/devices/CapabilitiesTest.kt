package com.occaecat.hush.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {
    private class Fake(state: DeviceState, override val noise: NoiseControl? = null) : DeviceController {
        override val state: StateFlow<DeviceState> = MutableStateFlow(state)
        @Composable override fun Render(modifier: Modifier, connected: Boolean) {}
    }

    private val connected = DeviceState("Buds", Connection.CONNECTED, subtitle = null)

    private val hardwareNoise = object : NoiseControl {
        override val modes = emptyList<NoiseMode>()
        override val state: StateFlow<NoiseUi?> = MutableStateFlow(null)
        override fun setMode(id: String) {}
    }

    @Test
    fun bareHeadsetHasOnlyPhoneFeatures() {
        val caps = Fake(connected).capabilities
        assertFalse(Feature.NOISE in caps)
        assertFalse(Feature.EQUALIZER in caps)
        assertFalse(Feature.BATTERY in caps)
        assertEquals(Source.PHONE, caps.source(Feature.VOLUME))
        assertEquals(Source.PHONE, caps.source(Feature.SLEEP_TIMER))
    }

    @Test
    fun hardwareAndEmulatedAreTold() {
        val s = connected.copy(volumeSteps = 30, batteries = listOf(BatteryLevel(70, providedBy = Source.PHONE)))
        val caps = Fake(s, hardwareNoise).capabilities
        assertEquals(Source.DEVICE, caps.source(Feature.NOISE))
        assertEquals(Source.DEVICE, caps.source(Feature.VOLUME))
        assertTrue(caps.emulated(Feature.BATTERY))
        assertTrue(Feature.SCENES in caps)
    }
}
