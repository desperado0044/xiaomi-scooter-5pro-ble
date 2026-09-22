package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class RideWindowTest {
    private fun seg(mode: Long, km: Double, pct: Double) = RideSegment(mode, km, pct)

    @Test
    fun withinTheWindowNothingIsDropped() {
        val entries = listOf(seg(2, 100.0, 10.0), seg(2, 150.0, 15.0))
        assertEquals(entries, RideWindow.trim(entries, maxKm = 300.0))
    }

    @Test
    fun theOldestEntriesDropOnceTheWindowIsExceeded() {
        val entries = listOf(seg(2, 100.0, 10.0), seg(2, 100.0, 10.0), seg(2, 150.0, 15.0))
        // total 350 > 300: the oldest (first) 100km entry must go, leaving 250km <= 300
        val trimmed = RideWindow.trim(entries, maxKm = 300.0)
        assertEquals(listOf(seg(2, 100.0, 10.0), seg(2, 150.0, 15.0)), trimmed)
    }

    @Test
    fun aSingleEntryLongerThanTheWindowIsKeptRatherThanEmptied() {
        val entries = listOf(seg(2, 400.0, 40.0))
        assertEquals(entries, RideWindow.trim(entries, maxKm = 300.0))
    }

    @Test
    fun anAgeingBatteryOrWornTyresShowUpAsOlderEntriesRollOut() {
        // 200km at 1%/km (healthy), then 150km at 2%/km (after wear/ageing) - once the window has
        // rolled past the old data, the stats reflect only the recent, worse rate.
        var entries = listOf(seg(2, 200.0, 200.0))
        entries = RideWindow.trim(entries + seg(2, 150.0, 300.0), maxKm = 300.0)
        val stats = RideWindow.statsByMode(entries).getValue(2L)
        assertEquals(150.0, stats.km, 0.001)
        assertEquals(2.0, stats.percentPerKm, 0.001)
    }

    @Test
    fun statsAreKeptSeparatePerMode() {
        val entries = listOf(seg(2, 100.0, 10.0), seg(3, 50.0, 8.0), seg(2, 20.0, 2.0))
        val stats = RideWindow.statsByMode(entries)
        assertEquals(120.0, stats.getValue(2L).km, 0.001)
        assertEquals(12.0, stats.getValue(2L).percentUsed, 0.001)
        assertEquals(50.0, stats.getValue(3L).km, 0.001)
    }
}
