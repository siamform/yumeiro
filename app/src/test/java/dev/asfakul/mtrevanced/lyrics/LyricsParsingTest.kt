/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LyricsParsingTest {
    @Test fun parsesSyncedLrcWithMetaOffsetAndMultiTags() {
        val lrc = "\uFEFF[ti:Song]\n[ar:Someone]\n[offset:+100]\n[00:01.50]Hello\n[00:05.00][00:10]Chorus\n[00:03.2]<00:03.20>Word <00:03.50>timed\n"
        val l = Lyrics(LrcParser.parse(lrc), LyricsSource.SIDECAR)
        assertTrue(l.isSynced)
        assertEquals(listOf(1400L, 3100L, 4900L, 9900L), l.lines.map { it.timeMs })
        assertEquals("Word timed", l.lines[1].text)
        assertEquals(-1, l.indexAt(0))
        assertEquals(0, l.indexAt(2000))
        assertEquals(2, l.indexAt(5000))
        assertEquals(3, l.indexAt(999_999))
    }

    @Test fun parsesPlainText() {
        val l = Lyrics(LrcParser.parse("\n\nLine one\r\n\r\n\r\nLine two\n\n"), LyricsSource.EMBEDDED)
        assertFalse(l.isSynced)
        assertEquals(listOf("Line one", "", "Line two"), l.lines.map { it.text })
        assertEquals(-1, l.indexAt(1000))
    }

    @Test fun emptyAndGarbageInputIsSafe() {
        assertTrue(LrcParser.parse(null).isEmpty())
        assertTrue(LrcParser.parse("   ").isEmpty())
        LrcParser.parse("[99999:99.9999]x\n[:]\n[ab:cd]")
    }

    private fun withFile(bytes: ByteArray, block: (java.nio.channels.FileChannel) -> Unit) {
        val f = File.createTempFile("lyr", ".bin")
        try {
            f.writeBytes(bytes)
            RandomAccessFile(f, "r").use { block(it.channel) }
        } finally { f.delete() }
    }

    private fun syncsafe(n: Int) = byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())

    @Test fun readsId3v24Uslt() {
        val text = "[00:01.00]Embedded line".toByteArray(Charsets.UTF_8)
        val body = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + text
        val frame = "USLT".toByteArray() + syncsafe(body.size) + byteArrayOf(0, 0) + body
        val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncsafe(frame.size) + frame
        withFile(tag + ByteArray(64)) { ch ->
            val raw = EmbeddedLyrics.read(ch)
            assertEquals("[00:01.00]Embedded line", raw)
            assertEquals(1000L, LrcParser.parse(raw).single().timeMs)
        }
    }

    @Test fun readsFlacVorbisLyrics() {
        fun le(n: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array()
        val out = ByteArrayOutputStream()
        val vendor = "test".toByteArray()
        out.write(le(vendor.size)); out.write(vendor); out.write(le(2))
        for (c in listOf("TITLE=x", "LYRICS=Plain flac lyrics")) { val b = c.toByteArray(); out.write(le(b.size)); out.write(b) }
        val vc = out.toByteArray()
        val streamInfo = byteArrayOf(0, 0, 0, 34) + ByteArray(34)
        val vcHdr = byteArrayOf((0x80 or 4).toByte(), (vc.size shr 16).toByte(), (vc.size shr 8).toByte(), vc.size.toByte())
        withFile("fLaC".toByteArray() + streamInfo + vcHdr + vc) { ch ->
            assertEquals("Plain flac lyrics", EmbeddedLyrics.read(ch))
        }
    }

    @Test fun unknownOrTruncatedFilesReturnNull() {
        withFile(ByteArray(3)) { assertNull(EmbeddedLyrics.read(it)) }
        withFile("ID3".toByteArray() + byteArrayOf(4, 0, 0, 0x7F, 0x7F, 0x7F, 0x7F)) { assertNull(EmbeddedLyrics.read(it)) }
        withFile("fLaC".toByteArray() + byteArrayOf(0x84.toByte(), 0x7F, 0x7F, 0x7F)) { assertNull(EmbeddedLyrics.read(it)) }
    }
}
