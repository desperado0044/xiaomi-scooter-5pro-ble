package com.scooterre.client.protocol

/**
 * The live ride log: while the phone is connected to the scooter during a ride, every reading of odometer, remaining
 * charge, voltage and riding mode passes through here, and the ride is cut into segments - one per riding mode, and
 * one per [SEGMENT_KM] so little is lost if the connection drops. A segment's distance is the odometer difference,
 * its energy the drop in remaining charge times the average voltage. Nothing is asked and nothing is stored here;
 * the caller adds the returned segments to the mode's totals ([BatteryHistoryStore.addRide]).
 *
 * Without a connection during the ride there is no live log - such rides are not recorded at all.
 */
class LiveRideTracker {
    data class Reading(val km: Double, val mah: Long, val voltage: Double)

    /** A finished piece of a ride in one riding [mode]. */
    data class Segment(val mode: Long, val km: Double, val wh: Double)

    private var last: Reading? = null
    private var start: Reading? = null
    private var mode: Long? = null

    /** Feeds one reading; returns the segments it completes (usually none). */
    fun onReading(reading: Reading, riding: Boolean, currentMode: Long?): List<Segment> {
        val done = mutableListOf<Segment>()
        val begin = start
        if (riding && currentMode != null && currentMode in TRACKED_MODES) {
            when {
                // The ride starts between two readings: measure from the last one, so no metres are lost.
                begin == null -> {
                    start = last ?: reading
                    mode = currentMode
                }
                mode != currentMode -> {
                    close(reading)?.let(done::add)
                    start = reading
                    mode = currentMode
                }
                reading.km - begin.km >= SEGMENT_KM -> {
                    close(reading)?.let(done::add)
                    start = reading
                }
            }
        } else if (begin != null) {
            close(reading)?.let(done::add)
            start = null
            mode = null
        }
        last = reading
        return done
    }

    /** Forget the running ride (new connection, or the connection ended). */
    fun reset() {
        last = null
        start = null
        mode = null
    }

    private fun close(end: Reading): Segment? {
        val begin = start ?: return null
        val ridingMode = mode ?: return null
        val km = end.km - begin.km
        val wh = (begin.mah - end.mah) / 1000.0 * ((begin.voltage + end.voltage) / 2.0)
        return if (km >= MIN_SEGMENT_KM && RangeEstimate.isPlausibleRide(km, wh)) Segment(ridingMode, km, wh) else null
    }

    companion object {
        /** RIDING_MODE values that are tracked: 11 = Walk, 2 = Drive, 3 = Sport. */
        val TRACKED_MODES = setOf(11L, 2L, 3L)
        const val SEGMENT_KM = 1.0
        const val MIN_SEGMENT_KM = 0.05
    }
}
