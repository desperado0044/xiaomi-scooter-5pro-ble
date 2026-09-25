package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PollPlanTest {
    @Test
    fun serialNumbersAreReadOnce() {
        assertNull(PollPlan.intervalMs("SCOOTER_SN", riding = false, tab = PollTab.OVERVIEW))
        assertNull(PollPlan.intervalMs("FIRMWARE_VERSION", riding = true, tab = PollTab.BATTERY))
    }

    @Test
    fun rideRecordingValuesAreAlwaysFast() {
        for (tab in PollTab.entries) for (riding in listOf(true, false)) {
            for (name in listOf("IS_RIDING", "RIDING_MODE", "BATTERY_LEVEL")) {
                assertEquals(PollPlan.FAST_MS, PollPlan.intervalMs(name, riding, tab, stillScale = 4.0))
            }
        }
    }

    @Test
    fun tripValuesFollowTheRidingStateOnTheOverview() {
        assertEquals(PollPlan.FAST_MS, PollPlan.intervalMs("CURRENT_MILEAGE", true, PollTab.OVERVIEW))
        assertEquals(PollPlan.PARKED_MS, PollPlan.intervalMs("CURRENT_MILEAGE", false, PollTab.OVERVIEW))
        assertEquals(PollPlan.SLOW_MS, PollPlan.intervalMs("CURRENT_MILEAGE", true, PollTab.SETTINGS))
    }

    @Test
    fun rangeAlgorithmIsCurrentWhereItIsShown() {
        assertEquals(PollPlan.PARKED_MS, PollPlan.intervalMs("REMAINING_MILEAGE_ALGORITHM", false, PollTab.OVERVIEW))
        assertEquals(PollPlan.PARKED_MS, PollPlan.intervalMs("REMAINING_MILEAGE_ALGORITHM", false, PollTab.BATTERY))
        assertEquals(PollPlan.SLOW_MS, PollPlan.intervalMs("REMAINING_MILEAGE_ALGORITHM", false, PollTab.VEHICLE))
    }

    @Test
    fun odometerIsNeverStretchedByTheRefreshRate() {
        assertEquals(PollPlan.PARKED_MS, PollPlan.intervalMs("TOTAL_MILEAGE", false, PollTab.OTHER, stillScale = 4.0))
    }

    @Test
    fun batteryLiveValuesAreFastOnlyOnTheBatteryTab() {
        assertEquals(PollPlan.BATTERY_LIVE_MS, PollPlan.intervalMs("VOLTAGE", false, PollTab.BATTERY))
        assertEquals(PollPlan.SLOW_MS, PollPlan.intervalMs("VOLTAGE", false, PollTab.OVERVIEW))
    }

    @Test
    fun lockAndSlowGroupsAndUnknownNames() {
        assertEquals(PollPlan.MEDIUM_MS, PollPlan.intervalMs("IS_LOCKED", false, PollTab.OVERVIEW))
        assertEquals(PollPlan.SLOW_MS, PollPlan.intervalMs("SOH", false, PollTab.OVERVIEW))
        assertEquals(PollPlan.SLOW_MS, PollPlan.intervalMs("LOG_3", false, PollTab.OVERVIEW))
        assertEquals(PollPlan.DEFAULT_MS, PollPlan.intervalMs("SOMETHING_ELSE", false, PollTab.OVERVIEW))
    }

    @Test
    fun refreshRateStretchesOnlyParkedValues() {
        assertEquals(PollPlan.MEDIUM_MS * 2, PollPlan.intervalMs("IS_LOCKED", false, PollTab.OVERVIEW, stillScale = 2.0))
        assertEquals(PollPlan.FAST_MS, PollPlan.intervalMs("CURRENT_MILEAGE", true, PollTab.OVERVIEW, stillScale = 2.0))
    }
}
