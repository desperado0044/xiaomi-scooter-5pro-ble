package com.scooterre.client.protocol

import com.scooterre.client.protocol.LiveRideTracker.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRideTrackerTest {
    private fun r(km: Double, pct: Long) = Reading(km, pct)

    @Test
    fun aRideIsCutAtEveryModeChangeAndAtItsEnd() {
        val t = LiveRideTracker()
        assertTrue(t.onReading(r(100.0, 90), riding = false, currentMode = 3L).isEmpty())
        assertTrue(t.onReading(r(100.4, 89), riding = true, currentMode = 3L).isEmpty())
        val afterSport = t.onReading(r(100.8, 88), riding = true, currentMode = 2L)
        assertEquals(1, afterSport.size)
        assertEquals(3L, afterSport[0].mode)
        assertEquals(0.8, afterSport[0].km, 0.001)
        assertEquals(2.0, afterSport[0].percentUsed, 0.001)
        assertTrue(t.onReading(r(101.0, 87), riding = true, currentMode = 2L).isEmpty())
        val end = t.onReading(r(101.0, 87), riding = false, currentMode = 2L)
        assertEquals(1, end.size)
        assertEquals(2L, end[0].mode)
        assertEquals(0.2, end[0].km, 0.001)
        assertEquals(1.0, end[0].percentUsed, 0.001)
    }

    @Test
    fun aLongRideIsCutEveryKilometre() {
        val t = LiveRideTracker()
        t.onReading(r(100.0, 90), riding = false, currentMode = 3L)
        assertTrue(t.onReading(r(100.6, 87), riding = true, currentMode = 3L).isEmpty())
        val cut = t.onReading(r(101.2, 84), riding = true, currentMode = 3L)
        assertEquals(1, cut.size)
        assertEquals(1.2, cut[0].km, 0.001)
        assertEquals(6.0, cut[0].percentUsed, 0.001)
    }

    @Test
    fun implausibleSegmentsAreDropped() {
        val t = LiveRideTracker()
        t.onReading(r(100.0, 90), riding = false, currentMode = 3L)
        t.onReading(r(100.4, 91), riding = true, currentMode = 3L)          // charge went up
        assertTrue(t.onReading(r(100.8, 92), riding = false, currentMode = 3L).isEmpty())
    }

    @Test
    fun untrackedModesAndStandingStillRecordNothing() {
        val t = LiveRideTracker()
        t.onReading(r(100.0, 90), riding = false, currentMode = 3L)
        assertTrue(t.onReading(r(100.4, 89), riding = true, currentMode = 5L).isEmpty())
        assertTrue(t.onReading(r(100.8, 88), riding = true, currentMode = null).isEmpty())
        assertTrue(t.onReading(r(100.8, 88), riding = false, currentMode = 3L).isEmpty())
    }

    @Test
    fun resetForgetsTheRunningRide() {
        val t = LiveRideTracker()
        t.onReading(r(100.0, 90), riding = false, currentMode = 3L)
        t.onReading(r(100.4, 89), riding = true, currentMode = 3L)
        t.reset()
        assertTrue(t.onReading(r(100.4, 89), riding = false, currentMode = 3L).isEmpty())
    }
}
