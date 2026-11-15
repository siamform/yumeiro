package dev.asfakul.yumeiro.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedReadTest {
    /** Hands out at most 3 bytes per read, like a slow network stream. */
    private class Trickle(private val data: ByteArray) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xff else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            val n = minOf(len, 3, data.size - pos)
            System.arraycopy(data, pos, b, off, n); pos += n; return n
        }
    }

    @Test fun shortStreamIsReadWhole() {
        val data = ByteArray(10_000) { it.toByte() }
        assertArrayEquals(data, Trickle(data).readAtMost(20_000))
    }

    @Test fun longStreamStopsAtTheLimit() {
        val data = ByteArray(50_000) { (it * 7).toByte() }
        val got = Trickle(data).readAtMost(12_345)
        assertEquals(12_345, got.size)
        assertArrayEquals(data.copyOf(12_345), got)
    }

    @Test fun limitPlusOneDetectsOversize() {
        val max = 100
        assertEquals(max + 1, ByteArrayInputStream(ByteArray(500)).readAtMost(max + 1).size)
        assertEquals(max, ByteArrayInputStream(ByteArray(max)).readAtMost(max + 1).size)
    }

    @Test fun zeroLimitReadsNothing() {
        assertEquals(0, ByteArrayInputStream(ByteArray(5)).readAtMost(0).size)
    }

    /** Guard: no production source may call the API-33-only readNBytes again. */
    @Test fun noReadNBytesInAppSources() {
        val root = java.io.File("src/main/java")
        val hits = root.walkTopDown().filter { it.extension == "kt" }
            .filter { f -> f.readLines().any { l -> l.contains(".readNBytes(") && !l.trimStart().startsWith("*") && !l.trimStart().startsWith("//") } }
            .map { it.path }.toList()
        assertEquals("InputStream.readNBytes needs API 33; use readAtMost: $hits", emptyList<String>(), hits)
    }
}
