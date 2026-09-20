package com.scooterre.client.protocol

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password encryption for the full backup: PBKDF2-HMAC-SHA256 -> AES-256-GCM.
 * Layout: "SCB3" | iterations (4 bytes, big endian) | salt (16) | iv (12) | ciphertext+tag.
 * The iteration count travels in the header, so it can be raised later without breaking old files.
 */
object BundleCrypto {
    private val MAGIC = "SCB3".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 600_000
    private const val HEADER = 4 + 4 + 16 + 12
    private const val TAG = 16

    fun isBackup(head: ByteArray): Boolean = head.size >= 4 && head.copyOf(4).contentEquals(MAGIC)

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plain: ByteArray, password: String, iterations: Int = ITERATIONS): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        return MAGIC + ByteBuffer.allocate(4).putInt(iterations).array() + salt + iv + cipher.doFinal(plain)
    }

    /** Null when the password is wrong or the file was changed/damaged (GCM cannot tell those apart). */
    fun decrypt(data: ByteArray, password: String): ByteArray? {
        if (data.size < HEADER + TAG || !isBackup(data)) return null
        val iterations = ByteBuffer.wrap(data, 4, 4).int
        if (iterations !in 1..5_000_000) return null
        val salt = data.copyOfRange(8, 24)
        val iv = data.copyOfRange(24, 36)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(data, HEADER, data.size - HEADER)
        } catch (e: BadPaddingException) {
            null
        }
    }
}
