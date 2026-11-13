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

    companion object {
        /**
         * The readable indicator list behind [ExtensionScanner.PRINTS]. It lives only in the test
         * sources so the shipped DEX doesn't carry malware-signature text (see AUDIT.md "Antivirus
         * heuristics"). Edit both together; [fingerprintsMatchReadableIndicators] enforces it.
         */
        val INDICATORS: Map<String, List<String>> = mapOf(
        "Runs system commands" to listOf("m:Ljava/lang/Runtime;->exec", "m:Ljava/lang/ProcessBuilder;-><init>", "m:Ljava/lang/ProcessBuilder;->start"),
        "Loads hidden code" to listOf("t:Ldalvik/system/DexClassLoader;", "t:Ldalvik/system/InMemoryDexClassLoader;", "m:Ldalvik/system/PathClassLoader;-><init>", "t:Ldalvik/system/DexFile;"),
        "Loads native code" to listOf("m:Ljava/lang/System;->loadLibrary", "m:Ljava/lang/System;->load", "m:Ljava/lang/Runtime;->loadLibrary", "m:Ljava/lang/Runtime;->load"),
        "Reads or sends SMS" to listOf("t:Landroid/telephony/SmsManager;", "u:content://sms", "u:content://mms"),
        "Reads contacts or call log" to listOf("c:Landroid/provider/ContactsContract", "c:Landroid/provider/CallLog", "u:content://contacts", "u:content://call_log", "u:content://com.android.contacts"),
        "Tracks location" to listOf("m:Landroid/location/LocationManager;->requestLocationUpdates", "m:Landroid/location/LocationManager;->getLastKnownLocation", "m:Landroid/location/LocationManager;->getCurrentLocation", "p:Lcom/google/android/gms/location/"),
        "Records camera or microphone" to listOf("p:Landroid/hardware/camera2/", "t:Landroid/hardware/Camera;", "t:Landroid/media/AudioRecord;", "t:Landroid/media/MediaRecorder;"),
        "Installs apps or controls the device" to listOf("c:Landroid/content/pm/PackageInstaller", "p:Landroid/app/admin/", "p:Landroid/accessibilityservice/", "x:application/vnd.android.package-archive"),
        "Reads your accounts" to listOf("m:Landroid/accounts/AccountManager;->getAccounts", "m:Landroid/accounts/AccountManager;->getAccountsByType", "m:Landroid/accounts/AccountManager;->getPassword", "m:Landroid/accounts/AccountManager;->peekAuthToken"),
        "Crypto miner" to listOf("s:stratum+tcp", "s:stratum+ssl", "w:coinhive", "w:xmrig", "w:cryptonight", "w:coin-hive"),
        "Reads device identifiers" to listOf("t:Landroid/telephony/TelephonyManager;"),
        "Android ID" to listOf("w:android_id"),
        "Lists your installed apps" to listOf("m:Landroid/content/pm/PackageManager;->getInstalledPackages", "m:Landroid/content/pm/PackageManager;->getInstalledApplications", "m:Landroid/content/pm/PackageManager;->queryIntentActivities"),
        "Reads the clipboard" to listOf("m:Landroid/content/ClipboardManager;->getPrimaryClip", "m:Landroid/content/ClipboardManager;->getText"),
        "Defines code at run time" to listOf("m:Ljava/lang/ClassLoader;->defineClass"),
        "Analytics or ad trackers" to listOf("h:google-analytics.com", "h:googletagmanager.com", "h:app-measurement.com", "h:appsflyer.com", "h:doubleclick.net", "h:graph.facebook.com", "h:adjust.com", "h:branch.io", "h:mixpanel.com", "h:onesignal.com"),
        "JavaScript bridge in a web page" to listOf("m:Landroid/webkit/WebView;->addJavascriptInterface"),
        "Shows its own notifications or starts services" to listOf("m:Landroid/app/NotificationManager;->notify", "p:Landroidx/work/", "m:Landroid/content/Context;->startService", "m:Landroid/content/Context;->startForegroundService")
        )
    }

    @Test fun fingerprintsMatchReadableIndicators() {
        assertEquals(INDICATORS.keys, ExtensionScanner.PRINTS.keys)
        for ((title, keys) in INDICATORS) {
            assertEquals(title, keys.map { ExtensionScanner.fingerprint(it) }, ExtensionScanner.PRINTS.getValue(title).toList())
        }
        val all = INDICATORS.values.flatten()
        assertEquals("fingerprint collision", all.size, all.map { ExtensionScanner.fingerprint(it) }.toSet().size)
    }
    @Test fun fingerprintIsFnv1a() {
        assertEquals(-3750763034362895579L, ExtensionScanner.fingerprint(""))
        assertEquals(-5808556873153909620L, ExtensionScanner.fingerprint("a"))
    }
    @Test fun keysCoverTypesPackagesUrisHostsAndWords() {
        val k = dex(
            types = setOf("Landroid/provider/ContactsContract\$Contacts;"),
            methods = setOf("Landroid/location/LocationManager;->getLastKnownLocation"),
            strings = listOf("stratum+ssl://pool.example:3333", "Content://SMS/inbox", "cdn.app-measurement.com", "run XMRig now"),
        ).keys()
        for (want in listOf("c:Landroid/provider/ContactsContract", "p:Landroid/provider/", "m:Landroid/location/LocationManager;->getLastKnownLocation",
            "s:stratum+ssl", "u:content://sms", "h:app-measurement.com", "w:xmrig")) assertTrue(want, want in k)
    }
    @Test fun everyIndicatorStillTriggersItsRule() {
        for ((title, keys) in INDICATORS) {
            if (title == "Android ID") continue
            for (key in keys) {
                val v = key.substringAfter(':')
                val d = when (key[0]) {
                    't' -> dex(types = setOf(v))
                    'c' -> dex(types = setOf("$v;"))
                    'p' -> dex(types = setOf("${v}Thing;"))
                    'm' -> dex(methods = setOf(v))
                    's' -> dex(strings = listOf("$v://host"))
                    'u' -> dex(strings = listOf("$v/path"))
                    'h' -> dex(strings = listOf("https://x.$v/a"))
                    'w' -> dex(strings = listOf("uses $v here"))
                    else -> dex(strings = listOf(v))
                }
                assertTrue("$title <- $key", ExtensionScanner.report(d).findings.any { it.title == title })
            }
        }
    }
    @Test fun androidIdNeedsSettingsRead() {
        assertEquals(Level.SAFE, ExtensionScanner.report(dex(strings = listOf("android_id"))).level)
        val r = ExtensionScanner.report(dex(methods = setOf("Landroid/provider/Settings\$Secure;->getString"), strings = listOf("android_id")))
        assertTrue(r.findings.any { it.title == "Reads device identifiers" })
    }
    @Test fun lookalikesDoNotTrigger() {
        val r = ExtensionScanner.report(dex(strings = listOf("https://mysms.site/content", "notadoubleclick.network", "Stratum of anime")))
        assertEquals(Level.SAFE, r.level)
    }
}
