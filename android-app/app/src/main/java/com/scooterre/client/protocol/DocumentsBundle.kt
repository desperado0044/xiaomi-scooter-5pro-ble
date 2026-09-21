package com.scooterre.client.protocol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Only the documents of one scooter - no key, no history - as a plain ZIP (manifest.json +
 * documents/<id>/<page>). Meant for family members who ride the same scooter: when a new insurance
 * paper exists, it is sent from one phone and merged on the other (documents already there stay).
 */
object DocumentsBundle {
    const val FORMAT = "scooterre-docs-v1"

    sealed interface ImportResult {
        data class Ok(val added: Int) : ImportResult
        data object UnknownScooter : ImportResult
        data object Invalid : ImportResult
    }

    /** Writes the documents of [mac] to [out] (blocking). False if the scooter is unknown or has no documents. */
    fun export(context: Context, mac: String, out: OutputStream): Boolean {
        val device = DeviceRegistry(context).list().firstOrNull { it.mac.equals(mac, ignoreCase = true) } ?: return false
        val docStore = DocumentStore(context)
        val docs = docStore.list(device.mac)
        if (docs.isEmpty()) return false
        val manifest = JSONObject().put("format", FORMAT).put("mac", device.mac).put(
            "documents",
            JSONArray(docs.map { d -> JSONObject().put("id", d.id).put("name", d.name).put("pages", JSONArray(d.pages)).put("added", d.addedMillis) }),
        )
        val zipBytes = ByteArrayOutputStream()
        ZipOutputStream(zipBytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            docs.forEach { d ->
                d.pages.forEachIndexed { i, page ->
                    zip.putNextEntry(ZipEntry("documents/${d.id}/$page"))
                    docStore.file(device.mac, d, i).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        out.write(zipBytes.toByteArray())
        out.flush()
        return true
    }

    /** Merges the documents of [data] into the scooter with the same MAC on this phone (blocking). */
    fun import(context: Context, data: ByteArray): ImportResult {
        return try {
            val entries = mutableMapOf<String, ByteArray>()
            val budget = ZipBudget()
            ZipInputStream(ByteArrayInputStream(data)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) entries[e.name] = zip.readBytesWithin(budget)
                }
            }
            val manifest = JSONObject(String(entries["manifest.json"] ?: return ImportResult.Invalid, Charsets.UTF_8))
            if (manifest.optString("format") != FORMAT) return ImportResult.Invalid
            val mac = manifest.getString("mac")
            val device = DeviceRegistry(context).list().firstOrNull { it.mac.equals(mac, ignoreCase = true) }
                ?: return ImportResult.UnknownScooter
            val docStore = DocumentStore(context)
            val before = docStore.count(device.mac)
            val docs = manifest.optJSONArray("documents") ?: JSONArray()
            for (i in 0 until docs.length()) {
                val o = docs.getJSONObject(i)
                val pages = o.getJSONArray("pages")
                val doc = ScooterDocument(o.getString("id"), o.getString("name"), (0 until pages.length()).map { pages.getString(it) }, o.getLong("added"))
                if (!DeviceBundle.SAFE_NAME.matches(doc.id) || doc.pages.any { !DeviceBundle.SAFE_NAME.matches(it) }) continue
                docStore.restore(device.mac, doc) { page -> entries["documents/${doc.id}/$page"]?.let { ByteArrayInputStream(it) } }
            }
            ImportResult.Ok(docStore.count(device.mac) - before)
        } catch (e: Exception) {
            ImportResult.Invalid
        }
    }
}
