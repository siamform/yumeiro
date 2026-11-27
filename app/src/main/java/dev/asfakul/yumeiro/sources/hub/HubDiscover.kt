/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.hub

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.online.OnlineItem
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Group O (B14, F5, F6, F9): Yumeiro Cloud's discovery calls (TMDB metadata, the token stays on the Worker):
 * `/v1/hub/discover` (type, sort, genres, streaming services, country of origin), `/recommend` ("If you like")
 * and `/providers` (streaming services in a region). Titles open as Yumeiro catalog titles, so playing one
 * searches the user's own sources as before. Metadata only.
 *
 * Small memory cache (last [CACHE] answers) so a page that composes again does not ask again; the edge caches
 * the rest (6-24 h), which is what keeps 200k users cheap.
 */
object HubDiscover {
    enum class Sort(val key: String) { POPULAR("popular"), TOP("top"), NEW("new"), UPCOMING("upcoming"), RATING("rating") }

    data class Query(
        val kind: String, // "movie" | "tv"
        val sort: Sort = Sort.POPULAR,
        val genres: List<Int> = emptyList(),
        val providers: List<Int> = emptyList(),
        val region: String? = null,
        val origin: String? = null,
        val lang: String? = null,
        val anime: Boolean = false,
        /** Group W (B1, B2): themes (TMDB keyword ids, all of them must match), a year range and a lowest score (1-9). */
        val keywords: List<Int> = emptyList(),
        val yearFrom: Int? = null,
        val yearTo: Int? = null,
        val minRating: Int? = null,
    )

    data class Item(
        val title: String, val kind: String, val id: Long, val poster: String?, val year: Int?, val date: LocalDate?,
        val rating: Double?, val votes: Int?, val genres: List<String>, val anime: Boolean,
        val backdrop: String? = null,
    ) {
        val url: String get() = "${YumeiroCatalog.PART}:$kind:$id"
    }

    data class Page(val items: List<Item>, val hasNext: Boolean)
    data class Provider(val id: Int, val name: String, val logo: String?)

    /** Genres for the chips (TMDB ids; names as TMDB gives them). Movie and series lists differ a little. */
    val MOVIE_GENRES = listOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to tr(R.string.science_fiction, "Science Fiction"), 53 to "Thriller", 10752 to "War", 37 to "Western",
    )
    val TV_GENRES = listOf(
        10759 to tr(R.string.action_adventure, "Action & Adventure"), 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 10762 to "Kids", 9648 to "Mystery", 10764 to "Reality",
        10765 to tr(R.string.sci_fi_fantasy, "Sci-Fi & Fantasy"), 10768 to tr(R.string.war_politics, "War & Politics"), 37 to "Western",
    )

    /** Tests point this at a local server; null = Yumeiro Cloud. */
    @Volatile internal var baseForTest: String? = null
    private val base: String get() = baseForTest ?: "https://${BuildConfig.CLOUD_HOST}/v1/hub"

    /** True when the calls can work at all (a host is built in, or a test server is set). */
    fun available(): Boolean = baseForTest != null || BuildConfig.CLOUD_HOST.isNotBlank()

    private val ISO2 = Regex("^[A-Z]{2}$")
    private val LANG = Regex("^[a-z]{2}$")

    /** Pure: the path and query for [q] (only checked values pass). */
    fun path(q: Query, page: Int = 1): String {
        require(q.kind == "movie" || q.kind == "tv") { "kind" }
        val p = ArrayList<String>()
        p += "type=${q.kind}"
        p += "sort=${q.sort.key}"
        q.genres.filter { it in 1..999_999 }.take(3).takeIf { it.isNotEmpty() }?.let { p += "genre=" + it.joinToString(",") }
        q.providers.filter { it in 1..999_999 }.take(3).takeIf { it.isNotEmpty() }?.let { p += "provider=" + it.joinToString(",") }
        q.region?.takeIf { ISO2.matches(it) }?.let { p += "region=$it" }
        q.origin?.takeIf { ISO2.matches(it) }?.let { p += "origin=$it" }
        q.lang?.takeIf { LANG.matches(it) }?.let { p += "lang=$it" }
        if (q.anime) p += "anime=1"
        q.keywords.filter { it in 1..9_999_999 }.distinct().take(3).takeIf { it.isNotEmpty() }?.let { p += "keyword=" + it.joinToString(",") }
        q.yearFrom?.takeIf { it in 1900..2100 }?.let { p += "year_from=$it" }
        q.yearTo?.takeIf { it in 1900..2100 }?.let { p += "year_to=$it" }
        q.minRating?.takeIf { it in 1..9 }?.let { p += "min_rating=$it" }
        p += "page=${page.coerceIn(1, 20)}"
        return "/discover?" + p.joinToString("&")
    }

    /** Pure: a hub list answer → items. Broken rows are skipped. */
    fun parsePage(body: String?): Page? {
        val o = runCatching { JSONObject(body ?: return null) }.getOrNull() ?: return null
        val arr = o.optJSONArray("items") ?: return Page(emptyList(), false)
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val kind = r.optString("type").takeIf { it == "movie" || it == "tv" } ?: continue
            val id = r.optString("id").substringAfterLast(':').toLongOrNull()?.takeIf { it > 0 } ?: continue
            val title = r.optString("title").trim().ifEmpty { continue }
            out += Item(
                title = title.take(200), kind = kind, id = id,
                poster = r.optString("poster").takeIf { it.startsWith("https://") },
                year = r.optInt("year").takeIf { it in 1801..2999 },
                date = r.optString("date").takeIf { it.length == 10 }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                rating = r.optDouble("rating").takeIf { !it.isNaN() && it > 0 && it <= 10 },
                votes = r.optInt("votes").takeIf { it > 0 },
                genres = r.optJSONArray("genres").strings(),
                anime = r.optBoolean("anime"),
                backdrop = r.optString("backdrop").takeIf { it.startsWith("https://") },
            )
        }
        return Page(out, o.optBoolean("hasNext"))
    }

    fun parseProviders(body: String?): List<Provider>? {
        val o = runCatching { JSONObject(body ?: return null) }.getOrNull() ?: return null
        val arr = o.optJSONArray("providers") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = r.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
            val name = r.optString("name").trim().ifEmpty { return@mapNotNull null }
            Provider(id, name.take(60), r.optString("logo").takeIf { it.startsWith("https://") })
        }
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().takeIf { s -> s.isNotEmpty() } }.take(4)

    /** Item → a card that opens the catalog title (played through the user's own sources). */
    fun Item.toOnlineItem(): OnlineItem = OnlineItem(
        name = title, url = url, api = YumeiroHub.NAME, posterUrl = poster,
        type = when { anime -> "Anime"; kind == "tv" -> tr(R.string.tv_series, "TV series"); else -> "Movie" },
        score = rating,
    )

    // region network (suspend; IO inside the fetch)

    private const val CACHE = 48
    private val memo = object : LinkedHashMap<String, String>(CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > CACHE
    }

    /** Tests and the code that swaps the server only. */
    internal fun clearCache() = synchronized(memo) { memo.clear() }

    /**
     * Group W (B3): the hub answers in well under a second; a call that hangs is almost always a dead pooled
     * connection or a lost packet (TV api24 run 46042804018: the services page waited 30 s, then showed the error,
     * while the same address answered in 0.35 s). So each try is cut at 8 s and tried once more on a fresh call.
     */
    @Volatile internal var fetch: suspend (String) -> String? = { url ->
        val get = dev.asfakul.yumeiro.sources.classics.YumeiroClassicsProvider
        get.httpGet(url, timeoutSec = 8) ?: get.httpGet(url, timeoutSec = 8)
    }

    private suspend fun get(path: String): String? {
        val url = base + path
        synchronized(memo) { memo[url] }?.let { return it }
        val body = withTimeoutOrNull(20_000) { fetch(url) } ?: return null
        synchronized(memo) { memo[url] = body }
        return body
    }

    suspend fun page(q: Query, page: Int = 1): Page? = if (!available()) null else parsePage(get(path(q, page)))

    suspend fun recommend(kind: String, id: Long): Page? =
        if (!available() || (kind != "movie" && kind != "tv") || id <= 0) null else parsePage(get("/recommend?type=$kind&id=$id"))

    suspend fun shelf(key: String, page: Int = 1): Page? =
        if (!available()) null else parsePage(get("/catalog?shelf=${java.net.URLEncoder.encode(key, "UTF-8")}&page=${page.coerceIn(1, 20)}"))

    suspend fun providers(region: String?): List<Provider>? =
        if (!available()) null else parseProviders(get("/providers?region=${region?.takeIf { ISO2.matches(it) } ?: "US"}"))

    /**
     * Group W (B3): TMDB lists no services for some countries (Bangladesh, Nepal…). Pure: the country whose list
     * is shown instead (the nearest big market), or null when [region] is already that one.
     */
    fun nearbyRegion(region: String): String? = when (region) {
        "BD", "NP", "LK", "BT", "MV", "PK", "AF" -> "IN"
        "US" -> null
        else -> "US"
    }

    /** The services in [region], or in [nearbyRegion] when it has none; with the region they are for. */
    suspend fun providersNear(region: String): Pair<String, List<Provider>>? = kotlinx.coroutines.coroutineScope {
        val near = nearbyRegion(region)
        // Countries TMDB is known to list nothing for (they fall back to India): ask both at once, not one after the other.
        val early = if (near == "IN") async { providers(near) } else null
        val here = providers(region) ?: run { early?.cancel(); return@coroutineScope null }
        if (here.isNotEmpty()) { early?.cancel(); return@coroutineScope region to here }
        if (near == null) return@coroutineScope region to here
        val list = (early?.await() ?: providers(near)) ?: return@coroutineScope null
        near to list
    }

    // endregion
}
