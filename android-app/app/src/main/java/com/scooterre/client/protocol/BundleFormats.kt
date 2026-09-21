package com.scooterre.client.protocol

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Tells apart the kinds of plain (unencrypted) ZIP files the app writes: scooter bundle, full backup
 * and documents-only file all carry a "format" field in their manifest.json. */
object BundleFormats {
    /** The "format" of the manifest.json inside a plain ZIP, or null if [data] is no such ZIP. */
    fun plainFormat(data: ByteArray): String? = try {
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            var format: String? = null
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "manifest.json") {
                    val json = JSONObject(String(zip.readBytesWithin(ZipBudget(1L * 1024 * 1024)), Charsets.UTF_8))
                    format = json.optString("format").ifEmpty { null }
                    break
                }
            }
            format
        }
    } catch (e: Exception) {
        null
    }
}
