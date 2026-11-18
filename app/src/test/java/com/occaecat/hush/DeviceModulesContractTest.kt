package com.occaecat.hush

import com.occaecat.hush.devices.Contract
import com.occaecat.hush.devices.DeviceRegistry
import com.occaecat.hush.devices.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

/** Every brand Hush ships keeps the module contract; a new brand is checked the moment it is listed. */
class DeviceModulesContractTest {
    @Test
    fun everyModuleKeepsTheContract() = assertEquals(emptyList<String>(), Brands.all.flatMap(Contract::check))

    @Test
    fun moduleIdsAreUnique() = assertEquals(Brands.all.size, Brands.all.map { it.id }.toSet().size)

    @Test
    fun startupSkipsSavedDevicesForUnavailableModules() {
        val unavailable = DeviceRegistry.Entry(
            "unavailable:headset", "unavailable", "headset", "Other headset", Transport.BLUETOOTH, null, 1,
        )
        val sony = DeviceRegistry.Entry(
            "sony:00:11:22:33:44:55", "sony", "wh-1000xm4", "WH-1000XM4",
            Transport.BLUETOOTH, "00:11:22:33:44:55", 2,
        )

        val devices = DeviceModules.merge(listOf(unavailable, sony), live = emptyList())

        assertEquals(listOf(sony.key), devices.map { it.key })
        assertEquals(sony.key, DeviceModules.pick(devices)?.key)
        assertNull(DeviceModules.pick(DeviceModules.merge(listOf(unavailable), live = emptyList())))
    }

    companion object {
        /** Modules start their state flows on the main thread when first touched. */
        @OptIn(ExperimentalCoroutinesApi::class)
        @BeforeClass @JvmStatic
        fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())
    }
}
