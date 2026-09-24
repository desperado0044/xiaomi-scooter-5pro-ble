package com.scooterre.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class RideBookTest {
    private fun rec(d: Int, km: Int) = RideRecord(d, km, 180, 200)

    @Test
    fun parsesRecordsAndSkipsEmptyOnes() {
        val raw = "0240004401060160" + "0000000000000000"
        val r = RideBook.parse(raw)
        assertEquals(listOf(RideRecord(240, 44, 106, 160)), r)
        assertEquals(24.0, r[0].minutes, 1e-9)
        assertEquals(4.4, r[0].km, 1e-9)
    }

    @Test
    fun parsesTwoRecordsInOneSlot() {
        assertEquals(2, RideBook.parse("0240004401060160" + "0020000501800360").size)
    }

    @Test
    fun ignoresGarbage() {
        assertEquals(emptyList<RideRecord>(), RideBook.parse("not a ride log!!!"))
        assertEquals(emptyList<RideRecord>(), RideBook.parse(""))
    }

    @Test
    fun onlyRecordsNotSeenBeforeAreNew() {
        val a = rec(100, 30)
        val b = rec(200, 50)
        val c = rec(300, 70)
        assertEquals(listOf(c), RideBook.newRecords(listOf(a, b), listOf(b, c, a)))
    }

    @Test
    fun identicalRidesCountTwice() {
        val a = rec(100, 30)
        assertEquals(listOf(a), RideBook.newRecords(listOf(a), listOf(a, a)))
    }

    @Test
    fun nothingNewWhenNothingChanged() {
        val a = rec(100, 30)
        assertEquals(emptyList<RideRecord>(), RideBook.newRecords(listOf(a), listOf(a)))
    }
}
