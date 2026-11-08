/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.stremio

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.sources.stremio.StremioAddons.bool
import dev.asfakul.yumeiro.sources.stremio.StremioAddons.str
import dev.asfakul.yumeiro.sources.stremio.StremioAddons.strings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * One Stremio catalog add-on as a [MainAPI]. Item URLs are `<type>/<id>`; episode data is
 * `<type>/<videoId>`. Streams and subtitles are gathered from every installed add-on that
 * answers that type and id prefix.
 */
class StremioProvider(private val addon: StremioAddons.Addon, displayName: String) : MainAPI() {
    override var name = displayName
    override var mainUrl = addon.base
    override var lang = "all"
    override val hasMainPage = true
    override val supportedTypes = addon.types.map(::tvType).toSet().ifEmpty { setOf(TvType.Movie, TvType.TvSeries) }

    init { sourcePlugin = "yumeiro-bridge:stremio:" + addon.id }

    override val mainPage = addon.catalogs.filter { !it.requiresExtra }
        .map { MainPageData("${it.name} · ${it.type.replaceFirstChar(Char::titlecase)}", "${it.type}/${it.id}") }

    private val pageSize = ConcurrentHashMap<String, Int>()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (type, id) = request.data.split("/", limit = 2)
        val step = pageSize[request.data] ?: 0
        val skip = if (page <= 1) "" else "/skip=${(page - 1) * step.coerceAtLeast(1)}"
        if (page > 1 && step == 0) return newHomePageResponse(request.name, emptyList(), false)
        val metas = catalog(type, id, skip)
        if (page <= 1) pageSize[request.data] = metas.size
        return newHomePageResponse(request.name, metas.map { it.toResult() }, metas.isNotEmpty() && page < 50)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (page > 1) return newSearchResponseList(emptyList(), false)
        val q = "/search=" + URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val results = coroutineScope {
            addon.catalogs.filter { it.searchable }.map { c -> async { runCatching { catalog(c.type, c.id, q) }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatten().distinctBy { it.str("id") }
        return newSearchResponseList(results.map { it.toResult() }, false)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse {
        val (type, id) = url.split("/", limit = 2)
        val meta = metaFor(type, id) ?: JsonObject(mapOf("id" to JsonPrimitive(id), "name" to JsonPrimitive(id)))
        val title = meta.str("name") ?: id
        val videos = (meta["videos"] as? JsonArray).orEmpty().map { it.jsonObject }
        val year = meta.str("releaseInfo")?.take(4)?.toIntOrNull() ?: meta.str("year")?.take(4)?.toIntOrNull()
        val tags = meta.strings("genres").ifEmpty { meta.strings("genre") }
        val tv = tvType(type)
        return if (videos.isEmpty() || type == "movie") {
            newMovieLoadResponse(title, url, tv, "$type/$id") {
                posterUrl = meta.str("poster"); backgroundPosterUrl = meta.str("background")
                plot = meta.str("description"); this.year = year; this.tags = tags
                meta.str("imdbRating")?.toDoubleOrNull()?.let { this.score = com.lagradost.cloudstream3.Score.from10(it) }
            }
        } else {
            val eps = videos.sortedWith(compareBy({ it.num("season") ?: 0 }, { it.num("episode") ?: 0 })).mapIndexed { i, v ->
                newEpisode("$type/${v.str("id")}") {
                    name = v.str("title") ?: v.str("name")
                    season = v.num("season")
                    episode = v.num("episode") ?: (i + 1)
                    posterUrl = v.str("thumbnail")
                    description = v.str("overview") ?: v.str("description")
                }
            }
            newTvSeriesLoadResponse(title, url, tv, eps) {
                posterUrl = meta.str("poster"); backgroundPosterUrl = meta.str("background")
                plot = meta.str("description"); this.year = year; this.tags = tags
                meta.str("imdbRating")?.toDoubleOrNull()?.let { this.score = com.lagradost.cloudstream3.Score.from10(it) }
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val (type, id) = data.split("/", limit = 2)
        val ctx = StremioAddons.context()
        val all = StremioAddons.addons.value
        var found = false
        coroutineScope {
            val streamJobs = all.filter { it.supports("stream", type, id) }.map { a ->
                async {
                    val res = withTimeoutOrNull(20_000) { runCatching { StremioAddons.getJson(ctx, "${a.base}/stream/$type/${enc(id)}.json").jsonObject }.getOrNull() }
                    a to (res?.get("streams") as? JsonArray).orEmpty().map { it.jsonObject }
                }
            }
            val subJobs = all.filter { it.supports("subtitles", type, id) }.map { a ->
                async {
                    withTimeoutOrNull(15_000) { runCatching { StremioAddons.getJson(ctx, "${a.base}/subtitles/$type/${enc(id)}.json").jsonObject }.getOrNull() }
                        ?.let { (it["subtitles"] as? JsonArray).orEmpty().map { s -> s.jsonObject } }.orEmpty()
                }
            }
            for ((a, streams) in streamJobs.awaitAll()) {
                for (s in streams) {
                    val url = s.str("url") ?: continue // torrents (infoHash) and YouTube ids are skipped
                    val label = listOfNotNull(s.str("name"), s.str("title") ?: s.str("description")).joinToString(" · ").replace("\n", " ").ifBlank { a.name }
                    val headers = ((s["behaviorHints"] as? JsonObject)?.get("proxyHeaders") as? JsonObject)?.get("request")
                        ?.let { h -> (h as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() } }.orEmpty()
                    val linkType = if (url.contains(".m3u8", true)) ExtractorLinkType.M3U8 else if (url.contains(".mpd", true)) ExtractorLinkType.DASH else ExtractorLinkType.VIDEO
                    callback(newExtractorLink(a.name, label.take(160), url, linkType) {
                        this.headers = headers
                        quality = Regex("""(2160|1440|1080|720|576|480|360)[pP]""").find(label)?.groupValues?.get(1)?.toIntOrNull()
                            ?: if (label.contains("4k", true)) 2160 else 0
                    })
                    (s["subtitles"] as? JsonArray)?.forEach { sub -> sub.jsonObject.let { o -> o.str("url")?.let { u -> subtitleCallback(SubtitleFile(o.str("lang") ?: "Unknown", u)) } } }
                    found = true
                }
            }
            subJobs.awaitAll().flatten().forEach { o -> o.str("url")?.let { u -> subtitleCallback(SubtitleFile(o.str("lang") ?: "Unknown", u)) } }
        }
        return found
    }

    private suspend fun catalog(type: String, id: String, extra: String): List<JsonObject> {
        val res = StremioAddons.getJson(StremioAddons.context(), "${addon.base}/catalog/$type/${enc(id)}$extra.json").jsonObject
        return (res["metas"] as? JsonArray).orEmpty().map { it.jsonObject }
    }

    /** Meta from this add-on when it can, otherwise from any add-on that answers the id. */
    private suspend fun metaFor(type: String, id: String): JsonObject? {
        val candidates = listOf(addon) + StremioAddons.addons.value.filter { it.manifestUrl != addon.manifestUrl }
        for (a in candidates.filter { it.supports("meta", type, id) }) {
            val m = runCatching { StremioAddons.getJson(StremioAddons.context(), "${a.base}/meta/$type/${enc(id)}.json").jsonObject["meta"] as? JsonObject }.getOrNull()
            if (m != null) return m
        }
        return null
    }

    private fun JsonObject.toResult(): SearchResponse {
        val type = str("type") ?: addon.types.firstOrNull() ?: "movie"
        return newMovieSearchResponse(str("name") ?: str("id").orEmpty(), "$type/${str("id")}", tvType(type)) {
            posterUrl = str("poster")
        }
    }

    private fun JsonObject.num(k: String): Int? = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()

    companion object {
        fun tvType(type: String): TvType = when (type.lowercase()) {
            "movie" -> TvType.Movie
            "series" -> TvType.TvSeries
            "anime" -> TvType.Anime
            "tv", "channel" -> TvType.Live
            else -> TvType.Others
        }

        private fun enc(id: String) = URLEncoder.encode(id, "UTF-8").replace("+", "%20")
    }
}
