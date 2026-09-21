package com.scooterre.client.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads every property in a fixed range - reading only, nothing is ever written - and turns the answers into a text
 * a person can copy into a bug report. It is how a scooter model that this app does not know yet can be described:
 * which (siid, piid) answer, with how many bytes and which values. Strings (serial numbers, pairing secrets) are never
 * included; only version-like text is kept.
 */
object PropertyExplorer {
    const val MAX_SIID = 8
    const val MAX_PIID = 35
    private const val TIMEOUT_MS = 2500L
    private const val GIVE_UP_AFTER_SILENT = 8

    class Probe(val siid: Int, val piid: Int, val status: Int, val raw: ByteArray)

    suspend fun sweep(spec: SpecClient, onSiid: (Int) -> Unit = {}): List<Probe> {
        val probes = mutableListOf<Probe>()
        var silent = 0
        outer@ for (siid in 1..MAX_SIID) {
            onSiid(siid)
            for (piid in 1..MAX_PIID) {
                val result = spec.get(SpecProperty(siid, piid, "PROBE", SpecType.STRING), timeoutMs = TIMEOUT_MS)
                probes += Probe(siid, piid, result.status, result.raw)
                silent = if (result.status == -1) silent + 1 else 0
                if (silent >= GIVE_UP_AFTER_SILENT) break@outer
            }
        }
        return probes
    }

    private val versionLike = Regex("^\\d+(\\.\\d+){1,3}([_.-]\\w+)*$")

    fun isVersionLike(text: String) = versionLike.matches(text)

    /** One answer as text: numbers in every plausible reading (so the type and scale can be worked out later),
     * version strings as they are, every other string or blob only by its length. */
    fun describe(raw: ByteArray): String {
        if (raw.isEmpty()) return "len=0"
        if (raw.size >= 5 && raw.all { it in 0x20..0x7E }) {
            val text = String(raw, Charsets.US_ASCII)
            return if (isVersionLike(text)) "string \"$text\"" else "string len=${raw.size} (masked)"
        }
        if (raw.size > 8) return "binary len=${raw.size} (masked)"
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val hex = raw.joinToString("") { "%02x".format(it) }
        val readings = when (raw.size) {
            1 -> "u8=${raw[0].toInt() and 0xFF} i8=${raw[0]}"
            2 -> "u16=${buffer.short.toInt() and 0xFFFF} i16=${buffer.getShort(0)}"
            4 -> "u32=${buffer.int.toLong() and 0xFFFFFFFFL} i32=${buffer.getInt(0)} f32=${buffer.getFloat(0)}"
            else -> ""
        }
        return "len=${raw.size} hex=$hex $readings".trim()
    }

    private fun statusText(status: Int) = if (status == -1) "timeout" else "0x%04x".format(status and 0xFFFF)

    private fun ranges(ids: List<Int>): String {
        val sorted = ids.sorted()
        val parts = mutableListOf<String>()
        var start = sorted.first()
        var previous = start
        for (id in sorted.drop(1) + Int.MAX_VALUE) {
            if (id == previous + 1) {
                previous = id
                continue
            }
            parts += if (start == previous) "$start" else "$start-$previous"
            start = id
            previous = id
        }
        return parts.joinToString(", ")
    }

    fun report(probes: List<Probe>): String = buildString {
        appendLine("Property sweep (read only, siid 1-$MAX_SIID, piid 1-$MAX_PIID)")
        val readable = probes.filter { it.status == 0 }
        appendLine("Readable: ${readable.size} of ${probes.size} asked")
        readable.forEach { appendLine("(${it.siid},${it.piid}) ${describe(it.raw)}") }
        val rest = probes.filter { it.status != 0 }.groupBy { it.siid to it.status }
        if (rest.isNotEmpty()) appendLine("Not readable:")
        for ((key, list) in rest.toSortedMap(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))) {
            appendLine("  siid ${key.first} ${statusText(key.second)}: piid ${ranges(list.map { it.piid })}")
        }
        if (probes.size < MAX_SIID * MAX_PIID) appendLine("Stopped early: the scooter stopped answering.")
    }.trimEnd()
}
