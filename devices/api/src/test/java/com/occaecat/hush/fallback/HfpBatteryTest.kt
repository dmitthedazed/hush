package com.occaecat.hush.fallback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HfpBatteryTest {
    @Test
    fun appleAccessoryEvent() {
        // AT+IPHONEACCEV=2,1,7,2,0: battery 7 of 0–9, docked no.
        assertEquals(80, HfpBattery.level("+IPHONEACCEV", listOf("2", "1", "7", "2", "0")))
        assertEquals(10, HfpBattery.level("+IPHONEACCEV", listOf("1", "1", "0")))
        assertEquals(100, HfpBattery.level("+IPHONEACCEV", listOf("1", "1", "9")))
    }

    @Test
    fun appleEventWithoutBattery() {
        assertNull(HfpBattery.level("+IPHONEACCEV", listOf("1", "2", "0")))
        assertNull(HfpBattery.level("+IPHONEACCEV", listOf("1", "1", "12")))
        assertNull(HfpBattery.level("+IPHONEACCEV", emptyList()))
    }

    @Test
    fun plantronicsEvent() {
        assertEquals(60, HfpBattery.level("+XEVENT", listOf("BATTERY", "3", "5", "0", "0")))
        assertNull(HfpBattery.level("+XEVENT", listOf("USER-AGENT", "x")))
        assertNull(HfpBattery.level("+XEVENT", listOf("BATTERY", "6", "5")))
    }

    @Test
    fun otherCommands() = assertNull(HfpBattery.level("+ANDROID", listOf("1")))
}
