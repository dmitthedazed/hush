package com.occaecat.hush.ui

import com.occaecat.hush.devices.Model
import com.occaecat.hush.devices.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchModelTest {
    private fun bt(name: String) = "sony" to Model(name.lowercase(), name, Transport.BLUETOOTH, "")
    private val models = listOf(bt("WH-1000XM4"), bt("LinkBuds"), bt("LinkBuds S"), "apple" to Model("earpods", "EarPods", Transport.USB, ""))

    @Test fun exactName() = assertEquals("WH-1000XM4", matchModel("WH-1000XM4", models)?.second?.name)

    @Test fun renamedAliasStillMatches() = assertEquals("WH-1000XM4", matchModel("Dima's wh-1000xm4", models)?.second?.name)

    @Test fun longestNameWins() = assertEquals("LinkBuds S", matchModel("LinkBuds S", models)?.second?.name)

    @Test fun onlyBluetoothModels() = assertNull(matchModel("EarPods", models))

    @Test fun unknownHeadset() = assertNull(matchModel("Galaxy Buds2 Pro", models))
}
