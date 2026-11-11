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
 * The indicator rules are stored as fingerprints, not readable text; see [Dex].
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

    /**
     * What a DEX file references, reduced to a set of normalised keys and their 64-bit fingerprints.
     *
     * Why fingerprints instead of plain text: the scanner's job is to recognise malware indicators
     * (miner pool schemes, SMS/contacts URIs, device-admin and installer APIs...). Shipping those
     * indicators as plain string constants made Yumeiro's own `classes.dex` look like the thing it
     * was looking for, and antivirus heuristics flagged the app (Kaspersky
     * `HEUR:RiskTool.AndroidOS.Miner`). The rules now hold FNV-1a fingerprints of the indicator keys;
     * the readable indicator list lives in the unit test (`ExtensionScannerTest.INDICATORS`) and in
     * docs/security/AUDIT.md, and the test fails if the two ever drift apart. Detection behaviour is
     * unchanged: every key below is derived from the extension's own DEX tables.
     *
     * Key kinds: `t:` exact type, `c:` outer class (type without `$Inner` / `;`), `p:` every parent
     * package (ending in `/`), `m:` `owner->method`, `s:` URI scheme, `u:` `scheme://authority`,
     * `h:` host and each parent domain, `w:` word, `x:` whole lower-cased string.
     */
    class Dex(val types: Set<String>, val methods: Set<String>, val strings: List<String>) {
        fun method(owner: String, vararg names: String) = names.any { "$owner->$it" in methods }
        fun ownerAny(prefix: String) = methods.any { it.startsWith(prefix) }
        fun str(vararg needles: String) = strings.any { s -> needles.any { s.contains(it, ignoreCase = true) } }

        val fingerprints: Set<Long> by lazy { keys().mapTo(HashSet()) { fingerprint(it) } }
        fun has(prints: LongArray) = prints.any { it in fingerprints }

        internal fun keys(): Set<String> {
            val out = HashSet<String>()
            for (t in types) {
                out += "t:$t"
                out += "c:" + t.substringBefore('$').removeSuffix(";")
                var i = t.indexOf('/')
                while (i > 0) { out += "p:" + t.substring(0, i + 1); i = t.indexOf('/', i + 1) }
            }
            for (m in methods) out += "m:$m"
            for (raw in strings) {
                if (raw.isEmpty() || raw.length > 4096) continue
                val s = raw.lowercase()
                out += "x:" + s.trim()
                for (u in URI.findAll(s)) {
                    out += "s:" + u.groupValues[1]
                    out += "u:" + u.groupValues[1] + "://" + u.groupValues[2]
                }
                for (tok in s.split(NOT_HOST)) {
                    if ('.' !in tok) continue
                    var d = tok.trim('.', '-')
                    while ('.' in d) { out += "h:$d"; d = d.substringAfter('.') }
                }
                for (w in s.split(NOT_WORD)) if (w.length >= 4) out += "w:$w"
            }
            return out
        }
    }

    private val URI = Regex("([a-z][a-z0-9+.-]*)://([^/?#\\s]*)")
    private val NOT_HOST = Regex("[^a-z0-9.-]+")
    private val NOT_WORD = Regex("[^a-z0-9_-]+")

    /** 64-bit FNV-1a over UTF-16 code units. Pure; the test recomputes every rule from plain text. */
    internal fun fingerprint(key: String): Long {
        var h = -3750763034362895579L
        for (ch in key) { h = h xor ch.code.toLong(); h *= 1099511628211L }
        return h
    }

    /** Fingerprints per rule title (plus "Android ID", used inside the identifiers rule). */
    internal val PRINTS: Map<String, LongArray> = mapOf(
"Runs system commands" to longArrayOf(-8515285176054807133L, 824854758198270122L, -3088690013225074758L),
        "Loads hidden code" to longArrayOf(6412061160159583850L, 4016095347430006182L, -6072442011111908598L, -7969573423343877787L),
        "Loads native code" to longArrayOf(-7536426817983834016L, 8741940318201221973L, -6264855173204871543L, 6963941040179462486L),
        "Reads or sends SMS" to longArrayOf(5823415202459178909L, 2393136113756540946L, 2068931913688755404L),
        "Reads contacts or call log" to longArrayOf(-5125429051731922203L, -9062252826201702932L, -5770238714235745384L, -383747214944912672L, 1215786470683453330L),
        "Tracks location" to longArrayOf(6659045308175126886L, -487602606115300494L, -6757485564638235336L, 2712146006843241429L),
        "Records camera or microphone" to longArrayOf(2010411781217233058L, 538531614352695442L, -2542712398305458092L, 5264090595065035547L),
        "Installs apps or controls the device" to longArrayOf(5810971674429543178L, 2952588882313615237L, -202641046673432505L, 3588168015629564960L),
        "Reads your accounts" to longArrayOf(7990218430549916703L, 8933879542739483002L, -8113821619219923038L, -50644448056283099L),
        "Crypto miner" to longArrayOf(-5922478972328333164L, -7961361658109483247L, -4400792159314210929L, -6369912979274310061L, -784066563150807279L, 1241591426987859522L),
        "Reads device identifiers" to longArrayOf(-1114911356981229772L),
        "Android ID" to longArrayOf(6997208227483349513L),
        "Lists your installed apps" to longArrayOf(-262755896195567880L, -6354673130514848958L, 5204021051242794842L),
        "Reads the clipboard" to longArrayOf(-2955764606371679387L, 8805050876464635652L),
        "Defines code at run time" to longArrayOf(6971146242821264772L),
        "Analytics or ad trackers" to longArrayOf(6602032456394241178L, 4394798964339181464L, 5236129178452524100L, 5007435865945107896L, -408720940404878409L, 1528976097608244986L, -8955142192909190277L, 8968025067809325227L, -1725342892645124720L, 5433735315999159342L),
        "JavaScript bridge in a web page" to longArrayOf(1379443387417517185L),
        "Shows its own notifications or starts services" to longArrayOf(3300855383696586903L, -5614417512824944907L, -447133521103251304L, -7096003695154740569L),
    )

    private fun rule(level: Level, title: String, detail: String, also: (Dex) -> Boolean = { false }): Rule {
        val prints = PRINTS.getValue(title)
        return Rule(level, title, detail) { it.has(prints) || also(it) }
    }

    private val RULES = listOf(
        rule(Level.DANGER, "Runs system commands", "Can start shell commands on your phone (Runtime.exec / ProcessBuilder)."),
        rule(Level.DANGER, "Loads hidden code", "Downloads or unpacks extra code at run time, which this check can't see."),
        rule(Level.DANGER, "Loads native code", "Loads native libraries (System.loadLibrary / System.load)."),
        rule(Level.DANGER, "Reads or sends SMS", "Uses the SMS manager or SMS inbox."),
        rule(Level.DANGER, "Reads contacts or call log", "Reads your contacts or call history."),
        rule(Level.DANGER, "Tracks location", "Asks Android for your location."),
        rule(Level.DANGER, "Records camera or microphone", "Uses the camera or records audio."),
        rule(Level.DANGER, "Installs apps or controls the device", "Uses the app installer, device admin or accessibility control."),
        rule(Level.DANGER, "Reads your accounts", "Lists the accounts on this phone."),
        rule(Level.DANGER, "Crypto miner", "Contains crypto-mining code or pool addresses."),
        Rule(Level.DANGER, "Deletes files on your storage", "Deletes files in shared storage (not just its own cache).") {
            (it.method("Ljava/io/File;", "delete", "deleteRecursively") || it.ownerAny("Lkotlin/io/FilesKt") && it.str("deleteRecursively")) &&
                (it.method("Landroid/os/Environment;", "getExternalStorageDirectory", "getExternalStoragePublicDirectory") || it.str("/sdcard/", "/storage/emulated/"))
        },
        rule(Level.CAUTION, "Reads device identifiers", "Reads the Android ID or phone details, which can identify your device.") {
            it.method("Landroid/provider/Settings\$Secure;", "getString") && it.has(PRINTS.getValue("Android ID"))
        },
        rule(Level.CAUTION, "Lists your installed apps", "Reads which apps are installed."),
        rule(Level.CAUTION, "Reads the clipboard", "Can read what you copied."),
        rule(Level.CAUTION, "Defines code at run time", "Builds classes at run time (often a JavaScript engine for a site's player)."),
        Rule(Level.CAUTION, "Reaches into Yumeiro itself", "References Yumeiro's own classes, which extensions normally never do.") {
            it.types.any { t -> t.startsWith("Ldev/asfakul/yumeiro/") } || it.str("dev.asfakul.yumeiro")
        },
        rule(Level.CAUTION, "Analytics or ad trackers", "Contains analytics / ad-network addresses."),
        rule(Level.CAUTION, "JavaScript bridge in a web page", "Exposes code to web pages (addJavascriptInterface)."),
        rule(Level.CAUTION, "Shows its own notifications or starts services", "Posts notifications or runs background work."),
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
