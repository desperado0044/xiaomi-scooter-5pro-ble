package com.scooterre.client.crypto

import com.scooterre.client.ble.Protocol
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto for the CONFIRMED-working "Dreame/Xiaomi Scooter 5 Pro" security-chip protocol
 * (KuziaMother/SCOOTER_5_PRO reference, live-verified 2026-09-13 against the real device via
 * core/dreame_auth.py): ECDH-P256 -> HKDF-SHA256(shared||ltmk, salt="smartcfg-login-salt",
 * info="smartcfg-login-info") -> 64-byte session key -> AES-CCM confirmation
 * (key=sk[16:32], nonce=fixed 0x10..0x1b, plaintext=CRC32(devicePubKey) LE).
 */
object MiCrypto {

    private const val CURVE_NAME = "secp256r1"

    // --- ECDH ---

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(CURVE_NAME), SecureRandom())
        return kpg.generateKeyPair()
    }

    /** Device sends/receives its public key as a raw 64-byte X||Y point, no 0x04 prefix on the wire. */
    fun publicKeyFromRawXY(xy64: ByteArray): PublicKey {
        require(xy64.size == 64) { "expected 64-byte raw X||Y point, got ${xy64.size} bytes" }
        val x = BigInteger(1, xy64.copyOfRange(0, 32))
        val y = BigInteger(1, xy64.copyOfRange(32, 64))
        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec(CURVE_NAME))
        }
        val ecParameterSpec = params.getParameterSpec(ECParameterSpec::class.java)
        val pubSpec = ECPublicKeySpec(ECPoint(x, y), ecParameterSpec)
        return KeyFactory.getInstance("EC").generatePublic(pubSpec)
    }

    fun publicKeyToRawXY(pub: PublicKey): ByteArray {
        val ecPub = pub as ECPublicKey
        val x = ecPub.w.affineX.toFixedByteArray(32)
        val y = ecPub.w.affineY.toFixedByteArray(32)
        return x + y
    }

    private fun BigInteger.toFixedByteArray(len: Int): ByteArray {
        val raw = this.toByteArray()
        return when {
            raw.size == len -> raw
            raw.size == len + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
            raw.size < len -> ByteArray(len - raw.size) + raw
            else -> throw IllegalStateException("unexpected BigInteger encoding length ${raw.size} for target $len")
        }
    }

    fun ecdh(privateKey: PrivateKey, remotePublicKey: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(remotePublicKey, true)
        return ka.generateSecret()
    }

    // --- HMAC / HKDF (RFC 5869), SHA-256 ---

    private const val HASH_LEN = 32

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        val actualSalt = salt ?: ByteArray(HASH_LEN)
        val prk = hmacSha256(actualSalt, ikm)

        val n = (length + HASH_LEN - 1) / HASH_LEN
        require(n <= 255) { "HKDF output too long" }

        val okm = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        for (i in 1..n) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            val copyLen = minOf(HASH_LEN, length - pos)
            System.arraycopy(t, 0, okm, pos, copyLen)
            pos += copyLen
        }
        return okm
    }

    // --- AES-128-CCM (4-byte tag) ---

    fun aesCcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray = ByteArray(0), tagLenBits: Int = 32): ByteArray {
        val cipher = Cipher.getInstance("AES/CCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagLenBits, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun aesCcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray = ByteArray(0), tagLenBits: Int = 32): ByteArray {
        val cipher = Cipher.getInstance("AES/CCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(tagLenBits, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    /** AES-128-CBC/NoPadding, used only to unwrap a PIN-protected ltmk from the cloud response. */
    fun aesCbcDecryptNoPadding(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    fun crc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }

    // --- Session key derivation + login confirmation ---

    /** shared secret (32B) + 64-byte session key, per HKDF(shared||ltmk, salt, info). */
    data class SessionKeys(val shared: ByteArray, val sessionKey: ByteArray) {
        /** Key used for app -> device SPEC writes (also the login-confirmation key). */
        val appKey: ByteArray get() = sessionKey.copyOfRange(16, 32)
        val appIv: ByteArray get() = sessionKey.copyOfRange(36, 40)

        /** Key used for device -> app SPEC responses. */
        val devKey: ByteArray get() = sessionKey.copyOfRange(0, 16)
        val devIv: ByteArray get() = sessionKey.copyOfRange(32, 36)
    }

    fun deriveSessionKeys(myPrivateKey: PrivateKey, devicePublicKeyRawXY: ByteArray, ltmk: ByteArray): SessionKeys {
        val devicePublicKey = publicKeyFromRawXY(devicePublicKeyRawXY)
        val shared = ecdh(myPrivateKey, devicePublicKey)
        val keymix = shared + ltmk
        val sessionKey = hkdfSha256(keymix, Protocol.HKDF_SALT, Protocol.HKDF_INFO, 64)
        return SessionKeys(shared, sessionKey)
    }

    /** Builds the AES-CCM login-confirmation payload sent on the login channel. */
    fun buildLoginConfirmation(keys: SessionKeys, devicePublicKeyRawXY: ByteArray): ByteArray {
        val crc = crc32(devicePublicKeyRawXY)
        val plaintext = byteArrayOf(
            (crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte(),
            ((crc shr 16) and 0xFF).toByte(), ((crc shr 24) and 0xFF).toByte()
        )
        return aesCcmEncrypt(keys.appKey, Protocol.CCM_LOGIN_NONCE, plaintext)
    }

    // --- MIoT SPEC channel encryption (per-message counter, 4-byte tag) ---

    private fun counterBytes(counter: Int): ByteArray {
        val low = counter and 0xFFFF
        val high = (counter ushr 16) and 0xFFFF
        return byteArrayOf((low and 0xFF).toByte(), (low ushr 8).toByte(), (high and 0xFF).toByte(), (high ushr 8).toByte())
    }

    /** Encrypts an app->device SPEC frame. Returns [u16 LE counter] || ciphertext||tag. */
    fun encryptSpecFrame(keys: SessionKeys, counter: Int, frame: ByteArray): ByteArray {
        val nonce = keys.appIv + ByteArray(4) + counterBytes(counter)
        val ct = aesCcmEncrypt(keys.appKey, nonce, frame)
        return byteArrayOf((counter and 0xFF).toByte(), ((counter shr 8) and 0xFF).toByte()) + ct
    }

    /** Decrypts a device->app SPEC response. `payload` = [u16 LE counter] || ciphertext||tag. */
    fun decryptSpecFrame(keys: SessionKeys, payload: ByteArray): ByteArray {
        val counter = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)
        val ct = payload.copyOfRange(2, payload.size)
        val nonce = keys.devIv + ByteArray(4) + counterBytes(counter)
        return aesCcmDecrypt(keys.devKey, nonce, ct)
    }
}
