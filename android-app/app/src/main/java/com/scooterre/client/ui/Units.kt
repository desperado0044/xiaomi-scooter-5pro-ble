package com.scooterre.client.ui

import androidx.compose.runtime.compositionLocalOf

/** Display units only - the scooter itself always reports metric values, conversion happens at
 * the last moment before showing them. */
enum class UnitSystem { METRIC, IMPERIAL }

val LocalUnits = compositionLocalOf { UnitSystem.METRIC }

private const val KM_PER_MILE = 1.609344

fun UnitSystem.distance(km: Double): Double = if (this == UnitSystem.IMPERIAL) km / KM_PER_MILE else km
fun UnitSystem.speed(kmh: Double): Double = distance(kmh)
fun UnitSystem.temperature(celsius: Double): Double = if (this == UnitSystem.IMPERIAL) celsius * 9.0 / 5.0 + 32.0 else celsius
// A rate (something "per km", e.g. %/km) converts the other way round from a plain distance: a
// mile is longer than a km, so the same consumption costs more per mile, not less - multiply
// instead of divide.
fun UnitSystem.ratePerDistance(perKm: Double): Double = if (this == UnitSystem.IMPERIAL) perKm * KM_PER_MILE else perKm

val UnitSystem.distanceUnit: String get() = if (this == UnitSystem.IMPERIAL) "mi" else "km"
val UnitSystem.speedUnit: String get() = if (this == UnitSystem.IMPERIAL) "mph" else "km/h"
val UnitSystem.temperatureUnit: String get() = if (this == UnitSystem.IMPERIAL) "°F" else "°C"
