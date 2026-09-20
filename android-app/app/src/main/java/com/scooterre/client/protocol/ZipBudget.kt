package com.scooterre.client.protocol

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/** Caps how much a bundle may expand while it is unpacked, so a crafted small file cannot exhaust the memory. */
internal class ZipBudget(var left: Long = 150L * 1024 * 1024)

internal fun ZipInputStream.readBytesWithin(budget: ZipBudget): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = read(buf)
        if (n < 0) break
        budget.left -= n
        if (budget.left < 0) throw IOException("bundle too large")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
