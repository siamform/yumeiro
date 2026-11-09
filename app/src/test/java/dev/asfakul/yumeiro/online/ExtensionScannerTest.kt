/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.ExtensionScanner.Dex
import dev.asfakul.yumeiro.online.ExtensionScanner.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionScannerTest {
    private fun dex(types: Set<String> = emptySet(), methods: Set<String> = emptySet(), strings: List<String> = emptyList()) =
        Dex(types + "Lcom/lagradost/cloudstream3/MainAPI;", methods, strings)

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun normalExtensionIsSafe() {
        val r = ExtensionScanner.report(dex(strings = listOf("https://site.to", "Episode")))
        assertEquals(Level.SAFE, r.level); assertTrue(r.readable)
        assertTrue(ExtensionReview.autoDecision(r))
    }
    @Test fun plainHttpIsOnlyInfo() {
        val r = ExtensionScanner.report(dex(strings = listOf("http://site.to/api", "http://www.w3.org/2000/svg")))
        assertEquals(Level.SAFE, r.level)
        assertTrue(r.findings.any { it.title == "Unencrypted links" && it.detail.contains("1 plain") })
    }
    @Test fun shellIsDanger() {
        val r = ExtensionScanner.report(dex(methods = setOf("Ljava/lang/Runtime;->exec")))
        assertEquals(Level.DANGER, r.level)
        assertFalse(ExtensionReview.autoDecision(r))
    }
    @Test fun smsAndMinerAreDanger() {
        assertEquals(Level.DANGER, ExtensionScanner.report(dex(strings = listOf("content://sms/inbox"))).level)
        assertEquals(Level.DANGER, ExtensionScanner.report(dex(strings = listOf("stratum+tcp://pool"))).level)
        assertEquals(Level.DANGER, ExtensionScanner.report(dex(types = setOf("Ldalvik/system/DexClassLoader;"))).level)
    }
    @Test fun trackersAreCaution() {
        val r = ExtensionScanner.report(dex(strings = listOf("https://www.google-analytics.com/collect")))
        assertEquals(Level.CAUTION, r.level)
    }
    @Test fun dangerFindingsSortFirst() {
        val r = ExtensionScanner.report(dex(methods = setOf("Ljava/lang/Runtime;->exec", "Landroid/content/ClipboardManager;->getPrimaryClip")))
        assertEquals(Level.DANGER, r.findings.first().level)
    }
    @Test fun garbageIsUnreadable() {
        val r = ExtensionScanner.scan("not a zip".toByteArray())
        assertFalse(r.readable); assertEquals(Level.DANGER, r.level)
    }
    @Test fun zipWithoutDexIsRejected() {
        val r = ExtensionScanner.scan(zip("manifest.json" to "{}".toByteArray()))
        assertFalse(r.readable)
    }
    @Test fun nativeLibraryIsFlagged() {
        val r = ExtensionScanner.scan(zip("classes.dex" to ByteArray(16), "lib/arm64/libx.so" to ByteArray(4)))
        assertEquals(Level.DANGER, r.level)
        assertTrue(r.findings.any { it.title == "Ships native code" })
    }
    @Test fun reviewModes() {
        val safe = ExtensionScanner.report(dex())
        val risky = ExtensionScanner.report(dex(strings = listOf("doubleclick.net")))
        assertTrue(ExtensionReview.needsAsking(ExtensionReview.Mode.ALWAYS, safe))
        assertFalse(ExtensionReview.needsAsking(ExtensionReview.Mode.IF_RISKY, safe))
        assertTrue(ExtensionReview.needsAsking(ExtensionReview.Mode.IF_RISKY, risky))
        assertFalse(ExtensionReview.needsAsking(ExtensionReview.Mode.AUTO, risky))
    }
}
