package com.scooterre.client.protocol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One point of the battery health log: at most one per day and scooter, and only when health or cycles changed. */
data class BatteryLogEntry(val epochDay: Long, val soh: Long?, val cycles: Long?, val km: Double)

private const val PREFS_NAME = "scooter_prefs"
private const val KEY_WINDOW_PREFIX = "ride_window_"
// Older formats this store no longer writes, only still cleared on "Verlauf zurücksetzen" or
// "Vergessen" so nothing orphaned lingers on a phone updated from an older version.
private const val KEY_LEGACY_REF_PREFIX = "efficiency_ref_"
private const val KEY_LEGACY_TOTALS_PREFIX = "efficiency_totals_"
private const val KEY_LOG_PREFIX = "battery_log_"
private const val MAX_LOG_ENTRIES = 400

/**
 * The rolling ride window (see [RideWindow]) plus the daily battery health log, per device. The
 * window is fed only by the live ride log ([LiveRideTracker]): while the phone is connected to the
 * scooter during a ride, distance and battery percentage are attributed to the riding mode that
 * was active - no questions asked. Rides without a connection are simply not recorded. Uses the
 * same plain (non-encrypted) prefs file [DeviceRegistry] already uses - none of this is secret,
 * it's just telemetry the device already reports openly over BLE.
 */
class BatteryHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Adds one recorded ride segment to the window, then drops whatever has aged out of it. */
    fun addSegment(mac: String, mode: Long, km: Double, percentUsed: Double, startMs: Long = 0, endMs: Long = 0) {
        val entries = loadWindow(mac) + RideSegment(mode, km, percentUsed, startMs, endMs)
        saveWindow(mac, RideWindow.trim(entries))
    }

    fun windowStats(mac: String): Map<Long, RideWindow.Stats> = RideWindow.statsByMode(loadWindow(mac))

    /** The ride list, derived from the same window (see [RideLog]) - not stored separately. */
    fun rideLog(mac: String): RideLog.Result = RideLog.build(loadWindow(mac))

    /** Wipes one device's ride window and battery log - offered behind a confirmation dialog in
     * the UI ("Verlauf zurücksetzen"), since this is a one-way action with no undo. The right tool
     * for a *known* moment (e.g. a battery replacement) - the window (see [RideWindow]) already
     * handles the *unnoticed*, gradual kind (ageing, worn tyres) on its own. */
    fun clear(mac: String) {
        prefs.edit()
            .remove(KEY_WINDOW_PREFIX + mac).remove(KEY_LOG_PREFIX + mac)
            .remove(KEY_LEGACY_REF_PREFIX + mac).remove(KEY_LEGACY_TOTALS_PREFIX + mac)
            .apply()
    }

    /** Notes the battery health (SOH, charge cycles) and odometer - only when health or cycles differ
     * from the last entry, so days without any change add nothing; the latest reading of a day wins;
     * the oldest entries drop out after [MAX_LOG_ENTRIES]. */
    fun recordDaily(mac: String, soh: Long?, cycles: Long?, km: Double, epochDay: Long = java.time.LocalDate.now().toEpochDay()) {
        if (soh == null && cycles == null) return
        val before = dailyLog(mac).filter { it.epochDay != epochDay }
        val last = before.lastOrNull()
        if (last != null && last.soh == soh && last.cycles == cycles) return
        val entries = before + BatteryLogEntry(epochDay, soh, cycles, km)
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

    /** The raw stored ride-window JSON for an export bundle. */
    fun exportWindowRaw(mac: String): String? = prefs.getString(KEY_WINDOW_PREFIX + mac, null)

    /** Restores an exported window - but never overwrites one this phone already has. */
    fun importWindowRaw(mac: String, raw: String?) {
        if (raw != null && prefs.getString(KEY_WINDOW_PREFIX + mac, null) == null) prefs.edit().putString(KEY_WINDOW_PREFIX + mac, raw).apply()
    }

    private fun loadWindow(mac: String): List<RideSegment> {
        val raw = prefs.getString(KEY_WINDOW_PREFIX + mac, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                RideSegment(o.getLong("mode"), o.getDouble("km"), o.getDouble("pct"), o.optLong("t0", 0), o.optLong("t1", 0))
            }
        }.getOrDefault(emptyList())
    }

    private fun saveWindow(mac: String, entries: List<RideSegment>) {
        val a = JSONArray()
        for (e in entries) {
            val o = JSONObject().put("mode", e.mode).put("km", e.km).put("pct", e.percentUsed)
            if (e.startMs > 0) o.put("t0", e.startMs).put("t1", e.endMs)
            a.put(o)
        }
        prefs.edit().putString(KEY_WINDOW_PREFIX + mac, a.toString()).apply()
    }
}
