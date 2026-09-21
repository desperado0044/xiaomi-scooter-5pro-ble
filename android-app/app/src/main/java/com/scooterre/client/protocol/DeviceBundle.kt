package com.scooterre.client.protocol

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Everything that belongs to one scooter in a single file: the BLE key, name/model, the
 * documents and the consumption history. A ZIP (manifest.json + documents/<id>/<page>), optionally
 * wrapped in AES-256-GCM with a key derived from a password (the scooter PIN, in practice) - the file
 * carries the key and personal papers, so it should not travel unprotected. The plain text export code
 * ([DeviceExport]) stays for quick key-only sharing.
 *
 * Encrypted layout: "SCB2" | salt(16) | iv(12) | AES-GCM(zip bytes).
 */
object DeviceBundle {
    private const val FORMAT = "scooterre-bundle-v2"
    private val MAGIC = "SCB2".toByteArray(Charsets.US_ASCII)
    private const val PBKDF2_ITERATIONS = 200_000
    internal val SAFE_NAME = Regex("[A-Za-z0-9._-]{1,80}")

    enum class Kind { ENCRYPTED, PLAIN, NONE }

    sealed interface ImportResult {
        data class Ok(val device: KnownDevice) : ImportResult
        data object BadPassword : ImportResult
        data object Invalid : ImportResult
    }

    fun kindOf(head: ByteArray): Kind = when {
        // SCB2 = one scooter, SCB3 = full backup (told apart by the importer)
        head.size >= 4 && (head.copyOf(4).contentEquals(MAGIC) || head.copyOf(4).contentEquals("SCB3".toByteArray(Charsets.US_ASCII))) -> Kind.ENCRYPTED
        head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> Kind.PLAIN
        else -> Kind.NONE
    }

    /** Writes the bundle of [mac] to [out] (blocking - call off the main thread). False if the scooter
     * or its key is not saved. An empty [password] writes the plain, unencrypted ZIP. */
    fun export(context: Context, mac: String, out: OutputStream, password: String?): Boolean {
        val device = DeviceRegistry(context).list().firstOrNull { it.mac.equals(mac, ignoreCase = true) } ?: return false
        val ltmk = SecureStore(context).loadLtmk(device.mac) ?: return false
        val docStore = DocumentStore(context)
        val docs = docStore.list(device.mac)
        val totals = BatteryHistoryStore(context).exportTotalsRaw(device.mac)

        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("mac", device.mac)
            .put("ltmk", ltmk.joinToString("") { "%02x".format(it) })
        device.model?.let { manifest.put("model", it) }
        device.name?.let { manifest.put("name", it) }
        totals?.let { manifest.put("efficiencyTotals", JSONObject(it)) }
        BatteryHistoryStore(context).exportLogRaw(device.mac)?.let { manifest.put("batteryLog", JSONArray(it)) }
        manifest.put(
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
        out.write(if (password.isNullOrEmpty()) zipBytes.toByteArray() else encrypt(zipBytes.toByteArray(), password))
        out.flush()
        return true
    }

    /** Restores a bundle (blocking). Existing documents (same id) and an existing local consumption
     * history are kept; the key, name and model are taken from the file. */
    fun import(context: Context, data: ByteArray, password: String?): ImportResult {
        val zipBytes = when (kindOf(data)) {
            Kind.ENCRYPTED -> {
                if (password.isNullOrEmpty()) return ImportResult.BadPassword
                decrypt(data, password) ?: return ImportResult.BadPassword
            }
            Kind.PLAIN -> data
            Kind.NONE -> return ImportResult.Invalid
        }
        return try {
            val entries = mutableMapOf<String, ByteArray>()
            val budget = ZipBudget()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (!e.isDirectory) entries[e.name] = zip.readBytesWithin(budget)
                }
            }
            val manifest = JSONObject(String(entries["manifest.json"] ?: return ImportResult.Invalid, Charsets.UTF_8))
            if (manifest.optString("format") != FORMAT) return ImportResult.Invalid
            val mac = manifest.getString("mac")
            val ltmkHex = manifest.getString("ltmk")
            if (ltmkHex.length != 64) return ImportResult.Invalid
            val ltmk = ByteArray(32) { ((Character.digit(ltmkHex[it * 2], 16) shl 4) + Character.digit(ltmkHex[it * 2 + 1], 16)).toByte() }
            val device = KnownDevice(
                mac,
                if (manifest.has("model")) manifest.getString("model") else null,
                if (manifest.has("name")) manifest.getString("name") else null,
            )
            SecureStore(context).saveLtmk(mac, ltmk)
            DeviceRegistry(context).upsert(device)
            BatteryHistoryStore(context).importTotalsRaw(mac, manifest.optJSONObject("efficiencyTotals")?.toString())
            BatteryHistoryStore(context).importLogRaw(mac, manifest.optJSONArray("batteryLog")?.toString())

            val docStore = DocumentStore(context)
            val docs = manifest.optJSONArray("documents") ?: JSONArray()
            for (i in 0 until docs.length()) {
                val o = docs.getJSONObject(i)
                val pages = o.getJSONArray("pages")
                val doc = ScooterDocument(o.getString("id"), o.getString("name"), (0 until pages.length()).map { pages.getString(it) }, o.getLong("added"))
                if (!SAFE_NAME.matches(doc.id) || doc.pages.any { !SAFE_NAME.matches(it) }) continue
                docStore.restore(mac, doc) { page -> entries["documents/${doc.id}/$page"]?.let { ByteArrayInputStream(it) } }
            }
            ImportResult.Ok(device)
        } catch (e: Exception) {
            ImportResult.Invalid
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    private fun encrypt(plain: ByteArray, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    /** Null when the password is wrong (or the file was damaged - GCM cannot tell those apart). */
    private fun decrypt(data: ByteArray, password: String): ByteArray? {
        if (data.size < MAGIC.size + 16 + 12 + 16) return null
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(data, 32, data.size - 32)
        } catch (e: AEADBadTagException) {
            null
        }
    }
}
