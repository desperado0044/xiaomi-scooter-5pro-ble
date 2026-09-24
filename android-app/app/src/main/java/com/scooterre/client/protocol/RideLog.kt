package com.scooterre.client.protocol

/** One ride as shown in the ride list: consecutive segments with no long pause between them. */
data class RideTrip(
    val startMs: Long,
    val endMs: Long,
    /** Time actually spent riding (sum of the segments), without the standing time in between. */
    val movingMs: Long,
    val km: Double,
    val percentUsed: Double,
    /** Kilometres per riding mode, the most-ridden mode first. */
    val modeKm: List<Pair<Long, Double>>,
) {
    val mode: Long get() = modeKm.first().first
    val percentPerKm: Double get() = if (km > 0) percentUsed / km else 0.0
    /** Average speed while riding in km/h, or null if the time is too short to say. */
    val avgKmh: Double? get() = if (movingMs >= 60_000) km / (movingMs / 3_600_000.0) else null
}

/**
 * The ride list, derived on the fly from the same segments the range estimate uses ([RideWindow]) -
 * nothing of its own is stored. Segments belong to one ride while the pause between them stays
 * below [TRIP_GAP_MS]; a segment recorded without a time (older versions) can't be placed and is
 * only summed up as [Result.untimed].
 */
object RideLog {
    const val TRIP_GAP_MS = 10 * 60_000L

    data class Result(val trips: List<RideTrip>, val untimed: RideWindow.Stats?)

    fun build(segments: List<RideSegment>, gapMs: Long = TRIP_GAP_MS): Result {
        val timed = segments.filter { it.startMs > 0 && it.endMs >= it.startMs }
        val untimed = segments.filter { it.startMs <= 0 || it.endMs < it.startMs }
        val trips = mutableListOf<RideTrip>()
        var group = mutableListOf<RideSegment>()
        for (seg in timed) {
            if (group.isNotEmpty() && seg.startMs - group.last().endMs > gapMs) {
                trips += toTrip(group)
                group = mutableListOf()
            }
            group += seg
        }
        if (group.isNotEmpty()) trips += toTrip(group)
        val untimedStats = if (untimed.isEmpty()) null else RideWindow.Stats(untimed.sumOf { it.km }, untimed.sumOf { it.percentUsed })
        return Result(trips, untimedStats)
    }

    private fun toTrip(group: List<RideSegment>) = RideTrip(
        startMs = group.first().startMs,
        endMs = group.last().endMs,
        movingMs = group.sumOf { it.endMs - it.startMs },
        km = group.sumOf { it.km },
        percentUsed = group.sumOf { it.percentUsed },
        modeKm = group.groupBy { it.mode }.map { (m, segs) -> m to segs.sumOf { it.km } }.sortedByDescending { it.second },
    )
}
