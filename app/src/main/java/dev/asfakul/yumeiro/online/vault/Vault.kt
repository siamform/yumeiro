/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.vault

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * F1 Yumeiro Vault (Beta chapter, audits/AUDIT_R.md): a saved title with the source links found on its page, kept
 * as one small self-contained .html file in Download/Yumeiro/Vault. Pure model, HTML writer/reader and link rules
 * (no Android), so they are unit tested.
 */
data class VaultSource(
    val name: String,
    val url: String,
    /** What the link is: a stream the page's player loaded, a direct file, an embed/player page or a link. */
    val kind: Kind,
    /** Only Referer and User-Agent are kept (never cookies or tokens: the file sits in shared storage). */
    val referer: String? = null,
    val userAgent: String? = null,
) {
    enum class Kind(val label: String) { STREAM("Stream"), FILE("Video file"), EMBED("Player page"), LINK("Link") }
}

data class VaultEntry(
    val id: String,
    val title: String,
    /** The page the sources were saved from (lets an expired stream be found again on its page). */
    val pageUrl: String,
    val sources: List<VaultSource>,
    val savedAt: Long,
    val version: Int = FORMAT,
) {
    companion object { const val FORMAT = 1 }
}

object VaultHtml {
    private const val DATA_ID = "yumeiro-vault"
    private val json = Json { ignoreUnknownKeys = true }

    fun escape(s: String): String = buildString(s.length + 16) {
        for (c in s) when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;")
            '"' -> append("&quot;"); '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    /** The entry as JSON (inside the file, so a Vault folder can be read back after a reinstall). */
    fun toJson(e: VaultEntry): JsonObject = buildJsonObject {
        put("format", e.version); put("id", e.id); put("title", e.title); put("page", e.pageUrl); put("saved", e.savedAt)
        put("sources", buildJsonArray {
            for (s in e.sources) add(buildJsonObject {
                put("name", s.name); put("url", s.url); put("kind", s.kind.name)
                s.referer?.let { put("referer", it) }; s.userAgent?.let { put("ua", it) }
            })
        })
    }

    fun fromJson(o: JsonObject): VaultEntry? = runCatching {
        fun str(k: String, from: JsonObject = o) = (from[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val sources = (o["sources"] as? JsonArray).orEmpty().mapNotNull { el ->
            val s = el.jsonObject
            val url = str("url", s)?.takeIf(VaultLinks::isSafeUrl) ?: return@mapNotNull null
            VaultSource(
                name = str("name", s).orEmpty().ifBlank { VaultLinks.defaultName(url) }, url = url,
                kind = runCatching { VaultSource.Kind.valueOf(str("kind", s).orEmpty()) }.getOrDefault(VaultSource.Kind.LINK),
                referer = str("referer", s)?.takeIf(VaultLinks::isSafeUrl), userAgent = str("ua", s)?.take(400),
            )
        }
        VaultEntry(
            id = str("id")?.takeIf { it.matches(ID) } ?: return null,
            title = str("title").orEmpty().ifBlank { "Untitled" }.take(MAX_TITLE),
            pageUrl = str("page")?.takeIf(VaultLinks::isSafeUrl).orEmpty(),
            sources = sources,
            savedAt = (o["saved"] as? JsonPrimitive)?.longOrNull ?: 0L,
            version = (o["format"] as? JsonPrimitive)?.content?.toIntOrNull() ?: VaultEntry.FORMAT,
        )
    }.getOrNull()

    const val MAX_TITLE = 160
    private val ID = Regex("[A-Za-z0-9_-]{4,64}")

    /**
     * The saved page: a Yumeiro header, the title and the list of source names linked to their addresses. Nothing
     * else from the site (no snapshot, ads, posters or page text). A Content-Security-Policy forbids every script,
     * frame and remote load, so the file is safe even when opened outside Yumeiro; the JSON block is data only.
     */
    fun render(e: VaultEntry, savedLabel: String): String {
        // <, > and & are written as \u escapes, so no text in the data block can end or confuse the script element.
        val data = json.encodeToString(JsonObject.serializer(), toJson(e)).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
        val rows = e.sources.joinToString("\n") { s ->
            """      <li><a href="${escape(s.url)}" data-kind="${s.kind.name}"><span class="n">${escape(s.name)}</span><span class="k">${escape(s.kind.label)}</span></a></li>"""
        }
        val host = VaultLinks.hostOf(e.pageUrl).orEmpty()
        return """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'">
<meta name="referrer" content="no-referrer">
<meta name="generator" content="Yumeiro Vault">
<title>${escape(e.title)}</title>
<style>
  :root { color-scheme: dark light; --bg:#0d0c12; --card:#17151f; --line:#2a2733; --ink:#f3efe6; --soft:#a9a3b3; --gold:#e3b66b; }
  @media (prefers-color-scheme: light) { :root { --bg:#f7f4ee; --card:#ffffff; --line:#e7e1d6; --ink:#1d1a22; --soft:#6d6676; --gold:#a8741f; } }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--ink); font:15px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans", sans-serif; }
  header { padding:20px 18px 8px; max-width:720px; margin:0 auto; }
  .mark { display:flex; align-items:center; gap:10px; letter-spacing:.14em; text-transform:uppercase; font-size:11px; font-weight:700; color:var(--gold); }
  .mark i { width:22px; height:22px; border-radius:7px; background:linear-gradient(135deg,var(--gold),#c46a8a); display:inline-block; }
  h1 { font-size:22px; line-height:1.25; margin:14px 0 4px; font-weight:800; word-wrap:break-word; }
  .meta { color:var(--soft); font-size:12.5px; }
  main { max-width:720px; margin:0 auto; padding:8px 14px 28px; }
  ul { list-style:none; margin:10px 0 0; padding:0; display:grid; gap:8px; }
  a { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:14px 16px; border-radius:12px; background:var(--card); border:1px solid var(--line); color:var(--ink); text-decoration:none; min-height:52px; }
  a:active, a:focus { border-color:var(--gold); outline:none; }
  .n { font-weight:650; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
  .k { flex:none; font-size:11px; font-weight:700; letter-spacing:.06em; text-transform:uppercase; color:var(--gold); }
  footer { color:var(--soft); font-size:11.5px; text-align:center; padding:6px 16px 24px; }
</style>
</head>
<body>
  <header>
    <div class="mark"><i></i>Yumeiro Vault</div>
    <h1>${escape(e.title)}</h1>
    <div class="meta">${escape(savedLabel)}${if (host.isNotEmpty()) " · " + escape(host) else ""} · ${e.sources.size} ${if (e.sources.size == 1) "source" else "sources"}</div>
  </header>
  <main>
    <ul>
$rows
    </ul>
  </main>
  <footer>Saved in Yumeiro. Links can stop working when a site changes them.</footer>
<script type="application/json" id="$DATA_ID">$data</script>
</body>
</html>
"""
    }

    private val dataBlock = Regex("""<script type="application/json" id="$DATA_ID">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    /** Reads an entry back from a Vault file; null for anything that is not one (or is damaged). */
    fun parse(html: String): VaultEntry? {
        val raw = dataBlock.find(html)?.groupValues?.get(1) ?: return null
        return runCatching { fromJson(json.parseToJsonElement(raw).jsonObject) }.getOrNull()
    }
}

object VaultLinks {
    private val DN = Regex("""[?&]dn=([^&]+)""")
    private val SPACES = Regex("\\s+")
    private val DASHES = Regex("-+")

    /** The host of an http(s) address, lower case without "www." (read by hand: java.net.URI refuses many real links). */
    fun hostOf(url: String): String? {
        val u = url.trim()
        val rest = u.substringAfter("://", "").takeIf { it.isNotEmpty() && u.substringBefore("://").let { s -> s.equals("http", true) || s.equals("https", true) } } ?: return null
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
        val host = (if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')).lowercase()
        if (host.isEmpty() || host.any { it.isWhitespace() || it == '<' || it == '>' || it == '"' || it == '\\' }) return null
        return host.removePrefix("www.")
    }

    /** Only web addresses and magnet links are saved: never javascript:, data:, file: or content: links. */
    fun isSafeUrl(url: String): Boolean {
        val u = url.trim()
        if (u.length > 8_192 || u.any { it.isISOControl() }) return false
        val scheme = u.substringBefore(':', "").lowercase()
        return when (scheme) {
            "http", "https" -> hostOf(u) != null
            "magnet" -> u.startsWith("magnet:?", true)
            else -> false
        }
    }

    fun defaultName(url: String): String {
        if (url.startsWith("magnet:", true)) return DN.find(url)?.groupValues?.get(1)
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }?.take(80) ?: "Torrent"
        val host = hostOf(url) ?: return "Source"
        val file = url.substringBefore('?').substringBefore('#').substringAfterLast('/').takeIf { '.' in it && it.length in 3..80 }
        return if (file != null) "$host · $file" else host
    }

    private val mediaExt = setOf("m3u8", "mpd", "mp4", "mkv", "webm", "mov", "m4v", "ts", "flv", "avi", "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "ism")
    private val embedPath = Regex("""/(embed|e|v|player|watch|stream|video|play|iframe|file|f|d)(/|-|\?|$)""", RegexOption.IGNORE_CASE)
    private val junkPath = Regex("""(/(login|signin|signup|register|account|privacy|terms|dmca|contact|about|faq|tag|tags|category|genre|search|page)(/|\?|$))|(\.(css|js|png|jpe?g|gif|webp|svg|ico|woff2?|ttf|xml|json|txt)(\?|$))""", RegexOption.IGNORE_CASE)
    private val social = setOf("facebook.com", "twitter.com", "x.com", "instagram.com", "t.me", "telegram.me", "discord.gg", "discord.com", "reddit.com", "pinterest.com", "tiktok.com", "whatsapp.com", "wa.me", "linkedin.com", "youtube.com/channel")

    /** A link found on the page, as the collector script saw it. */
    data class Found(val url: String, val text: String, val from: String)

    /**
     * Turns what the page held into Vault sources: the streams its player actually loaded first, then direct media
     * files, then player/embed pages (iframes and links to other hosts that look like players). Ads, trackers,
     * navigation inside the site, social links and assets are dropped. Duplicates (same address without its
     * fragment) are kept once, under the best name seen.
     */
    fun classify(pageUrl: String, captured: List<Pair<String, String>>, found: List<Found>, isAd: (String) -> Boolean, isMedia: (String) -> Boolean): List<VaultSource> {
        val pageHost = hostOf(pageUrl)
        val out = LinkedHashMap<String, VaultSource>()
        fun key(u: String) = u.trim().substringBefore('#')
        fun add(s: VaultSource) {
            val k = key(s.url)
            val old = out[k]
            if (old == null || old.kind.ordinal > s.kind.ordinal) out[k] = s
        }
        for ((url, label) in captured) {
            if (!isSafeUrl(url) || isAd(url)) continue
            add(VaultSource(label.ifBlank { defaultName(url) }.take(120), url.trim(), VaultSource.Kind.STREAM, referer = pageUrl.takeIf(::isSafeUrl)))
        }
        for (f in found) {
            val url = f.url.trim()
            if (!isSafeUrl(url) || isAd(url)) continue
            val host = hostOf(url)
            if (host != null && social.any { host == it || host.endsWith(".$it") }) continue
            val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase()
            val name = f.text.trim().replace(SPACES, " ").take(120).ifBlank { defaultName(url) }
            when {
                url.startsWith("magnet:", true) -> add(VaultSource(name, url, VaultSource.Kind.FILE))
                ext in mediaExt || isMedia(url) -> add(VaultSource(name, url, VaultSource.Kind.FILE, referer = pageUrl.takeIf(::isSafeUrl)))
                junkPath.containsMatchIn(url.substringAfter(host ?: "")) -> Unit
                f.from == "iframe" || f.from == "video" -> add(VaultSource(name, url, VaultSource.Kind.EMBED, referer = pageUrl.takeIf(::isSafeUrl)))
                host != pageHost && embedPath.containsMatchIn(url.substringAfter(host ?: "")) -> add(VaultSource(name, url, VaultSource.Kind.EMBED, referer = pageUrl.takeIf(::isSafeUrl)))
                f.from == "server" -> add(VaultSource(name, url, VaultSource.Kind.EMBED, referer = pageUrl.takeIf(::isSafeUrl)))
                else -> Unit
            }
        }
        return out.values.sortedBy { it.kind.ordinal }.take(MAX_SOURCES)
    }

    const val MAX_SOURCES = 60

    /** A file name for [title] (readable, safe on every file system); the id keeps it unique. Pure. */
    fun fileName(title: String, id: String): String {
        val base = title.lowercase().map { c -> if (c.isLetterOrDigit()) c else '-' }.joinToString("")
            .replace(DASHES, "-").trim('-').take(48).ifBlank { "title" }
        return "$base-$id.html"
    }

    /** The collector script's answer (a JSON array of {u, t, f}). */
    fun parseFound(raw: String?): List<Found> {
        val text = raw?.trim()?.let { if (it.startsWith("\"")) runCatching { Json.parseToJsonElement(it).jsonPrimitive.content }.getOrNull() else it } ?: return emptyList()
        return runCatching {
            Json.parseToJsonElement(text).jsonArray.mapNotNull { el ->
                val o = el.jsonObject
                val u = (o["u"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                Found(u, (o["t"] as? JsonPrimitive)?.content.orEmpty(), (o["f"] as? JsonPrimitive)?.content.orEmpty())
            }
        }.getOrDefault(emptyList()).take(2_000)
    }
}
