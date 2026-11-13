/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.js

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.sources.js.JsJson.arr
import dev.asfakul.yumeiro.sources.js.JsJson.s
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Request

private fun q(s: String) = JsonPrimitive(s).toString()

/** Shared parsing of Mangayomi result shapes. */
internal object JsShapes {
    data class Item(val name: String, val link: String, val image: String?)
    data class Detail(val name: String?, val image: String?, val description: String?, val author: String?, val status: Int?, val genres: List<String>, val chapters: List<JsonObject>)

    fun pages(e: JsonElement): Pair<List<Item>, Boolean> {
        val o = e as? JsonObject ?: return emptyList<Item>() to false
        val list = o.arr("list").mapNotNull { it as? JsonObject }.map { Item(it.s("name").orEmpty(), it.s("link").orEmpty(), it.s("imageUrl")) }
        return list to (o.s("hasNextPage") == "true")
    }

    fun detail(e: JsonElement): Detail {
        val o = e as? JsonObject ?: JsonObject(emptyMap())
        val genres = o.arr("genre").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val chapters = (o.arr("chapters").ifEmpty { o.arr("episodes") }).mapNotNull { it as? JsonObject }
        return Detail(o.s("name"), o.s("imageUrl"), o.s("description"), o.s("author") ?: o.s("artist"), o.s("status")?.toIntOrNull(), genres, chapters)
    }

    fun status(s: Int?) = when (s) { 0 -> "Ongoing"; 1 -> "Completed"; 2 -> "On hiatus"; 3 -> "Cancelled"; 4 -> "Finished"; else -> null }
}

/** A Mangayomi anime / video JavaScript source as a [MainAPI]. */
class JsVideoProvider(private val entry: JsSources.Entry, displayName: String) : MainAPI() {
    override var name = displayName
    override var lang = entry.lang.ifBlank { "all" }
    override var mainUrl = entry.raw.s("baseUrl").orEmpty()
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Movie, TvType.TvSeries)

    init { sourcePlugin = "yumeiro-bridge:js:" + entry.id }

    private val engine get() = JsSources.engine(entry)

    override val mainPage = mainPageOf("popular" to "Popular", "latest" to "Latest")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val res = engine.call(if (request.data == "latest") "getLatestUpdates($page)" else "getPopular($page)")
        val (items, next) = JsShapes.pages(res)
        return newHomePageResponse(request.name, items.map { it.toResult() }, next)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val (items, next) = JsShapes.pages(engine.call("search(${q(query)}, $page, extention.getFilterList() || [])"))
        return newSearchResponseList(items.map { it.toResult() }, next)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse {
        val d = JsShapes.detail(engine.call("getDetail(${q(url)})"))
        val eps = d.chapters.reversed().mapIndexed { i, c ->
            newEpisode(c.s("url").orEmpty()) { name = c.s("name"); episode = i + 1; date = c.s("dateUpload")?.toLongOrNull() }
        }
        val headers = engine.headers(mainUrl)
        return newAnimeLoadResponse(d.name ?: url, url, TvType.Anime) {
            posterUrl = d.image; posterHeaders = headers; plot = d.description; tags = d.genres
            showStatus = when (d.status) { 0 -> ShowStatus.Ongoing; 1, 4 -> ShowStatus.Completed; else -> null }
            addEpisodes(DubStatus.None, eps)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val videos = (engine.call("getVideoList(${q(data)})") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        for (v in videos) {
            val url = v.s("url") ?: continue
            val headers = (v["headers"] as? JsonObject)?.mapValues { (_, x) -> (x as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()
            val label = v.s("quality").orEmpty()
            callback(newExtractorLink(name, label.ifBlank { name }, url, if (url.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
                this.headers = headers
                referer = headers["Referer"] ?: headers["referer"] ?: ""
                quality = Regex("""(\d{3,4})p""").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            })
            v.arr("subtitles").mapNotNull { it as? JsonObject }.forEach { s -> s.s("file")?.let { subtitleCallback(SubtitleFile(s.s("label") ?: "Subtitle", it)) } }
        }
        return videos.isNotEmpty()
    }

    private fun JsShapes.Item.toResult() = newAnimeSearchResponse(name, link, TvType.Anime) { posterUrl = image }
}

/** A Mangayomi manga JavaScript source as a [MangaProvider]. */
class JsMangaProvider(private val entry: JsSources.Entry) : MangaProvider {
    override val id = "js:" + entry.id
    override val name = entry.name
    override val lang = entry.lang
    override val ecosystem = "JavaScript"
    override val nsfw = entry.nsfw
    override val supportsLatest = true
    override val baseUrl = entry.raw.s("baseUrl")

    private val engine get() = JsSources.engine(entry)

    override fun coverHeaders(): Map<String, String> = baseUrl?.takeIf { it.startsWith("http") }?.let { mapOf("Referer" to it.trimEnd('/') + "/") }.orEmpty()

    private fun page(e: JsonElement) = JsShapes.pages(e).let { (items, next) -> MangaListPage(items.map { MangaItem(id, it.link, it.name, it.image) }, next) }

    override suspend fun popular(page: Int) = page(engine.call("getPopular($page)"))
    override suspend fun latest(page: Int) = page(engine.call("getLatestUpdates($page)"))
    override suspend fun search(query: String, page: Int) = page(engine.call("search(${q(query)}, $page, extention.getFilterList() || [])"))

    override suspend fun details(url: String): MangaDetails {
        val d = JsShapes.detail(engine.call("getDetail(${q(url)})"))
        return MangaDetails(id, url, d.name ?: url, d.image, d.author, d.description, d.genres, JsShapes.status(d.status))
    }

    override suspend fun chapters(url: String): List<MangaChapter> = JsShapes.detail(engine.call("getDetail(${q(url)})")).chapters.map { c ->
        MangaChapter(c.s("url").orEmpty(), c.s("name").orEmpty(), -1f, c.s("dateUpload")?.toLongOrNull() ?: 0, c.s("scanlator"))
    }

    override suspend fun pages(chapterUrl: String): List<MangaPageRef> =
        (engine.call("getPageList(${q(chapterUrl)})") as? JsonArray).orEmpty().mapIndexedNotNull { i, p ->
            val url = (p as? JsonPrimitive)?.contentOrNull ?: (p as? JsonObject)?.s("url") ?: return@mapIndexedNotNull null
            // Per-page headers are kept in the url field as JSON so image() can use them.
            val headers = (p as? JsonObject)?.get("headers")?.toString()
            MangaPageRef(i, headers.orEmpty(), url)
        }

    override suspend fun image(page: MangaPageRef): MangaImage = withContext(Dispatchers.IO) {
        val url = page.imageUrl ?: error("No image")
        val pageHeaders = page.url.takeIf { it.startsWith("{") }?.let { JsJson.json.parseToJsonElement(it) as? JsonObject }
            ?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull.orEmpty() }
        val headers = pageHeaders ?: engine.headers(url)
        NetworkClient.get(JsSources.appContext()).newCall(Request.Builder().url(url).headers(headers.toHeaders()).build()).execute().use { r ->
            if (!r.isSuccessful) error("Image failed (HTTP ${r.code})")
            MangaImage(r.body.bytes(), r.body.contentType()?.toString())
        }
    }
}
