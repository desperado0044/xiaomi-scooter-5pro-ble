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
fun UnitSystem.energyPerDistance(whPerKm: Double): Double = if (this == UnitSystem.IMPERIAL) whPerKm * KM_PER_MILE else whPerKm

val UnitSystem.distanceUnit: String get() = if (this == UnitSystem.IMPERIAL) "mi" else "km"
val UnitSystem.speedUnit: String get() = if (this == UnitSystem.IMPERIAL) "mph" else "km/h"
val UnitSystem.temperatureUnit: String get() = if (this == UnitSystem.IMPERIAL) "°F" else "°C"
