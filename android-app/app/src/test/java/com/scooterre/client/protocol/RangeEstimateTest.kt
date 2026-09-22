package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeEstimateTest {
    @Test
    fun rangeIsRemainingPercentDividedByConsumption() {
        val stats = RideWindow.Stats(km = 100.0, percentUsed = 50.0) // 0.5 %/km
        assertEquals(120.0, RangeEstimate.rangeKm(remainingPercent = 60.0, stats)!!, 0.001)
    }

    @Test
    fun tooLittleDataOrZeroConsumptionGivesNoEstimate() {
        assertNull(RangeEstimate.rangeKm(60.0, null))
        assertNull(RangeEstimate.rangeKm(60.0, RideWindow.Stats(4.9, 5.0)))
        assertNull(RangeEstimate.rangeKm(60.0, RideWindow.Stats(50.0, 0.0)))
        assertNotNull(RangeEstimate.rangeKm(60.0, RideWindow.Stats(5.0, 5.0)))
    }

    @Test
    fun ridesWithImplausibleConsumptionAreNotCounted() {
        assertTrue(RangeEstimate.isPlausibleRide(10.0, 15.0))   // 1.5 %/km
        assertFalse(RangeEstimate.isPlausibleRide(13.9, 1.0))   // 0.07 %/km: charged in between
        assertFalse(RangeEstimate.isPlausibleRide(1.0, 40.0))   // 40 %/km
        assertFalse(RangeEstimate.isPlausibleRide(0.0, 10.0))
        assertFalse(RangeEstimate.isPlausibleRide(10.0, 0.0))
    }

    private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)
}
