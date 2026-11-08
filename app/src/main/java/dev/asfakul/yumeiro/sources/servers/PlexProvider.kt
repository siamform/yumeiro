/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.servers

import android.content.Context
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
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
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.sources.servers.JellyfinClient.arr
import dev.asfakul.yumeiro.sources.servers.JellyfinClient.n
import dev.asfakul.yumeiro.sources.servers.JellyfinClient.s
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.Request
import java.net.URLEncoder

/**
 * Plex Media Server. Sign-in uses Plex's own PIN flow (the user approves on plex.tv) so Yumeiro
 * never sees the Plex password; a server URL plus an existing X-Plex-Token also works.
 */
object PlexClient {
    private const val PRODUCT = "Yumeiro"

    data class Pin(val id: Long, val code: String, val authUrl: String)

    private fun headers(context: Context, b: Request.Builder) = b.header("Accept", "application/json")
        .header("X-Plex-Product", PRODUCT).header("X-Plex-Client-Identifier", HomeServers.deviceId(context))

    suspend fun createPin(context: Context): Pin = withContext(Dispatchers.IO) {
        val req = headers(context, Request.Builder().url("https://plex.tv/api/v2/pins?strong=true").post(FormBody.Builder().build())).build()
        val o = NetworkClient.get(context).newCall(req).execute().use { JellyfinClient.json.parseToJsonElement(it.body.string()).jsonObject }
        val id = o.s("id")?.toLong() ?: error("Plex didn't create a PIN")
        val code = o.s("code") ?: error("Plex didn't create a PIN")
        val url = "https://app.plex.tv/auth#?clientID=${HomeServers.deviceId(context)}&code=$code&context%5Bdevice%5D%5Bproduct%5D=$PRODUCT"
        Pin(id, code, url)
    }

    /** Polls until the user approves the PIN (or ~5 minutes pass). Returns the auth token. */
    suspend fun awaitToken(context: Context, pin: Pin): String = withContext(Dispatchers.IO) {
        repeat(150) {
            val req = headers(context, Request.Builder().url("https://plex.tv/api/v2/pins/${pin.id}")).build()
            val token = runCatching { NetworkClient.get(context).newCall(req).execute().use { JellyfinClient.json.parseToJsonElement(it.body.string()).jsonObject.s("authToken") } }.getOrNull()
            if (!token.isNullOrBlank()) return@withContext token
            delay(2000)
        }
        error("Plex sign-in timed out")
    }

    suspend fun probe(context: Context, base: String, token: String): HomeServers.Server = withContext(Dispatchers.IO) {
        val req = headers(context, Request.Builder().url("$base/identity")).header("X-Plex-Token", token).build()
        val name = NetworkClient.get(context).newCall(req).execute().use { r ->
            if (r.code == 401) error("Plex token was refused")
            if (!r.isSuccessful) error("Plex answered HTTP ${r.code}")
            "Plex"
        }
        HomeServers.Server(kind = HomeServers.Kind.PLEX, name = name, url = base, secret = HomeServers.seal(token))
    }

    internal fun Request.Builder.plex(context: Context, token: String) = headers(context, this).header("X-Plex-Token", token)
}

class PlexProvider(val server: HomeServers.Server, displayName: String) : MainAPI() {
    override var name = displayName
    override var mainUrl = server.url
    override var lang = "all"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Others)

    init { sourcePlugin = "yumeiro-bridge:server:" + server.id }

    private val token by lazy { server.secretPlain() }
    private val ctx get() = HomeServers.appContext

    override val mainPage = listOf(MainPageData("On deck", "deck"), MainPageData("Recently added", "recent"), MainPageData("Libraries", "sections"))

    private suspend fun get(path: String): JsonObject = withContext(Dispatchers.IO) {
        val req = with(PlexClient) { Request.Builder().url(server.url + path).plex(ctx, token) }.build()
        NetworkClient.get(ctx).newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Plex answered HTTP ${r.code}")
            (JellyfinClient.json.parseToJsonElement(r.body.string()).jsonObject["MediaContainer"] as? JsonObject) ?: JsonObject(emptyMap())
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1 && !request.data.startsWith("section:")) return newHomePageResponse(request.name, emptyList(), false)
        val list = when (request.data) {
            "deck" -> get("/library/onDeck").arr("Metadata")
            "recent" -> get("/library/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=40").arr("Metadata")
            "sections" -> get("/library/sections").arr("Directory").filter { it.s("type") == "movie" || it.s("type") == "show" }
            else -> get("/library/sections/${request.data.removePrefix("section:")}/all?X-Plex-Container-Start=${(page - 1) * 40}&X-Plex-Container-Size=40").arr("Metadata")
        }
        return newHomePageResponse(request.name, list.map { it.toResult() }, false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val hubs = get("/hubs/search?query=${URLEncoder.encode(query, "UTF-8")}&limit=30").arr("Hub")
        val items = hubs.filter { it.s("type") in setOf("movie", "show", "episode") }.flatMap { it.arr("Metadata") }
        return newSearchResponseList(items.map { it.toResult() }, false)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse {
        val key = url.substringAfter("plex:")
        if (key.startsWith("section/")) {
            val id = key.removePrefix("section/")
            val all = get("/library/sections/$id/all").arr("Metadata")
            return newMovieLoadResponse("Library", url, TvType.Others, "") { recommendations = all.map { it.toResult() }; plot = "${all.size} titles" }
        }
        val m = get("/library/metadata/$key").arr("Metadata").firstOrNull() ?: error("Not found")
        val title = m.s("title") ?: key
        val tags = m.arr("Genre").mapNotNull { it.s("tag") }
        if (m.s("type") == "show") {
            val eps = get("/library/metadata/$key/allLeaves").arr("Metadata").map { e ->
                newEpisode("rk:" + e.s("ratingKey")) {
                    name = e.s("title"); season = e.n("parentIndex"); episode = e.n("index")
                    description = e.s("summary"); posterUrl = img(e.s("thumb"))
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                posterUrl = img(m.s("thumb")); backgroundPosterUrl = img(m.s("art")); plot = m.s("summary"); year = m.n("year"); this.tags = tags
                m.s("rating")?.toDoubleOrNull()?.let { score = Score.from10(it) }
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, "rk:$key") {
            posterUrl = img(m.s("thumb")); backgroundPosterUrl = img(m.s("art")); plot = m.s("summary"); year = m.n("year"); this.tags = tags
            m.s("rating")?.toDoubleOrNull()?.let { score = Score.from10(it) }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val rk = data.removePrefix("rk:").ifBlank { return false }
        val m = get("/library/metadata/$rk").arr("Metadata").firstOrNull() ?: return false
        val part = m.arr("Media").firstOrNull()?.arr("Part")?.firstOrNull()
        part?.s("key")?.let { k -> callback(newExtractorLink(name, "Original", "${server.url}$k?X-Plex-Token=$token", ExtractorLinkType.VIDEO) { quality = 0 }) }
        val hls = "${server.url}/video/:/transcode/universal/start.m3u8?path=${URLEncoder.encode("/library/metadata/$rk", "UTF-8")}" +
            "&protocol=hls&directPlay=0&directStream=1&X-Plex-Token=$token&X-Plex-Client-Identifier=${HomeServers.deviceId(ctx)}&X-Plex-Product=Yumeiro&X-Plex-Platform=Android"
        callback(newExtractorLink(name, "Transcoded (HLS)", hls, ExtractorLinkType.M3U8) { quality = 0 })
        part?.arr("Stream")?.filter { it.n("streamType") == 3 && it.s("key") != null }?.forEach { s ->
            subtitleCallback(SubtitleFile(s.s("displayTitle") ?: s.s("language") ?: "Subtitle", "${server.url}${s.s("key")}?X-Plex-Token=$token"))
        }
        return true
    }

    private fun img(path: String?) = path?.let { "${server.url}/photo/:/transcode?width=400&height=600&minSize=1&url=${URLEncoder.encode(it, "UTF-8")}&X-Plex-Token=$token" }

    private fun JsonObject.toResult(): SearchResponse {
        if (s("key")?.startsWith("/library/sections") == false && s("ratingKey") == null && s("type") in setOf("movie", "show")) {
            // A library section directory.
            return newMovieSearchResponse(s("title").orEmpty(), "plex:section/${s("key")}", TvType.Others) { posterUrl = img(s("thumb")) }
        }
        val type = s("type")
        val target = if (type == "episode") s("grandparentRatingKey") ?: s("ratingKey") else s("ratingKey")
        val title = if (type == "episode") "${s("grandparentTitle").orEmpty()} · ${s("title").orEmpty()}" else s("title").orEmpty()
        val tv = when (type) { "show", "episode" -> TvType.TvSeries; "movie" -> TvType.Movie; else -> TvType.Others }
        return newMovieSearchResponse(title, "plex:$target", tv) {
            posterUrl = img(if (type == "episode") s("grandparentThumb") ?: s("thumb") else s("thumb")); year = n("year")
        }
    }
}
