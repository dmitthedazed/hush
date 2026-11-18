package com.occaecat.hush.jabra

import com.occaecat.hush.devices.DeviceRegistry
import com.occaecat.hush.devices.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JabraAudioDeviceTest {
    private val elite = entry("jabra", "00:11:22:33:44:AA")
    private val bleBond = entry("jabra", "00:11:22:33:44:BB")
    private val sony = entry("sony", "00:11:22:33:44:CC")

    @Test
    fun selectsSavedAudioEndpointWithoutPriorSelection() {
        val entries = listOf(bleBond, sony, elite)
        assertEquals(elite, savedJabraAudioDevice(entries, listOf("00:11:22:33:44:aa"), selected = null))
    }

    @Test
    fun onlyRegisteredJabraAudioEndpointsCanStartASession() {
        assertNull(savedJabraAudioDevice(listOf(elite, sony), listOf(sony.address!!, bleBond.address!!), selected = null))
    }

    @Test
    fun keepsSelectedDeviceWhenBothAudioLinksAreAlreadyConnected() {
        val entries = listOf(elite, bleBond)
        assertEquals(bleBond, savedJabraAudioDevice(entries, entries.mapNotNull { it.address }, selected = bleBond.address))
    }

    private fun entry(module: String, address: String) =
        DeviceRegistry.Entry("$module:$address", module, "headset", "Headset", Transport.BLUETOOTH, address, 0)
}
