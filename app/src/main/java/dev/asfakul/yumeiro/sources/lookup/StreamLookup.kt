/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.lookup

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.hub.YumeiroCatalogProvider
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.sources.stremio.StremioStreams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * SEARCH-CAP-1 (A0, ADR-063): search for sources that can't search.
 *
 * Bug: "All" search found nothing from Nuvio or Stremio. Cause, traced through the whole path
 * (search box → OnlineSearchScreen → ExtensionManager.providers() → OnlineRepository.search): Nuvio
 * scrapers and stream-only Stremio add-ons publish no [MainAPI] at all (NuvioAdapter.build returns
 * null, StremioAddons.publish skips add-ons without catalogs), so the search never had anything to
 * ask, and their links only joined titles that happened to carry a TMDB / IMDb id.
 *
 * Capability adapter: when at least one stream-only source of an ecosystem is installed, one lookup
 * source is published for it ("Nuvio", "Stremio streams"). Its search turns the words into titles
 * with ids (Yumeiro Cloud's TMDB search first, Cinemeta as a key-free fallback); its title page
 * carries both the TMDB and the IMDb id, so the stream add-ons (F-05-N seam, StreamAddons) are asked
 * when it is played. The results are tagged with the ecosystem's name like any other source, a
 * failure shows as that source's own status, and one failing lookup never blocks the rest (the
 * search already runs each source on its own).
 *
 * Built from the public Stremio add-on protocol and the Nuvio scraper contract; no code from any
 * other app is used.
 */
object StreamLookup {
    private const val TAG = "StreamLookup"
    const val NUVIO_NAME = "Nuvio"
    val STREMIO_NAME: String get() = tr(R.string.stremio_streams, "Stremio streams")
    const val PLUGIN_PREFIX = "yumeiro-lookup:"
    const val CINEMETA_BASE = "https://v3-cinemeta.strem.io"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    /** What should be published, from what is installed. Pure → unit-tested. */
    data class Plan(val nuvio: Boolean, val stremio: Boolean)

    fun plan(nuvioScrapers: Int, nuvioOn: Boolean, streamOnlyStremio: Int, stremioOn: Boolean, on: Boolean): Plan =
        Plan(nuvio = on && nuvioOn && nuvioScrapers > 0, stremio = on && stremioOn && streamOnlyStremio > 0)

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            combine(EcosystemRegistry.state, StremioAddons.addons) { reg, addons ->
                plan(
                    nuvioScrapers = reg.installed.count { it.ecosystem == SourceHub.Ecosystem.NUVIO },
                    nuvioOn = FeatureFlags.on(app, FeatureFlags.Flag.SCRIPTS_NUVIO),
                    streamOnlyStremio = StremioStreams.streamOnly(addons).size,
                    stremioOn = FeatureFlags.on(app, FeatureFlags.Flag.F05_STREMIO_STREAMS),
                    on = FeatureFlags.on(app, FeatureFlags.Flag.SEARCH_LOOKUP) && FeatureFlags.on(app, FeatureFlags.Flag.STREAM_ADDONS),
                )
            }.distinctUntilChanged().collect { p ->
                Log.i(TAG, "lookup sources: nuvio=${p.nuvio} stremio=${p.stremio}")
                if (p.nuvio) SourceHub.publishVideo("lookup-nuvio", SourceHub.Ecosystem.NUVIO, listOf(StreamLookupProvider(NUVIO_NAME, "nuvio")))
                else SourceHub.remove("lookup-nuvio")
                if (p.stremio) SourceHub.publishVideo("lookup-stremio", SourceHub.Ecosystem.STREMIO, listOf(StreamLookupProvider(STREMIO_NAME, "stremio")))
                else SourceHub.remove("lookup-stremio")
            }
        }
    }

    // region Cinemeta (key-free fallback and id bridge). Pure parsers → unit-tested.

    data class Hit(val kind: String, val imdb: String, val name: String, val year: Int?, val poster: String?)

    /** Cinemeta search answer (`{metas:[{id,type,name,releaseInfo,poster}]}`) as hits. */
    fun cinemetaHits(body: String?, kind: String): List<Hit> {
        val arr = runCatching { JSONObject(body ?: return emptyList()).optJSONArray("metas") }.getOrNull() ?: return emptyList()
        val out = ArrayList<Hit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("imdb_id").ifBlank { o.optString("id") }
            if (!id.startsWith("tt")) continue
            val name = o.optString("name").ifBlank { continue }
            out += Hit(kind, id, name, o.optString("releaseInfo").take(4).toIntOrNull(), o.optString("poster").ifBlank { null })
        }
        return out
    }

    /** TMDB id inside a Cinemeta meta answer (`meta.moviedb_id`), or null. */
    fun tmdbOfCinemeta(body: String?): Int? = runCatching {
        JSONObject(body ?: return null).optJSONObject("meta")?.opt("moviedb_id")?.toString()?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
    }.getOrNull()

    /** Cinemeta id for a title: `cinemeta:<movie|series>:<tt…>`; episodes add `:<season>:<episode>`. */
    data class CineRef(val kind: String, val imdb: String, val season: Int? = null, val episode: Int? = null)

    fun cineRefOf(s: String): CineRef? {
        val i = s.indexOf("cinemeta:")
        if (i < 0) return null
        val p = s.substring(i + 9).split(':')
        val kind = p.getOrNull(0)?.takeIf { it == "movie" || it == "series" } ?: return null
        val imdb = p.getOrNull(1)?.takeIf { it.startsWith("tt") } ?: return null
        return CineRef(kind, imdb, p.getOrNull(2)?.toIntOrNull(), p.getOrNull(3)?.toIntOrNull())
    }

    internal suspend fun httpGet(url: String): String? =
        dev.asfakul.yumeiro.sources.classics.YumeiroClassicsProvider.httpGet(url)

    /**
     * IMDb → TMDB through Cinemeta (no key). Used when a title from any source has only an IMDb id and
     * a Nuvio scraper (which looks titles up by TMDB id) should still be asked. Memory cache.
     */
    private val tmdbCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    suspend fun tmdbForImdb(imdb: String, tv: Boolean): Int? {
        if (!imdb.startsWith("tt")) return null
        tmdbCache[imdb]?.let { return it }
        val kind = if (tv) "series" else "movie"
        val id = withTimeoutOrNull(8_000) { tmdbOfCinemeta(httpGet("$CINEMETA_BASE/meta/$kind/$imdb.json")) } ?: return null
        if (tmdbCache.size > 512) tmdbCache.clear()
        tmdbCache[imdb] = id
        return id
    }

    /** TMDB → IMDb through Yumeiro Cloud's title page (the TMDB key stays on the Worker). Memory cache. */
    private val imdbCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun imdbForTmdb(tmdb: Int, tv: Boolean): String? {
        val key = (if (tv) "tv:" else "movie:") + tmdb
        imdbCache[key]?.let { return it }
        val host = dev.asfakul.yumeiro.BuildConfig.CLOUD_HOST.takeIf { it.isNotBlank() } ?: return null
        val body = withTimeoutOrNull(8_000) { httpGet("https://$host/v1/hub/title?type=${if (tv) "tv" else "movie"}&id=$tmdb") } ?: return null
        val imdb = runCatching { JSONObject(body).optString("imdb") }.getOrNull()?.takeIf { it.startsWith("tt") } ?: return null
        if (imdbCache.size > 512) imdbCache.clear()
        imdbCache[key] = imdb
        return imdb
    }

    // endregion
}

/**
 * A lookup source for one stream-only ecosystem (see [StreamLookup]). No home rows (nothing to browse:
 * the add-ons have no catalog); search and title pages only. Links come from the stream add-ons through
 * StreamAddons.withAddons when a title or episode is played, so [loadLinks] adds none itself.
 */
class StreamLookupProvider(
    displayName: String,
    key: String,
    private val catalog: MainAPI = YumeiroCatalogProvider(),
    private val fetch: suspend (String) -> String? = { StreamLookup.httpGet(it) },
) : MainAPI() {
    override var name = displayName
    override var mainUrl = "https://yumeiro.click"
    override var lang = "all"
    override val hasMainPage = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama, TvType.Documentary)

    init { sourcePlugin = StreamLookup.PLUGIN_PREFIX + key }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val q = query.trim().take(80)
        if (q.isEmpty() || page > 1) return newSearchResponseList(emptyList(), false)
        // Yumeiro Cloud (TMDB) first: it gives TMDB ids, which Nuvio scrapers need, and IMDb ids on the title page.
        val primary = runCatching { withTimeoutOrNull(12_000) { catalog.search(q, 1)?.items } }
            .onFailure { Log.w("StreamLookup", "cloud search: ${it.message}") }.getOrNull().orEmpty()
        if (primary.isNotEmpty()) return newSearchResponseList(primary, false)
        // Key-free fallback: Cinemeta (IMDb ids), films and series at once.
        val hits = coroutineScope {
            listOf("movie", "series").map { kind ->
                async { StreamLookup.cinemetaHits(fetch("${StreamLookup.CINEMETA_BASE}/catalog/$kind/top/search=${enc(q)}.json"), kind) }
            }.awaitAll()
        }.flatten()
        val items = hits.map { h ->
            if (h.kind == "series") newTvSeriesSearchResponse(h.name, "cinemeta:series:${h.imdb}", TvType.TvSeries) { posterUrl = h.poster; year = h.year }
            else newMovieSearchResponse(h.name, "cinemeta:movie:${h.imdb}", TvType.Movie) { posterUrl = h.poster; year = h.year }
        }
        return newSearchResponseList(items, false)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? {
        val cine = StreamLookup.cineRefOf(url) ?: return catalog.load(url)
        val body = fetch("${StreamLookup.CINEMETA_BASE}/meta/${cine.kind}/${cine.imdb}.json") ?: error("Cinemeta didn't answer. Try again in a moment.")
        val meta = JSONObject(body).optJSONObject("meta") ?: error("This title isn't available anymore")
        val title = meta.optString("name").ifBlank { cine.imdb }
        val tmdb = StreamLookup.tmdbOfCinemeta(body)
        val r: LoadResponse = if (cine.kind == "series") {
            val videos = meta.optJSONArray("videos") ?: JSONArray()
            val eps = (0 until videos.length()).mapNotNull { videos.optJSONObject(it) }
                .filter { it.optInt("season", 0) > 0 }
                .sortedWith(compareBy({ it.optInt("season") }, { it.optInt("episode", it.optInt("number")) }))
                .map { v ->
                    val s = v.optInt("season"); val e = v.optInt("episode", v.optInt("number"))
                    newEpisode("cinemeta:series:${cine.imdb}:$s:$e") {
                        name = v.optString("name").ifBlank { v.optString("title") }.ifBlank { null }
                        season = s; episode = e
                        posterUrl = v.optString("thumbnail").ifBlank { null }
                        description = v.optString("overview").ifBlank { null }
                    }
                }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) { fill(meta) }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) { fill(meta) }
        }
        with(LoadResponse) {
            r.addImdbId(cine.imdb)
            tmdb?.let { r.addTMDbId(it.toString()) }
        }
        return r
    }

    private fun LoadResponse.fill(m: JSONObject) {
        posterUrl = m.optString("poster").ifBlank { null }
        backgroundPosterUrl = m.optString("background").ifBlank { null }
        plot = m.optString("description").ifBlank { null }
        year = m.optString("releaseInfo").take(4).toIntOrNull() ?: m.optString("year").take(4).toIntOrNull()
        tags = m.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) }.filter { it.isNotBlank() } }
        m.optString("imdbRating").toDoubleOrNull()?.let { score = com.lagradost.cloudstream3.Score.from10(it) }
    }

    /** The stream add-ons answer through StreamAddons.withAddons (merged with this call); nothing to add here. */
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean = false

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
