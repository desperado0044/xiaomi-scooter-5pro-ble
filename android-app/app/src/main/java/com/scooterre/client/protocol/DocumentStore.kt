package com.scooterre.client.protocol

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

/** One document of a scooter: either several JPEG pages (photos/scans) or a single PDF. */
data class ScooterDocument(val id: String, val name: String, val pages: List<String>, val addedMillis: Long) {
    val isPdf: Boolean get() = pages.firstOrNull()?.endsWith(".pdf", ignoreCase = true) == true
}

private const val PREFS_NAME = "scooter_documents"
private const val MAX_EDGE_PX = 2400
private const val JPEG_QUALITY = 85

/**
 * Per-scooter document storage (insurance confirmation, registration papers, ...) - app-private
 * files, so nothing shows up in the gallery and no storage permission is needed. Layout:
 * `files/documents/<MAC without colons>/<document id>/page_N.jpg | document.pdf`, plus one JSON list
 * per scooter in SharedPreferences (same pattern as [DeviceRegistry]) - deliberately simple so a later
 * backup/share feature can pack exactly these folders.
 */
class DocumentStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(mac: String) = "docs_" + mac.uppercase()
    private fun docDir(mac: String, id: String) = File(context.filesDir, "documents/" + mac.replace(":", "").uppercase() + "/" + id)

    fun list(mac: String): List<ScooterDocument> {
        val raw = prefs.getString(key(mac), null) ?: return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val pages = o.getJSONArray("pages")
            ScooterDocument(o.getString("id"), o.getString("name"), (0 until pages.length()).map { pages.getString(it) }, o.getLong("added"))
        }
    }

    private fun save(mac: String, docs: List<ScooterDocument>) {
        val array = JSONArray()
        docs.forEach { d ->
            array.put(JSONObject().put("id", d.id).put("name", d.name).put("pages", JSONArray(d.pages)).put("added", d.addedMillis))
        }
        prefs.edit().putString(key(mac), array.toString()).apply()
    }

    fun count(mac: String): Int = list(mac).size

    fun file(mac: String, doc: ScooterDocument, page: Int): File = File(docDir(mac, doc.id), doc.pages[page])

    /** Stores a photo/image as a new one-page document. Throws if the image cannot be decoded. */
    fun addImage(mac: String, name: String, open: () -> InputStream?): ScooterDocument {
        val id = UUID.randomUUID().toString()
        val dir = docDir(mac, id).apply { mkdirs() }
        try {
            writeJpeg(open, File(dir, "page_1.jpg"))
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        val doc = ScooterDocument(id, name, listOf("page_1.jpg"), System.currentTimeMillis())
        save(mac, list(mac) + doc)
        return doc
    }

    fun appendImage(mac: String, docId: String, open: () -> InputStream?) {
        val docs = list(mac)
        val doc = docs.firstOrNull { it.id == docId } ?: return
        if (doc.isPdf) return
        val fileName = "page_${doc.pages.size + 1}.jpg"
        writeJpeg(open, File(docDir(mac, docId), fileName))
        save(mac, docs.map { if (it.id == docId) it.copy(pages = it.pages + fileName) else it })
    }

    fun addPdf(mac: String, name: String, open: () -> InputStream?): ScooterDocument {
        val id = UUID.randomUUID().toString()
        val dir = docDir(mac, id).apply { mkdirs() }
        try {
            val input = open() ?: error("cannot open file")
            input.use { src -> File(dir, "document.pdf").outputStream().use { src.copyTo(it) } }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        val doc = ScooterDocument(id, name, listOf("document.pdf"), System.currentTimeMillis())
        save(mac, list(mac) + doc)
        return doc
    }

    fun rename(mac: String, docId: String, name: String) {
        save(mac, list(mac).map { if (it.id == docId) it.copy(name = name) else it })
    }

    fun delete(mac: String, docId: String) {
        docDir(mac, docId).deleteRecursively()
        save(mac, list(mac).filterNot { it.id == docId })
    }

    /** Decodes, applies the EXIF rotation, shrinks to [MAX_EDGE_PX] and writes a JPEG - a raw
     * 50 MP camera shot would otherwise be ~10 MB per page. */
    private fun writeJpeg(open: () -> InputStream?, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream returns null when only the bounds are requested - that is not a failure.
        (open() ?: error("cannot open image")).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("not an image")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        var bitmap = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("cannot decode image")
        val rotation = runCatching { open()?.use { rotationDegrees(ExifInterface(it)) } }.getOrNull() ?: 0
        if (rotation != 0) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge > MAX_EDGE_PX) {
            val scale = MAX_EDGE_PX.toFloat() / longEdge
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    }

    private fun rotationDegrees(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
