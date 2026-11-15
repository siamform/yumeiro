package dev.asfakul.yumeiro.network

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads at most [limit] bytes and stops, without throwing when more remain.
 *
 * Use this instead of `InputStream.readNBytes`: that method exists only from Android 13 (API 33),
 * and the apps run from API 28 (phone) and API 24 (TV). On older systems it throws
 * NoSuchMethodError (G12 X10-2: Cast subtitles failed this way on Android 9–12).
 * Callers that must reject oversized input read `limit + 1` and check the size.
 */
fun InputStream.readAtMost(limit: Int): ByteArray {
    require(limit >= 0) { "limit must be >= 0" }
    val out = ByteArrayOutputStream(minOf(limit, 64 * 1024))
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
