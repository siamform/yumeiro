/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import kotlin.math.abs

/**
 * NBUG-12: the video sources of a KVS (Kernel Video Sharing) page, read from its player config.
 *
 * KVS powers many video sites (Porn4fans among them). Its `kt_player(…, flashvars)` call carries
 * `video_url`, `video_alt_url`, `video_alt_url2`, … with `_text` labels ("480p", "720p"). Two things
 * made these invisible to [WebPageExtractor]: the addresses end in `.mp4/` (a trailing slash, so no
 * file extension is seen), and some sites hide the hash in `function/0/…` URLs that the player
 * de-scrambles with the page's `license_code`.
 *
 * The de-scrambling is a port of yt-dlp's `_kvs_get_real_url` / `_kvs_get_license_token`
 * (yt_dlp/extractor/generic.py, public domain under the Unlicense); the unit test checks it against
 * output of that Python code. Entries that need a sign-in (`*_redirect: '1'`, no `/get_file/`) are skipped.
 */
internal object KvsPlayer {
    data class Source(val url: String, val label: String?, val height: Int?)

    private val KT_CALL = Regex("""kt_player\s*\(\s*'[^']*'\s*,\s*'[^']*'\s*,\s*'[^']*'\s*,\s*'[^']*'\s*,\s*([A-Za-z_$][\w$]*)\s*\)""")
    private val PAIR = Regex("""([A-Za-z_]\w*)\s*:\s*'((?:[^'\\]|\\.)*)'""")
    private val URL_KEY = Regex("""video_(?:url|alt_url\d*)""")
    private val HEIGHT = Regex("""(\d{3,4})p""")
    private const val MAX_CONFIG = 40_000
    private const val HASH = 32

    /** The page's player config as key → value, or null when the page has no KVS player. */
    fun flashvars(html: String): Map<String, String>? {
        if (!html.contains("kt_player") && !html.contains("flashvars")) return null
        val name = KT_CALL.find(html)?.groupValues?.get(1) ?: "flashvars"
        // The variable name comes from the page, so the pattern is cached, not compiled per call.
        val start = dev.asfakul.yumeiro.text.RegexCache.get("""\b${Regex.escape(name)}\s*=\s*\{""").find(html) ?: return null
        val from = start.range.last + 1
        val end = closingBrace(html, from)?.takeIf { it - from <= MAX_CONFIG } ?: return null
        val body = html.substring(from, end)
        val out = LinkedHashMap<String, String>()
        PAIR.findAll(body).forEach { m -> out.putIfAbsent(m.groupValues[1], unescape(m.groupValues[2])) }
        return out.takeIf { it.isNotEmpty() }
    }

    /** Playable sources of the page, best quality first; empty when it is not a KVS page. */
    fun sources(html: String, pageUrl: String): List<Source> {
        val fv = flashvars(html) ?: return emptyList()
        val license = fv["license_code"]
        return fv.keys.filter { URL_KEY.matches(it) }.mapNotNull { key ->
            val raw = fv[key].orEmpty()
            if (fv["${key}_redirect"] == "1" || "/get_file/" !in raw) return@mapNotNull null
            val real = realUrl(raw, license) ?: return@mapNotNull null
            val abs = WebPageExtractor.absolutize(real, pageUrl) ?: return@mapNotNull null
            val label = fv["${key}_text"]?.ifBlank { null }
            Source(abs, label, label?.let { HEIGHT.find(it)?.groupValues?.get(1)?.toIntOrNull() })
        }.distinctBy { it.url }.sortedByDescending { it.height ?: 0 }
    }

    /** De-scrambles a `function/0/` address; returns plain addresses unchanged, null when it can't. */
    fun realUrl(url: String, licenseCode: String?): String? {
        if (!url.startsWith("function/0/")) return url
        val token = licenseCode?.let { licenseToken(it) } ?: return null
        val rest = url.removePrefix("function/0/")
        val schemeEnd = rest.indexOf("://").takeIf { it > 0 } ?: return null
        val pathStart = rest.indexOf('/', schemeEnd + 3).takeIf { it > 0 } ?: return null
        val tailStart = rest.indexOfAny(charArrayOf('?', '#'), pathStart).let { if (it < 0) rest.length else it }
        val parts = rest.substring(pathStart, tailStart).split('/').toMutableList()
        if (parts.size < 4 || parts[3].length < HASH) return null
        val hash = parts[3].substring(0, HASH)
        val idx = IntArray(HASH) { it }
        var accum = 0
        for (src in HASH - 1 downTo 0) {
            accum += token[src]
            val dest = (src + accum) % HASH
            val t = idx[src]; idx[src] = idx[dest]; idx[dest] = t
        }
        parts[3] = String(CharArray(HASH) { hash[idx[it]] }) + parts[3].substring(HASH)
        return rest.substring(0, pathStart) + parts.joinToString("/") + rest.substring(tailStart)
    }

    /** yt-dlp `_kvs_get_license_token`: 32 digits from the page's `license_code`. */
    internal fun licenseToken(code: String): IntArray? = runCatching {
        val lc = code.replace("$", "")
        if (lc.isEmpty() || !lc.all { it.isDigit() }) return null
        val values = lc.map { it.digitToInt() }
        val mod = lc.replace('0', '1')
        val center = mod.length / 2
        val front = mod.substring(0, center + 1).toLong()
        val back = mod.substring(center).toLong()
        val m = (4 * abs(front - back)).toString().take(center + 1)
        val out = ArrayList<Int>(m.length * 4)
        m.forEachIndexed { index, c -> for (offset in 0 until 4) out += (values[index + offset] + c.digitToInt()) % 10 }
        out.toIntArray().takeIf { it.size >= HASH }
    }.getOrNull()

    /** Index of the `}` that closes the object starting at [from], skipping quoted strings ("{time}"). */
    private fun closingBrace(s: String, from: Int): Int? {
        var depth = 0
        var quote = '\u0000'
        var i = from
        val limit = minOf(s.length, from + MAX_CONFIG + 1)
        while (i < limit) {
            val c = s[i]
            when {
                quote != '\u0000' -> if (c == '\\') i++ else if (c == quote) quote = '\u0000'
                c == '\'' || c == '"' -> quote = c
                c == '{' -> depth++
                c == '}' -> if (depth == 0) return i else depth--
            }
            i++
        }
        return null
    }

    private fun unescape(s: String) = s.replace("\\/", "/").replace("\\'", "'").replace("\\\\", "\\")
}
