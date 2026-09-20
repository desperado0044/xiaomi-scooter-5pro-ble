package com.scooterre.client.reminder

import com.scooterre.client.reminder.InsuranceSchedule.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class InsuranceScheduleTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun theRunningPlatePeriodEndsOnTheLastDayOfFebruary() {
        assertEquals(d("2027-02-28"), InsuranceSchedule.expiryFor(d("2026-09-20")))
        assertEquals(d("2027-02-28"), InsuranceSchedule.expiryFor(d("2027-01-01")))
        assertEquals(d("2027-02-28"), InsuranceSchedule.expiryFor(d("2027-02-28"))) // last day still belongs to it
    }

    @Test
    fun theDayAfterExpiryStartsTheNextPeriodAndLeapYearsAreHandled() {
        assertEquals(d("2028-02-29"), InsuranceSchedule.expiryFor(d("2027-03-01")))
        assertEquals(d("2029-02-28"), InsuranceSchedule.expiryFor(d("2028-03-01")))
    }

    @Test
    fun stagesFollowOneMonthOneWeekAndTheLastDay() {
        val expiry = d("2027-02-28")
        assertEquals(Stage.NONE, InsuranceSchedule.stageOn(d("2027-01-27"), expiry))
        assertEquals(Stage.MONTH, InsuranceSchedule.stageOn(d("2027-01-28"), expiry))
        assertEquals(Stage.MONTH, InsuranceSchedule.stageOn(d("2027-02-20"), expiry))
        assertEquals(Stage.WEEK, InsuranceSchedule.stageOn(d("2027-02-21"), expiry))
        assertEquals(Stage.WEEK, InsuranceSchedule.stageOn(d("2027-02-27"), expiry))
        assertEquals(Stage.LAST_DAY, InsuranceSchedule.stageOn(d("2027-02-28"), expiry))
    }

    @Test
    fun leapYearStagesShiftWithTheExpiry() {
        val expiry = d("2028-02-29")
        assertEquals(Stage.NONE, InsuranceSchedule.stageOn(d("2028-01-28"), expiry))
        assertEquals(Stage.MONTH, InsuranceSchedule.stageOn(d("2028-01-29"), expiry))
        assertEquals(Stage.WEEK, InsuranceSchedule.stageOn(d("2028-02-22"), expiry))
        assertEquals(Stage.LAST_DAY, InsuranceSchedule.stageOn(d("2028-02-29"), expiry))
    }

    @Test
    fun outsideTheWindowNothingIsDue() {
        assertNull(InsuranceSchedule.stageToNotify(d("2026-09-20"), Stage.NONE))
        assertNull(InsuranceSchedule.stageToNotify(d("2027-03-01"), Stage.NONE)) // new period, far from its end
    }

    @Test
    fun eachStageIsNotifiedOnlyOnce() {
        assertEquals(Stage.MONTH, InsuranceSchedule.stageToNotify(d("2027-01-28"), Stage.NONE))
        assertNull(InsuranceSchedule.stageToNotify(d("2027-02-05"), Stage.MONTH))
        assertEquals(Stage.WEEK, InsuranceSchedule.stageToNotify(d("2027-02-21"), Stage.MONTH))
        assertNull(InsuranceSchedule.stageToNotify(d("2027-02-24"), Stage.WEEK))
        assertEquals(Stage.LAST_DAY, InsuranceSchedule.stageToNotify(d("2027-02-28"), Stage.WEEK))
        assertNull(InsuranceSchedule.stageToNotify(d("2027-02-28"), Stage.LAST_DAY))
    }

    @Test
    fun aMissedStageCatchesUpWithTheLatestOne() {
        // Phone was off in the month window: on the first run in the week window only the week reminder comes.
        assertEquals(Stage.WEEK, InsuranceSchedule.stageToNotify(d("2027-02-23"), Stage.NONE))
    }
}
