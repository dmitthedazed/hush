package com.occaecat.hush

import com.occaecat.hush.devices.Contract
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

    companion object {
        /** Modules start their state flows on the main thread when first touched. */
        @OptIn(ExperimentalCoroutinesApi::class)
        @BeforeClass @JvmStatic
        fun mainThread() = Dispatchers.setMain(UnconfinedTestDispatcher())
    }
}
