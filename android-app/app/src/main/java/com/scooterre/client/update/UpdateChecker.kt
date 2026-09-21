package com.scooterre.client.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A newer release: its version, the release page and (if it has one) the APK asset to download.
 * [apkSha256] is GitHub's published digest of that asset, when available. */
data class UpdateInfo(
    val version: String,
    val url: String,
    val apkUrl: String? = null,
    val apkSha256: String? = null,
)

/** Looks up the newest published GitHub release of this project. Public API, no login; drafts and
 * pre-releases are never returned by `/releases/latest`. */
object UpdateChecker {
    private const val REPO = "desperado0044/xiaomi-scooter-link"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /** The only place an APK is ever downloaded from. */
    const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"

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
                parseRelease(response.body?.string() ?: return null)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Reads a GitHub release JSON. The APK asset is only taken if it really lives under [DOWNLOAD_PREFIX]. */
    fun parseRelease(body: String): UpdateInfo? {
        return try {
            val json = JSONObject(body)
            var apkUrl: String? = null
            var apkSha: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val url = asset.optString("browser_download_url")
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true) && url.startsWith(DOWNLOAD_PREFIX)) {
                        apkUrl = url
                        apkSha = normalizeDigest(asset.optString("digest"))
                        break
                    }
                }
            }
            UpdateInfo(json.getString("tag_name").removePrefix("v"), json.getString("html_url"), apkUrl, apkSha)
        } catch (e: Exception) {
            null
        }
    }

    /** "sha256:ABC..." -> "abc..." (lower case hex), or null if it is not a SHA-256 digest. */
    fun normalizeDigest(digest: String?): String? {
        val hex = digest?.removePrefix("sha256:")?.lowercase() ?: return null
        return if (digest.startsWith("sha256:") && Regex("[0-9a-f]{64}").matches(hex)) hex else null
    }

    /** True if [latest] is a higher dotted version than [installed] ("1.10" > "1.9", "v" prefix ignored; "2.6" > "2.6-alpha1"). */
    fun isNewer(latest: String, installed: String): Boolean {
        fun parts(version: String) = version.removePrefix("v").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return '-' in installed && '-' !in latest
    }
}
