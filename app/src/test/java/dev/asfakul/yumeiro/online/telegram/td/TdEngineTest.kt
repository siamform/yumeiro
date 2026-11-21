/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram.td

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/** F-02 / ADR-052: the downloaded engine is only installed when both pinned hashes and the size match. */
class TdEngineTest {
    @get:Rule val tmp = TemporaryFolder()

    private val so = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val gz = ByteArrayOutputStream().also { GZIPOutputStream(it).use { z -> z.write(so) } }.toByteArray()
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun pin(soSha: String = sha(so), gzSha: String = sha(gz), soBytes: Long = so.size.toLong()) =
        TdEngine.Pin("arm64-v8a", soSha, gzSha, gz.size.toLong(), soBytes)

    private fun install(input: ByteArray, p: TdEngine.Pin): File {
        val dest = File(tmp.newFolder(), "libtdjsonjava.so")
        TdEngine.install(ByteArrayInputStream(input), p, dest)
        return dest
    }

    private fun expectRejected(input: ByteArray, p: TdEngine.Pin) {
        val dir = tmp.newFolder()
        val dest = File(dir, "libtdjsonjava.so")
        try {
            TdEngine.install(ByteArrayInputStream(input), p, dest)
            fail("installed a file that doesn't match the pin")
        } catch (e: SecurityException) {
            // expected
        }
        assertFalse("rejected file must not be in place", dest.exists())
        assertEquals("no leftovers", 0, dir.listFiles()!!.size)
    }

    @Test fun goodDownloadIsInstalledReadOnly() {
        var seen = 0L
        val dest = File(tmp.newFolder(), "libtdjsonjava.so")
        TdEngine.install(ByteArrayInputStream(gz), pin(), dest) { seen += it }
        assertArrayEquals(so, dest.readBytes())
        assertFalse(dest.canWrite())
        assertEquals(gz.size.toLong(), seen)
    }

    @Test fun wrongLibraryHashIsDeleted() = expectRejected(gz, pin(soSha = "0".repeat(64)))
    @Test fun wrongGzipHashIsDeleted() = expectRejected(gz, pin(gzSha = "0".repeat(64)))
    @Test fun truncatedGzipIsDeleted() = expectRejected(gz.copyOf(gz.size / 2), pin())
    @Test fun notGzipIsDeleted() = expectRejected(so.copyOf(1000), pin())
    @Test fun largerThanPinnedIsDeleted() = expectRejected(gz, pin(soBytes = so.size - 1L))

    @Test fun tamperedByteIsDeleted() {
        // Same length, one byte changed after the gzip header: the hashes no longer match.
        val bad = gz.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        expectRejected(bad, pin())
    }

    @Test fun picksTheFirstSupportedAbi() {
        val pins = listOf(pin(), TdEngine.Pin("x86_64", "a", "b", 1, 1))
        assertEquals("arm64-v8a", TdEngine.pick(listOf("arm64-v8a", "armeabi-v7a"), pins)?.abi)
        assertEquals("x86_64", TdEngine.pick(listOf("x86_64", "x86"), pins)?.abi)
        assertNull(TdEngine.pick(listOf("mips"), pins))
    }

    @Test fun sha256OfFile() {
        val f = tmp.newFile().also { it.writeBytes(so) }
        assertEquals(sha(so), TdEngine.sha256(f))
        assertTrue(sha(so).length == 64)
    }
}
