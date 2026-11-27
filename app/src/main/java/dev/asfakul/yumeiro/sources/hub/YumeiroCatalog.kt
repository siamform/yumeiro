/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.hub

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.SearchRanker
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * E1-A + E1-B + E1-D (owner 2026-11-22: "everything without installing a single extension, all in one").
 *
 * The catalog part of the one "Yumeiro" source. Shelves (trending, anime, Korean / Chinese / Japanese drama,
 * Hindi, Telugu, Tamil, Bangla, Turkish, documentaries, kids...), search and title pages come from Yumeiro
 * Cloud (`/v1/hub/...`, TMDB metadata; the TMDB token stays on the Worker). Metadata only: Yumeiro hosts no video.
 *
 * Play (E1-B resolver fan-out): the title is looked up on every video source the user has installed (any
 * format), matched by title (and year when the source gives one) and type; each match's links are passed on.
 * E1-D: sources that answered before are asked first ([Ranking]). With no source installed the title page still
 * opens and says so. Ids: `catalog:movie:603`, `catalog:tv:1396`, episodes `catalog:tv:1396:<season>:<episode>`.
 */
object YumeiroCatalog {
    val NAME: String get() = tr(R.string.yumeiro_catalog, "Yumeiro Catalog")
    const val PART = "catalog"

    fun on(): Boolean = FeatureFlags.isOn(FeatureFlags.Flag.HUB_CATALOG) && YumeiroHub.on() && BuildConfig.CLOUD_HOST.isNotBlank()

    /** E1-D: per-source answers in this run (found a match with links / did not). Asked first = most found. */
    object Ranking {
        private val ok = ConcurrentHashMap<String, Int>()
        private val miss = ConcurrentHashMap<String, Int>()
        fun hit(name: String) { ok.merge(name, 1, Int::plus) }
        fun miss(name: String) { miss.merge(name, 1, Int::plus) }
        fun order(apis: List<MainAPI>): List<MainAPI> = apis.withIndex()
            .sortedWith(compareByDescending<IndexedValue<MainAPI>> { (ok[it.value.name] ?: 0) - (miss[it.value.name] ?: 0) / 3 }.thenBy { it.index })
            .map { it.value }
        internal fun clear() { ok.clear(); miss.clear() }
    }

    data class Shelf(val key: String, val name: String)

    /** Same keys as server/src/hub.ts SHELVES. */
    val SHELVES = listOf(
        Shelf("trending_movies", tr(R.string.trending_movies, "Trending movies")), Shelf("trending_tv", tr(R.string.trending_series, "Trending series")),
        Shelf("popular_movies", tr(R.string.popular_movies, "Popular movies")), Shelf("top_tv", tr(R.string.top_rated_series, "Top rated series")),
        Shelf("anime", tr(R.string.anime_series, "Anime series")), Shelf("anime_movies", tr(R.string.anime_movies, "Anime movies")),
        Shelf("kdrama", tr(R.string.korean_drama, "Korean drama")), Shelf("cdrama", tr(R.string.chinese_drama, "Chinese drama")), Shelf("jdrama", tr(R.string.japanese_drama, "Japanese drama")),
        Shelf("thai", tr(R.string.thai_series, "Thai series")), Shelf("turkish", tr(R.string.turkish_series, "Turkish series")),
        Shelf("hindi", tr(R.string.hindi_movies, "Hindi movies")), Shelf("telugu", tr(R.string.telugu_movies, "Telugu movies")), Shelf("tamil", tr(R.string.tamil_movies, "Tamil movies")), Shelf("bangla", tr(R.string.bangla_movies, "Bangla movies")),
        Shelf("spanish", tr(R.string.spanish_language_series, "Spanish-language series")), Shelf("documentaries", "Documentaries"), Shelf("kids", tr(R.string.kids_and_family, "Kids and family")),
    )

    data class Ref(val kind: String, val id: Long, val season: Int? = null, val episode: Int? = null)

    /** Parses a catalog id anywhere in [s] (CloudStream's fixUrl may have put a host in front). */
    fun refOf(s: String): Ref? {
        val i = s.indexOf("$PART:")
        if (i < 0) return null
        val p = s.substring(i + PART.length + 1).split(':')
        val kind = p.getOrNull(0)?.takeIf { it == "movie" || it == "tv" } ?: return null
        val id = p.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val season = p.getOrNull(2)?.toIntOrNull()
        val episode = p.getOrNull(3)?.toIntOrNull()
        return Ref(kind, id, season, episode)
    }

    /** Words a source search can use: letters, digits and spaces only (case kept), at most 8 words. */
    fun query(title: String): String = buildString {
        for (c in title) append(if (c.isLetterOrDigit()) c else ' ')
    }.trim().split(' ').filter { it.isNotEmpty() }.take(8).joinToString(" ")

    /** Type fits: a film matches films, a series matches any episodic type (pure; unit-tested). */
    fun typeFits(kind: String, t: TvType?): Boolean = when (t) {
        null, TvType.Others -> true
        TvType.Movie, TvType.AnimeMovie, TvType.Documentary -> kind == "movie"
        TvType.TvSeries, TvType.Anime, TvType.AsianDrama, TvType.Cartoon, TvType.OVA -> kind == "tv"
        else -> false
    }

    private fun yearOf(r: SearchResponse): Int? = when (r) {
        is MovieSearchResponse -> r.year
        is TvSeriesSearchResponse -> r.year
        is AnimeSearchResponse -> r.year
        else -> null
    }

    /** Best search hit for the title: score >= 85 on title or original title, type fits, year within 1 (pure; unit-tested). */
    /** [strict]: both years must be known and match (own Archive part: a remake must never play the old film). */
    fun best(results: List<SearchResponse>, kind: String, title: String, original: String?, year: Int?, strict: Boolean = false): SearchResponse? =
        results.asSequence()
            .filter { typeFits(kind, it.type) }
            .filter { r ->
                val y = yearOf(r)
                if (strict) year != null && y != null && kotlin.math.abs(y - year) <= 1
                else year == null || y == null || kotlin.math.abs(y - year) <= 1
            }
            .map { r -> r to maxOf(SearchRanker.score(title, r.name), original?.let { SearchRanker.score(it, r.name) } ?: 0) }
            .filter { it.second >= 85 }
            .sortedByDescending { it.second }
            .firstOrNull()?.first

    /** The play data for [ref] inside a source's title page (pure; unit-tested). */
    fun dataFor(r: LoadResponse, ref: Ref): String? = when (r) {
        is MovieLoadResponse -> r.dataUrl.takeIf { ref.kind == "movie" && it.isNotBlank() }
        is TvSeriesLoadResponse -> pickEpisode(r.episodes, ref)
        is AnimeLoadResponse -> pickEpisode(r.episodes.values.flatten(), ref)
        else -> null
    }

    internal fun pickEpisode(eps: List<Episode>, ref: Ref): String? {
        if (ref.kind != "tv") return null
        val s = ref.season ?: 1; val e = ref.episode ?: 1
        eps.firstOrNull { it.season == s && it.episode == e }?.let { return it.data }
        // Sources that list one season without season numbers: season 1 only.
        if (s == 1) eps.firstOrNull { it.season == null && it.episode == e }?.let { return it.data }
        return null
    }

    internal fun eligible(apis: List<MainAPI>): List<MainAPI> =
        apis.filter { it.name != YumeiroHub.NAME && !(it.sourcePlugin ?: "").startsWith("yumeiro-builtin:") }
}

class YumeiroCatalogProvider(
    private val fetch: suspend (String) -> String? = { dev.asfakul.yumeiro.sources.classics.YumeiroClassicsProvider.httpGet(it) },
    private val sources: () -> List<MainAPI> = { SourceHub.providers() },
    private val base: String = "https://${BuildConfig.CLOUD_HOST}/v1/hub",
    /** E1-C (owner 2026-11-23): Yumeiro's own legal resolvers, asked first and with no install: today the
     *  Internet Archive (Yumeiro Classics, public-domain films). Only an exact title + year + type match plays. */
    private val builtins: () -> List<MainAPI> = { YumeiroHub.resolvers() },
    /** FIND-2: V2 sweep (default) or the V1 fan-out. */
    private val findV2: () -> Boolean = { FeatureFlags.isOn(FeatureFlags.Flag.FIND_V2) },
) : MainAPI() {
    override var name = YumeiroCatalog.NAME
    override var mainUrl = "https://yumeiro.click"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama, TvType.Documentary, TvType.Cartoon)

    init { sourcePlugin = "yumeiro-builtin:${YumeiroCatalog.PART}" }

    override val mainPage: List<MainPageData> = YumeiroCatalog.SHELVES.map { MainPageData(it.name, it.key) }

    private suspend fun getJson(path: String): JSONObject? = fetch("$base$path")?.let { runCatching { JSONObject(it) }.getOrNull() }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val o = getJson("/catalog?shelf=${enc(request.data)}&page=${page.coerceAtLeast(1)}") ?: return null
        return newHomePageResponse(request.name, items(o.optJSONArray("items")), o.optBoolean("hasNext"))
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val q = query.trim().take(80)
        if (q.isEmpty()) return newSearchResponseList(emptyList(), false)
        val o = getJson("/search?q=${enc(q)}&page=${page.coerceAtLeast(1)}") ?: return newSearchResponseList(emptyList(), false)
        return newSearchResponseList(items(o.optJSONArray("items")), o.optBoolean("hasNext"))
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val ref = YumeiroCatalog.refOf(url) ?: return null
        val o = getJson("/title?type=${ref.kind}&id=${ref.id}") ?: error("Yumeiro Cloud didn't answer. Try again in a moment.")
        val title = o.optString("title").ifBlank { return null }
        val id = "${YumeiroCatalog.PART}:${ref.kind}:${ref.id}"
        val anime = o.optBoolean("anime")
        val r: LoadResponse = if (ref.kind == "tv") {
            val seasons = o.optJSONArray("seasons") ?: JSONArray()
            val eps = ArrayList<Episode>()
            for (i in 0 until seasons.length()) {
                val s = seasons.optJSONObject(i) ?: continue
                val n = s.optInt("n"); val count = s.optInt("count").coerceAtMost(500)
                for (e in 1..count) {
                    if (eps.size >= MAX_EPISODES) break
                    eps += newEpisode("$id:$n:$e") { season = n; episode = e }
                }
            }
            newTvSeriesLoadResponse(title, id, if (anime) TvType.Anime else TvType.TvSeries, eps) { fill(o) }
        } else {
            newMovieLoadResponse(title, id, if (anime) TvType.AnimeMovie else TvType.Movie, id) { fill(o) }
        }
        with(LoadResponse) {
            o.optString("imdb").takeIf { it.startsWith("tt") }?.let { r.addImdbId(it) }
            r.addTMDbId(ref.id.toString())
        }
        return r
    }

    private fun LoadResponse.fill(o: JSONObject) {
        posterUrl = o.optString("poster").ifBlank { null }
        backgroundPosterUrl = o.optString("backdrop").ifBlank { null }
        plot = o.optString("overview").ifBlank { null }
        year = o.optInt("year").takeIf { it > 0 }
        tags = o.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) }.filter { it.isNotBlank() } }
        o.optDouble("rating").takeIf { !it.isNaN() && it > 0 }?.let { score = Score.from10(it) }
        duration = o.optInt("runtime").takeIf { it > 0 }
    }

    /**
     * E1-B: asks every installed video source for this title and passes on the links of each match.
     * Sources run side by side, each with its own time limit; one slow or broken source never blocks the rest.
     */
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val ref = YumeiroCatalog.refOf(data) ?: return false
        val o = getJson("/title?type=${ref.kind}&id=${ref.id}") ?: return false
        val title = o.optString("title").ifBlank { return false }
        val original = o.optString("original").ifBlank { null }
        val year = o.optInt("year").takeIf { it > 0 }
        val own = if (dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.HUB_ARCHIVE)) builtins() else emptyList()
        if (findV2()) {
            return loadLinksV2(ref, title, original, year, own, isCasting, subtitleCallback, callback)
        }
        val apis = own + YumeiroCatalog.Ranking.order(YumeiroCatalog.eligible(sources())).take(MAX_SOURCES)
        if (apis.isEmpty()) return false
        val found = withTimeoutOrNull(TOTAL_MS) {
            coroutineScope {
                apis.map { api ->
                    async {
                        val got = withTimeoutOrNull(PER_SOURCE_MS) {
                            runCatching { resolve(api, ref, title, original, year, api in own, isCasting, subtitleCallback, callback) }.getOrDefault(false)
                        } ?: false
                        if (got) YumeiroCatalog.Ranking.hit(api.name) else YumeiroCatalog.Ranking.miss(api.name)
                        got
                    }
                }.awaitAll().any { it }
            }
        }
        return found == true
    }

    /** FIND-2: the V2 sweep (see [SourceFinder2]). Throws [SourceFinder2.NotFound] with the true reason when nothing plays. */
    private suspend fun loadLinksV2(
        ref: YumeiroCatalog.Ref, title: String, original: String?, year: Int?, own: List<MainAPI>, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val installed = YumeiroCatalog.eligible(sources())
        val ranked = YumeiroCatalog.Ranking.order(installed).take(MAX_SOURCES)
        val key = "${ref.kind}:${ref.id}"
        val memo = SourceFinder2.recall(key)
        val remembered = memo?.let { m -> ranked.firstOrNull { it.name == m.first } }
        val waves = SourceFinder2.waves(own, ranked, remembered)
        if (waves.isEmpty()) throw SourceFinder2.NotFound(SourceFinder2.detail(emptyList(), 0))
        val t0 = System.currentTimeMillis()
        val outcomes = SourceFinder2.sweep(
            waves, name = { it.name }, isChosen = { it === remembered },
            ask = { api ->
                val page = if (api === remembered) memo?.second else null
                resolveV2(api, ref, title, original, year, api in own, page, isCasting, subtitleCallback, callback)
            },
            onFound = { api, page -> if (api !in own && page != null) SourceFinder2.remember(key, api.name, page) },
        )
        outcomes.forEach { o -> if (o.reason == SourceFinder2.Reason.FOUND) YumeiroCatalog.Ranking.hit(o.source) else if (o.reason != SourceFinder2.Reason.TIMED_OUT) YumeiroCatalog.Ranking.miss(o.source) }
        android.util.Log.w("SourceFinder2", "$key ${System.currentTimeMillis() - t0} ms asked=${outcomes.size}/${own.size + ranked.size} " +
            outcomes.joinToString { "${it.source}=${it.reason}@${it.ms}" })
        if (outcomes.any { it.reason == SourceFinder2.Reason.FOUND }) return true
        // The remembered page stopped working: forget it so the next play searches again.
        if (memo != null) SourceFinder2.forget(key)
        throw SourceFinder2.NotFound(SourceFinder2.detail(outcomes, ranked.size + own.size))
    }

    private suspend fun resolveV2(
        api: MainAPI, ref: YumeiroCatalog.Ref, title: String, original: String?, year: Int?, strict: Boolean, rememberedPage: String?,
        isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit,
    ): SourceFinder2.Answer {
        val pageUrl = rememberedPage ?: run {
            var hit = api.search(YumeiroCatalog.query(title)).orEmpty().let { YumeiroCatalog.best(it, ref.kind, title, original, year, strict) }
            if (hit == null && original != null && original != title) {
                hit = api.search(YumeiroCatalog.query(original)).orEmpty().let { YumeiroCatalog.best(it, ref.kind, title, original, year, strict) }
            }
            hit?.url
        } ?: return SourceFinder2.Answer(SourceFinder2.Reason.NO_TITLE_MATCH)
        val page = api.load(pageUrl) ?: return SourceFinder2.Answer(SourceFinder2.Reason.NO_TITLE_MATCH)
        val play = YumeiroCatalog.dataFor(page, ref) ?: return SourceFinder2.Answer(SourceFinder2.Reason.EPISODE_MISSING, pageUrl)
        var any = false
        api.loadLinks(play, isCasting, subtitleCallback) { link -> any = true; callback(link) }
        return SourceFinder2.Answer(if (any) SourceFinder2.Reason.FOUND else SourceFinder2.Reason.NO_LINKS, pageUrl)
    }

    private suspend fun resolve(
        api: MainAPI, ref: YumeiroCatalog.Ref, title: String, original: String?, year: Int?, strict: Boolean, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var hit = api.search(YumeiroCatalog.query(title)).orEmpty().let { YumeiroCatalog.best(it, ref.kind, title, original, year, strict) }
        if (hit == null && original != null && original != title) {
            hit = api.search(YumeiroCatalog.query(original)).orEmpty().let { YumeiroCatalog.best(it, ref.kind, title, original, year, strict) }
        }
        val page = api.load(hit?.url ?: return false) ?: return false
        val play = YumeiroCatalog.dataFor(page, ref) ?: return false
        var any = false
        api.loadLinks(play, isCasting, subtitleCallback) { link -> any = true; callback(link) }
        return any
    }

    private fun items(arr: JSONArray?): List<SearchResponse> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val kind = o.optString("type"); val id = o.optString("id").substringAfter(':').toLongOrNull() ?: return@mapNotNull null
            val title = o.optString("title").ifBlank { return@mapNotNull null }
            val url = "${YumeiroCatalog.PART}:$kind:$id"
            val anime = o.optBoolean("anime")
            val poster = o.optString("poster").ifBlank { null }
            val y = o.optInt("year").takeIf { it > 0 }
            when (kind) {
                "movie" -> newMovieSearchResponse(title, url, if (anime) TvType.AnimeMovie else TvType.Movie, fix = false) {
                    posterUrl = poster; year = y
                }
                "tv" -> newTvSeriesSearchResponse(title, url, if (anime) TvType.Anime else TvType.TvSeries, fix = false) {
                    posterUrl = poster; year = y
                }
                else -> null
            }
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        const val MAX_EPISODES = 3000
        const val MAX_SOURCES = 12
        const val PER_SOURCE_MS = 30_000L
        const val TOTAL_MS = 45_000L
    }
}
