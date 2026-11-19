/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.ecosystem

import android.content.Context
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
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 * ADR-047: runtime adapter for the Vega ecosystem (Zenda-Cross/vega-providers). A Vega provider is
 * five CommonJS files (`catalog`, `posts`, `meta`, `episodes`, `stream`); this maps them onto
 * CloudStream's [MainAPI] so they show up like any other source (home rows, search, details, the
 * stream list, Source status). Nothing Vega-specific lives outside this file.
 */
object VegaAdapter : RuntimeAdapter {
    override val ecosystem = SourceHub.Ecosystem.VEGA
    override val flag = FeatureFlags.Flag.SCRIPTS_VEGA
    override fun required(p: PluginEntry): Set<String> = VegaManifestParser.REQUIRED.map { "$it.js" }.toSet()

    /** The provider's home rows (`catalog` export, a list or a function) are read once at install. */
    override suspend fun prepare(context: Context, plugin: EcosystemRegistry.Installed, dir: File): JsonObject {
        val cat = runCatching {
            ScriptRuntime.call(context, plugin.storeName, modules(dir), "catalog", "catalog", "{}")
        }.getOrNull()
        val rows = (cat as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .mapNotNull { o -> o.str("title")?.let { t -> JsonObject(mapOf("title" to JsonPrimitive(t), "filter" to JsonPrimitive(o.str("filter").orEmpty()))) } }
        return buildJsonObject { put("catalog", JsonArray(rows.take(MAX_ROWS))) }
    }

    override fun build(context: Context, plugin: EcosystemRegistry.Installed, dir: File, displayName: String): MainAPI =
        VegaProvider(context.applicationContext, plugin, dir, displayName)

    internal fun modules(dir: File): List<ScriptRuntime.Module> =
        VegaManifestParser.FILES.map { File(dir, "$it.js") }.filter { it.isFile }.map { ScriptRuntime.Module(it.nameWithoutExtension, it) }

    internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private const val MAX_ROWS = 12

    /** A Vega stream is dropped when it carries DRM data (proposal F-05 §4: no provider-supplied keys). */
    fun playable(stream: JsonObject): Boolean = stream["drm"].let { it == null || it is kotlinx.serialization.json.JsonNull } && stream.str("link") != null

    /** Quality number out of "1080", "1080p" or a server label, without a regex. */
    fun qualityOf(vararg labels: String?): Int {
        for (l in labels) {
            l ?: continue
            for (q in listOf(2160, 1440, 1080, 720, 576, 480, 360, 240)) if (l.contains(q.toString())) return q
            if (l.contains("4k", true)) return 2160
        }
        return 0
    }

    /** One episode or movie target: what `getStream` needs. */
    fun target(link: String, type: String): String = buildJsonObject { put("link", link); put("type", type) }.toString()
}

internal class VegaProvider(
    private val context: Context,
    private val plugin: EcosystemRegistry.Installed,
    private val dir: File,
    displayName: String,
) : MainAPI() {
    override var name = displayName
    override var lang = plugin.lang ?: "all"
    override var mainUrl = "https://${plugin.id}.vega.invalid"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    init { sourcePlugin = "yumeiro-bridge:vega:" + plugin.key }

    override val mainPage: List<MainPageData> = (plugin.extra["catalog"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        .map { MainPageData(with(VegaAdapter) { it.str("title") } ?: "Latest", with(VegaAdapter) { it.str("filter") }.orEmpty()) }
        .ifEmpty { listOf(MainPageData("Latest", "")) }

    private suspend fun call(module: String, fn: String, args: JsonObject): JsonElement =
        ScriptRuntime.call(context, plugin.storeName, VegaAdapter.modules(dir), module, fn, args.toString())

    private fun posts(e: JsonElement): List<SearchResponse> = (e as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { p ->
        with(VegaAdapter) {
            val link = p.str("link") ?: return@mapNotNull null
            newMovieSearchResponse(p.str("title") ?: link, link, TvType.Movie, fix = false) { posterUrl = p.str("image") }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = posts(call("posts", "getPosts", buildJsonObject { put("filter", request.data); put("page", page); put("providerValue", plugin.id) }))
        return newHomePageResponse(request.name, list, list.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val list = posts(call("posts", "getSearchPosts", buildJsonObject { put("searchQuery", query); put("page", page); put("providerValue", plugin.id) }))
        return newSearchResponseList(list, list.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse { return with(VegaAdapter) {
        val info = call("meta", "getMeta", buildJsonObject { put("link", url); put("provider", plugin.id) }) as? JsonObject
            ?: error("This source returned no details")
        val title = info.str("title") ?: url
        val series = info.str("type")?.lowercase()?.let { it == "series" || it == "show" || it == "tv" } == true
        val links = (info["linkList"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        if (!series) {
            val targets = links.flatMap { l -> (l["directLinks"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject } }
                .mapNotNull { d -> d.str("link")?.let { JsonObject(mapOf("link" to JsonPrimitive(it), "type" to JsonPrimitive(d.str("type") ?: "movie"))) } }
                .ifEmpty {
                    // A movie whose links are behind an episodes page: one call for the first list only.
                    links.firstNotNullOfOrNull { it.str("episodesLink") }?.let { episodes(it) }.orEmpty()
                        .map { JsonObject(mapOf("link" to JsonPrimitive(it.second), "type" to JsonPrimitive("movie"))) }
                }
            return newMovieLoadResponse(title, url, TvType.Movie, JsonArray(targets.take(MAX_TARGETS)).toString()) {
                posterUrl = info.str("image"); backgroundPosterUrl = info.str("poster"); plot = info.str("synopsis")
                tags = (info["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            }
        }
        // Each link list is one season (or one quality of a season), in the provider's order.
        val eps = links.take(MAX_LISTS).flatMapIndexed { i, l ->
            val direct = (l["directLinks"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .mapNotNull { d -> d.str("link")?.let { (d.str("title") ?: "Episode") to it } }
            val list = direct.ifEmpty { l.str("episodesLink")?.let { runCatching { episodes(it) }.getOrDefault(emptyList()) }.orEmpty() }
            list.mapIndexed { n, (epTitle, link) ->
                newEpisode(VegaAdapter.target(link, "series"), {
                    name = listOfNotNull(epTitle, l.str("title")?.takeIf { links.size > 1 }).joinToString(" · ")
                    season = i + 1; episode = n + 1
                }, false)
            }
        }
        newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
            posterUrl = info.str("image"); backgroundPosterUrl = info.str("poster"); plot = info.str("synopsis")
            tags = (info["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        }
    } }

    private suspend fun episodes(url: String): List<Pair<String, String>> = with(VegaAdapter) {
        (call("episodes", "getEpisodes", buildJsonObject { put("url", url) }) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .mapNotNull { e -> e.str("link")?.let { (e.str("title") ?: "Episode") to it } }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parsed = runCatching { RepoFormats.json.parseToJsonElement(data) }.getOrNull()
        val targets = when (parsed) {
            is JsonArray -> parsed.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(parsed)
            else -> emptyList()
        }
        var found = false
        var dropped = 0
        for (t in targets.take(MAX_TARGETS)) {
            val streams = runCatching {
                call("stream", "getStream", buildJsonObject { put("link", with(VegaAdapter) { t.str("link") }.orEmpty()); put("type", with(VegaAdapter) { t.str("type") } ?: "movie") })
            }.getOrNull()
            for (s in (streams as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }) with(VegaAdapter) {
                if (!playable(s)) { dropped++; return@with }
                val link = s.str("link")!!
                val headers = (s["headers"] as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()
                val type = s.str("type").orEmpty().lowercase()
                val linkType = when {
                    type == "m3u8" || link.contains(".m3u8", true) -> ExtractorLinkType.M3U8
                    type == "mpd" || type == "dash" || link.contains(".mpd", true) -> ExtractorLinkType.DASH
                    else -> ExtractorLinkType.VIDEO
                }
                val label = listOfNotNull(s.str("server"), s.str("quality")?.let { if (it.all(Char::isDigit)) "${it}p" else it }).joinToString(" · ").ifBlank { name }
                callback(newExtractorLink(name, label.take(160), link, linkType) {
                    this.headers = headers
                    referer = headers["Referer"] ?: headers["referer"] ?: ""
                    quality = qualityOf(s.str("quality"), s.str("server"))
                })
                subtitles(s["subtitles"]).forEach(subtitleCallback)
                found = true
            }
        }
        if (dropped > 0) android.util.Log.i("VegaProvider", "$name: $dropped protected stream(s) not supported from extensions")
        return found
    }

    private fun subtitles(e: JsonElement?): List<SubtitleFile> = when (e) {
        is JsonArray -> e.flatMap { subtitles(it) }
        is JsonObject -> with(VegaAdapter) { e.str("uri")?.let { listOf(SubtitleFile(e.str("language") ?: e.str("title") ?: "Subtitle", it)) }.orEmpty() }
        else -> emptyList()
    }

    private companion object {
        const val MAX_TARGETS = 6
        const val MAX_LISTS = 8
    }
}
