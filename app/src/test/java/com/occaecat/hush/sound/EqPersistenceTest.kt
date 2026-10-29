package com.occaecat.hush.sound

import com.occaecat.hush.mdr.EqBandSpec
import com.occaecat.hush.mdr.EqCapability
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class EqPersistenceTest {
    private val sony = EqCapability(6, 21, emptyList())
    private val jabra = EqCapability(6, 0, emptyList(), namespace = "jabra-gnp",
        bandSpecs = List(6) { EqBandSpec("Band $it", 100.0 * (it + 1), -360, 360, 0, 60.0, 30) }, supportsEnable = true)

    @Test fun legacyLibraryAndActionsRemainSonyOnly() {
        SoundStore.importJson(JSONObject("""{"library":[{"id":"old","name":"My curve","levels":[10,11,12,13,14,15]}],"scenes":[{"id":"scene","name":"Music","action":{"eqPreset":160,"mode":"AMBIENT","level":17}}]}"""))
        val config = SoundStore.config.value
        assertEquals(sony.layoutId, config.library.single().compatibleLayout(sony))
        assertNull(config.library.single().compatibleLayout(jabra))
        assertEquals(EqChoice.Preset(160, "sony-mdr"), config.scenes.single().action.eq)
        assertEquals("sony-mdr", config.scenes.single().action.noiseNamespace)
        assertEquals(2, SoundStore.exportJson().getInt("version"))
    }

    @Test fun shareCodePreservesExactJabraRawUnitsAndLayout() {
        val saved = SavedEq("jabra", "Voice | soft", listOf(-29, 17, 0, 100, -200, 359), layoutId = jabra.layoutId)
        val parsed = SoundStore.parseCode(SoundStore.shareCode(saved))!!
        assertEquals(saved.name, parsed.name)
        assertEquals(saved.levels, parsed.levels)
        assertEquals(saved.layoutId, parsed.layoutId)
        assertEquals(jabra.layoutId, parsed.compatibleLayout(jabra))
        assertNull(parsed.compatibleLayout(sony))
    }

    @Test fun oldShareCodeCannotBecomeJabraCurve() {
        val parsed = SoundStore.parseCode("HUSHEQ1:-2,1,2,5,2,2|Old curve")!!
        assertEquals(listOf(8, 11, 12, 15, 12, 12), parsed.levels)
        assertEquals(sony.layoutId, parsed.compatibleLayout(sony))
        assertNull(parsed.compatibleLayout(jabra))
        assertNull(SoundStore.parseCode("HUSHEQ1:11|Out of range"))
        assertNull(SoundStore.parseCode("noise HUSHEQ1:0|Not a code"))
        assertNull(SoundStore.parseCode("HUSHEQ2:not-json"))
    }

    @Test fun changedBandMetadataOrOutOfRangeGainCannotApply() {
        val saved = SavedEq("jabra", "Curve", List(6) { 0 }, layoutId = jabra.layoutId)
        val different = jabra.copy(bandSpecs = jabra.bandSpecs.mapIndexed { i, b -> if (i == 0) b.copy(frequencyHz = 80.0) else b })
        assertNull(saved.compatibleLayout(different))
        assertNull(saved.copy(levels = listOf(361, 0, 0, 0, 0, 0)).compatibleLayout(jabra))
    }

    @Test fun incompatibleAutomationChoicesAreSkippedBeforeAnyRestorePoint() {
        val old = SavedEq("old", "Sony", List(6) { 10 })
        val current = SavedEq("jabra", "Jabra", List(6) { 0 }, layoutId = jabra.layoutId)
        val config = SoundConfig(library = listOf(old, current))
        val noBypass = jabra.copy(supportsEnable = false)
        assertFalse(canApplyEq(EqChoice.Library(old.id), config, noBypass))
        assertFalse(canApplyEq(EqChoice.Preset(0), config, noBypass))
        assertFalse(canApplyEq(EqChoice.Library("missing"), config, noBypass))
        assertTrue(canApplyEq(EqChoice.Library(current.id), config, noBypass))
        assertTrue(canApplyEq(EqChoice.Library(old.id), config, sony))
        assertTrue(canApplyEq(EqChoice.Preset(0), config, sony))
        assertFalse(canApplyEq(EqChoice.Preset(255), config, sony))
    }

    @Test fun malformedRawGainDoesNotGetSilentlyTruncated() {
        fun code(level: String): String = "HUSHEQ2:" + Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"name":"Test","layout":"jabra-gnp:test","levels":[$level]}""".toByteArray())
        assertNull(SoundStore.parseCode(code("1.5")))
        assertNull(SoundStore.parseCode(code("2147483648")))
        assertNull(SoundStore.parseCode(code("\"12\"")))
        assertEquals(listOf(-29), SoundStore.parseCode(code("-29"))!!.levels)
    }

    @Test fun backupSoundRoundtripKeepsJabraProvenanceAndSceneReference() {
        SoundStore.importJson(JSONObject("""{"version":2,"library":[],"scenes":[]} """))
        val saved = SoundStore.saveEq("Jabra", listOf(1, 2, 3, 4, 5, 6), layoutId = jabra.layoutId)
        SoundStore.update { it.copy(scenes = listOf(Scene("s", "Scene", "star", SoundAction(eq = EqChoice.Library(saved.id), noiseNamespace = "jabra-gnp")))) }
        val json = SoundStore.exportJson()
        SoundStore.importJson(json)
        assertEquals(jabra.layoutId, SoundStore.config.value.library.single().layoutId)
        assertEquals("jabra-gnp", SoundStore.config.value.scenes.single().action.noiseNamespace)
        SoundStore.deleteEq(saved.id)
        assertNull(SoundStore.config.value.scenes.single().action.eq)
    }
}
