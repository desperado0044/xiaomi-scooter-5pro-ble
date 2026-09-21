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

/** Distance/energy consumed since the last time this device was connected, not yet attributed to
 * a riding mode - the app has no background service (see project research), so it cannot observe
 * which mode was active *during* a ride that happened while the app was closed. Instead of
 * guessing, [checkForPendingRide] surfaces this delta so the UI can ask the person who actually
 * knows: "you rode Xkm and used Ywh since Z - mostly which mode?" [sinceMillis] is null the very
 * first time a device is ever seen (nothing to compare against yet). */
data class PendingRideDelta(val km: Double, val wh: Double, val sinceMillis: Long?)

/** The single most recent reading a device's totals were last measured against - not a growing
 * history list. Refreshed every time a pending delta is resolved (attributed to a mode, or
 * explicitly skipped), so the next connection always measures from exactly where this one left
 * off, with nothing double-counted or silently dropped. */
private data class ReferencePoint(val timestamp: Long, val totalMileageKm: Double, val remainingBatteryMah: Long, val voltage: Double)

/** A per-connection delta smaller than this is not worth asking about - e.g. reconnecting a few
 * times in a row while debugging a BLE issue, or opening the app just to check the battery level
 * without riding at all. */
private const val MIN_PENDING_KM = 0.1

private const val PREFS_NAME = "scooter_prefs"
private const val KEY_REF_PREFIX = "efficiency_ref_"
private const val KEY_TOTALS_PREFIX = "efficiency_totals_"
private const val KEY_LOG_PREFIX = "battery_log_"
private const val MAX_LOG_ENTRIES = 400

/**
 * Tracks real-world ride efficiency (km ridden and Wh consumed, per riding mode) as a small,
 * fixed-size running total per device - not a growing log, and not sampled continuously while
 * riding (the app has no background service, so it can't be). Instead, each time the app
 * reconnects to a device, [checkForPendingRide] compares the scooter's own odometer/battery
 * readings against the last known reading and reports what changed since then; the UI asks the
 * user which mode that reflects, and [attributeRide] folds the answer into that mode's lifetime
 * total. Uses the same plain (non-encrypted) prefs file [DeviceRegistry] already uses - none of
 * this is secret, it's just telemetry the device already reports openly over BLE.
 */
class BatteryHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Call once right after a fresh connect, with the just-read TOTAL_MILEAGE (km)/
     * REMAINING_BATTERY (mAh)/VOLTAGE (V). Returns a delta to ask the user about if the odometer
     * has moved meaningfully since the last time this device connected and the battery was
     * actually drawn down (not charged) in between - null if there's nothing worth asking about
     * (first-ever connection, negligible movement, or the battery went up since last time,
     * meaning it was charged rather than ridden). Does **not** update the stored reference point
     * itself - that only happens once the caller resolves the delta via [attributeRide] or
     * [skipPendingRide], so a delta the user hasn't answered yet isn't silently lost if they
     * disconnect before responding.
     */
    fun checkForPendingRide(mac: String, totalMileageKm: Double, remainingBatteryMah: Long, voltage: Double): PendingRideDelta? {
        val ref = loadReference(mac) ?: run {
            saveReference(mac, ReferencePoint(System.currentTimeMillis(), totalMileageKm, remainingBatteryMah, voltage))
            return null
        }
        val deltaKm = totalMileageKm - ref.totalMileageKm
        if (deltaKm < MIN_PENDING_KM) return null
        val deltaMah = ref.remainingBatteryMah - remainingBatteryMah
        if (deltaMah <= 0) return null // battery went up (charged) since last time, not ridden
        val avgVoltage = (ref.voltage + voltage) / 2.0
        val deltaWh = deltaMah / 1000.0 * avgVoltage
        if (deltaWh <= 0) return null
        return PendingRideDelta(deltaKm, deltaWh, ref.timestamp)
    }

    /** Folds a resolved pending delta into [mode]'s lifetime total, then advances the reference
     * point to the current reading so the next connection measures from here. */
    fun attributeRide(mac: String, mode: Long, delta: PendingRideDelta, totalMileageKm: Double, remainingBatteryMah: Long, voltage: Double) {
        val current = totals(mac).toMutableMap()
        val existing = current[mode] ?: ModeEfficiencyTotals(0.0, 0.0)
        current[mode] = existing.withRide(delta.km, delta.wh)
        saveTotals(mac, current)
        saveReference(mac, ReferencePoint(System.currentTimeMillis(), totalMileageKm, remainingBatteryMah, voltage))
    }

    /** The user didn't know/didn't want to attribute this delta to a mode - it's dropped (not
     * counted toward any mode's total), and the reference point still advances so the same delta
     * isn't asked about again next connect. */
    fun skipPendingRide(mac: String, totalMileageKm: Double, remainingBatteryMah: Long, voltage: Double) {
        saveReference(mac, ReferencePoint(System.currentTimeMillis(), totalMileageKm, remainingBatteryMah, voltage))
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

    /** Wipes one device's accumulated totals and reference point - offered behind a confirmation
     * dialog in the UI (matches [DeviceRegistry.remove]'s "Vergessen" pattern), since this is a
     * one-way action with no undo. Useful e.g. after a battery replacement, when the old
     * efficiency numbers are no longer comparable to future rides. */
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

    /** The raw stored JSON (reference point, per-mode totals) for an export bundle. */
    fun exportRaw(mac: String): Pair<String?, String?> =
        prefs.getString(KEY_REF_PREFIX + mac, null) to prefs.getString(KEY_TOTALS_PREFIX + mac, null)

    /** Restores an exported history - but never overwrites one this phone already has. */
    fun importRaw(mac: String, reference: String?, totals: String?) {
        val edit = prefs.edit()
        if (reference != null && prefs.getString(KEY_REF_PREFIX + mac, null) == null) edit.putString(KEY_REF_PREFIX + mac, reference)
        if (totals != null && prefs.getString(KEY_TOTALS_PREFIX + mac, null) == null) edit.putString(KEY_TOTALS_PREFIX + mac, totals)
        edit.apply()
    }

    private fun saveTotals(mac: String, totals: Map<Long, ModeEfficiencyTotals>) {
        val o = JSONObject()
        for ((m, t) in totals) {
            o.put(m.toString(), JSONObject().put("km", t.totalKm).put("wh", t.totalWh).put("rkm", t.recentKm).put("rwh", t.recentWh))
        }
        prefs.edit().putString(KEY_TOTALS_PREFIX + mac, o.toString()).apply()
    }

    private fun loadReference(mac: String): ReferencePoint? {
        val raw = prefs.getString(KEY_REF_PREFIX + mac, null) ?: return null
        val o = JSONObject(raw)
        return ReferencePoint(o.getLong("t"), o.getDouble("km"), o.getLong("mah"), o.getDouble("v"))
    }

    private fun saveReference(mac: String, ref: ReferencePoint) {
        val o = JSONObject()
        o.put("t", ref.timestamp)
        o.put("km", ref.totalMileageKm)
        o.put("mah", ref.remainingBatteryMah)
        o.put("v", ref.voltage)
        prefs.edit().putString(KEY_REF_PREFIX + mac, o.toString()).apply()
    }
}
