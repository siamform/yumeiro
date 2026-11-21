package dev.asfakul.yumeiro.stream.torrent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TorrentEngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private val so = ByteArray(700_000) { (it * 31 % 251).toByte() }
    private val jar = ByteArrayOutputStream().also { b ->
        ZipOutputStream(b).use { z ->
            z.putNextEntry(ZipEntry("META-INF/MANIFEST.MF")); z.write("Manifest-Version: 1.0\n".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("lib/x86_64/libtorrent4j.so")); z.write(so); z.closeEntry()
        }
    }.toByteArray()

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun pin(jarSha: String = sha(jar), soSha: String = sha(so), jarBytes: Long = jar.size.toLong()) =
        TorrentEngine.Pin("9.9.9-1", "x86_64", "x86_64", jarSha, jarBytes, soSha, so.size.toLong())

    @Test fun goodJarInstallsOnlyTheLibraryReadOnly() {
        val seen = mutableListOf<Long>()
        val dest = File(tmp.newFolder(), "libtorrent4j.so")
        TorrentEngine.install(ByteArrayInputStream(jar), pin(), dest) { seen += it }
        assertArrayEquals(so, dest.readBytes())
        assertFalse(dest.canWrite())
        assertEquals(seen.sorted(), seen)
        assertEquals(jar.size.toLong(), seen.last()) // every jar byte was read (and hashed)
        assertFalse(File(dest.parentFile, dest.name + ".part").exists())
    }

    @Test fun wrongJarHashOrLibraryHashIsDeleted() {
        for (p in listOf(pin(jarSha = "0".repeat(64)), pin(soSha = "0".repeat(64)), pin(jarBytes = 1))) {
            val dest = File(tmp.newFolder(), "libtorrent4j.so")
            try { TorrentEngine.install(ByteArrayInputStream(jar), p, dest); fail("accepted a bad pin") } catch (e: SecurityException) { }
            assertFalse(dest.exists())
            assertFalse(File(dest.parentFile, dest.name + ".part").exists())
        }
    }

    @Test fun pinsCoverEveryAbiForBothBuildsAndTheJarVersion() {
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        for (v in listOf("2.1.0-39", "2.1.0-38")) for (a in abis) assertNotNull("$v $a", TorrentEngine.pick(listOf(a), v))
        // The jar on the unit-test classpath (phone build) has a pin set: the native part always matches it.
        val v = org.libtorrent4j.LibTorrent.libtorrent4jVersion()
        assertEquals("2.1.0-39", v)
        assertNotNull(TorrentEngine.pick(listOf("arm64-v8a"), v))
        assertNull(TorrentEngine.pick(listOf("mips"), v))
        assertEquals("arm64-v8a", TorrentEngine.pick(listOf("arm64-v8a", "armeabi-v7a"), v)?.abi)
        assertTrue(TorrentEngine.PINS.all { it.url.startsWith("https://repo1.maven.org/maven2/org/libtorrent4j/") && it.jarSha256.length == 64 && it.soSha256.length == 64 })
    }
}
