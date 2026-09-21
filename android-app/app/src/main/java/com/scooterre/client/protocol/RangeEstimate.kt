package com.scooterre.client.protocol

/**
 * The range the scooter would reach at the rider's own consumption in one riding mode: the energy
 * left in the battery divided by the Wh/km measured on the rider's real rides (recent rides count
 * more, see [ModeEfficiencyTotals.recentWhPerKm]). Only shown once a mode has [MIN_KM] of data;
 * before that the scooter's own estimate is all there is.
 */
object RangeEstimate {
    const val MIN_KM = 5.0

    /** Energy left in the battery: the remaining charge at the current voltage - the same way the
     * consumption per km was measured, so the two fit together. */
    fun remainingWh(remainingMah: Long, voltage: Double): Double = remainingMah / 1000.0 * voltage

    fun rangeKm(remainingWh: Double, totals: ModeEfficiencyTotals?): Double? {
        if (totals == null || totals.totalKm < MIN_KM || remainingWh <= 0.0) return null
        val perKm = totals.recentWhPerKm
        return if (perKm > 0.0) remainingWh / perKm else null
    }
}
