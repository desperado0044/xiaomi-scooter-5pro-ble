package com.scooterre.client.reminder

import java.time.LocalDate
import java.time.YearMonth

/**
 * Reminder dates for the German insurance plate (Versicherungskennzeichen): it always runs from
 * 1 March to the last day of February, so there is no per-document date - the schedule is fixed:
 * one month before, one week before and on the last day.
 */
object InsuranceSchedule {
    /** Ordered by urgency; a stage is notified at most once per plate period. */
    enum class Stage { NONE, MONTH, WEEK, LAST_DAY }

    /** The last day of the plate period that is running on [today] (28/29 February). */
    fun expiryFor(today: LocalDate): LocalDate {
        val thisYear = YearMonth.of(today.year, 2).atEndOfMonth()
        return if (today <= thisYear) thisYear else YearMonth.of(today.year + 1, 2).atEndOfMonth()
    }

    fun stageOn(today: LocalDate, expiry: LocalDate): Stage = when {
        today > expiry -> Stage.NONE
        today == expiry -> Stage.LAST_DAY
        today >= expiry.minusWeeks(1) -> Stage.WEEK
        today >= expiry.minusMonths(1) -> Stage.MONTH
        else -> Stage.NONE
    }

    /** The stage to notify about today, or null when nothing new is due (already notified or too early). */
    fun stageToNotify(today: LocalDate, lastNotified: Stage): Stage? {
        val current = stageOn(today, expiryFor(today))
        return if (current > lastNotified) current else null
    }
}
