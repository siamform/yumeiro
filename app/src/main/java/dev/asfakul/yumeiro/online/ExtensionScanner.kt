/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Safety check that runs between "download" and "install" for every extension (market, repository,
 * file, link and updates). It never runs the code: it opens the `.cs3` (a zip with `classes.dex` and
 * `manifest.json`), reads the DEX tables of referenced classes, methods and strings, and flags APIs a
 * streaming source has no business using — running shell commands, loading hidden code, reading
 * SMS / contacts / location, recording, installing apps, accessibility control, wiping storage,
 * miners and trackers.
 *
 * It's a static, best-effort check — a clean result means "nothing obviously harmful", not a
 * guarantee — so the UI words it that way. Pure → unit tested with synthetic DEX files.
 */
object ExtensionScanner {

    enum class Level { SAFE, CAUTION, DANGER }

    data class Finding(val level: Level, val title: String, val detail: String)

    data class Report(
        val level: Level,
        val findings: List<Finding>,
        val classes: Int = 0,
        val methods: Int = 0,
        val sizeBytes: Int = 0,
        /** False when the file couldn't be read at all (not a valid extension). */
        val readable: Boolean = true,
    ) {
        val summary: String get() = when {
            !readable -> "This file couldn't be checked — it isn't a valid extension"
            level == Level.DANGER -> "Possibly harmful code found"
            level == Level.CAUTION -> "Nothing harmful found, but some things are worth knowing"
            else -> "No risky code found"
        }
    }

    private data class Rule(val level: Level, val title: String, val detail: String, val match: (Dex) -> Boolean)

    /** What a DEX file references. */
    class Dex(val types: Set<String>, val methods: Set<String>, val strings: List<String>) {
        fun type(prefix: String) = types.any { it.startsWith(prefix) }
        fun method(owner: String, vararg names: String) = names.any { "$owner->$it" in methods }
        fun ownerAny(prefix: String) = methods.any { it.startsWith(prefix) }
        fun str(vararg needles: String) = strings.any { s -> needles.any { s.contains(it, ignoreCase = true) } }
    }

    private val RULES = listOf(
        Rule(Level.DANGER, "Runs system commands", "Can start shell commands on your phone (Runtime.exec / ProcessBuilder).") {
            it.method("Ljava/lang/Runtime;", "exec") || it.method("Ljava/lang/ProcessBuilder;", "<init>", "start")
        },
        Rule(Level.DANGER, "Loads hidden code", "Downloads or unpacks extra code at run time (DexClassLoader), which this check can't see.") {
            it.type("Ldalvik/system/DexClassLoader;") || it.type("Ldalvik/system/InMemoryDexClassLoader;") ||
                it.method("Ldalvik/system/PathClassLoader;", "<init>") || it.type("Ldalvik/system/DexFile;")
        },
        Rule(Level.DANGER, "Loads native code", "Loads native libraries (System.loadLibrary / System.load).") {
            it.method("Ljava/lang/System;", "loadLibrary", "load") || it.method("Ljava/lang/Runtime;", "loadLibrary", "load")
        },
        Rule(Level.DANGER, "Reads or sends SMS", "Uses the SMS manager or SMS inbox.") {
            it.type("Landroid/telephony/SmsManager;") || it.str("content://sms", "content://mms")
        },
        Rule(Level.DANGER, "Reads contacts or call log", "Reads your contacts or call history.") {
            it.type("Landroid/provider/ContactsContract") || it.type("Landroid/provider/CallLog") ||
                it.str("content://contacts", "content://call_log", "content://com.android.contacts")
        },
        Rule(Level.DANGER, "Tracks location", "Asks Android for your location.") {
            it.method("Landroid/location/LocationManager;", "requestLocationUpdates", "getLastKnownLocation", "getCurrentLocation") ||
                it.type("Lcom/google/android/gms/location/")
        },
        Rule(Level.DANGER, "Records camera or microphone", "Uses the camera or records audio.") {
            it.type("Landroid/hardware/camera2/") || it.type("Landroid/hardware/Camera;") ||
                it.type("Landroid/media/AudioRecord;") || it.type("Landroid/media/MediaRecorder;")
        },
        Rule(Level.DANGER, "Installs apps or controls the device", "Uses the app installer, device admin or accessibility control.") {
            it.type("Landroid/content/pm/PackageInstaller") || it.type("Landroid/app/admin/") ||
                it.type("Landroid/accessibilityservice/") || it.str("application/vnd.android.package-archive")
        },
        Rule(Level.DANGER, "Reads your accounts", "Lists the accounts on this phone.") {
            it.method("Landroid/accounts/AccountManager;", "getAccounts", "getAccountsByType", "getPassword", "peekAuthToken")
        },
        Rule(Level.DANGER, "Crypto miner", "Contains crypto-mining code or pool addresses.") {
            it.str("stratum+tcp://", "stratum+ssl://", "coinhive", "xmrig", "cryptonight", "coin-hive")
        },
        Rule(Level.DANGER, "Deletes files on your storage", "Deletes files in shared storage (not just its own cache).") {
            (it.method("Ljava/io/File;", "delete", "deleteRecursively") || it.ownerAny("Lkotlin/io/FilesKt") && it.str("deleteRecursively")) &&
                (it.method("Landroid/os/Environment;", "getExternalStorageDirectory", "getExternalStoragePublicDirectory") || it.str("/sdcard/", "/storage/emulated/"))
        },
        Rule(Level.CAUTION, "Reads device identifiers", "Reads the Android ID or phone details, which can identify your device.") {
            it.type("Landroid/telephony/TelephonyManager;") ||
                (it.method("Landroid/provider/Settings\$Secure;", "getString") && it.str("android_id"))
        },
        Rule(Level.CAUTION, "Lists your installed apps", "Reads which apps are installed.") {
            it.method("Landroid/content/pm/PackageManager;", "getInstalledPackages", "getInstalledApplications", "queryIntentActivities")
        },
        Rule(Level.CAUTION, "Reads the clipboard", "Can read what you copied.") {
            it.method("Landroid/content/ClipboardManager;", "getPrimaryClip", "getText")
        },
        Rule(Level.CAUTION, "Defines code at run time", "Builds classes at run time (often a JavaScript engine for a site's player).") {
            it.method("Ljava/lang/ClassLoader;", "defineClass")
        },
        Rule(Level.CAUTION, "Reaches into Yumeiro itself", "References Yumeiro's own classes, which extensions normally never do.") {
            it.type("Ldev/asfakul/yumeiro/") || it.str("dev.asfakul.yumeiro")
        },
        Rule(Level.CAUTION, "Analytics or ad trackers", "Contains analytics / ad-network addresses.") {
            it.str("google-analytics.com", "googletagmanager.com", "app-measurement.com", "appsflyer.com", "doubleclick.net",
                "graph.facebook.com", "adjust.com/", "branch.io", "mixpanel.com", "onesignal.com")
        },
        Rule(Level.CAUTION, "JavaScript bridge in a web page", "Exposes code to web pages (addJavascriptInterface).") {
            it.method("Landroid/webkit/WebView;", "addJavascriptInterface")
        },
        Rule(Level.CAUTION, "Shows its own notifications or starts services", "Posts notifications or runs background work.") {
            it.method("Landroid/app/NotificationManager;", "notify") || it.type("Landroidx/work/") ||
                it.method("Landroid/content/Context;", "startService", "startForegroundService")
        },
    )

    private const val MAX_DEX = 48 * 1024 * 1024

    fun scan(bytes: ByteArray): Report {
        val dexes = mutableListOf<ByteArray>()
        val entryFindings = mutableListOf<Finding>()
        var entries = 0
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    entries++
                    val name = e.name
                    val lower = name.lowercase()
                    when {
                        Regex("^classes\\d*\\.dex$").matches(name) -> {
                            val d = zip.readBytes()
                            if (d.size <= MAX_DEX) dexes += d
                        }
                        lower.endsWith(".so") -> entryFindings += Finding(Level.DANGER, "Ships native code", "Contains a native library ($name) this check can't read.")
                        lower.endsWith(".dex") || lower.endsWith(".apk") || lower.endsWith(".jar") || lower.endsWith(".odex") ->
                            entryFindings += Finding(Level.DANGER, "Hides extra code", "Contains another code file ($name) that could be loaded later.")
                        lower.endsWith(".sh") || lower.endsWith(".elf") || lower == "busybox" ->
                            entryFindings += Finding(Level.DANGER, "Ships a program", "Contains an executable ($name).")
                    }
                }
            }
        } catch (_: Exception) {
            return Report(Level.DANGER, listOf(Finding(Level.DANGER, "Unreadable file", "The extension file is damaged or not a .cs3.")), readable = false)
        }
        if (entries == 0 || dexes.isEmpty()) {
            return Report(Level.DANGER, entryFindings + Finding(Level.DANGER, "No code found", "The file has no classes.dex — it isn't a CloudStream extension."),
                sizeBytes = bytes.size, readable = false)
        }
        val parsed = dexes.mapNotNull { runCatching { parseDex(it) }.getOrNull() }
        if (parsed.isEmpty()) {
            return Report(Level.DANGER, entryFindings + Finding(Level.DANGER, "Unreadable code", "classes.dex couldn't be read."), sizeBytes = bytes.size, readable = false)
        }
        val dex = Dex(parsed.flatMap { it.types }.toSet(), parsed.flatMap { it.methods }.toSet(), parsed.flatMap { it.strings })
        return report(dex, entryFindings, bytes.size)
    }

    /** Applies the rules to an already-parsed [dex]. Pure → unit tested. */
    fun report(dex: Dex, extra: List<Finding> = emptyList(), size: Int = 0): Report {
        val findings = extra + RULES.filter { r -> runCatching { r.match(dex) }.getOrDefault(false) }.map { Finding(it.level, it.title, it.detail) }
        val plainHttp = dex.strings.count { s -> s.startsWith("http://") && !s.startsWith("http://localhost") && !s.startsWith("http://127.") && !s.startsWith("http://schemas.") && !s.startsWith("http://www.w3.org") }
        val all = if (plainHttp > 0) findings + Finding(Level.SAFE, "Unencrypted links", "Uses $plainHttp plain http:// address${if (plainHttp == 1) "" else "es"} that others on your network could read.") else findings
        val level = when {
            all.any { it.level == Level.DANGER } -> Level.DANGER
            all.any { it.level == Level.CAUTION } -> Level.CAUTION
            else -> Level.SAFE
        }
        val sorted = all.distinctBy { it.title }.sortedBy { -it.level.ordinal }
        return Report(level, sorted, classes = dex.types.size, methods = dex.methods.size, sizeBytes = size)
    }

    // region DEX parsing (format: https://source.android.com/docs/core/runtime/dex-format)

    internal fun parseDex(d: ByteArray): Dex {
        require(d.size >= 0x70 && d[0] == 'd'.code.toByte() && d[1] == 'e'.code.toByte() && d[2] == 'x'.code.toByte()) { "not a dex" }
        fun u32(o: Int): Int = (d[o].toInt() and 0xff) or ((d[o + 1].toInt() and 0xff) shl 8) or
            ((d[o + 2].toInt() and 0xff) shl 16) or ((d[o + 3].toInt() and 0xff) shl 24)
        fun u16(o: Int): Int = (d[o].toInt() and 0xff) or ((d[o + 1].toInt() and 0xff) shl 8)
        val stringCount = u32(0x38); val stringOff = u32(0x3C)
        val typeCount = u32(0x40); val typeOff = u32(0x44)
        val methodCount = u32(0x58); val methodOff = u32(0x5C)
        require(stringCount in 0..2_000_000 && typeCount in 0..200_000 && methodCount in 0..500_000) { "bad sizes" }
        val strings = ArrayList<String>(stringCount)
        for (i in 0 until stringCount) {
            var p = u32(stringOff + i * 4)
            // uleb128 utf16 length, then MUTF-8 bytes ending in 0.
            while (p < d.size && (d[p].toInt() and 0x80) != 0) p++
            p++
            val start = p
            while (p < d.size && d[p].toInt() != 0) p++
            strings += String(d, start, (p - start).coerceAtLeast(0), Charsets.UTF_8)
        }
        val types = ArrayList<String>(typeCount)
        for (i in 0 until typeCount) types += strings.getOrElse(u32(typeOff + i * 4)) { "" }
        val methods = HashSet<String>(methodCount * 2)
        for (i in 0 until methodCount) {
            val o = methodOff + i * 8
            val cls = types.getOrElse(u16(o)) { "" }
            val name = strings.getOrElse(u32(o + 4)) { "" }
            methods += "$cls->$name"
        }
        return Dex(types.toSet(), methods, strings)
    }

    // endregion
}
