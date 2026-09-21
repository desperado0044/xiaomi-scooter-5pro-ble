package com.scooterre.client.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short text a person can paste into a bug report: app/phone/scooter model, the settings that
 * matter and the last error messages. Deliberately without anything personal - no MAC address,
 * serial number, key, document or account data; error texts are scrubbed by [redact] as well.
 */
object Diagnostics {
    private const val MAX_ERRORS = 15
    private const val MAX_LOG = 60
    private const val MAX_ERROR_LENGTH = 200

    data class Info(
        val app: String,
        val android: String,
        val phone: String,
        val settings: List<Pair<String, String>>,
        val scooter: String,
        val errors: List<Pair<Long, String>>,
        val log: List<Pair<Long, String>> = emptyList(),
    )

    private val errors = ArrayDeque<Pair<Long, String>>()

    @Synchronized
    fun recordError(text: String, nowMillis: Long = System.currentTimeMillis()) {
        val clean = redact(text).take(MAX_ERROR_LENGTH)
        if (clean.isBlank() || errors.lastOrNull()?.second == clean) return
        errors.addLast(nowMillis to clean)
        while (errors.size > MAX_ERRORS) errors.removeFirst()
    }

    @Synchronized
    fun recentErrors(): List<Pair<Long, String>> = errors.toList()

    private val log = ArrayDeque<Pair<Long, String>>()

    /** A step of what the app is doing (connect, login, model, checks) - the debug log in the diagnostics text. */
    @Synchronized
    fun note(text: String, nowMillis: Long = System.currentTimeMillis()) {
        log.addLast(nowMillis to redact(text).take(MAX_ERROR_LENGTH))
        while (log.size > MAX_LOG) log.removeFirst()
    }

    @Synchronized
    fun recentLog(): List<Pair<Long, String>> = log.toList()

    @Synchronized
    fun clearErrors() {
        errors.clear()
        log.clear()
    }

    private val urlQuery = Regex("(https?://[^\\s?#]+)[?#]\\S*")
    private val email = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val mac = Regex("(?i)\\b[0-9a-f]{2}([:-][0-9a-f]{2}){5}\\b")
    private val longHex = Regex("(?i)\\b[0-9a-f]{16,}\\b")
    private val longToken = Regex("\\b[A-Za-z0-9_-]{32,}\\b")

    /** Removes what could identify a person or open a door: URL parameters, e-mail addresses, MAC
     * addresses, long hex strings (keys, serials) and other long token-like strings. */
    fun redact(text: String): String = text
        .replace(urlQuery, "$1?<removed>")
        .replace(email, "<email>")
        .replace(mac, "<mac>")
        .replace(longHex, "<hex>")
        .replace(longToken, "<token>")

    fun build(info: Info): String = buildString {
        appendLine("Scooter Link diagnostics")
        appendLine("App: ${info.app}")
        appendLine("Android: ${info.android}")
        appendLine("Phone: ${info.phone}")
        appendLine("Scooter: ${info.scooter}")
        appendLine("Settings: " + info.settings.joinToString(", ") { "${it.first}=${it.second}" })
        if (info.errors.isEmpty()) {
            appendLine("Recent errors: none")
        } else {
            appendLine("Recent errors:")
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
            info.errors.forEach { (time, message) -> appendLine("  ${format.format(Date(time))}  $message") }
        }
        if (info.log.isNotEmpty()) {
            appendLine("Log:")
            val format = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
            info.log.forEach { (time, message) -> appendLine("  ${format.format(Date(time))}  $message") }
        }
    }.trimEnd()
}
