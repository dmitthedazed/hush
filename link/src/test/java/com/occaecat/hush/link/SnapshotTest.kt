package com.occaecat.hush.link

import com.occaecat.hush.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotTest {
    @Test fun optionalDeviceMetadataRoundTrips() {
        val snapshot = Snapshot(
            phase = Phase.CONNECTED, name = "Example earbuds", battery = 72,
            brand = "example", capabilities = mapOf("NOISE" to "DEVICE", "VOLUME" to "PHONE"),
            components = listOf(Snapshot.ComponentBattery("MAIN", 72, false, "DEVICE"),
                Snapshot.ComponentBattery("CASE", 90, true, "DEVICE")),
            eq = Snapshot.EqMetadata("example-5", listOf("60", "250"), listOf(2, -1), -6, 6, true),
        )
        assertEquals(snapshot, Snapshot.fromJson(snapshot.toJson()))
    }

    @Test fun oldSonyMessageStillDecodesWithMissingOptionalFields() {
        val old = """{"phase":"CONNECTED","name":"WH-1000XM4","battery":80,"mode":"NOISE_CANCELLING","dseeAuto":true,"eqPresets":[0,1]}"""
        val decoded = Snapshot.fromJson(old)!!
        assertEquals("WH-1000XM4", decoded.name)
        assertEquals(listOf(Snapshot.Toggle("dsee", "DSEE Extreme", true)), decoded.toggles)
        assertEquals(listOf("0", "1"), decoded.eqPresets.map { it.id })
        assertNull(decoded.brand)
        assertEquals(emptyMap<String, String>(), decoded.capabilities)
        assertEquals(emptyList<Snapshot.ComponentBattery>(), decoded.components)
        assertNull(decoded.eq)
    }
}
