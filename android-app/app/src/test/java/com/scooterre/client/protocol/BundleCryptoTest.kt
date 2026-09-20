package com.scooterre.client.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleCryptoTest {
    private val plain = "backup content".toByteArray()

    @Test
    fun roundTripWithCorrectPassword() {
        val encrypted = BundleCrypto.encrypt(plain, "correct-horse", iterations = 1000)
        assertTrue(BundleCrypto.isBackup(encrypted))
        assertArrayEquals(plain, BundleCrypto.decrypt(encrypted, "correct-horse"))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val encrypted = BundleCrypto.encrypt(plain, "correct-horse", iterations = 1000)
        assertNull(BundleCrypto.decrypt(encrypted, "wrong-password"))
    }

    @Test
    fun tamperedFileIsRejected() {
        val encrypted = BundleCrypto.encrypt(plain, "correct-horse", iterations = 1000)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 1).toByte()
        assertNull(BundleCrypto.decrypt(encrypted, "correct-horse"))
    }

    @Test
    fun plainDataIsNotMistakenForABackup() {
        assertFalse(BundleCrypto.isBackup("PK\u0003\u0004".toByteArray()))
        assertNull(BundleCrypto.decrypt("PK\u0003\u0004 not a backup".toByteArray(), "x"))
    }
}
