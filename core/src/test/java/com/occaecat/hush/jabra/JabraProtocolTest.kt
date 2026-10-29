package com.occaecat.hush.jabra

import com.occaecat.hush.mdr.AmbientMode
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class JabraProtocolTest {
    @Test fun eventSubscriptionUsesOnlySupportedBitsAndPreservesAllMaskBlocks() {
        assertArrayEquals(bytes(0, 0, 2, 0x96), JabraProtocol.eventSubscription(bytes(0, 0, 7, 0xff)))
        // Captured Elite 85t 2.0.0 supported mask: on-head bit04 is absent, ANC/HearThrough bit9 exists.
        assertArrayEquals(bytes(0, 0, 2, 0x92), JabraProtocol.eventSubscription(bytes(0, 0, 2, 0x92)))
        assertArrayEquals(bytes(0, 0, 0, 0x90, 0, 0, 0, 0),
            JabraProtocol.eventSubscription(bytes(0, 0, 1, 0x90, 0, 0, 0, 1)))
        assertNull(JabraProtocol.eventSubscription(bytes()))
        assertNull(JabraProtocol.eventSubscription(bytes(0, 2, 0x96)))
        assertNull(JabraProtocol.eventSubscription(bytes(0, 0, 0, 2, 0x96)))
        assertNull(JabraProtocol.eventSubscription(ByteArray(60)))
    }

    @Test fun acceptsOnlyTheTwoVerifiedProductIds() {
        assertEquals(JabraModel.ELITE_10, JabraProtocol.model(bytes(0x46, 0x2e)))
        assertEquals(JabraModel.ELITE_85T, JabraProtocol.model(bytes(0xcd, 0x24)))
        assertThrows(IOException::class.java) { JabraProtocol.model(bytes(0x2e, 0x46)) }
        assertThrows(IOException::class.java) { JabraProtocol.model(bytes(0x46)) }
    }

    @Test fun modeValuesAndOptionalSelectorEchoAreExplicit() {
        assertEquals(AmbientMode.NOISE_CANCELLING, JabraProtocol.mode(4))
        assertEquals(AmbientMode.AMBIENT, JabraProtocol.mode(2))
        assertEquals(AmbientMode.OFF, JabraProtocol.mode(1))
        assertNull(JabraProtocol.mode(3))
        assertEquals(4, JabraProtocol.indexedScalar(bytes(4), 1))
        assertEquals(4, JabraProtocol.indexedScalar(bytes(1, 4), 1))
        assertNull(JabraProtocol.indexedScalar(bytes(2, 4), 1))
        assertEquals(5, JabraProtocol.ambienceCapabilities(bytes(7, 5, 4, 0, 0))!!.ambientMax)
        assertEquals(5, JabraProtocol.ambienceCapabilities(bytes(0, 7, 5, 4, 0, 0))!!.ambientMax)
    }

    @Test fun parsesVariableBatteryCountsAndExcludesCaseFromAggregate() {
        val battery = JabraProtocol.battery(bytes(0x80, 90, 0, 0, 2, 0x15, 0, 10, 5, 0, 85))!!
        assertEquals(85, battery.aggregate)
        assertEquals(listOf("Earbud 1", "Case", "Earbud 2"), battery.components.map { it.label })
        assertEquals(listOf(90, 10, 85), battery.components.map { it.level })
        assertEquals(0, JabraProtocol.battery(bytes(0, 0))!!.aggregate)
        assertEquals(60, JabraProtocol.battery(bytes(0x80, 60, 0, 0, 1, 0x15, 0, 2))!!.aggregate)
        assertNull(JabraProtocol.battery(bytes(0x80, 90, 0, 0, 2, 0x15, 0, 80)))
        assertNull(JabraProtocol.battery(bytes(0x80, 90)))
    }

    @Test fun unknownPercentagesAndSecondaryChargingAreNotGuessed() {
        val battery = JabraProtocol.battery(bytes(0x81, 255, 0, 0, 2, 0x15, 0, 255, 5, 1, 70))!!
        assertEquals(70, battery.aggregate)
        assertNull(battery.components.first().level)
        assertTrue(battery.components.first().charging == true)
        assertNull(battery.components.last().charging)
        assertFalse(battery.charging)
        assertTrue(JabraProtocol.battery(bytes(1, 75))!!.charging)
    }

    @Test fun firmwareLengthCannotReadBeyondTheReply() {
        assertEquals("4.6.0", JabraProtocol.text(bytes(5) + "4.6.0".toByteArray()))
        assertNull(JabraProtocol.text(bytes(6) + "4.6.0".toByteArray()))
    }

    @Test fun autoPauseUsesTheModelSpecificPolarityAndTarget() {
        assertArrayEquals(bytes(0, 1), JabraModel.ELITE_10.pauseWrite(true))
        assertArrayEquals(bytes(0, 0), JabraModel.ELITE_10.pauseWrite(false))
        assertArrayEquals(bytes(0), JabraModel.ELITE_85T.pauseWrite(true))
        assertArrayEquals(bytes(1), JabraModel.ELITE_85T.pauseWrite(false))
        assertTrue(JabraModel.ELITE_10.parsePause(bytes(0, 1)) == true)
        assertNull(JabraModel.ELITE_10.parsePause(bytes(1, 1)))
        assertTrue(JabraModel.ELITE_85T.parsePause(bytes(0)) == true)
    }

    @Test fun equalizerEditsPreserveMasterFrequencyAndQ() {
        val eq = JabraEqualizer.parse(eqFixture())!!
        assertEquals(-60, eq.masterGain)
        assertEquals(-120, eq.bands[0].gain)
        assertEquals(125.0, eq.capability().bandSpecs[0].frequencyHz!!, 0.0)
        assertEquals(60.0, eq.capability().bandSpecs[0].unitsPerDb, 0.0)
        assertEquals(-720, eq.capability().bandSpecs[0].min)
        assertEquals(30, eq.capability().bandSpecs[0].uiStep)
        val write = eq.write(listOf(60, -60))
        assertArrayEquals(bytes(2, 0, 0xff, 0xc4, 1, 0x77, 0, 60, 0x10, 0,
            5, 0xdc, 0xff, 0xc4, 0x20, 0), write)
        assertThrows(IllegalArgumentException::class.java) { eq.write(listOf(0)) }
        assertThrows(IllegalArgumentException::class.java) { eq.write(listOf(721, 0)) }
    }

    @Test fun malformedEqualizerDoesNotCreateWritableCapabilities() {
        assertNull(JabraEqualizer.parse(eqFixture().copyOf(10)))
        assertNull(JabraEqualizer.parse(bytes(6, 0, 0) + ByteArray(42)))
        assertNull(JabraEqualizer.parse(eqFixture().also { it[3] = 0 }))
        assertNull(JabraEqualizer.parse(eqFixture().also { it[4] = 0; it[5] = 1 }))
    }

    @Test fun capturedElite10CurvePreservesUnroundedGainsAndItsActualFrequencies() {
        val eq = JabraEqualizer.parse(elite10CapturedEq())!!
        assertEquals(listOf(270, -118, 0, 0, 0), eq.bands.map { it.gain })
        assertEquals(listOf(60.0, 250.0, 1000.0, 4000.0, 7600.0), eq.capability().bandSpecs.map { it.frequencyHz })
        assertEquals(listOf(450, 360, 360, 360, 600), eq.bands.map { it.maxGain })
        assertEquals(listOf(1730, 1654, 1577, 1597, 2088), eq.bands.map { it.q })
        assertFalse(eq.capability(supportsEnable = false).supportsEnable)
        // Restoring a captured curve must not quantize an existing -118/60 dB setting to the UI step.
        assertArrayEquals(bytes(5, 0, 0, 0,
            0, 0xb4, 1, 0x0e, 6, 0xc2,
            2, 0xee, 0xff, 0x8a, 6, 0x76,
            0x0b, 0xb8, 0, 0, 6, 0x29,
            0x2e, 0xe0, 0, 0, 6, 0x3d,
            0x59, 0x10, 0, 0, 8, 0x28), eq.write(eq.bands.map { it.gain }))
    }
}

internal fun eqFixture() = bytes(2, 0xff, 0xc4,
    48, 1, 0x77, 0xff, 0x88, 0x10, 0,
    48, 5, 0xdc, 0, 120, 0x20, 0)

internal fun elite10CapturedEq() = bytes(5, 0, 0,
    0x1e, 0, 0xb4, 1, 0x0e, 6, 0xc2,
    0x18, 2, 0xee, 0xff, 0x8a, 6, 0x76,
    0x18, 0x0b, 0xb8, 0, 0, 6, 0x29,
    0x18, 0x2e, 0xe0, 0, 0, 6, 0x3d,
    0x28, 0x59, 0x10, 0, 0, 8, 0x28)
