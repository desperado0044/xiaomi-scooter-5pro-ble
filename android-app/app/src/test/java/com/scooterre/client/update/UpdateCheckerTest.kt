package com.scooterre.client.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateCheckerTest {
    private val sha = "b8e629f48f26581807d8cdbe03863e85ff45bdeb6853ecaaf9965949cbc97dc9"
    private val apkUrl = UpdateChecker.DOWNLOAD_PREFIX + "v2.3/scooter-5pro-ble-v2.3.apk"

    private fun release(assets: String) =
        """{"tag_name":"v2.3","html_url":"https://github.com/x/y/releases/tag/v2.3","assets":[$assets]}"""

    @Test
    fun theApkAssetAndItsDigestAreTaken() {
        val info = UpdateChecker.parseRelease(
            release("""{"name":"scooter-5pro-ble-v2.3.apk","browser_download_url":"$apkUrl","digest":"sha256:${sha.uppercase()}"}"""),
        )!!
        assertEquals("2.3", info.version)
        assertEquals(apkUrl, info.apkUrl)
        assertEquals(sha, info.apkSha256)
    }

    @Test
    fun anApkFromAnotherPlaceIsNeverOffered() {
        val info = UpdateChecker.parseRelease(
            release("""{"name":"evil.apk","browser_download_url":"https://example.com/evil.apk","digest":"sha256:$sha"}"""),
        )!!
        assertNull(info.apkUrl)
        assertEquals("2.3", info.version) // the release page link still works
    }

    @Test
    fun aReleaseWithoutApkOrDigestStillParses() {
        assertNull(UpdateChecker.parseRelease(release("")) !!.apkUrl)
        val noDigest = UpdateChecker.parseRelease(release("""{"name":"a.apk","browser_download_url":"$apkUrl"}"""))!!
        assertEquals(apkUrl, noDigest.apkUrl)
        assertNull(noDigest.apkSha256)
    }

    @Test
    fun brokenJsonGivesNull() {
        assertNull(UpdateChecker.parseRelease("not json"))
        assertNull(UpdateChecker.parseRelease("""{"assets":[]}"""))
    }

    @Test
    fun digestsAreNormalizedAndValidated() {
        assertEquals(sha, UpdateChecker.normalizeDigest("sha256:$sha"))
        assertNull(UpdateChecker.normalizeDigest("sha1:abc"))
        assertNull(UpdateChecker.normalizeDigest("sha256:tooshort"))
        assertNull(UpdateChecker.normalizeDigest(null))
    }

    @Test
    fun versionsCompareNumerically() {
        assertTrue(UpdateChecker.isNewer("2.3", "2.2"))
        assertTrue(UpdateChecker.isNewer("v2.10", "2.9"))
        assertFalse(UpdateChecker.isNewer("2.3", "2.3"))
        assertFalse(UpdateChecker.isNewer("2.2", "2.3"))
        assertFalse(UpdateChecker.isNewer("2.3", "2.3.1"))
        assertTrue(UpdateChecker.isNewer("2.6", "2.6-alpha1"))
        assertFalse(UpdateChecker.isNewer("2.6-alpha1", "2.6"))
        assertFalse(UpdateChecker.isNewer("2.6-alpha1", "2.6-alpha1"))
    }

    @Test
    fun sha256OfAFileIsComputedCorrectly() {
        val file = File.createTempFile("sha", ".bin")
        try {
            file.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateInstaller.sha256Hex(file))
        } finally {
            file.delete()
        }
    }
}
