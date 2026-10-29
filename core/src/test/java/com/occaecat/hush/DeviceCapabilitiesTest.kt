package com.occaecat.hush

import com.occaecat.hush.mdr.EqBandSpec
import com.occaecat.hush.mdr.EqCapability
import org.junit.Assert.*
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test fun renamedDevicesKeepTheirOriginalBrand() {
        assertEquals(HeadphoneBrand.JABRA, headphoneBrand("Jabra Elite 10", "My headphones"))
        assertEquals(HeadphoneBrand.JABRA, headphoneBrand("Jabra Elite 85t", null))
        assertEquals(HeadphoneBrand.JABRA, headphoneBrand("Konrad Elite 10", "Konrad Elite 10"))
        assertEquals(HeadphoneBrand.JABRA, headphoneBrand("Travel Elite 85t", null))
        assertEquals(HeadphoneBrand.UNKNOWN, headphoneBrand("Elite 100", null))
        assertEquals(HeadphoneBrand.SONY, headphoneBrand("WH-1000XM4", "Commute"))
        assertEquals(HeadphoneBrand.UNKNOWN, headphoneBrand("Elite gaming keyboard", null))
    }

    @Test fun equalBandCountDoesNotMakeVendorCurvesCompatible() {
        val sony = EqCapability(5, 21, emptyList())
        val jabra = EqCapability(5, 721, emptyList(), "jabra-gnp",
            List(5) { EqBandSpec("Band $it", 100.0 * (it + 1), -360, 360, 0, 60.0, 30) }, true)
        assertNotEquals(sony.layoutId, jabra.layoutId)
        assertTrue(jabra.supports(listOf(-360, 0, 30, 60, 360)))
        assertFalse(jabra.supports(listOf(-361, 0, 30, 60, 360)))
        assertFalse(jabra.supports(listOf(0, 0)))
        assertNotEquals(jabra.layoutId, jabra.copy(bandSpecs = jabra.bandSpecs.mapIndexed { i, b ->
            if (i == 0) b.copy(frequencyHz = 120.0) else b
        }).layoutId)
    }

    @Test fun rawGainScalingPreservesSonyAndJabraUnits() {
        val sony = EqBandSpec.sony(6, 21)[1]
        val jabra = EqBandSpec("1k", 1000.0, -360, 360, 0, 60.0, 30)
        assertEquals(0.0, sony.toDb(10), 0.0)
        assertEquals(12, sony.fromDb(2.0))
        assertEquals(-1.25, jabra.toDb(-75), 0.0)
        assertEquals(90, jabra.fromDb(1.5))
        assertEquals(360, jabra.fromDb(20.0))
        assertEquals(-360, jabra.fromDb(-20.0))
    }
}
