/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.drama

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
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The built-in short-drama source. Off until the user turns it on (Extension market → Drama,
 * or Yumix → Sources); then it shows up everywhere a video source does: Yumix, search,
 * details, the player, downloads, history and bookmarks.
 */
object YumeiroDrama {
    const val NAME = "Yumeiro Drama"
    private const val PREFS = "yumeiro_drama"
    private const val KEY_ON = "enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Catalog adapters, in priority order. */
    internal val catalogs: List<DramaCatalog> by lazy { listOf(OnShortCatalog()) }

    fun start(context: Context) {
        val on = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)
        _enabled.value = on
        publish(on)
    }

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        _enabled.value = on
        publish(on)
    }

    private fun publish(on: Boolean) {
        if (on) SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.YUMEIRO, listOf(YumeiroDramaProvider(catalogs)))
        else SourceHub.remove(BRIDGE)
    }

    private const val BRIDGE = "drama"

    /** Words that mark a short-drama source or title (market shelf and category). */
    val SHORT_DRAMA = Regex(
        "(?i)\\b(dramabox|drama ?box|reelshort|reel ?short|shortmax|short ?max|netshort|goodshort|flextv|flex ?tv|dramawave|drama ?wave|" +
            "freereels|free ?reels|flickreels|moboreels|topshort|stardust ?tv|shorttv|short ?tv|minidrama|mini ?drama|short ?drama[s]?|micro ?drama|vertical ?drama|dracin|onshort|melolo|dramabite|kalostv|sereal\\+?)\\b",
    )

    fun isShortDrama(vararg text: String?): Boolean = text.any { !it.isNullOrBlank() && SHORT_DRAMA.containsMatchIn(it) }
}

/** CloudStream-compatible face of [YumeiroDrama]; ids are `drama:<catalog>|<series url>[|<episode>]`. */
class YumeiroDramaProvider(private val catalogs: List<DramaCatalog>) : MainAPI() {
    override var name = YumeiroDrama.NAME
    override var mainUrl = "https://yumeiro.click/drama"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries)

    init { sourcePlugin = "yumeiro-builtin:drama" }

    override val mainPage: List<MainPageData> = catalogs.flatMap { c -> c.platforms.map { p -> MainPageData(p.name, "${c.id}|${p.slug}") } }

    private fun catalog(id: String) = catalogs.firstOrNull { it.id == id } ?: error("Unknown drama catalog $id")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (cid, slug) = request.data.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val c = catalog(cid)
        val platform = c.platforms.first { it.slug == slug }
        val shows = c.shelf(platform, page)
        return newHomePageResponse(request.name, shows.map { it.toResult(c) }, shows.size >= 12)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (page > 1 || query.isBlank()) return newSearchResponseList(emptyList(), false)
        val results = coroutineScope {
            catalogs.map { c -> async { withTimeoutOrNull(20_000) { runCatching { c.search(query).map { it.toResult(c) } }.getOrNull() }.orEmpty() } }.awaitAll()
        }.flatten().distinctBy { it.url }
        return newSearchResponseList(results, false)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse {
        val (cid, seriesUrl) = parseId(url)
        val c = catalog(cid)
        val s = c.series(seriesUrl)
        val eps = (1..s.episodes).map { n ->
            newEpisode("drama:${c.id}|${s.url}|$n") { name = "Episode $n"; episode = n; season = 1 }
        }
        return newTvSeriesLoadResponse(s.title, url, TvType.AsianDrama, eps) {
            posterUrl = s.cover
            plot = s.plot
            tags = listOfNotNull(s.platform, "Short drama") + s.hints["tags"].orEmpty().split('|').filter { it.isNotBlank() }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parts = data.removePrefix("drama:").split('|')
        if (parts.size < 3) return false
        val c = catalog(parts[0])
        val n = parts.last().toIntOrNull() ?: return false
        val seriesUrl = parts.subList(1, parts.size - 1).joinToString("|")
        val s = c.series(seriesUrl)
        val streams = c.streams(s, n)
        val audio = s.language?.let { languageLabel(it) }?.let { "$it audio" } ?: "Original audio"
        streams.sources.entries.sortedByDescending { it.key.toIntOrNull() ?: 0 }.forEach { (q, u) ->
            val type = if (u.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            val label = listOfNotNull(c.label, s.platform, q.takeIf { it.isNotBlank() }?.let { "${it}p" }, audio).joinToString(" · ")
            callback(newExtractorLink(name, label, u, type) {
                referer = streams.referer.orEmpty()
                quality = q.toIntOrNull() ?: 0
            })
        }
        streams.subtitles.forEach { sub ->
            subtitleCallback(SubtitleFile(sub.label ?: sub.language?.let { languageLabel(it) } ?: "Subtitles", sub.url))
        }
        return streams.sources.isNotEmpty()
    }

    private fun DramaShow.toResult(c: DramaCatalog): SearchResponse =
        newTvSeriesSearchResponse(title, "drama:${c.id}|$url", TvType.AsianDrama) {
            posterUrl = cover
        }

    companion object {
        fun parseId(url: String): Pair<String, String> {
            val body = url.removePrefix("drama:")
            val cid = body.substringBefore('|')
            return cid to body.substringAfter('|')
        }

        fun languageLabel(code: String): String =
            java.util.Locale.forLanguageTag(code.substringBefore('_')).getDisplayLanguage(java.util.Locale.ENGLISH).ifBlank { code }
    }
}
