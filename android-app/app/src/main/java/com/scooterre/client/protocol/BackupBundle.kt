package com.scooterre.client.protocol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup of everything the app holds: every scooter (as the same per-scooter bundle the single
 * export writes - key, name/model, documents, history) plus the app settings, packed into one ZIP and
 * encrypted with a password ([BundleCrypto]). The password is mandatory; without it the file is unreadable.
 * Restoring merges: keys/names come from the file, existing documents and history stay.
 */
object BackupBundle {
    private const val FORMAT = "scooterre-backup-v3"
    private const val MAX_BYTES = 200L * 1024 * 1024
    private const val PREFS = "scooter_prefs"

    // App lock, last connected scooter and the update cache are deliberately not part of a backup.
    val SETTINGS_KEYS = listOf(
        "lang", "theme_mode", "keep_screen_on", "auto_brightness", "units",
        "auto_connect", "refresh_rate", "confirm_critical", "ride_tracking", "update_check",
    )

    sealed interface RestoreResult {
        data class Ok(val devices: Int, val documents: Int) : RestoreResult
        data object BadPassword : RestoreResult
        data object Invalid : RestoreResult
        data object TooLarge : RestoreResult
    }

    /** The encrypted backup, or null if it would exceed the size limit (built in memory). */
    fun create(context: Context, password: String): ByteArray? {
        val registry = DeviceRegistry(context)
        val secure = SecureStore(context)
        val docs = DocumentStore(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val settings = JSONObject()
        for (k in SETTINGS_KEYS) {
            when (val v = prefs.all[k]) {
                is Boolean -> settings.put(k, v)
                is String -> settings.put(k, v)
            }
        }
        val devices = JSONArray()
        var total = 0L
        val zipBytes = ByteArrayOutputStream()
        ZipOutputStream(zipBytes).use { zip ->
            for (d in registry.list()) {
                if (secure.loadLtmk(d.mac) == null) continue
                val inner = ByteArrayOutputStream()
                if (!DeviceBundle.export(context, d.mac, inner, null)) continue
                total += inner.size()
                if (total > MAX_BYTES) return null
                zip.putNextEntry(ZipEntry("devices/${d.mac.replace(":", "")}.bundle"))
                zip.write(inner.toByteArray())
                zip.closeEntry()
                devices.put(JSONObject().put("mac", d.mac).put("documents", docs.count(d.mac)))
            }
            val manifest = JSONObject().put("format", FORMAT).put("created", System.currentTimeMillis())
                .put("settings", settings).put("devices", devices)
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return BundleCrypto.encrypt(zipBytes.toByteArray(), password)
    }

    /** Checks and decrypts everything first; only then writes anything. */
    fun restore(context: Context, data: ByteArray, password: String, withSettings: Boolean): RestoreResult {
        if (data.size > MAX_BYTES + 1024 * 1024) return RestoreResult.TooLarge
        if (!BundleCrypto.isBackup(data)) return RestoreResult.Invalid
        val zipBytes = BundleCrypto.decrypt(data, password) ?: return RestoreResult.BadPassword
        return try {
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) entries[e.name] = zip.readBytes()
                }
            }
            val manifest = JSONObject(String(entries["manifest.json"] ?: return RestoreResult.Invalid, Charsets.UTF_8))
            if (manifest.optString("format") != FORMAT) return RestoreResult.Invalid
            var devices = 0
            var documents = 0
            val list = manifest.getJSONArray("devices")
            for (i in 0 until list.length()) {
                val o = list.getJSONObject(i)
                val bytes = entries["devices/${o.getString("mac").replace(":", "")}.bundle"] ?: continue
                if (DeviceBundle.import(context, bytes, null) is DeviceBundle.ImportResult.Ok) {
                    devices++
                    documents += o.optInt("documents")
                }
            }
            val settings = manifest.optJSONObject("settings")
            if (withSettings && settings != null) {
                val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                for (k in SETTINGS_KEYS) {
                    if (!settings.has(k)) continue
                    when (val v = settings.get(k)) {
                        is Boolean -> edit.putBoolean(k, v)
                        is String -> edit.putString(k, v)
                    }
                }
                edit.apply()
            }
            RestoreResult.Ok(devices, documents)
        } catch (e: Exception) {
            RestoreResult.Invalid
        }
    }
}
