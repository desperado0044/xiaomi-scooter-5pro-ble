package com.scooterre.client.protocol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow

/** One riding mode's lifetime totals: how far it's actually been ridden in that mode, and the
 * real-world energy cost per km derived from summing distance and energy separately before
 * dividing once (not averaging each ride's own ratio) - the actual battery-health signal a
 * healthy battery should hold roughly steady over time; a rising Wh/km in the same mode is the
 * kind of drift SOH% alone might not surface yet. */
data class ModeEfficiencyTotals(
    val totalKm: Double,
    val totalWh: Double,
    /** The same, but with older rides fading out (half-life [RECENT_HALF_LIFE_KM]) - what the range
     * estimate uses, so an ageing battery or a changed riding style shows up. Old data without it
     * starts out equal to the lifetime totals. */
    val recentKm: Double = totalKm,
    val recentWh: Double = totalWh,
) {
    val whPerKm: Double get() = if (totalKm > 0) totalWh / totalKm else 0.0
    val recentWhPerKm: Double get() = if (recentKm > 0) recentWh / recentKm else 0.0

    /** These totals plus one more ride; the recent totals fade by [RECENT_HALF_LIFE_KM] first. */
    fun withRide(km: Double, wh: Double): ModeEfficiencyTotals {
        val fade = 0.5.pow(km / RECENT_HALF_LIFE_KM)
        return ModeEfficiencyTotals(totalKm + km, totalWh + wh, recentKm * fade + km, recentWh * fade + wh)
    }
}

/** One point of the battery health log: at most one per day and scooter. */
data class BatteryLogEntry(val epochDay: Long, val soh: Long?, val cycles: Long?, val km: Double)

/** After this many km, an older ride counts half as much in the recent totals. */
const val RECENT_HALF_LIFE_KM = 150.0

private const val PREFS_NAME = "scooter_prefs"
private const val KEY_REF_PREFIX = "efficiency_ref_" // no longer written; removed when a scooter's history is cleared
private const val KEY_TOTALS_PREFIX = "efficiency_totals_"
private const val KEY_LOG_PREFIX = "battery_log_"
private const val MAX_LOG_ENTRIES = 400

/**
 * Real-world ride efficiency (km ridden and Wh consumed, per riding mode) as a small, fixed-size
 * running total per device, plus the daily battery health log. The totals are fed only by the live
 * ride log ([LiveRideTracker]): while the phone is connected to the scooter during a ride, the
 * distance and energy are attributed to the riding mode that was active - no questions asked. Rides
 * without a connection are simply not recorded. Uses the same plain (non-encrypted) prefs file
 * [DeviceRegistry] already uses - none of this is secret, it's just telemetry the device already
 * reports openly over BLE.
 */
class BatteryHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Adds one recorded ride segment to [mode]'s totals. */
    fun addRide(mac: String, mode: Long, km: Double, wh: Double) {
        val current = totals(mac).toMutableMap()
        current[mode] = (current[mode] ?: ModeEfficiencyTotals(0.0, 0.0)).withRide(km, wh)
        saveTotals(mac, current)
    }

    fun totals(mac: String): Map<Long, ModeEfficiencyTotals> {
        val raw = prefs.getString(KEY_TOTALS_PREFIX + mac, null) ?: return emptyMap()
        val o = JSONObject(raw)
        val result = mutableMapOf<Long, ModeEfficiencyTotals>()
        for (key in o.keys()) {
            val entry = o.getJSONObject(key)
            val km = entry.getDouble("km")
            val wh = entry.getDouble("wh")
            result[key.toLong()] = ModeEfficiencyTotals(km, wh, entry.optDouble("rkm", km), entry.optDouble("rwh", wh))
        }
        return result
    }

    /** Wipes one device's accumulated totals and log - offered behind a confirmation dialog in the
     * UI (matches [DeviceRegistry.remove]'s "Vergessen" pattern), since this is a one-way action with
     * no undo. Useful e.g. after a battery replacement, when the old efficiency numbers are no
     * longer comparable to future rides. */
    fun clear(mac: String) {
        prefs.edit().remove(KEY_REF_PREFIX + mac).remove(KEY_TOTALS_PREFIX + mac).remove(KEY_LOG_PREFIX + mac).apply()
    }

    /** Notes today's battery health (SOH, charge cycles) and odometer - one entry per day, the
     * latest reading of the day wins; the oldest entries drop out after [MAX_LOG_ENTRIES] days. */
    fun recordDaily(mac: String, soh: Long?, cycles: Long?, km: Double, epochDay: Long = java.time.LocalDate.now().toEpochDay()) {
        if (soh == null && cycles == null) return
        val entries = dailyLog(mac).filter { it.epochDay != epochDay } + BatteryLogEntry(epochDay, soh, cycles, km)
        saveLog(mac, entries.sortedBy { it.epochDay }.takeLast(MAX_LOG_ENTRIES))
    }

    fun dailyLog(mac: String): List<BatteryLogEntry> {
        val raw = prefs.getString(KEY_LOG_PREFIX + mac, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                BatteryLogEntry(
                    o.getLong("d"),
                    if (o.has("soh")) o.getLong("soh") else null,
                    if (o.has("cyc")) o.getLong("cyc") else null,
                    o.getDouble("km"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun exportLogRaw(mac: String): String? = prefs.getString(KEY_LOG_PREFIX + mac, null)

    /** Restores an exported log - but never overwrites one this phone already has. */
    fun importLogRaw(mac: String, log: String?) {
        if (log != null && prefs.getString(KEY_LOG_PREFIX + mac, null) == null) prefs.edit().putString(KEY_LOG_PREFIX + mac, log).apply()
    }

    private fun saveLog(mac: String, entries: List<BatteryLogEntry>) {
        val a = JSONArray()
        for (e in entries) {
            val o = JSONObject().put("d", e.epochDay).put("km", e.km)
            e.soh?.let { o.put("soh", it) }
            e.cycles?.let { o.put("cyc", it) }
            a.put(o)
        }
        prefs.edit().putString(KEY_LOG_PREFIX + mac, a.toString()).apply()
    }

    /** The raw stored per-mode totals JSON for an export bundle. */
    fun exportTotalsRaw(mac: String): String? = prefs.getString(KEY_TOTALS_PREFIX + mac, null)

    /** Restores exported totals - but never overwrites ones this phone already has. */
    fun importTotalsRaw(mac: String, totals: String?) {
        if (totals != null && prefs.getString(KEY_TOTALS_PREFIX + mac, null) == null) prefs.edit().putString(KEY_TOTALS_PREFIX + mac, totals).apply()
    }

    private fun saveTotals(mac: String, totals: Map<Long, ModeEfficiencyTotals>) {
        val o = JSONObject()
        for ((m, t) in totals) {
            o.put(m.toString(), JSONObject().put("km", t.totalKm).put("wh", t.totalWh).put("rkm", t.recentKm).put("rwh", t.recentWh))
        }
        prefs.edit().putString(KEY_TOTALS_PREFIX + mac, o.toString()).apply()
    }
}
