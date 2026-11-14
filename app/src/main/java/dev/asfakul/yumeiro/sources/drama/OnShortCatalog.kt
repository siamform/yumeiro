/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.drama

import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapter for ONShort (onshort.net), a public web index of short dramas from many studios.
 *
 * Its web player uses one of three transports, chosen per series by the site:
 *  - `runtime`: one JSON with every episode (`data-runtime`);
 *  - `rest-get`: one episode per call, with a short-lived session ticket sent as
 *    `X-ONShort-Ticket` (refreshed from the answer, and re-tried once on 401/403);
 *  - legacy: `/wp-json/onshort-<platform>/v1/series/<post>/episode/<n>`.
 * Yumeiro reads the same public endpoints the page uses, falling back in that order.
 */
class OnShortCatalog(
    private val base: String = "https://onshort.net",
    private val fetch: suspend (url: String, headers: Map<String, String>) -> Pair<Int, String> = ::httpGet,
) : DramaCatalog {
    override val id = "onshort"
    override val label = "ONShort"
    // Round 2: more studios. A platform whose episodes the site can't serve right now is hidden
    // by YumeiroDramaProvider's health check (ShortMax and FlexTV were down at the source on 2026-11-14).
    override val platforms = listOf(
        DramaPlatform("dramabox", "DramaBox"), DramaPlatform("reelshort", "ReelShort"),
        DramaPlatform("netshort", "NetShort"), DramaPlatform("dramawave", "DramaWave"),
        DramaPlatform("goodshort", "GoodShort"), DramaPlatform("shortmax", "ShortMax"),
        DramaPlatform("dramabite", "DramaBite"), DramaPlatform("shortswave", "ShortsWave"),
        DramaPlatform("stardusttv", "StardustTV"), DramaPlatform("flextv", "FlexTV"),
    )

    /** Parsed series pages for a few minutes, so every episode doesn't refetch the page. */
    private val seriesCache = ConcurrentHashMap<String, Pair<Long, DramaSeries>>()
    /** Runtime JSON per post id (all episodes at once). */
    private val runtimeCache = ConcurrentHashMap<String, Pair<Long, Map<Int, DramaStreams>>>()

    override suspend fun shelf(platform: DramaPlatform, page: Int): List<DramaShow> {
        val url = if (page <= 1) "$base/platform/${platform.slug}/" else "$base/platform/${platform.slug}/page/$page/"
        val (code, body) = fetch(url, mapOf("Accept" to "text/html"))
        if (code == 404) return emptyList()
        if (code !in 200..299) throw IOException("${platform.name} shelf answered HTTP $code")
        return OnShortParser.cards(body, base)
    }

    override suspend fun search(query: String): List<DramaShow> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val (code, body) = fetch("$base/wp-json/onshort-theme/v1/search?q=$q&limit=48&lang=en", mapOf("Accept" to "application/json"))
        if (code !in 200..299) throw IOException("Search answered HTTP $code")
        return OnShortParser.searchResults(body)
    }

    override suspend fun series(url: String): DramaSeries {
        seriesCache[url]?.let { (at, s) -> if (System.currentTimeMillis() - at < SERIES_TTL) return s }
        val (code, body) = fetch(url, mapOf("Accept" to "text/html"))
        if (code !in 200..299) throw IOException("Series page answered HTTP $code")
        val s = OnShortParser.series(body, url) ?: throw IOException("This series has no player")
        seriesCache[url] = System.currentTimeMillis() to s
        return s
    }

    override suspend fun streams(series: DramaSeries, episode: Int): DramaStreams {
        val h = series.hints
        val referer = series.url
        var lastError: Throwable? = null
        // 1. Runtime: every episode in one call.
        h["runtime"]?.takeIf { it.isNotBlank() }?.let { rt ->
            runCatching {
                val post = h["post"].orEmpty()
                val cached = runtimeCache[post]?.takeIf { System.currentTimeMillis() - it.first < RUNTIME_TTL }?.second
                val all = cached?.takeIf { episode in it } ?: run {
                    // A series the index hasn't warmed yet answers `{refreshing:true, episodes:[]}`
                    // for a moment (the web player polls too): retry briefly, then force a refresh.
                    var parsed: Map<Int, DramaStreams> = emptyMap()
                    for (attempt in 0 until 3) {
                        val sep = if (rt.contains('?')) '&' else '?'
                        val u = if (attempt == 2) "$rt${sep}force=1" else rt
                        val (code, body) = fetch(u, mapOf("Referer" to referer, "Accept" to "application/json", "Cache-Control" to "no-cache"))
                        if (code !in 200..299 && attempt == 2) throw IOException("Runtime answered HTTP $code")
                        if (code in 200..299) parsed = OnShortParser.runtime(body, referer)
                        if (episode in parsed) break
                        kotlinx.coroutines.delay(900L)
                    }
                    parsed.also { if (it.isNotEmpty()) runtimeCache[post] = System.currentTimeMillis() to it }
                }
                all[episode]?.takeIf { it.sources.isNotEmpty() }?.let { return it }
            }.onFailure { lastError = it }
        }
        // 2. REST with the page's session ticket (refreshed from every answer).
        val endpoint = h["endpoint"].orEmpty()
        if (endpoint.isNotBlank() && h["post"] != null) {
            val endpoints = listOf(endpoint, h["endpointAlt"].orEmpty()).filter { it.isNotBlank() }.distinct()
            var ticket = h["ticket"].orEmpty()
            for (ep in endpoints) {
                val sep = if (ep.contains('?')) '&' else '?'
                val u = "$ep${sep}post=${h["post"]}&episode=$episode&_t=${System.currentTimeMillis()}"
                for (attempt in 0..1) {
                    val r = runCatching {
                        fetch(u, mapOf("Referer" to referer, "X-ONShort-Player" to "1", "X-ONShort-Ticket" to ticket, "Accept" to "application/json", "Cache-Control" to "no-cache"))
                    }.onFailure { lastError = it }.getOrNull() ?: break
                    OnShortParser.ticketOf(r.second)?.let { ticket = it }
                    if ((r.first == 401 || r.first == 403) && attempt == 0) {
                        // Ticket expired: the answer carries a fresh one; otherwise reload the page once.
                        if (OnShortParser.ticketOf(r.second) == null) {
                            seriesCache.remove(series.url)
                            runCatching { series(series.url).hints["ticket"] }.getOrNull()?.let { ticket = it }
                        }
                        continue
                    }
                    if (r.first in 200..299) {
                        val s = OnShortParser.episode(r.second, referer)
                        if (s != null && s.sources.isNotEmpty()) return s
                    }
                    lastError = IOException("Episode $episode answered HTTP ${r.first}")
                    break
                }
            }
        }
        // 3. Legacy per-platform endpoint.
        val platform = h["platformSlug"]
        if (!platform.isNullOrBlank() && h["post"] != null) {
            runCatching {
                val (code, body) = fetch("$base/wp-json/onshort-$platform/v1/series/${h["post"]}/episode/$episode", mapOf("Referer" to referer, "Accept" to "application/json"))
                if (code in 200..299) OnShortParser.episode(body, referer)?.takeIf { it.sources.isNotEmpty() }?.let { return it }
                throw IOException("Episode $episode answered HTTP $code")
            }.onFailure { lastError = it }
        }
        throw lastError ?: IOException("No stream for episode $episode")
    }

    companion object {
        private const val SERIES_TTL = 5 * 60_000L
        private const val RUNTIME_TTL = 10 * 60_000L

        suspend fun httpGet(url: String, headers: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            NetworkClient.get().newCall(req).execute().use { r -> r.code to r.body.string() }
        }
    }
}

/** Pure parsing, kept apart from the network so it can be unit-tested on saved pages. */
object OnShortParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun cards(html: String, base: String = "https://onshort.net"): List<DramaShow> {
        val doc = Jsoup.parse(html, base)
        return doc.select("article.series-card").mapNotNull { card ->
            val a = card.selectFirst("a.series-card__link") ?: card.selectFirst("a[href]") ?: return@mapNotNull null
            val title = card.selectFirst("h3")?.text()?.trim().orEmpty().ifBlank { card.selectFirst("img[alt]")?.attr("alt").orEmpty() }
            if (title.isBlank()) return@mapNotNull null
            DramaShow(
                title = title,
                url = a.absUrl("href").ifBlank { a.attr("href") },
                cover = card.selectFirst(".series-card__poster img")?.let { it.absUrl("src").ifBlank { it.attr("data-src") } },
                episodes = card.selectFirst(".episode-pill")?.ownText()?.trim()?.toIntOrNull(),
                platform = card.selectFirst(".platform-badge")?.attr("title")?.ifBlank { null } ?: card.selectFirst(".series-card__copy span")?.text(),
            )
        }.distinctBy { it.url }
    }

    fun searchResults(body: String): List<DramaShow> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        return (root["results"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val url = o.s("url") ?: return@mapNotNull null
            val p = o["platform"]
            DramaShow(
                title = o.s("title") ?: o.s("base_title") ?: return@mapNotNull null,
                url = url, cover = o.s("cover"), episodes = o.s("total")?.toDoubleOrNull()?.toInt(),
                platform = (p as? JsonObject)?.s("name") ?: (p as? JsonPrimitive)?.contentOrNull,
                language = o.s("lang")?.ifBlank { null },
            )
        }
    }

    fun series(html: String, url: String): DramaSeries? {
        val doc: Document = Jsoup.parse(html, url)
        val player = doc.getElementById("onshort-player") ?: return null
        val d = { k: String -> player.attr("data-$k").trim() }
        val episodes = doc.select("button.episode-button[data-episode], [data-episode-list] [data-episode]")
            .mapNotNull { it.attr("data-episode").toIntOrNull() }.maxOrNull()
        val runtime = d("runtime")
        val platformSlug = Regex("""/onshort-([a-z0-9_-]+)/v1/""").find(runtime)?.groupValues?.get(1)
            ?: doc.selectFirst("a[href*=/platform/]")?.attr("href")?.let { Regex("""/platform/([a-z0-9_-]+)/""").find(it)?.groupValues?.get(1) }
        val meta = { p: String -> doc.selectFirst("meta[property=$p]")?.attr("content")?.trim()?.ifBlank { null } }
        val totalFromMeta = meta("og:description")?.let { Regex("""(\d+)\s+listed episodes""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return DramaSeries(
            title = d("title").ifBlank { doc.selectFirst("h1")?.text().orEmpty() }.ifBlank { meta("og:title").orEmpty() },
            url = url,
            cover = d("cover").ifBlank { null } ?: meta("og:image"),
            plot = doc.selectFirst(".story-copy")?.text()?.trim()?.ifBlank { null } ?: meta("og:description"),
            platform = doc.selectFirst("a[href*=/platform/]")?.text()?.trim()?.ifBlank { null } ?: platformSlug,
            episodes = maxOf(episodes ?: 0, totalFromMeta ?: 0).coerceAtLeast(1),
            language = d("lang").ifBlank { null },
            hints = buildMap {
                put("post", d("post"))
                put("transport", d("transport"))
                if (runtime.isNotBlank()) put("runtime", runtime)
                d("player-endpoint").takeIf { it.isNotBlank() }?.let { put("endpoint", it) }
                d("player-endpoint-alt").takeIf { it.isNotBlank() }?.let { put("endpointAlt", it) }
                d("player-ticket").takeIf { it.isNotBlank() }?.let { put("ticket", it) }
                platformSlug?.let { put("platformSlug", it) }
            },
        ).let { s -> s.copy(hints = s.hints + ("tags" to doc.select(".tag-box .tag-cloud span").joinToString("|") { it.text() })) }
    }

    fun ticketOf(body: String): String? =
        runCatching { (json.parseToJsonElement(body) as? JsonObject)?.s("ticket") }.getOrNull()?.ifBlank { null }

    /** One episode answer: `{url, quality, sources:{q:url}, subtitles:[{url,lang,label}]}`. */
    fun episode(body: String, referer: String?): DramaStreams? {
        val o = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        if ((o["ok"] as? JsonPrimitive)?.contentOrNull == "false") return null
        return streamsOf(o, referer)
    }

    /** Runtime answer: `{episodes:[{episode, url, sources?, subtitles}]}` → episode → streams. */
    fun runtime(body: String, referer: String?): Map<Int, DramaStreams> {
        val o = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyMap()
        return (o["episodes"] as? JsonArray).orEmpty().mapNotNull { e ->
            val eo = e as? JsonObject ?: return@mapNotNull null
            val n = eo.s("episode")?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            n to (streamsOf(eo, referer) ?: return@mapNotNull null)
        }.toMap()
    }

    private fun streamsOf(o: JsonObject, referer: String?): DramaStreams? {
        val sources = LinkedHashMap<String, String>()
        (o["sources"] as? JsonObject)?.forEach { (q, v) -> (v as? JsonPrimitive)?.contentOrNull?.takeIf { it.startsWith("http") }?.let { sources[q.filter(Char::isDigit)] = it } }
        o.s("url")?.takeIf { it.startsWith("http") }?.let { u -> if (u !in sources.values) sources[o.s("quality")?.filter(Char::isDigit).orEmpty()] = u }
        if (sources.isEmpty()) return null
        val subs = (o["subtitles"] as? JsonArray).orEmpty().mapNotNull { s ->
            val so = s as? JsonObject ?: return@mapNotNull null
            val u = so.s("url") ?: so.s("src") ?: return@mapNotNull null
            DramaSubtitle(u, so.s("lang") ?: so.s("language"), so.s("label"))
        }
        return DramaStreams(sources, subs, referer)
    }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    @Suppress("unused") private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull
}
