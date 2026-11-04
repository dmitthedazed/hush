package com.occaecat.hush

import com.occaecat.hush.devices.Contract
import com.occaecat.hush.devices.Device
import com.occaecat.hush.devices.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

/** Every brand Hush ships keeps the module contract; a new brand is checked the moment it is listed. */
class DeviceModulesContractTest {
    @Test
    fun everyModuleKeepsTheContract() = assertEquals(emptyList<String>(), Brands.all.flatMap(Contract::check))

    @Test
    fun moduleIdsAreUnique() = assertEquals(Brands.all.size, Brands.all.map { it.id }.toSet().size)

    @Test
    fun savedBluetoothSelectionSurvivesStartup() {
        val sony = Device("sony:one", "sony", "Sony", Transport.BLUETOOTH, active = true)
        val jabra = Device("jabra:85t", "jabra", "Elite 85t", Transport.BLUETOOTH, active = false)
        assertEquals(jabra, DeviceModules.preferred(listOf(sony, jabra), jabra.key))
        assertEquals(sony, DeviceModules.preferred(listOf(sony, jabra), "removed"))

        val wired = Device("apple:usb", "apple", "EarPods", Transport.USB, active = true)
        assertEquals(wired, DeviceModules.preferred(listOf(sony, jabra, wired), jabra.key))
    }

    companion object {
        /** Modules start their state flows on the main thread when first touched. */
        @OptIn(ExperimentalCoroutinesApi::class)
        @BeforeClass @JvmStatic
        fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())
    }
}
