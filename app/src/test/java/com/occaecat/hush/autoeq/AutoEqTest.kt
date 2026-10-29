package com.occaecat.hush.autoeq

import com.occaecat.hush.mdr.EqBandSpec
import com.occaecat.hush.mdr.EqCapability
import org.junit.Assert.*
import org.junit.Test

class AutoEqTest {
    private val curve = (0..99).map { i -> AutoEq.Point(20.0 * Math.pow(1000.0, i / 99.0), (i - 50) * 0.4) }

    @Test fun jabraFitUsesReportedFrequenciesGainBoundsAndSteps() {
        val caps = EqCapability(3, 0, emptyList(), namespace = "jabra-gnp", bandSpecs = listOf(
            EqBandSpec("Bass", 100.0, -120, 120, 0, 60.0, 30),
            EqBandSpec("Mid", 1000.0, -360, 360, 0, 60.0, 30),
            EqBandSpec("Treble", 10000.0, -180, 180, 0, 60.0, 30),
        ))
        val fit = AutoEq.fit(curve, caps)!!
        assertTrue(fit.approximate)
        assertTrue(fit.rmsError.isNaN())
        assertEquals(listOf(100.0, 1000.0, 10000.0), fit.result.map { it.hz })
        assertTrue(caps.supports(fit.levels))
        assertTrue(fit.levels.all { it % 30 == 0 })
        assertEquals(-120, fit.levels.first())
        assertEquals(180, fit.levels.last())
    }

    @Test fun sonyResponseModelRemainsUnchanged() {
        val legacy = AutoEq.fit(curve, 6)!!
        val capability = AutoEq.fit(curve, EqCapability(6, 21, emptyList()))!!
        assertEquals(legacy, capability)
        assertFalse(capability.approximate)
    }

    @Test fun unknownFrequenciesAreNotAssumedToBeSonyBands() {
        val caps = EqCapability(6, 21, emptyList(), namespace = "jabra-gnp")
        assertNull(AutoEq.fit(curve, caps))
    }

    @Test fun sonyBandCountAloneDoesNotAuthorizeTheStandardGainScale() {
        assertNull(AutoEq.fit(curve, EqCapability(6, 41, emptyList())))
        assertNull(AutoEq.fit(curve, EqCapability(5, 21, emptyList())))
    }
}
