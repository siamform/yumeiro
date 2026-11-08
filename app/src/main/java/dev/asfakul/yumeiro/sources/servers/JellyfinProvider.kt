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
import dev.asfakul.yumeiro.BuildConfig
import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/** Jellyfin and Emby share the same REST API for everything Yumeiro needs. */
object JellyfinClient {
    val json = Json { ignoreUnknownKeys = true }

    fun authHeader(context: Context, token: String? = null): String =
        "MediaBrowser Client=\"Yumeiro\", Device=\"Android\", DeviceId=\"${HomeServers.deviceId(context)}\", Version=\"${BuildConfig.VERSION_NAME}\"" +
            (token?.let { ", Token=\"$it\"" } ?: "")

    suspend fun login(context: Context, kind: HomeServers.Kind, base: String, user: String, password: String): HomeServers.Server =
        withContext(Dispatchers.IO) {
            val body = """{"Username":${JsonPrimitive(user)},"Pw":${JsonPrimitive(password)}}""".toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$base/Users/AuthenticateByName").post(body)
                .header("X-Emby-Authorization", authHeader(context)).header("Authorization", authHeader(context)).build()
            val o = NetworkClient.get(context).newCall(req).execute().use { r ->
                if (r.code == 401) error("Wrong username or password")
                if (!r.isSuccessful) error("Server answered HTTP ${r.code}")
                json.parseToJsonElement(r.body.string()).jsonObject
            }
            val token = o.s("AccessToken") ?: error("No access token")
            val uid = (o["User"] as? JsonObject)?.s("Id") ?: error("No user id")
            val serverName = (o["User"] as? JsonObject)?.s("ServerName").orEmpty()
            HomeServers.Server(kind = kind, name = serverName, url = base, user = user, secret = HomeServers.seal(token), userId = uid)
        }

    internal fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    internal fun JsonObject.n(k: String): Int? = (this[k] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()
    internal fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
}

class JellyfinProvider(val server: HomeServers.Server, displayName: String) : MainAPI() {
    override var name = displayName
    override var mainUrl = server.url
    override var lang = "all"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.Others)

    init { sourcePlugin = "yumeiro-bridge:server:" + server.id }

    private val token by lazy { server.secretPlain() }
    private val ctx get() = HomeServers.appContext

    override val mainPage = listOf(MainPageData("Continue watching", "resume"), MainPageData("Recently added", "latest"), MainPageData("Libraries", "views"))

    private suspend fun get(path: String): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(server.url + path).header("X-Emby-Token", token)
            .header("X-Emby-Authorization", JellyfinClient.authHeader(ctx, token)).header("Accept", "application/json").build()
        NetworkClient.get(ctx).newCall(req).execute().use { r ->
            if (r.code == 401) error("Signed out of ${server.name} — remove and add the server again")
            if (!r.isSuccessful) error("${server.name} answered HTTP ${r.code}")
            JellyfinClient.json.parseToJsonElement(r.body.string())
        }
    }

    private val uid get() = server.userId
    private val itemFields = "Fields=Overview,Genres,ProductionYear&EnableImageTypes=Primary,Backdrop"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = with(JellyfinClient) {
        val start = (page - 1) * 40
        val items: List<JsonObject> = when (request.data) {
            "resume" -> if (page > 1) emptyList() else get("/Users/$uid/Items/Resume?Limit=24&MediaTypes=Video&$itemFields").jsonObject.arr("Items")
            "latest" -> if (page > 1) emptyList() else (get("/Users/$uid/Items/Latest?Limit=40&IncludeItemTypes=Movie,Series&$itemFields") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            "views" -> if (page > 1) emptyList() else get("/Users/$uid/Views").jsonObject.arr("Items").filter { it.s("CollectionType") != "music" }
            else -> get("/Users/$uid/Items?ParentId=${request.data.removePrefix("view:")}&Recursive=true&IncludeItemTypes=Movie,Series,Video&SortBy=DateCreated&SortOrder=Descending&StartIndex=$start&Limit=40&$itemFields").jsonObject.arr("Items")
        }
        return newHomePageResponse(request.name, items.map { it.toResult() }, request.data.startsWith("view:") && items.size == 40)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList = with(JellyfinClient) {
        val q = URLEncoder.encode(query, "UTF-8")
        val items = get("/Users/$uid/Items?searchTerm=$q&Recursive=true&IncludeItemTypes=Movie,Series,Episode,Video&Limit=60&StartIndex=${(page - 1) * 60}&$itemFields").jsonObject.arr("Items")
        return newSearchResponseList(items.map { it.toResult() }, items.size == 60)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse = with(JellyfinClient) {
        val id = url.substringAfterLast(":")
        val o = get("/Users/$uid/Items/$id").jsonObject
        val type = o.s("Type")
        val title = o.s("Name") ?: id
        val genres = (o["Genres"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        if (type == "CollectionFolder" || type == "Folder" || type == "BoxSet" || type == "UserView") {
            // A library folder: show its newest items as "recommendations" so the user can drill in.
            val children = get("/Users/$uid/Items?ParentId=$id&Recursive=true&IncludeItemTypes=Movie,Series,Video&SortBy=SortName&Limit=200&$itemFields").jsonObject.arr("Items")
            return newMovieLoadResponse(title, url, TvType.Others, "") {
                posterUrl = image(id); plot = "${children.size} titles"
                recommendations = children.map { it.toResult() }
            }
        }
        if (type == "Series") {
            val eps = get("/Shows/$id/Episodes?UserId=$uid&Fields=Overview").jsonObject.arr("Items").map { e ->
                newEpisode("item:" + e.s("Id")) {
                    name = e.s("Name"); season = e.n("ParentIndexNumber"); episode = e.n("IndexNumber")
                    description = e.s("Overview"); posterUrl = image(e.s("Id").orEmpty())
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                posterUrl = image(id); backgroundPosterUrl = image(id, "Backdrop"); plot = o.s("Overview")
                year = o.n("ProductionYear"); tags = genres
                o.s("CommunityRating")?.toDoubleOrNull()?.let { score = Score.from10(it) }
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, "item:$id") {
            posterUrl = image(id); backgroundPosterUrl = image(id, "Backdrop"); plot = o.s("Overview")
            year = o.n("ProductionYear"); tags = genres
            o.s("CommunityRating")?.toDoubleOrNull()?.let { score = Score.from10(it) }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean = with(JellyfinClient) {
        val id = data.removePrefix("item:")
        if (id.isBlank()) return false
        val o = get("/Users/$uid/Items/$id?Fields=MediaStreams,MediaSources").jsonObject
        val source = o.arr("MediaSources").firstOrNull()
        val sourceId = source?.s("Id") ?: id
        callback(newExtractorLink(name, "Original", "${server.url}/Videos/$id/stream?static=true&MediaSourceId=$sourceId&api_key=$token", ExtractorLinkType.VIDEO) { quality = 0 })
        callback(newExtractorLink(name, "Transcoded (HLS)", "${server.url}/Videos/$id/master.m3u8?MediaSourceId=$sourceId&VideoCodec=h264&AudioCodec=aac&api_key=$token&DeviceId=${HomeServers.deviceId(ctx)}", ExtractorLinkType.M3U8) { quality = 0 })
        (source?.arr("MediaStreams") ?: o.arr("MediaStreams")).filter { it.s("Type") == "Subtitle" && it.s("IsTextSubtitleStream") == "true" }.forEach { s ->
            val index = s.n("Index") ?: return@forEach
            val label = s.s("DisplayTitle") ?: s.s("Language") ?: "Subtitle $index"
            subtitleCallback(SubtitleFile(label, "${server.url}/Videos/$id/$sourceId/Subtitles/$index/Stream.vtt?api_key=$token"))
        }
        return true
    }

    private fun image(id: String, kind: String = "Primary") = "${server.url}/Items/$id/Images/$kind?maxHeight=600&quality=85&api_key=$token"

    private fun JsonObject.toResult(): SearchResponse = with(JellyfinClient) {
        val id = s("Id").orEmpty()
        val tv = when (s("Type")) { "Series" -> TvType.TvSeries; "Movie" -> TvType.Movie; else -> TvType.Others }
        // Episodes open their series; everything else opens itself.
        val target = if (s("Type") == "Episode") s("SeriesId") ?: id else id
        newMovieSearchResponse(if (s("Type") == "Episode") "${s("SeriesName") ?: ""} · ${s("Name")}" else s("Name").orEmpty(), "jf:$target", tv) {
            posterUrl = image(if (s("Type") == "Episode") target else id)
            year = n("ProductionYear")
        }
    }
}
