/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.drama

import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

// Precompiled once (never during composition, scrolling or per call): see docs/STABILITY-R15.md.
private val RX_ONSHORTCATALOG_1 = Regex("""/onshort-([a-z0-9_-]+)/v1/""")
private val RX_ONSHORTCATALOG_2 = Regex("""/platform/([a-z0-9_-]+)/""")
private val RX_ONSHORTCATALOG_3 = Regex("""(\d+)\s+listed episodes""")

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
    private val gate: OnShortGate = OnShortGate(),
    private val post: suspend (url: String, headers: Map<String, String>, body: String) -> Pair<Int, String> = ::httpPost,
    /** Clock for the shelf / series caches (tests move it). */
    private val clock: () -> Long = System::currentTimeMillis,
) : DramaCatalog {
    /**
     * R15: every call goes through [OnShortGate]. The site answers bursts of HTTP 502 (about 15 s)
     * and sometimes hangs for 25 s+ when it gets many calls at once (measured 2026-11-14). Without
     * the gate one stalled series page failed the whole title ("the banner shows but it never
     * plays") and a 502 during the health probe hid the studio from the feed.
     */
    override val recentlyBusy: Boolean get() = gate.recentlyBusy()

    private suspend fun gated(u: String, h: Map<String, String>, tries: Int = 0): Pair<Int, String> = gate.call(u, tries) { fetch(u, h) }
    /** A cached answer is reused while its signed links have more than 75 s left (as the web player does). */
    private fun fresh(st: DramaStreams, at: Long): Boolean {
        val nowMs = System.currentTimeMillis()
        return if (st.expiresAt > 0) st.expiresAt * 1000 - nowMs > 75_000 else nowMs - at < EPISODE_TTL
    }
    /** Episode answers (rest-get transport): one call serves every quality and later replays. */
    private val episodeCache = ConcurrentHashMap<String, Pair<Long, DramaStreams>>()
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

    /**
     * R15: shelves are stale-while-revalidate. A fresh answer (< [SHELF_TTL]) is reused; when the
     * site stalls (ReelShort / DramaWave shelves timed out on 2026-11-14) the last good shelf is
     * served instead of an empty row, so dramas don't vanish from the feed.
     */
    private val shelfCache = ConcurrentHashMap<String, Pair<Long, List<DramaShow>>>()

    override suspend fun shelf(platform: DramaPlatform, page: Int): List<DramaShow> {
        val url = if (page <= 1) "$base/platform/${platform.slug}/" else "$base/platform/${platform.slug}/page/$page/"
        val cached = shelfCache[url]
        if (cached != null && clock() - cached.first < SHELF_TTL) return cached.second
        val r = runCatching {
            val (code, body) = gated(url, mapOf("Accept" to "text/html"))
            if (code == 404) return@runCatching emptyList()
            if (code !in 200..299) throw IOException("${platform.name} shelf answered HTTP $code")
            OnShortParser.cards(body, base)
        }
        r.exceptionOrNull()?.let { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (cached != null && clock() - cached.first < STALE_TTL) return cached.second
            throw e
        }
        val shows = r.getOrThrow()
        if (shows.isNotEmpty() || cached == null) shelfCache[url] = clock() to shows
        return shows.ifEmpty { cached?.second.orEmpty() }
    }

    override suspend fun search(query: String): List<DramaShow> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val (code, body) = gated("$base/wp-json/onshort-theme/v1/search?q=$q&limit=48&lang=en", mapOf("Accept" to "application/json"))
        if (code !in 200..299) throw IOException("Search answered HTTP $code")
        return OnShortParser.searchResults(body)
    }

    /** R15: one fetch per series page at a time (the detail screen, playback and the health probe share it). */
    private val seriesInFlight = ConcurrentHashMap<String, Pair<kotlinx.coroutines.Deferred<DramaSeries>, GateLane>>()
    private val streamsInFlight = ConcurrentHashMap<String, Pair<kotlinx.coroutines.Deferred<DramaStreams>, GateLane>>()

    /** One request per key; a caller in a better lane promotes the shared request it joins (R16/R17). */
    private suspend fun <T> shared(map: ConcurrentHashMap<String, Pair<kotlinx.coroutines.Deferred<T>, GateLane>>, key: String, work: suspend () -> T): T {
        val mine = kotlin.coroutines.coroutineContext[GateLane]?.priority ?: GateLane.FEED
        val lane = GateLane(mine)
        val fresh = loads.async(lane, start = kotlinx.coroutines.CoroutineStart.LAZY) { work() }
        val entry = fresh to lane
        val d = map.putIfAbsent(key, entry)?.also { fresh.cancel() }
            ?: entry.also { fresh.invokeOnCompletion { _ -> map.remove(key, entry) }; fresh.start() }
        d.second.promote(mine)
        return d.first.await()
    }
    private val loads = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    override suspend fun series(url: String): DramaSeries {
        seriesCache[url]?.let { (at, s) -> if (clock() - at < SERIES_TTL) return s }
        return shared(seriesInFlight, url) { fetchSeries(url) }
    }

    private suspend fun fetchSeries(url: String): DramaSeries {
        val cached = seriesCache[url]
        cached?.let { (at, s) -> if (clock() - at < SERIES_TTL) return s }
        // R15: a stalled series page ("Daddy, Save Me" timed out twice on 2026-11-14) reuses the
        // last parsed page; episode links are fetched fresh anyway, so a stale page is safe.
        val r = runCatching {
            val (code, body) = gated(url, mapOf("Accept" to "text/html"))
            if (code !in 200..299) throw IOException("Series page answered HTTP $code")
            OnShortParser.series(body, url) ?: throw IOException("This series has no player")
        }
        r.exceptionOrNull()?.let { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (cached != null && clock() - cached.first < STALE_TTL) return cached.second
            throw e
        }
        val s = r.getOrThrow()
        seriesCache[url] = clock() to s
        return s
    }

    override suspend fun streams(series: DramaSeries, episode: Int): DramaStreams =
        shared(streamsInFlight, "${series.url}|$episode") { fetchStreams(series, episode) }

    private suspend fun fetchStreams(series: DramaSeries, episode: Int): DramaStreams {
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
                        val (code, body) = gated(u, mapOf("Referer" to referer, "Accept" to "application/json", "Cache-Control" to "no-cache"))
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
        val epKey = "${h["post"]}|$episode"
        episodeCache[epKey]?.takeIf { fresh(it.second, it.first) }?.let { return it.second }
        val endpoint = h["endpoint"].orEmpty()
        if (endpoint.isNotBlank() && h["post"] != null) {
            val endpoints = listOf(endpoint, h["endpointAlt"].orEmpty()).filter { it.isNotBlank() }.distinct()
            var ticket = h["ticket"].orEmpty()
            // The web player asks again with force=1 when the answer says "retryable" (the site
            // refreshes the studio's media then); so do we, once, on each endpoint.
            for (force in listOf(false, true)) {
                var retry = false
                for ((ei, ep) in endpoints.withIndex()) {
                    val sep = if (ep.contains('?')) '&' else '?'
                    val u = "$ep${sep}post=${h["post"]}&episode=$episode&_t=${System.currentTimeMillis()}" + if (force) "&force=1" else ""
                    // Like the web player: a busy (502) main route goes straight to the other route;
                    // the last route gets the gate's full retries.
                    val tries = if (ei < endpoints.size - 1) 1 else 0
                    for (attempt in 0..1) {
                        val r = runCatching {
                            gated(u, mapOf("Referer" to referer, "X-ONShort-Player" to "1", "X-ONShort-Ticket" to ticket, "Accept" to "application/json", "Cache-Control" to "no-cache"), tries)
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
                            if (s != null && s.sources.isNotEmpty()) { episodeCache[epKey] = System.currentTimeMillis() to s; return s }
                        }
                        if (OnShortParser.retryable(r.second)) retry = true
                        lastError = IOException("Episode $episode answered HTTP ${r.first}")
                        break
                    }
                }
                if (!retry) break
            }
        }
        // 2b. POST gateway transports ("same-page" posts to the series page, "root-post" to /index.php).
        val transport = h["transport"].orEmpty()
        if ((transport == "same-page" || transport == "root-post") && h["post"] != null && h["ticket"] != null) {
            val gateway = if (transport == "root-post") "$base/index.php" else series.url.substringBefore('#')
            for (force in listOf(false, true)) {
                val body = buildString {
                    append("onshort_player_action=").append(URLEncoder.encode(h["action"].orEmpty().ifBlank { "episode" }, "UTF-8"))
                    append("&post=").append(h["post"]).append("&episode=").append(episode)
                    append("&ticket=").append(URLEncoder.encode(h["ticket"].orEmpty(), "UTF-8")).append("&variant=0")
                    if (force) append("&force=1")
                }
                val r = runCatching { gate.call(gateway) { post(gateway, mapOf("Referer" to referer, "X-ONShort-Player" to "1", "Accept" to "application/json"), body) } }
                    .onFailure { lastError = it }.getOrNull() ?: break
                if (r.first in 200..299) OnShortParser.episode(r.second, referer)?.takeIf { it.sources.isNotEmpty() }?.let { episodeCache[epKey] = System.currentTimeMillis() to it; return it }
                lastError = IOException("Episode $episode answered HTTP ${r.first}")
                if (!OnShortParser.retryable(r.second)) break
            }
        }
        // 3. Legacy per-platform endpoint.
        val platform = h["platformSlug"]
        if (!platform.isNullOrBlank() && h["post"] != null) {
            runCatching {
                val (code, body) = gated("$base/wp-json/onshort-$platform/v1/series/${h["post"]}/episode/$episode", mapOf("Referer" to referer, "Accept" to "application/json"))
                if (code in 200..299) OnShortParser.episode(body, referer)?.takeIf { it.sources.isNotEmpty() }?.let { return it }
                throw IOException("Episode $episode answered HTTP $code")
            }.onFailure { e ->
                // The legacy route is only a fallback: its 404 must not hide the real reason
                // (e.g. "HTTP 424, studio unlock refused") from the REST answer above.
                if (lastError == null || !e.message.orEmpty().endsWith("HTTP 404")) lastError = e
            }
        }
        throw lastError ?: IOException("No stream for episode $episode")
    }

    companion object {
        private const val SERIES_TTL = 5 * 60_000L
        private const val SHELF_TTL = 10 * 60_000L
        /** How long a stale shelf / series page may stand in for a stalled answer. */
        private const val STALE_TTL = 24 * 60 * 60_000L
        private const val RUNTIME_TTL = 10 * 60_000L
        /** Signed CDN links in an episode answer stay valid well beyond this. */
        private const val EPISODE_TTL = 4 * 60_000L

        suspend fun httpGet(url: String, headers: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            // A hung answer is cut and retried by the gate (the client's own read timeout is 20 s
            // per read, which let one page take a minute). JSON answers are small: 12 s. HTML pages
            // (shelves, series) are 150-400 KB and the site renders them slowly under load: 25 s.
            val call = NetworkClient.get().newCall(req)
            val html = headers["Accept"].orEmpty().startsWith("text/html")
            call.timeout().timeout(if (html) 25L else 12L, java.util.concurrent.TimeUnit.SECONDS)
            call.execute().use { r -> r.code to r.body.string() }
        }

        suspend fun httpPost(url: String, headers: Map<String, String>, body: String): Pair<Int, String> = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .post(body.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType()))
                .apply {
                    header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
                    headers.forEach { (k, v) -> header(k, v) }
                }.build()
            val call = NetworkClient.get().newCall(req)
            call.timeout().timeout(12, java.util.concurrent.TimeUnit.SECONDS)
            call.execute().use { r -> r.code to r.body.string() }
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
        val platformSlug = RX_ONSHORTCATALOG_1.find(runtime)?.groupValues?.get(1)
            ?: doc.selectFirst("a[href*=/platform/]")?.attr("href")?.let { RX_ONSHORTCATALOG_2.find(it)?.groupValues?.get(1) }
        val meta = { p: String -> doc.selectFirst("meta[property=$p]")?.attr("content")?.trim()?.ifBlank { null } }
        val totalFromMeta = meta("og:description")?.let { RX_ONSHORTCATALOG_3.find(it)?.groupValues?.get(1)?.toIntOrNull() }
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
                d("player-action").takeIf { it.isNotBlank() }?.let { put("action", it) }
                if (runtime.isNotBlank()) put("runtime", runtime)
                d("player-endpoint").takeIf { it.isNotBlank() }?.let { put("endpoint", it) }
                d("player-endpoint-alt").takeIf { it.isNotBlank() }?.let { put("endpointAlt", it) }
                d("player-ticket").takeIf { it.isNotBlank() }?.let { put("ticket", it) }
                platformSlug?.let { put("platformSlug", it) }
            },
        ).let { s -> s.copy(hints = s.hints + ("tags" to doc.select(".tag-box .tag-cloud span").joinToString("|") { it.text() })) }
    }

    /** `{ok:false, retryable:true}`: the site asks for another try (its player retries with force=1). */
    fun retryable(body: String): Boolean =
        runCatching { ((json.parseToJsonElement(body) as? JsonObject)?.get("retryable") as? JsonPrimitive)?.contentOrNull == "true" }.getOrDefault(false)

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
        // R15: the site's player also tries `candidates` (other servers of the same episode).
        (o["candidates"] as? JsonArray).orEmpty().forEach { c ->
            val co = c as? JsonObject
            val u = (co?.s("url") ?: (c as? JsonPrimitive)?.contentOrNull)?.trim()
            if (u == null || !u.startsWith("http") || u in sources.values) return@forEach
            val q = co?.s("quality")?.filter(Char::isDigit).orEmpty()
            var k = q; var i = 1
            while (k in sources) { k = "$q~$i"; i++ }
            sources[k] = u
        }
        if (sources.isEmpty()) return null
        val expires = o.s("expires_at")?.toDoubleOrNull()?.toLong() ?: 0L
        val subs = (o["subtitles"] as? JsonArray).orEmpty().mapNotNull { s ->
            val so = s as? JsonObject ?: return@mapNotNull null
            val u = so.s("url") ?: so.s("src") ?: return@mapNotNull null
            DramaSubtitle(u, so.s("lang") ?: so.s("language"), so.s("label"))
        }
        return DramaStreams(sources, subs, referer, expires)
    }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    @Suppress("unused") private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull
}

/**
 * R15/R17: polite, self-healing access to one flaky host.
 *  - at most [maxConcurrent] feed / background calls at once, plus one slot only a call the user
 *    is waiting on (lane [GateLane.INTERACTIVE]) may take, and [minGapMs] between call starts;
 *  - waiters are served by lane first (interactive, then feed, then background), then in order;
 *  - HTTP 429 / 502 / 503 / 504, timeouts and dropped connections are retried with backoff
 *    ([backoffMs]) up to [attempts] times; while one call backs off, feed and background calls
 *    wait too (shared cool-down). A user's call is not held by that cool-down.
 * Other answers (200, 4xx) are returned as they are.
 *
 * R17 (run 43664241241): with two lanes, shelves of ten studios, episode prefetches and the tap
 * itself were all "user" calls in one first-come queue; a tap on Play waited 216 s for a slot.
 */
class OnShortGate(
    private val maxConcurrent: Int = 2,
    private val minGapMs: Long = 250L,
    private val attempts: Int = 4,
    private val backoffMs: (attempt: Int) -> Long = { a -> longArrayOf(1_200L, 3_000L, 6_000L, 9_000L)[a.coerceIn(0, 3)] },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Waiter(val lane: GateLane?, val seq: Long, val granted: kotlinx.coroutines.CompletableDeferred<Int>) {
        val priority: Int get() = lane?.priority ?: GateLane.FEED
    }
    private val lock = Any()
    private val waiters = ArrayList<Waiter>()
    private var running = 0
    private var backgroundRunning = 0
    private var seq = 0L
    /** Calls on the host right now (for the slow-wait log only). */
    private val active = java.util.concurrent.ConcurrentHashMap<Any, String>()
    private val spacing = kotlinx.coroutines.sync.Mutex()
    @Volatile private var lastStart = 0L
    @Volatile private var coolUntil = 0L
    @Volatile private var lastBusyAt = 0L
    private val waiting = java.util.concurrent.atomic.AtomicInteger()

    /** True when the host was busy within the last minute or calls are queued behind the limit. */
    fun recentlyBusy(): Boolean = now() - lastBusyAt < 60_000L || coolUntil > now() || waiting.get() > maxConcurrent

    /** Runs [block] with the gate's limits; [tries] > 0 overrides the number of attempts. */
    suspend fun call(url: String, tries: Int = 0, block: suspend () -> Pair<Int, String>): Pair<Int, String> {
        var last: Pair<Int, String>? = null
        var error: Throwable? = null
        val attempts = if (tries > 0) tries else attempts
        val lane = kotlin.coroutines.coroutineContext[GateLane]
        for (attempt in 0 until attempts) {
            waiting.incrementAndGet()
            val asked = now()
            val r = try {
                val heldAtAsk = active.values.toList()
                val got = acquire(lane)
                val ticket = Any()
                active[ticket] = "${GateLane.name(got)} ${url.substringAfter("://").take(60)}"
                try {
                    (now() - asked).takeIf { it > 1_000 }?.let { w -> runCatching { android.util.Log.i("YuGate", "waited ${w}ms for a slot (${GateLane.name(got)}): ${url.take(90)} · held by ${heldAtAsk.joinToString()}") } }
                    spacing.withLock {
                        val cool = if (got == GateLane.INTERACTIVE) 0L else coolUntil - now()
                        val wait = maxOf(cool, lastStart + minGapMs - now())
                        if (wait > 0) kotlinx.coroutines.delay(wait)
                        lastStart = now()
                    }
                    val began = now()
                    runCatching { block() }.also { res ->
                        // R16 (G4): a slow answer is the site, a long wait above is our queue. Run
                        // 43732060154 spent 52 s in one episode lookup with no wait logged.
                        (now() - began).takeIf { it > 5_000 }?.let { d -> runCatching { android.util.Log.i("YuGate", "slow answer ${d}ms (${GateLane.name(got)}, try ${attempt + 1}): ${url.take(90)} → ${res.getOrNull()?.first ?: res.exceptionOrNull()?.javaClass?.simpleName}") } }
                    }
                } finally { active.remove(ticket); release(got) }
            } finally { waiting.decrementAndGet() }
            val v = r.getOrNull()
            if (v != null && !transient(v.first)) return v
            // Only an answer that says "busy" (429/5xx) marks the host busy. A timeout does not:
            // a hung studio (ShortMax) must not look like a busy host, or it is never hidden.
            if (v != null) lastBusyAt = now()
            if (v == null) {
                val e = r.exceptionOrNull()!!
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (!retryable(e)) throw e
                // A hung host rarely answers seconds later: one more try, then give up.
                if (error != null) throw e
                error = e
            } else last = v
            if (attempt < attempts - 1) {
                val b = backoffMs(attempt)
                coolUntil = maxOf(coolUntil, now() + b)
                kotlinx.coroutines.delay(b)
            }
        }
        return last ?: throw (error ?: IOException("No answer from $url"))
    }

    /** Whether a call of [priority] may start now. Caller holds [lock]. */
    private fun fits(priority: Int, others: List<Waiter>): Boolean = when (priority) {
        // R16 G4: a running probe doesn't count against user or feed slots. Device logs showed
        // feed loads waiting up to 20 s while a probe (with its retries) held one of the two feed
        // slots, so the drama shelves filled slowly. A probe only starts on an idle host, so at
        // most one probe runs next to the normal limits.
        GateLane.INTERACTIVE -> running - backgroundRunning < maxConcurrent + 1
        GateLane.FEED -> running - backgroundRunning < maxConcurrent && others.none { it.priority == GateLane.INTERACTIVE }
        // Only on an idle host, one at a time: a feed or user call that arrives next finds a slot.
        else -> running == 0 && backgroundRunning == 0 && others.none { it.priority < GateLane.BACKGROUND }
    }

    /** Takes a slot; returns the lane priority it was granted under. */
    private suspend fun acquire(lane: GateLane?): Int {
        val w: Waiter
        synchronized(lock) {
            w = Waiter(lane, seq++, kotlinx.coroutines.CompletableDeferred())
            if (waiters.none { it.priority <= w.priority } && fits(w.priority, waiters)) { take(w.priority); return w.priority }
            waiters += w
        }
        try {
            // The lane can be promoted while waiting (a user joins a prefetch): re-check now and then.
            while (true) {
                val got = kotlinx.coroutines.withTimeoutOrNull(250) { w.granted.await() }
                if (got != null) return got
                synchronized(lock) { dispatch() }
            }
        } catch (e: Throwable) {
            synchronized(lock) {
                if (!waiters.remove(w) && w.granted.isCompleted) { val g = w.granted.getCompleted(); release0(g) }
            }
            throw e
        }
    }

    private fun take(priority: Int) { running++; if (priority == GateLane.BACKGROUND) backgroundRunning++ }

    private fun release0(priority: Int) { running--; if (priority == GateLane.BACKGROUND) backgroundRunning--; dispatch() }

    private fun release(priority: Int) = synchronized(lock) { release0(priority) }

    /** Grants slots to waiters, best lane first. Caller holds [lock]. */
    private fun dispatch() {
        while (waiters.isNotEmpty()) {
            val next = waiters.minWith(compareBy<Waiter>({ it.priority }, { it.seq }))
            val others = waiters.filter { it !== next }
            if (!fits(next.priority, others)) return
            waiters.remove(next)
            take(next.priority)
            next.granted.complete(next.priority)
        }
    }

    companion object {
        fun transient(code: Int) = code == 429 || code == 502 || code == 503 || code == 504
        fun retryable(e: Throwable): Boolean = generateSequence(e) { it.cause }.any {
            it is java.io.InterruptedIOException || it is java.net.SocketException || it is okhttp3.internal.http2.StreamResetException ||
                (it is IOException && it.message.orEmpty().contains("unexpected end of stream", true))
        }
    }
}

/**
 * Which lane an [OnShortGate] call uses: [INTERACTIVE] (the user is waiting: opening a drama,
 * pressing Play), [FEED] (shelves, prefetch; the default when no lane is set) or [BACKGROUND]
 * (health probes). A shared request is promoted by lowering [priority], so the user never queues
 * behind the prefetch or probe that started it.
 */
class GateLane(@Volatile var priority: Int) : kotlin.coroutines.AbstractCoroutineContextElement(GateLane) {
    constructor(background: Boolean) : this(if (background) BACKGROUND else FEED)
    val background: Boolean get() = priority == BACKGROUND
    /** Moves this lane up to [p] (never down). */
    fun promote(p: Int) { if (p < priority) priority = p }

    companion object Key : kotlin.coroutines.CoroutineContext.Key<GateLane> {
        const val INTERACTIVE = 0
        const val FEED = 1
        const val BACKGROUND = 2
        fun name(p: Int) = when (p) { INTERACTIVE -> "user"; FEED -> "feed"; else -> "background" }
    }
}
