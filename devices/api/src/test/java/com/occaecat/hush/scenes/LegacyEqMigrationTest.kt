package com.occaecat.hush.scenes

import com.occaecat.hush.devices.EqLibrary
import com.occaecat.hush.scenes.SceneStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LegacyEqMigrationTest {
    @Test fun deviceNamespaceKeepsOldScenesWithTheirModule() {
        val old = JSONObject().put("scenes", JSONArray().put(
            JSONObject().put("id", "commute").put("name", "Commute")
                .put("action", JSONObject().put("mode", "NOISE_CANCELLING")
                    .put("noiseNamespace", "other-gnp")),
        ))
        SceneStore.importLegacy(old)
        assertNotNull(SceneStore.config.value.legacyByModule["other"])
        SceneStore.importJson(SceneStore.exportJson())
        assertNotNull(SceneStore.config.value.legacyByModule["other"])
    }

    @Test fun explicitNonSonyLayoutIsPreservedWithoutSonyGainConversion() {
        val layout = "other-gnp:60.0:-450:450:0:60.0"
        val old = JSONObject().put("library", JSONArray().put(
            JSONObject().put("id", "legacy-other-test").put("name", "My other curve")
                .put("layout", layout).put("levels", JSONArray(listOf(270, -118))),
        ))
        SceneStore.importLegacy(old)
        val curve = EqLibrary.items.value.single { it.id == "legacy-other-test" }
        assertEquals(layout, curve.layout)
        assertEquals(listOf(270, -118), curve.levels)
    }

    @Test fun unversionedSonyLevelsUseTheSonyLayoutAndNeutralTen() {
        val old = JSONObject().put("library", JSONArray().put(
            JSONObject().put("id", "legacy-sony-test").put("name", "My Sony curve")
                .put("levels", JSONArray(listOf(10, 12, 8, 10, 10, 10))),
        ))
        SceneStore.importLegacy(old)
        val curve = EqLibrary.items.value.single { it.id == "legacy-sony-test" }
        assertEquals("sony-6", curve.layout)
        assertEquals(listOf(0, 2, -2, 0, 0, 0), curve.levels)
    }
}
