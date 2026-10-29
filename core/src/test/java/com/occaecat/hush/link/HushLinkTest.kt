package com.occaecat.hush.link

import com.occaecat.hush.BatteryComponent
import com.occaecat.hush.Phase
import com.occaecat.hush.HeadphonesState
import com.occaecat.hush.HeadphoneBrand
import com.occaecat.hush.mdr.AmbientMode
import com.occaecat.hush.mdr.EqBandSpec
import com.occaecat.hush.mdr.EqCapability
import com.occaecat.hush.mdr.EqState
import org.junit.Assert.*
import org.junit.Test

class HushLinkTest {
    @Test fun jabraCapabilitiesAndUnknownBatterySurviveTransport() {
        val snapshot = Snapshot(
            phase = Phase.CONNECTED, brand = "JABRA", ambientMin = 0, ambientMax = 5,
            ancLevel = 3, ancMax = 4, supportsVoiceFocus = false,
            noiseModes = setOf(AmbientMode.AMBIENT, AmbientMode.NOISE_CANCELLING),
            batteries = listOf(BatteryComponent("left", "Left", 72), BatteryComponent("case", "Case", null)),
            eqNamespace = "jabra-gnp", eqBands = listOf(-120, 60), eqLayoutId = "jabra:test", eqEnabled = true, supportsEqEnable = true,
            eqBandSpecs = listOf(EqBandSpec("60 Hz", 60.0, -360, 360, 0, 60.0, 60), EqBandSpec("250 Hz", 250.0, -360, 360, 0, 60.0, 60)),
        )
        assertEquals(snapshot, Snapshot.fromJson(snapshot.toJson()))
    }

    @Test fun olderPhoneSnapshotRetainsSonyDefaults() {
        val snapshot = Snapshot.fromJson("""{"phase":"CONNECTED","eqPresets":[0,16],"ambientLevel":12}""")!!
        assertEquals("sony-mdr", snapshot.eqNamespace)
        assertEquals(1, snapshot.ambientMin)
        assertEquals(20, snapshot.ambientMax)
        assertTrue(snapshot.supportsVoiceFocus)
        assertEquals(AmbientMode.entries.toSet(), snapshot.noiseModes)
    }

    @Test fun legacyEqCommandCannotClaimJabraNamespace() {
        assertEquals(Command.SetEqPreset(16, "sony-mdr"), Command.decode("""{"op":"eq","v":16}""".toByteArray()))
        val commands = listOf(Command.SetAncLevel(3), Command.SetEqEnabled(false), Command.SetEqBands(listOf(-60, 120), "jabra:test"))
        commands.forEach { assertEquals(it, Command.decode(it.encode())) }
    }

    @Test fun malformedCommandsAreIgnored() {
        assertNull(Command.decode("""{"op":"eqBands","v":[1]}""".toByteArray()))
        assertNull(Snapshot.fromJson("""{"phase":"CONNECTED","eqBandSpecs":[{}]}"""))
    }

    @Test fun queuedStrengthAndEqEnableCommandsCannotCrossDeviceLayouts() {
        val jabra = Snapshot(brand = "JABRA", ambientMin = 0, ambientMax = 5, ancMax = 4,
            eqNamespace = "jabra-gnp", eqLayoutId = "jabra:five-bands", eqEnabled = true, supportsEqEnable = true)
        val legacy = Command.decode("""{"op":"ambient","v":3}""".toByteArray())!!
        assertFalse(jabra.accepts(legacy))
        assertTrue(Snapshot().accepts(legacy))
        assertTrue(jabra.accepts(Command.SetAmbientLevel(3, "jabra-gnp", 0, 5)))
        assertFalse(jabra.accepts(Command.SetAmbientLevel(3, "jabra-gnp", 0, 4)))
        assertFalse(jabra.accepts(Command.SetAmbientLevel(6, "jabra-gnp", 0, 5)))
        assertFalse(jabra.accepts(Command.SetEqEnabled(false)))
        assertFalse(Snapshot(brand = "JABRA").accepts(Command.SetEqEnabled(false)))
        assertFalse(jabra.accepts(Command.SetEqEnabled(false, "jabra-gnp", "old-layout")))
        assertTrue(jabra.accepts(Command.SetEqEnabled(false, "jabra-gnp", "jabra:five-bands")))
        val valid = Command.SetAmbientLevel(3, "jabra-gnp", 0, 5)
        assertEquals(valid, Command.decode(valid.encode()))
    }

    @Test fun enabledEqWithoutBypassCapabilityKeepsBandsButRejectsEnableCommands() {
        val band = EqBandSpec("1 kHz", 1000.0, -360, 360, 0, 60.0, 30)
        val caps = EqCapability(1, 721, emptyList(), "jabra-gnp", listOf(band), supportsEnable = false)
        val state = HeadphonesState(brand = HeadphoneBrand.JABRA, phase = Phase.CONNECTED,
            eqCaps = caps, eq = EqState(-1, listOf(60), enabled = true))
        val snapshot = Snapshot.fromJson(Snapshot.from(state).toJson())!!
        assertEquals(true, snapshot.eqEnabled)
        assertFalse(snapshot.supportsEqEnable)
        assertFalse(snapshot.accepts(Command.SetEqEnabled(false, caps.namespace, caps.layoutId)))
        assertTrue(snapshot.accepts(Command.SetEqBands(listOf(120), caps.layoutId)))
        val supported = Snapshot.from(state.copy(eqCaps = caps.copy(supportsEnable = true)))
        assertTrue(supported.supportsEqEnable)
        assertTrue(supported.accepts(Command.SetEqEnabled(false, caps.namespace, caps.layoutId)))
    }

    @Test fun legacySnapshotCannotInferBypassSupportFromEnabledState() {
        val snapshot = Snapshot.fromJson("""{"phase":"CONNECTED","brand":"JABRA","eqNamespace":"jabra-gnp","eqLayoutId":"jabra:test","eqEnabled":true}""")!!
        assertFalse(snapshot.supportsEqEnable)
        assertFalse(snapshot.accepts(Command.SetEqEnabled(false, "jabra-gnp", "jabra:test")))
    }
}
