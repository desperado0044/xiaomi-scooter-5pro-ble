package com.scooterre.client.protocol

/**
 * The range the scooter would reach at the rider's own consumption in one riding mode: the energy
 * left in the battery divided by the Wh/km measured on the rider's real rides (recent rides count
 * more, see [ModeEfficiencyTotals.recentWhPerKm]). Only shown once a mode has [MIN_KM] of data;
 * before that the scooter's own estimate is all there is.
 */
object RangeEstimate {
    const val MIN_KM = 5.0

    // A real ride costs roughly 5-60 Wh/km. Far outside that, the odometer and the battery reading do not belong to
    // the same ride - typically because the scooter was charged (or ridden several times) between two connections.
    private const val MIN_PLAUSIBLE_WH_PER_KM = 4.0
    private const val MAX_PLAUSIBLE_WH_PER_KM = 80.0

    fun isPlausibleRide(km: Double, wh: Double): Boolean {
        if (km <= 0.0 || wh <= 0.0) return false
        return (wh / km) in MIN_PLAUSIBLE_WH_PER_KM..MAX_PLAUSIBLE_WH_PER_KM
    }

    /** Energy left in the battery: the remaining charge at the current voltage - the same way the
     * consumption per km was measured, so the two fit together. */
    fun remainingWh(remainingMah: Long, voltage: Double): Double = remainingMah / 1000.0 * voltage

    fun rangeKm(remainingWh: Double, totals: ModeEfficiencyTotals?): Double? {
        if (totals == null || totals.totalKm < MIN_KM || remainingWh <= 0.0) return null
        val perKm = totals.recentWhPerKm
        return if (perKm > 0.0) remainingWh / perKm else null
    }
}
