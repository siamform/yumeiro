/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.tachiyomi

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
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Presents one Aniyomi anime source as a CloudStream [MainAPI], so the whole online stack
 * (Home rows, search, details, player, downloads, bookmarks, new-episode checks) uses it as-is.
 *
 * Search results and details carry the source's own relative URLs; episodes carry the episode URL.
 */
class AniyomiProvider(val source: AnimeCatalogueSource, displayName: String) : MainAPI() {
    override var name = displayName
    override var lang = source.lang.ifBlank { "all" }
    override var mainUrl = (source as? AnimeHttpSource)?.baseUrl ?: ""
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    init { sourcePlugin = "yumeiro-bridge:aniyomi:" + source.id }

    private val http get() = source as? AnimeHttpSource
    private val posterHeaders get() = http?.headers?.toMap().orEmpty()

    override val mainPage = if (source.supportsLatest) mainPageOf("popular" to "Popular", "latest" to "Latest") else mainPageOf("popular" to "Popular")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse = io {
        val res = if (request.data == "latest") source.getLatestUpdates(page) else source.getPopularAnime(page)
        newHomePageResponse(request.name, res.toResults(), res.hasNextPage)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList = io {
        val res = source.getSearchAnime(page, query, source.getFilterList())
        newSearchResponseList(res.toResults(), res.hasNextPage)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse = io {
        val anime = SAnime.create().apply { this.url = url; title = "" }
        val details = runCatching { source.getAnimeDetails(anime) }.getOrDefault(anime)
        details.url = url
        // Aniyomi lists newest first; the player expects episode 1 first.
        val episodes = source.getEpisodeList(details).reversed()
        newAnimeLoadResponse(details.title.ifBlank { url }, url, TvType.Anime) {
            posterUrl = details.thumbnail_url
            posterHeaders = this@AniyomiProvider.posterHeaders
            backgroundPosterUrl = details.background_url
            plot = details.description
            tags = details.genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            showStatus = when (details.status) {
                SAnime.ONGOING -> ShowStatus.Ongoing
                SAnime.COMPLETED, SAnime.PUBLISHING_FINISHED -> ShowStatus.Completed
                else -> null
            }
            addEpisodes(DubStatus.None, episodes.mapIndexed { i, ep ->
                newEpisode(ep.url) {
                    name = ep.name
                    episode = ep.episode_number.takeIf { it >= 0 }?.toInt() ?: (i + 1)
                    posterUrl = ep.preview_url
                    description = ep.summary
                    date = ep.date_upload.takeIf { it > 0 }
                }
            })
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean = io {
        val episode = SEpisode.create().apply { url = data; name = "" }
        val videos = if (usesHosters()) {
            runCatching { source.getHosterList(episode) }.getOrNull()?.flatMap { h -> videosOf(h) }
                ?: source.getVideoList(episode)
        } else {
            source.getVideoList(episode)
        }
        var any = false
        for (raw in videos) {
            val video = if (raw.videoUrl.isBlank() || !raw.initialized) runCatching { http?.resolveVideo(raw) }.getOrNull() ?: raw else raw
            val url = video.videoUrl.ifBlank { runCatching { http?.getVideoUrl(video) }.getOrNull().orEmpty() }
            if (url.isBlank()) continue
            val type = if (url.contains(".m3u8", true)) ExtractorLinkType.M3U8 else if (url.contains(".mpd", true)) ExtractorLinkType.DASH else ExtractorLinkType.VIDEO
            val headers = (video.headers ?: http?.headers)?.toMap().orEmpty()
            callback(newExtractorLink(name, video.videoTitle.ifBlank { name }, url, type) {
                this.headers = headers
                referer = headers["Referer"] ?: headers["referer"] ?: ""
                quality = video.resolution ?: qualityOf(video.videoTitle)
            })
            video.subtitleTracks.forEach { subtitleCallback(SubtitleFile(it.lang, it.url)) }
            any = true
        }
        any
    }

    private suspend fun videosOf(h: Hoster): List<Video> =
        h.videoList ?: runCatching { source.getVideoList(h) }.getOrDefault(emptyList())

    /** Lib 16+ sources that override the hoster API; older ones only implement getVideoList(episode). */
    private fun usesHosters(): Boolean {
        var c: Class<*>? = source.javaClass
        while (c != null && c != AnimeHttpSource::class.java && c != Any::class.java) {
            if (c.declaredMethods.any { it.name == "getHosterList" || it.name == "hosterListParse" }) return true
            c = c.superclass
        }
        return false
    }

    private fun AnimesPage.toResults(): List<SearchResponse> = animes.map { a ->
        newAnimeSearchResponse(a.title, a.url, TvType.Anime) {
            posterUrl = a.thumbnail_url
            posterHeaders = this@AniyomiProvider.posterHeaders
        }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        fun qualityOf(title: String): Int = Regex("""(\d{3,4})[pP]""").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
