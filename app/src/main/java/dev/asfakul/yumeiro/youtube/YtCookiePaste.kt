/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * "Paste cookie" sign-in, the fallback for when Google refuses the in-app sign-in page.
 * Like ArchiveTune's "Login with token", the user brings a signed-in YouTube cookie from a real
 * browser. We accept every shape people usually end up with, so the guide can stay simple:
 *
 *  - the raw `cookie` request-header value (DevTools → Network), with or without `Cookie:`;
 *  - a "Copy as cURL" command (`-b '…'` or `-H 'cookie: …'`);
 *  - a Netscape `cookies.txt` file (what yt-dlp and the "Get cookies.txt LOCALLY" extension use);
 *  - a JSON cookie export (Cookie-Editor and similar extensions);
 *  - ArchiveTune / Metrolist token text (`***INNERTUBE COOKIE*** =…`).
 *
 * Only youtube.com cookies are kept; Google-wide cookies from other sites are dropped.
 * Pure Kotlin so it is unit tested.
 */
object YtCookiePaste {

    enum class Format(val label: String) {
        HEADER("Cookie header"),
        CURL("cURL command"),
        NETSCAPE("cookies.txt"),
        JSON("JSON export"),
        ARCHIVETUNE("ArchiveTune token"),
    }

    sealed interface Result {
        data class Ok(val cookie: String, val format: Format, val count: Int) : Result
        data class Error(val message: String) : Result
    }

    private const val MAX_CHARS = 200_000

    fun parse(raw: String): Result {
        val text = raw.trim().removePrefix("\uFEFF")
        if (text.isEmpty()) return Result.Error("Paste your YouTube cookie first")
        if (text.length > MAX_CHARS) return Result.Error("That is too long to be a cookie. Paste only youtube.com cookies")
        val (pairs, format) = archiveTune(text)?.let { it to Format.ARCHIVETUNE }
            ?: curl(text)?.let { it to Format.CURL }
            ?: json(text)?.let { it to Format.JSON }
            ?: netscape(text)?.let { it to Format.NETSCAPE }
            ?: (header(text) to Format.HEADER)
        val cookie = YtAccount.sanitize(pairs.joinToString("; ") { "${it.first}=${it.second}" })
        if (cookie.isEmpty()) return Result.Error("No cookies found. Copy the whole cookie value, or a cookies.txt file")
        if (!YtAccount.hasLoginCookies(cookie)) {
            return Result.Error("These cookies are not signed in. Make sure you are signed in on youtube.com and copy them again (SAPISID and SID are missing)")
        }
        return Result.Ok(cookie, format, YtAccount.cookieMap(cookie).size)
    }

    // region formats

    private val atLine = Regex("""(?im)^\s*\*{3}\s*INNERTUBE COOKIE\s*\*{3}\s*=\s*(.+)$""")

    private fun archiveTune(text: String): List<Pair<String, String>>? =
        atLine.find(text)?.groupValues?.get(1)?.let { header(it) }

    private val curlCookie = listOf(
        Regex("""(?:^|\s)(?:-b|--cookie)\s+(?:'([^']*)'|"([^"]*)"|\$'([^']*)')"""),
        Regex("""(?i)(?:-H|--header)\s+(?:'cookie:\s*([^']*)'|"cookie:\s*([^"]*)"|\$'cookie:\s*([^']*)')"""),
    )

    private fun curl(text: String): List<Pair<String, String>>? {
        if (!text.startsWith("curl", ignoreCase = true)) return null
        val v = curlCookie.firstNotNullOfOrNull { r -> r.find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() } }
        return v?.let { header(it.replace("\\\n", "").replace("\\'", "'")) } ?: emptyList()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun json(text: String): List<Pair<String, String>>? {
        if (!text.startsWith("[") && !text.startsWith("{")) return null
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root["cookies"] as? JsonArray ?: JsonArray(listOf(root))
            else -> return null
        }
        return items.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.content?.trim().orEmpty()
            val value = o["value"]?.jsonPrimitive?.content?.trim().orEmpty()
            val domain = o["domain"]?.jsonPrimitive?.content ?: o["host"]?.jsonPrimitive?.content
            if (name.isEmpty() || (domain != null && !isYouTube(domain))) null else name to value
        }
    }

    private fun netscape(text: String): List<Pair<String, String>>? {
        val rows = text.lines().map { it.trim('\r') }
            .filter { it.isNotBlank() && (!it.startsWith("#") || it.startsWith("#HttpOnly_")) }
            .map { it.removePrefix("#HttpOnly_").split('\t') }
        if (rows.isEmpty() || rows.none { it.size >= 7 }) return null
        return rows.filter { it.size >= 7 && isYouTube(it[0]) && it[5].isNotBlank() }.map { it[5].trim() to it[6].trim() }
    }

    private fun header(text: String): List<Pair<String, String>> =
        text.replace("\r", "").replace("\n", " ").trim()
            .removePrefix("Cookie:").removePrefix("cookie:").removePrefix("COOKIE:")
            .trim().trim('"', '\'')
            .split(';').mapNotNull { part ->
                val p = part.trim()
                if (!p.contains('=')) null else p.substringBefore('=').trim() to p.substringAfter('=').trim()
            }.filter { it.first.isNotEmpty() && !it.first.contains(' ') }

    internal fun isYouTube(domain: String): Boolean {
        val d = domain.trim().trimStart('.').lowercase()
        return d == "youtube.com" || d.endsWith(".youtube.com")
    }

    // endregion

    /** True when a youtube.com page fetched with the cookie says it is signed in. */
    internal fun pageLooksSignedIn(html: String): Boolean? = when {
        html.contains("\"LOGGED_IN\":true") -> true
        html.contains("\"LOGGED_IN\":false") -> false
        else -> null
    }
}
