package com.scooterre.client.protocol

/** One recorded stretch of riding in one mode: how far, and how many percentage points of a full
 * charge it used. */
data class RideSegment(val mode: Long, val km: Double, val percentUsed: Double)

/**
 * A rolling window of the most recent [WINDOW_KM] kilometres of riding (across all modes), used to
 * estimate the range at the rider's own consumption. A hard cutoff, not a fade: everything inside
 * the window counts exactly the same, everything older is fully gone - rather than an exponential
 * fade that technically never quite forgets. This is what makes an ageing battery or worn tyres
 * show up on their own, without anyone having to notice the moment to "reset" at, the way a battery
 * *replacement* is a single moment you do know and can reset for directly (see the "Verlauf
 * zurücksetzen" button) - the two are complementary, not alternatives to each other.
 *
 * The unit is percentage points of a full charge, not Wh or mAh: the range estimate this feeds
 * always divides the *current* percentage by this rate, so whatever a percentage point is actually
 * worth in energy cancels out of that division either way - multiplying it in first (an earlier
 * version of this file did, via mAh times voltage) added a source of error (voltage sags under
 * load) without buying back any accuracy for that comparison.
 */
object RideWindow {
    const val WINDOW_KM = 300.0

    data class Stats(val km: Double, val percentUsed: Double) {
        val percentPerKm: Double get() = if (km > 0) percentUsed / km else 0.0
    }

    /** Drops the oldest entries once the total distance covered exceeds [maxKm] - never down to
     * nothing, though: a single entry longer than the window alone is kept rather than discarded,
     * since dropping it would leave no data at all. */
    fun trim(entries: List<RideSegment>, maxKm: Double = WINDOW_KM): List<RideSegment> {
        val kept = entries.toMutableList()
        var total = kept.sumOf { it.km }
        while (kept.size > 1 && total > maxKm) {
            total -= kept.removeAt(0).km
        }
        return kept
    }

    fun statsByMode(entries: List<RideSegment>): Map<Long, Stats> =
        entries.groupBy { it.mode }.mapValues { (_, segs) -> Stats(segs.sumOf { it.km }, segs.sumOf { it.percentUsed }) }
}
