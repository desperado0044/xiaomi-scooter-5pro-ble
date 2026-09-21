package com.scooterre.client.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @After
    fun reset() = Diagnostics.clearErrors()

    @Test
    fun personalDataIsRemovedFromErrorTexts() {
        val text = "Connect failed for AA:BB:CC:DD:EE:FF (key 00112233445566778899aabbccddeeff), user a.b@example.com, " +
            "https://api.example.com/path?token=SECRET123&x=1 AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"
        val clean = Diagnostics.redact(text)
        assertFalse(clean, clean.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(clean, clean.contains("00112233445566778899aabbccddeeff"))
        assertFalse(clean, clean.contains("example.com,"))
        assertFalse(clean, clean.contains("SECRET123"))
        assertFalse(clean, clean.contains("AbCdEfGhIjKlMnOpQrStUvWxYz0123456789"))
        assertTrue(clean, clean.contains("https://api.example.com/path?<removed>"))
        assertTrue(clean, clean.contains("Connect failed for <mac>"))
    }

    @Test
    fun ordinaryMessagesStayReadable() {
        assertEquals("Wrong password (or damaged file).", Diagnostics.redact("Wrong password (or damaged file)."))
        assertEquals("Timeout after 12 s", Diagnostics.redact("Timeout after 12 s"))
    }

    @Test
    fun onlyTheLatestErrorsAreKeptAndRepeatsAreSkipped() {
        repeat(30) { Diagnostics.recordError("error $it", nowMillis = it * 1000L) }
        Diagnostics.recordError("error 29", nowMillis = 99_000L)
        val kept = Diagnostics.recentErrors()
        assertEquals(15, kept.size)
        assertEquals("error 15", kept.first().second)
        assertEquals("error 29", kept.last().second)
    }

    @Test
    fun theReportContainsTheFactsAndNoAddress() {
        Diagnostics.recordError("Timeout for 11:22:33:44:55:66", nowMillis = 0L)
        val report = Diagnostics.build(
            Diagnostics.Info(
                app = "2.6 (17)", android = "Android 15 (API 35)", phone = "Xiaomi 2506BPN68G",
                settings = listOf("units" to "METRIC", "appLock" to "false"),
                scooter = "model=xiaomi.scooter.5pro, firmware=2.7.0",
                errors = Diagnostics.recentErrors(),
            ),
        )
        assertTrue(report, report.contains("App: 2.6 (17)"))
        assertTrue(report, report.contains("Settings: units=METRIC, appLock=false"))
        assertTrue(report, report.contains("Timeout for <mac>"))
        assertFalse(report, report.contains("11:22:33:44:55:66"))
    }
}
