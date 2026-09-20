package com.scooterre.client.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(val version: String, val url: String)

/** Looks up the newest published GitHub release of this project. Public API, no login; drafts and
 * pre-releases are never returned by `/releases/latest`. */
object UpdateChecker {
    private const val LATEST_URL = "https://api.github.com/repos/desperado0044/xiaomi-scooter-5pro-ble/releases/latest"

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Blocking - call off the main thread. Null on any failure (offline, rate-limited, bad JSON). */
    fun fetchLatest(): UpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "scooter-client-update-check")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: return null)
                UpdateInfo(json.getString("tag_name").removePrefix("v"), json.getString("html_url"))
            }
        } catch (e: Exception) {
            null
        }
    }

    /** True if [latest] is a higher dotted version than [installed] ("1.10" > "1.9", "v" prefix ignored). */
    fun isNewer(latest: String, installed: String): Boolean {
        fun parts(version: String) = version.removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
