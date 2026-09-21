package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PropertyExplorerTest {
    private fun probe(siid: Int, piid: Int, status: Int, raw: ByteArray = ByteArray(0)) = PropertyExplorer.Probe(siid, piid, status, raw)

    @Test
    fun numbersAreShownInEveryReading() {
        assertEquals("len=1 hex=64 u8=100 i8=100", PropertyExplorer.describe(byteArrayOf(100)))
        assertEquals("len=2 hex=e803 u16=1000 i16=1000", PropertyExplorer.describe(byteArrayOf(0xE8.toByte(), 0x03)))
        val float = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(4050.0f).array()
        assertTrue(PropertyExplorer.describe(float).contains("f32=4050.0"))
    }

    @Test
    fun versionsAreKeptButSerialNumbersAndSecretsAreMasked() {
        assertEquals("string \"2.7.0_0015\"", PropertyExplorer.describe("2.7.0_0015".toByteArray()))
        val serial = PropertyExplorer.describe("35284/00012345".toByteArray())
        val secret = PropertyExplorer.describe("00112233445566778899aabbccddeeff".toByteArray())
        assertFalse(serial, serial.contains("35284"))
        assertFalse(secret, secret.contains("0011223344"))
        assertTrue(secret, secret.contains("masked"))
        assertTrue(PropertyExplorer.describe(ByteArray(20) { it.toByte() }).contains("masked"))
    }

    @Test
    fun theReportListsReadablePropertiesAndGroupsTheRest() {
        val probes = (1..35).map { piid ->
            when (piid) {
                1 -> probe(3, 1, 0, byteArrayOf(87))
                else -> probe(3, piid, 0xF05F)
            }
        } + (1..35).map { probe(4, it, 0xF05F) } + (5..8).flatMap { s -> (1..35).map { probe(s, it, 0xF05D) } }
        val report = PropertyExplorer.report(probes)
        assertTrue(report, report.contains("(3,1) len=1 hex=57 u8=87"))
        assertTrue(report, report.contains("siid 3 0xf05f: piid 2-35"))
        assertTrue(report, report.contains("siid 4 0xf05f: piid 1-35"))
        assertTrue(report, report.contains("Stopped early"))
    }

    @Test
    fun silentPropertiesAreShownAsTimeouts() {
        val report = PropertyExplorer.report(listOf(probe(2, 1, -1), probe(2, 2, -1), probe(2, 4, -1)))
        assertTrue(report, report.contains("siid 2 timeout: piid 1-2, 4"))
    }
}
