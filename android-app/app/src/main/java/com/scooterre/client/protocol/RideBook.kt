package com.scooterre.client.protocol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One ride as the scooter itself reports it: four numbers, in the scooter's own tenth units. */
data class RideRecord(val durationTenthsMin: Int, val distanceTenthsKm: Int, val avgTenthsKmh: Int, val topTenthsKmh: Int) {
    val minutes: Double get() = durationTenthsMin / 10.0
    val km: Double get() = distanceTenthsKm / 10.0
    val avgKmh: Double get() = avgTenthsKmh / 10.0
    val topKmh: Double get() = topTenthsKmh / 10.0
}

/** A ride kept in the app's own ride book. [savedMs] is when the app first saw it (a good stand-in for the
 * time of the ride, as the app reads the scooter right after a ride); 0 = seen before the ride book
 * existed, so the time is unknown. */
data class RideBookEntry(val savedMs: Long, val record: RideRecord)

/**
 * The scooter only keeps its last 5 ride-log slots (LOG_1..LOG_5, two records each) and overwrites the
 * oldest ones - the ride book copies rides out of them as they appear, so they aren't lost.
 */
object RideBook {
    const val MAX_ENTRIES = 2000

    /** One ride-log slot is a concatenation of 16-digit decimal records
     * "[duration*10 min:4][distance*10 km:4][avg-speed*10 kmh:4][top-speed*10 kmh:4]"; all-zero records
     * are empty. */
    fun parse(raw: String): List<RideRecord> {
        val s = raw.trim()
        val records = mutableListOf<RideRecord>()
        var i = 0
        while (i + 16 <= s.length) {
            val chunk = s.substring(i, i + 16)
            i += 16
            if (!chunk.all { it.isDigit() }) continue
            val r = RideRecord(
                chunk.substring(0, 4).toInt(), chunk.substring(4, 8).toInt(),
                chunk.substring(8, 12).toInt(), chunk.substring(12, 16).toInt(),
            )
            if (r.durationTenthsMin == 0 && r.distanceTenthsKm == 0 && r.avgTenthsKmh == 0 && r.topTenthsKmh == 0) continue
            records += r
        }
        return records
    }

    /** The records in [current] that were not in [previous] - as a multiset, so it doesn't matter where in
     * the slots a record sits, and two identical rides count twice. */
    fun newRecords(previous: List<RideRecord>, current: List<RideRecord>): List<RideRecord> {
        val remaining = previous.groupingBy { it }.eachCount().toMutableMap()
        val fresh = mutableListOf<RideRecord>()
        for (r in current) {
            val left = remaining[r] ?: 0
            if (left > 0) remaining[r] = left - 1 else fresh += r
        }
        return fresh
    }
}

/** The ride book per scooter, in the same plain prefs file as the other histories - none of it is secret. */
class RideBookStore(context: Context) {
    private val prefs = context.getSharedPreferences("scooter_prefs", Context.MODE_PRIVATE)

    fun entries(mac: String): List<RideBookEntry> = readEntries(prefs.getString(KEY_BOOK + mac, null))

    /**
     * Compares the scooter's current ride log with what was seen last time and adds the new rides.
     * Returns how many were added. The very first time (nothing seen, nothing saved) every ride in the
     * log is taken over without a time.
     */
    fun import(mac: String, current: List<RideRecord>, nowMs: Long = System.currentTimeMillis()): Int {
        val book = entries(mac)
        val seen = prefs.getString(KEY_SEEN + mac, null)?.let(::readRecords)
        val firstTime = seen == null && book.isEmpty()
        val previous = seen ?: book.map { it.record }
        val fresh = RideBook.newRecords(previous, current)
        val added = fresh.map { RideBookEntry(if (firstTime) 0 else nowMs, it) }
        val editor = prefs.edit().putString(KEY_SEEN + mac, writeRecords(current))
        if (added.isNotEmpty()) editor.putString(KEY_BOOK + mac, writeEntries((book + added).takeLast(RideBook.MAX_ENTRIES)))
        editor.apply()
        return added.size
    }

    fun clear(mac: String) {
        prefs.edit().remove(KEY_BOOK + mac).remove(KEY_SEEN + mac).apply()
    }

    fun exportRaw(mac: String): String? = prefs.getString(KEY_BOOK + mac, null)

    /** Restores an exported ride book - but never overwrites one this phone already has. */
    fun importRaw(mac: String, raw: String?) {
        if (raw != null && prefs.getString(KEY_BOOK + mac, null) == null) prefs.edit().putString(KEY_BOOK + mac, raw).apply()
    }

    private fun readEntries(raw: String?): List<RideBookEntry> {
        if (raw == null) return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                RideBookEntry(o.getLong("t"), RideRecord(o.getInt("dur"), o.getInt("dist"), o.getInt("avg"), o.getInt("top")))
            }
        }.getOrDefault(emptyList())
    }

    private fun writeEntries(entries: List<RideBookEntry>): String {
        val a = JSONArray()
        for (e in entries) {
            a.put(JSONObject().put("t", e.savedMs).put("dur", e.record.durationTenthsMin).put("dist", e.record.distanceTenthsKm).put("avg", e.record.avgTenthsKmh).put("top", e.record.topTenthsKmh))
        }
        return a.toString()
    }

    private fun readRecords(raw: String): List<RideRecord> = readEntries(raw).map { it.record }

    private fun writeRecords(records: List<RideRecord>): String = writeEntries(records.map { RideBookEntry(0, it) })

    private companion object {
        const val KEY_BOOK = "ride_book_"
        const val KEY_SEEN = "ride_book_seen_"
    }
}
