package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeEstimateTest {
    @Test
    fun energyLeftIsChargeTimesVoltage() {
        assertEquals(240.0, RangeEstimate.remainingWh(6000, 40.0), 0.001)
    }

    @Test
    fun rangeIsEnergyLeftDividedByConsumption() {
        val totals = ModeEfficiencyTotals(totalKm = 100.0, totalWh = 2000.0) // 20 Wh/km
        assertEquals(12.0, RangeEstimate.rangeKm(240.0, totals)!!, 0.001)
    }

    @Test
    fun tooLittleDataOrNoEnergyGivesNoEstimate() {
        assertNull(RangeEstimate.rangeKm(240.0, null))
        assertNull(RangeEstimate.rangeKm(240.0, ModeEfficiencyTotals(4.9, 100.0)))
        assertNull(RangeEstimate.rangeKm(0.0, ModeEfficiencyTotals(50.0, 1000.0)))
        assertNotNull(RangeEstimate.rangeKm(240.0, ModeEfficiencyTotals(5.0, 100.0)))
    }

    @Test
    fun recentRidesCountMoreThanOldOnes() {
        // 300 km at 20 Wh/km, then 150 km at 30 Wh/km: lifetime lands at 23.3, the recent value closer to 30
        val after = ModeEfficiencyTotals(300.0, 6000.0).withRide(150.0, 4500.0)
        assertEquals(450.0, after.totalKm, 0.001)
        assertEquals(10500.0 / 450.0, after.whPerKm, 0.001)
        assertTrue(after.recentWhPerKm > after.whPerKm)
        assertEquals(300.0 * 0.5 + 150.0, after.recentKm, 0.001)
    }

    @Test
    fun oldDataWithoutRecentTotalsStartsEqualToTheLifetimeTotals() {
        val old = ModeEfficiencyTotals(80.0, 1600.0)
        assertEquals(old.whPerKm, old.recentWhPerKm, 0.0001)
    }
}
