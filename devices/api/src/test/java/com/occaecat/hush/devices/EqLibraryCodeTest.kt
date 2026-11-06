package com.occaecat.hush.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EqLibraryCodeTest {
    @Test fun importedCurveRespectsEachBandLimit() {
        val layout = "jabra-11846-60-250"
        val code = "HUSHEQ2:$layout:8,0|Too high"
        assertNull(EqLibrary.parseCode(code, layout, 2, -8..8, listOf(-7..7, -8..8)))
        assertEquals(listOf(7, 8), EqLibrary.parseCode(
            "HUSHEQ2:$layout:7,8|Valid", layout, 2, -8..8, listOf(-7..7, -8..8))?.second)
    }
}
