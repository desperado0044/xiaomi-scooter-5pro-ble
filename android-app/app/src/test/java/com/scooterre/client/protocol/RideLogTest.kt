package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RideLogTest {
    private val min = 60_000L

    private fun seg(mode: Long, km: Double, pct: Double, startMin: Long, endMin: Long) =
        RideSegment(mode, km, pct, startMin * min, endMin * min)

    @Test
    fun segmentsCloseTogetherFormOneRide() {
        val r = RideLog.build(listOf(seg(2, 1.0, 2.0, 1, 4), seg(2, 1.0, 1.0, 4, 7), seg(3, 0.5, 1.0, 9, 10)))
        assertEquals(1, r.trips.size)
        val t = r.trips[0]
        assertEquals(2.5, t.km, 1e-9)
        assertEquals(4.0, t.percentUsed, 1e-9)
        assertEquals(2L, t.mode)
        assertEquals(listOf(2L, 3L), t.modeKm.map { it.first })
        assertEquals(7 * min, t.movingMs)
    }

    @Test
    fun longPauseSplitsRides() {
        val r = RideLog.build(listOf(seg(2, 1.0, 2.0, 1, 4), seg(11, 1.0, 1.0, 30, 40)))
        assertEquals(2, r.trips.size)
        assertEquals(11L, r.trips[1].mode)
    }

    @Test
    fun segmentsWithoutTimeAreOnlySummedUp() {
        val r = RideLog.build(listOf(RideSegment(2, 3.0, 4.0), RideSegment(3, 2.0, 2.0), seg(2, 1.0, 1.0, 5, 8)))
        assertEquals(1, r.trips.size)
        assertEquals(5.0, r.untimed!!.km, 1e-9)
        assertEquals(6.0, r.untimed!!.percentUsed, 1e-9)
    }

    @Test
    fun noUntimedSegmentsMeansNoSummary() {
        assertNull(RideLog.build(listOf(seg(2, 1.0, 1.0, 1, 3))).untimed)
        assertEquals(0, RideLog.build(emptyList()).trips.size)
    }

    @Test
    fun averageSpeedNeedsAMinuteOfRiding() {
        assertNull(RideLog.build(listOf(RideSegment(2, 0.1, 0.0, 1000, 20_000))).trips[0].avgKmh)
        assertNotNull(RideLog.build(listOf(seg(2, 5.0, 2.0, 0 + 1, 11))).trips[0].avgKmh)
        assertEquals(30.0, RideLog.build(listOf(seg(2, 5.0, 2.0, 1, 11))).trips[0].avgKmh!!, 1e-9)
    }
}
