package com.scooterre.client.protocol

/**
 * The range the scooter would reach at the rider's own consumption in one riding mode: the battery
 * percentage left divided by the percent-per-km measured on the rider's real rides in the last
 * [RideWindow.WINDOW_KM] km (see [RideWindow] and [LiveRideTracker]). Only shown once a mode has
 * [MIN_KM] of data in the window; before that the scooter's own estimate is all there is. No
 * capacity rating and no voltage are needed for this - see [RideWindow]'s doc comment for why
 * percentage alone is enough for this particular comparison.
 */
object RangeEstimate {
    const val MIN_KM = 5.0

    // A real ride costs roughly 0.5-20 %/km (a full charge lasts somewhere around 20-90 km,
    // depending on mode and terrain). Far outside that, the odometer and the battery reading do
    // not belong to the same ride - typically because the scooter was charged (or ridden several
    // times) between two connections.
    private const val MIN_PLAUSIBLE_PERCENT_PER_KM = 0.5
    private const val MAX_PLAUSIBLE_PERCENT_PER_KM = 20.0

    fun isPlausibleRide(km: Double, percentUsed: Double): Boolean {
        if (km <= 0.0 || percentUsed <= 0.0) return false
        return (percentUsed / km) in MIN_PLAUSIBLE_PERCENT_PER_KM..MAX_PLAUSIBLE_PERCENT_PER_KM
    }

    fun rangeKm(remainingPercent: Double, stats: RideWindow.Stats?): Double? {
        if (stats == null || stats.km < MIN_KM) return null
        val perKm = stats.percentPerKm
        return if (perKm > 0.0) remainingPercent / perKm else null
    }
}
