package com.scooterre.client.protocol

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** One scooter the user has previously connected to. [model] is the Xiaomi cloud's own model
 * string (e.g. "xiaomi.scooter.5max", from [com.scooterre.client.cloud.CloudDeviceMatch.model]) -
 * null if the device was only ever connected via a saved key without a fresh cloud login (so the
 * model was never learned), in which case [SpecProfiles.forModel] falls back to the 5 Pro table. */
data class KnownDevice(val mac: String, val model: String?, val name: String?)

private const val PREFS_NAME = "scooter_prefs"
private const val KEY_KNOWN_DEVICES = "known_devices"
private const val KEY_LEGACY_LAST_MAC = "last_mac"

/**
 * Persists the set of scooters this app has connected to, so a second (or third) device can be
 * added without losing access to the first - each device's actual secret (`ltmk`) stays in
 * [SecureStore], already keyed per-MAC there; this registry only remembers *which* MACs exist and
 * their cosmetic model/name, using the same plain (non-encrypted) prefs file [ScooterViewModel]
 * already uses for the MAC/language settings, since none of this is secret (BLE MACs are
 * broadcast openly, model/name are cosmetic).
 */
class DeviceRegistry(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Reads the saved device list, migrating the old single-MAC ("last_mac") storage into it on
     * first read if the list is still empty - a pre-existing single-scooter install loses nothing
     * (its `ltmk` was already saved per-MAC in [SecureStore] regardless). */
    fun list(): List<KnownDevice> {
        val raw = prefs.getString(KEY_KNOWN_DEVICES, null)
        if (raw != null) {
            val array = JSONArray(raw)
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                KnownDevice(
                    mac = o.getString("mac"),
                    model = if (o.has("model")) o.getString("model") else null,
                    name = if (o.has("name")) o.getString("name") else null,
                )
            }
        }
        val legacyMac = prefs.getString(KEY_LEGACY_LAST_MAC, null)
        if (legacyMac.isNullOrBlank()) return emptyList()
        val migrated = listOf(KnownDevice(mac = legacyMac, model = null, name = null))
        save(migrated)
        return migrated
    }

    /** Adds a device or merges new (non-null) fields into an existing entry with the same MAC. */
    fun upsert(device: KnownDevice) {
        val current = list().toMutableList()
        val idx = current.indexOfFirst { it.mac.equals(device.mac, ignoreCase = true) }
        if (idx >= 0) {
            val existing = current[idx]
            current[idx] = existing.copy(
                model = device.model ?: existing.model,
                name = device.name ?: existing.name,
            )
        } else {
            current.add(device)
        }
        save(current)
    }

    fun remove(mac: String) {
        save(list().filterNot { it.mac.equals(mac, ignoreCase = true) })
    }

    /** Explicitly sets (or, with null, clears) a device's user-chosen name - unlike [upsert], this
     * replaces the field outright instead of only filling it in when it was previously empty, so a
     * user can deliberately clear a label they set earlier. No-op if the MAC isn't known. */
    fun setName(mac: String, name: String?) {
        val current = list().toMutableList()
        val idx = current.indexOfFirst { it.mac.equals(mac, ignoreCase = true) }
        if (idx < 0) return
        current[idx] = current[idx].copy(name = name)
        save(current)
    }

    private fun save(devices: List<KnownDevice>) {
        val array = JSONArray()
        for (d in devices) {
            val o = JSONObject()
            o.put("mac", d.mac)
            if (d.model != null) o.put("model", d.model)
            if (d.name != null) o.put("name", d.name)
            array.put(o)
        }
        prefs.edit().putString(KEY_KNOWN_DEVICES, array.toString()).apply()
    }
}

/** A saved device's MAC/model/name plus its `ltmk`, packed as one shareable text blob so a
 * second person authorized on the same physical scooter (e.g. a spouse) can add it on their own
 * phone without repeating the cloud login/PIN dance this project's own testing showed is often
 * genuinely painful (QR ticket timing, Google-account sign-in, etc.) - the `ltmk` is the same
 * secret Xiaomi's own cloud would hand to anyone who successfully logs into the account and asks
 * for it, so sharing it directly between two people who already jointly own the device isn't a
 * new capability, just a shortcut around re-deriving it the hard way. */
object DeviceExport {
    private const val TAG = "scooterre-export-v1:"

    fun encode(device: KnownDevice, ltmk: ByteArray): String {
        val o = JSONObject()
        o.put("mac", device.mac)
        if (device.model != null) o.put("model", device.model)
        if (device.name != null) o.put("name", device.name)
        o.put("ltmk", ltmk.joinToString("") { "%02x".format(it) })
        return TAG + Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /** Returns null on anything that doesn't parse - the caller shows a generic "invalid code"
     * error rather than a raw exception message, since this text is meant to be copy-pasted by
     * hand and any mangling (missed characters, wrong field) should look the same to the user. */
    fun decode(text: String): Pair<KnownDevice, ByteArray>? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(TAG)) return null
        return try {
            val json = String(Base64.decode(trimmed.removePrefix(TAG), Base64.NO_WRAP), Charsets.UTF_8)
            val o = JSONObject(json)
            val mac = o.getString("mac")
            val model = if (o.has("model")) o.getString("model") else null
            val name = if (o.has("name")) o.getString("name") else null
            val ltmkHex = o.getString("ltmk")
            val ltmk = ByteArray(ltmkHex.length / 2) {
                ((Character.digit(ltmkHex[it * 2], 16) shl 4) + Character.digit(ltmkHex[it * 2 + 1], 16)).toByte()
            }
            if (ltmk.size != 32) return null
            KnownDevice(mac, model, name) to ltmk
        } catch (e: Exception) {
            null
        }
    }
}
