package com.occaecat.hush.features

import com.occaecat.hush.HeadphoneBrand
import com.occaecat.hush.HeadphonesState
import org.junit.Assert.*
import org.junit.Test

class InsightsDeviceTest {
    @Test fun oldSonyHistoryIsNotUsedForJabra() {
        val history = Insights.Snapshot(battery = listOf(
            Insights.BatterySample(1, 80, false, true, 1),
            Insights.BatterySample(2, 70, false, true, 2, "jabra-a"),
            Insights.BatterySample(3, 90, false, true, 3, "jabra-b"),
        ))
        val current = HeadphonesState(brand = HeadphoneBrand.JABRA, address = "jabra-a")
        assertEquals(listOf(70), Insights.forDevice(history, current).battery.map { it.level })
    }

    @Test fun jabraNeedsObservedDrainRatherThanSonyRatedBatteryLife() {
        assertTrue(Insights.drain(true, emptyList(), sonyRating = false).percentPerHour.isNaN())
        val now = System.currentTimeMillis()
        val samples = listOf(
            Insights.BatterySample(now - 3 * 3_600_000, 90, false, true, 1, "jabra-a"),
            Insights.BatterySample(now, 60, false, true, 1, "jabra-a"),
        )
        val measured = Insights.drain(true, samples, sonyRating = false)
        assertTrue(measured.measured)
        assertEquals(10.0, measured.percentPerHour, 0.001)
    }
}
