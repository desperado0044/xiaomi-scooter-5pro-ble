package com.scooterre.client.protocol

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists the per-device `ltmk` (32-byte long-term key, fetched once from Xiaomi's cloud with
 * the user's own account + device PIN) using Android Keystore-backed encryption at rest.
 * Never hardcoded in source - only ever written here after a successful cloud fetch.
 */
class SecureStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "scooter_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveLtmk(mac: String, ltmk: ByteArray) {
        prefs.edit().putString(ltmkKey(mac), ltmk.toHex()).apply()
    }

    fun loadLtmk(mac: String): ByteArray? = prefs.getString(ltmkKey(mac), null)?.fromHex()

    fun clearLtmk(mac: String) {
        prefs.edit().remove(ltmkKey(mac)).apply()
    }

    private fun ltmkKey(mac: String) = "ltmk_${mac.uppercase().replace(":", "")}"

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
