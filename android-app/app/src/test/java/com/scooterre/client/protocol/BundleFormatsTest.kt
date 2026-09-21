package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BundleFormatsTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, text) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun theFormatOfEachBundleKindIsRecognized() {
        assertEquals("scooterre-backup-v3", BundleFormats.plainFormat(zip("manifest.json" to """{"format":"scooterre-backup-v3"}""")))
        assertEquals(DocumentsBundle.FORMAT, BundleFormats.plainFormat(zip("manifest.json" to """{"format":"${DocumentsBundle.FORMAT}"}""")))
        assertEquals("scooterre-bundle-v2", BundleFormats.plainFormat(zip("a.txt" to "x", "manifest.json" to """{"format":"scooterre-bundle-v2"}""")))
    }

    @Test
    fun otherFilesHaveNoFormat() {
        assertNull(BundleFormats.plainFormat("just text".toByteArray()))
        assertNull(BundleFormats.plainFormat(zip("readme.txt" to "hi")))
        assertNull(BundleFormats.plainFormat(zip("manifest.json" to "not json")))
        assertNull(BundleFormats.plainFormat(zip("manifest.json" to """{"other":1}""")))
    }

    @Test
    fun encryptedBackupsAreKindsOfEncryptedFiles() {
        assertEquals(DeviceBundle.Kind.ENCRYPTED, DeviceBundle.kindOf("SCB3....".toByteArray()))
        assertEquals(DeviceBundle.Kind.ENCRYPTED, DeviceBundle.kindOf("SCB2....".toByteArray()))
        assertEquals(DeviceBundle.Kind.PLAIN, DeviceBundle.kindOf(zip("a" to "b")))
        assertEquals(DeviceBundle.Kind.NONE, DeviceBundle.kindOf("abcdef".toByteArray()))
    }
}
