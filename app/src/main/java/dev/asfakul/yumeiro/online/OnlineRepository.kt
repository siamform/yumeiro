/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.util.Log
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TorrentLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DrmExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

// region UI models (app-owned; screens never touch extension objects directly)

enum class OnlineKind { MOVIE, SERIES, ANIME, LIVE, OTHER }

data class OnlineItem(
    val name: String,
    val url: String,
    val api: String,
    val posterUrl: String?,
    val posterHeaders: Map<String, String> = emptyMap(),
    val type: String? = null,
    val quality: String? = null,
    val score: Double? = null,
    /** Anime dub / sub status names from the extension ("Dubbed", "Subbed"). */
    val dub: Set<String> = emptySet(),
) {
    val key: String get() = "$api|$url"
    /** Poster tags (language, release source, resolution, kind) — see [ReleaseTags]. */
    val tags: List<ReleaseTags.Tag> by lazy(LazyThreadSafetyMode.PUBLICATION) { ReleaseTags.parse(name, quality, type, dub) }
}

data class OnlineRow(
    val api: String,
    val title: String,
    val items: List<OnlineItem>,
    val horizontal: Boolean = false,
    /** The provider's main-page request this row came from, for "See all" paging. */
    val pageName: String? = null,
    val pageData: String? = null,
    val hasNext: Boolean = false,
)

data class OnlinePage(val items: List<OnlineItem>, val hasNext: Boolean)

data class OnlineEpisode(
    val data: String,
    val name: String?,
    val season: Int?,
    val episode: Int?,
    val posterUrl: String?,
    val description: String?,
    val runtimeMin: Int?,
) {
    val label: String get() = buildString {
        if (episode != null) append("E").append(episode)
        val n = name?.takeIf { it.isNotBlank() }
        if (n != null) { if (isNotEmpty()) append(" · "); append(n) }
        if (isEmpty()) append("Episode")
    }
}

data class OnlineSeason(val number: Int?, val title: String)

data class OnlineDetail(
    val api: String,
    val url: String,
    val name: String,
    val kind: OnlineKind,
    val type: String,
    val posterUrl: String?,
    val backgroundUrl: String?,
    val posterHeaders: Map<String, String>,
    val plot: String?,
    val year: Int?,
    val tags: List<String>,
    val durationMin: Int?,
    val score: Double?,
    val contentRating: String?,
    /** Movie / live stream data for loadLinks. */
    val playData: String?,
    /** Episodes per audio version ("Sub", "Dub", "" when the source has no versions). */
    val episodes: Map<String, List<OnlineEpisode>>,
    val seasonNames: Map<Int, String>,
    val recommendations: List<OnlineItem>,
    /** Ids on tracking/metadata sites (when the extension provides them). */
    val malId: Int? = null,
    val aniListId: Int? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val unsupportedReason: String? = null,
) {
    val versions: List<String> get() = episodes.keys.toList()
    fun seasons(version: String): List<OnlineSeason> =
        episodes[version].orEmpty().map { it.season }.distinct().sortedBy { it ?: Int.MAX_VALUE }.map { s ->
            OnlineSeason(s, s?.let { seasonNames[it] ?: "Season $it" } ?: "Episodes")
        }
}

data class OnlineLink(
    val name: String,
    val source: String,
    val url: String,
    val referer: String?,
    val quality: Int,
    val isHls: Boolean,
    val isDash: Boolean,
    val headers: Map<String, String>,
    /** The extension's own link object (needed for its video interceptor). Not compared. */
    val raw: ExtractorLink? = null,
    /** Separate audio files that play together with this video (url → headers). */
    val audioTracks: List<Pair<String, Map<String, String>>> = emptyList(),
) {
    override fun equals(other: Any?): Boolean = other is OnlineLink && other.url == url && other.name == name && other.source == source &&
        other.referer == referer && other.quality == quality && other.isHls == isHls && other.isDash == isDash && other.headers == headers
    override fun hashCode(): Int = listOf(name, source, url, referer, quality, isHls, isDash, headers).hashCode()
    val qualityLabel: String get() = when (quality) {
        0 -> "Auto"; 400, -1 -> ""; 2160 -> "4K"; in 1..4320 -> "${quality}p"; else -> ""
    }
    val formatLabel: String get() = when { isHls -> "HLS"; isDash -> "DASH"; audioOnly -> "Audio only"; else -> container?.uppercase() ?: "Direct" }
    /** File extension of the link's path, lowercase ("mp4", "mp3", "m3u8"…). */
    val container: String? get() = url.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }
    /** Plain audio files (no picture). Never preferred over a video. */
    val audioOnly: Boolean get() = !isHls && !isDash && (container in AUDIO_EXT || Regex("""\((mp3|m4a|aac|flac|ogg|opus|wav|vbr mp3)\)""", RegexOption.IGNORE_CASE).containsMatchIn(name))
    /** Comparable quality: "unknown" ranks like a typical 720p source. */
    internal val rank: Int get() = when { quality in 1..4320 && quality != 400 -> quality; else -> 720 }
}

private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "oga", "opus", "wav", "wma", "ogg")

data class OnlineSubtitle(val language: String, val url: String, val headers: Map<String, String>)

/** Events while resolving links for one title / episode. */
sealed interface LinkEvent {
    data class Link(val link: OnlineLink) : LinkEvent
    data class Subtitle(val subtitle: OnlineSubtitle) : LinkEvent
    data class Skipped(val reason: String) : LinkEvent
    data class Done(val ok: Boolean, val error: String? = null) : LinkEvent
}

// endregion

/**
 * Calls into extension providers with timeouts, error isolation and caching, and maps their
 * results to the app's models. One slow or broken provider never blocks the others.
 */
object OnlineRepository {
    private const val TAG = "Online"
    private const val HOME_TIMEOUT = 25_000L
    private const val SEARCH_TIMEOUT = 25_000L
    private const val LOAD_TIMEOUT = 35_000L
    private const val LINKS_TIMEOUT = 75_000L
    private const val CACHE_MS = 15 * 60 * 1000L
    /** Same query, same Hue: reuse results for a while (typing back and forth, retries). */
    private const val SEARCH_CACHE_MS = 10 * 60 * 1000L
    /** Searches saved on the phone stay fresh for at most an hour (new uploads show up). */
    private const val SEARCH_DISK_MS = 60 * 60 * 1000L
    /** Rows requested per provider for the home page. */
    private const val ROWS_PER_PROVIDER = 4

    private val parallel = Semaphore(6)
    private const val MAX_ROW_ITEMS = 40
    private const val MAX_ROWS_PER_PROVIDER = 12
    private data class Timed<T>(val at: Long, val value: T)
    private val homeCache = ConcurrentHashMap<String, Timed<List<OnlineRow>>>()
    private val detailCache = ConcurrentHashMap<String, Timed<OnlineDetail>>()
    /** Search results the user tapped, so details can show a title + poster instantly. */
    val previews = ConcurrentHashMap<String, OnlineItem>()

    /** Forgets loaded pages (memory and the copy saved on the phone): settings or extensions changed. */
    fun clearCaches() { homeCache.clear(); detailCache.clear(); searchCache.clear(); runCatching { dev.asfakul.yumeiro.cache.DataCache.clearAll() } }

    /** Items a home row already showed (for an instant "See all"). */
    fun cachedRow(api: String, pageData: String): List<OnlineItem>? =
        homeCache[api]?.value?.filter { it.pageData == pageData }?.flatMap { it.items }?.distinctBy { it.url }?.takeIf { it.isNotEmpty() }

    // region home

    /** Home rows of [api] (its main page). Cached for a while unless [force]. */
    suspend fun home(api: MainAPI, force: Boolean = false): List<OnlineRow> = withContext(ExtensionGuard.dispatcher + ExtensionGuard.coroutineHandler) {
        homeCache[api.name]?.takeIf { !force && System.currentTimeMillis() - it.at < CACHE_MS }?.let { return@withContext it.value }
        // Round 12: saved on the phone too, so reopening Yumix doesn't ask every extension again.
        val diskKey = diskKey(api, "home")
        if (!force) dev.asfakul.yumeiro.cache.DataCache.read("home", diskKey)?.let(dev.asfakul.yumeiro.cache.OnlineCodec::rows)?.takeIf { it.isNotEmpty() }?.let { rows ->
            homeCache[api.name] = Timed(System.currentTimeMillis(), rows)
            return@withContext rows
        }
        if (!runCatching { api.hasMainPage }.getOrDefault(false)) return@withContext emptyList()
        val pages = runCatching { api.mainPage }.getOrDefault(emptyList()).take(ROWS_PER_PROVIDER)
        // supervisorScope: one broken row (or one extension bug) never cancels the others.
        val rows = supervisorScope {
            pages.map { page ->
                async {
                    parallel.withPermit {
                        runCatching {
                            withTimeout(HOME_TIMEOUT) { api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages)) }
                        }.onFailure { Log.w(TAG, "home ${api.name}/${page.name}", it) }.getOrNull()?.let { page to it }
                    }
                }
            }.mapNotNull { runCatching { it.await() }.getOrNull() }
        }.flatMap { (page, resp) ->
            // Extension objects can break Kotlin's null-safety (reflection / Java), so mapping is guarded too.
            runCatching {
                resp.items.orEmpty().map { r ->
                    OnlineRow(
                        api.name, r.name.orEmpty(), r.list.orEmpty().mapNotNull { runCatching { it.toItem(api.name) }.getOrNull() }.distinctBy { it.url }.take(MAX_ROW_ITEMS),
                        r.isHorizontalImages,
                        pageName = page.name, pageData = page.data, hasNext = resp.hasNext && resp.items.size == 1,
                    )
                }
            }.onFailure { Log.w(TAG, "home rows ${api.name}/${page.name}", it) }.getOrDefault(emptyList())
        }.filter { it.items.isNotEmpty() }.distinctBy { it.title + "|" + it.pageData }.take(MAX_ROWS_PER_PROVIDER)
        if (rows.isNotEmpty()) {
            homeCache[api.name] = Timed(System.currentTimeMillis(), rows)
            dev.asfakul.yumeiro.cache.DataCache.write("home", diskKey, dev.asfakul.yumeiro.cache.OnlineCodec.rows(rows))
        }
        rows
    }

    /** Disk cache key: the provider and its current domain (a domain change starts fresh). */
    private fun diskKey(api: MainAPI, what: String, extra: String = ""): String =
        "$what|${api.name}|${runCatching { api.mainUrl }.getOrDefault("")}|$extra"

    /** Rows saved on the phone for [api] regardless of age (shown while fresh ones load), or null. */
    fun savedHome(api: MainAPI): List<OnlineRow>? =
        dev.asfakul.yumeiro.cache.DataCache.read("home", diskKey(api, "home"), maxAgeMs = dev.asfakul.yumeiro.cache.DataCache.Settings.keepDays * 86_400_000L)
            ?.let(dev.asfakul.yumeiro.cache.OnlineCodec::rows)

    /** One page of a home row ("See all"), [page] starting at 1. */
    suspend fun page(api: MainAPI, pageName: String, pageData: String, page: Int): OnlinePage = withContext(Dispatchers.IO) {
        val req = api.mainPage.firstOrNull { it.name == pageName && it.data == pageData } ?: api.mainPage.firstOrNull { it.data == pageData }
        val resp = parallel.withPermit {
            withTimeout(HOME_TIMEOUT) { api.getMainPage(page, MainPageRequest(pageName, pageData, req?.horizontalImages ?: false)) }
        } ?: return@withContext OnlinePage(emptyList(), false)
        val items = resp.items.flatMap { r -> r.list.mapNotNull { it.toItem(api.name) } }.distinctBy { it.url }
        OnlinePage(items, resp.hasNext && items.isNotEmpty())
    }

    // endregion

    // region search

    data class SearchUpdate(val api: String, val items: List<OnlineItem>, val error: String? = null)

    /** Searches every provider in parallel; emits each provider's results as soon as they arrive. */
    fun search(query: String, apis: List<MainAPI>): Flow<SearchUpdate> = channelFlow {
        val q = query.trim()
        if (q.isEmpty()) return@channelFlow
        for (api in apis) {
            launch {
                val update = parallel.withPermit {
                    runCatching {
                        withContext(ProviderBase(runCatching { api.mainUrl }.getOrDefault(""))) { withTimeout(SEARCH_TIMEOUT) { searchOne(api, q) } }
                    }.fold(
                        { SearchUpdate(api.name, it) },
                        { if (it is kotlinx.coroutines.CancellationException && it !is kotlinx.coroutines.TimeoutCancellationException) throw it
                          SearchUpdate(api.name, emptyList(), OnlineErrors.friendly(it)) },
                    )
                }
                send(update)
            }
        }
    }.flowOn(Dispatchers.IO)

    private val searchCache = ConcurrentHashMap<String, Timed<List<OnlineItem>>>()

    private suspend fun searchOne(api: MainAPI, q: String): List<OnlineItem> {
        val key = "${api.name}|${q.lowercase()}"
        searchCache[key]?.takeIf { System.currentTimeMillis() - it.at < SEARCH_CACHE_MS }?.let { return it.value }
        val dk = diskKey(api, "search", q.lowercase())
        dev.asfakul.yumeiro.cache.DataCache.read("search", dk, maxAgeMs = minOf(dev.asfakul.yumeiro.cache.DataCache.Settings.freshMs, SEARCH_DISK_MS))
            ?.let(dev.asfakul.yumeiro.cache.OnlineCodec::items)?.takeIf { it.isNotEmpty() }?.let { saved ->
                searchCache[key] = Timed(System.currentTimeMillis(), saved)
                return saved
            }
        val results = try {
            api.search(q, 1)?.items
        } catch (e: NotImplementedError) { null } ?: api.search(q).orEmpty()
        val items = results.mapNotNull { runCatching { it.toItem(api.name) }.getOrNull() }.distinctBy { it.url }
        if (items.isNotEmpty()) {
            if (searchCache.size > 64) searchCache.clear()
            searchCache[key] = Timed(System.currentTimeMillis(), items)
            dev.asfakul.yumeiro.cache.DataCache.write("search", dk, dev.asfakul.yumeiro.cache.OnlineCodec.items(items))
        }
        return items
    }

    // endregion

    // region details

    suspend fun load(api: MainAPI, url: String, force: Boolean = false): OnlineDetail = withContext(Dispatchers.IO) {
        val key = "${api.name}|$url"
        detailCache[key]?.takeIf { !force && System.currentTimeMillis() - it.at < CACHE_MS }?.let { return@withContext it.value }
        val dk = diskKey(api, "detail", url)
        if (!force) dev.asfakul.yumeiro.cache.DataCache.read("detail", dk)?.let(dev.asfakul.yumeiro.cache.OnlineCodec::detail)?.let { saved ->
            detailCache[key] = Timed(System.currentTimeMillis(), saved)
            return@withContext saved
        }
        val resp = withContext(ProviderBase(runCatching { api.mainUrl }.getOrDefault(""))) { withTimeout(LOAD_TIMEOUT) { api.load(url) } } ?: throw IllegalStateException("This title isn't available anymore")
        val detail = map(api, resp)
        detailCache[key] = Timed(System.currentTimeMillis(), detail)
        if (detail.unsupportedReason == null) dev.asfakul.yumeiro.cache.DataCache.write("detail", dk, dev.asfakul.yumeiro.cache.OnlineCodec.detail(detail))
        detail
    }

    internal fun map(api: MainAPI, r: LoadResponse): OnlineDetail {
        val seasonNames = (r as? EpisodeResponse)?.seasonNames.orEmpty()
            .mapNotNull { s -> s.name?.takeIf { it.isNotBlank() }?.let { s.season to it } }.toMap()
        var playData: String? = null
        var unsupported: String? = null
        val episodes: Map<String, List<OnlineEpisode>> = when (r) {
            is MovieLoadResponse -> { playData = r.dataUrl; emptyMap() }
            is LiveStreamLoadResponse -> { playData = r.dataUrl; emptyMap() }
            is TvSeriesLoadResponse -> mapOf("" to r.episodes.map { it.toEpisode() })
            is AnimeLoadResponse -> r.episodes.entries
                .filter { it.value.isNotEmpty() }
                .sortedBy { when (it.key) { DubStatus.Subbed -> 0; DubStatus.Dubbed -> 1; else -> 2 } }
                .associate { (status, list) -> versionLabel(status) to list.map { it.toEpisode() } }
            is TorrentLoadResponse -> { unsupported = "Torrent sources aren't supported"; emptyMap() }
            else -> { unsupported = "This kind of title isn't supported yet"; emptyMap() }
        }
        val kind = when {
            r is LiveStreamLoadResponse || r.type == TvType.Live -> OnlineKind.LIVE
            r is AnimeLoadResponse -> OnlineKind.ANIME
            r is TvSeriesLoadResponse -> OnlineKind.SERIES
            r is MovieLoadResponse -> OnlineKind.MOVIE
            else -> OnlineKind.OTHER
        }
        return OnlineDetail(
            api = api.name, url = r.url, name = r.name, kind = kind, type = typeLabel(r.type),
            posterUrl = r.posterUrl, backgroundUrl = r.backgroundPosterUrl, posterHeaders = r.posterHeaders.orEmpty(),
            plot = r.plot?.let { cleanHtml(it) }, year = r.year, tags = r.tags.orEmpty().filter { it.isNotBlank() }.distinct().take(12),
            durationMin = r.duration?.takeIf { it > 0 }, score = runCatching { r.score?.toDouble(10) }.getOrNull(),
            contentRating = r.contentRating, playData = playData,
            episodes = episodes.mapValues { (_, l) -> l.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: Int.MAX_VALUE })) },
            seasonNames = seasonNames,
            recommendations = r.recommendations.orEmpty().mapNotNull { it.toItem(api.name) }.distinctBy { it.url }.take(24),
            unsupportedReason = unsupported,
            malId = runCatching { with(LoadResponse) { r.getMalId() }?.toIntOrNull() }.getOrNull(),
            aniListId = runCatching { with(LoadResponse) { r.getAniListId() }?.toIntOrNull() }.getOrNull(),
            imdbId = runCatching { with(LoadResponse) { r.getImdbId() } }.getOrNull()?.takeIf { it.startsWith("tt") },
            tmdbId = runCatching { with(LoadResponse) { r.getTMDbId() }?.toIntOrNull() }.getOrNull(),
        )
    }

    private fun versionLabel(s: DubStatus) = when (s) { DubStatus.Subbed -> "Sub"; DubStatus.Dubbed -> "Dub"; else -> "Original" }

    // endregion

    // region links

    /** Resolves playable links (and subtitles) for [data]; streams them as they are found. */
    fun links(api: MainAPI, data: String): Flow<LinkEvent> = callbackFlow {
        val base = runCatching { api.mainUrl }.getOrNull().orEmpty()
        val job = launch(Dispatchers.IO + ProviderBase(base)) {
            val result = runCatching {
                withTimeout(LINKS_TIMEOUT) {
                    api.loadLinks(
                        data, false,
                        { sub -> UrlFix.normalize(sub.url, base).takeIf { UrlFix.isHttp(it) }?.let { trySend(LinkEvent.Subtitle(OnlineSubtitle(sub.lang.ifBlank { "Unknown" }, it, sub.headers.orEmpty()))) } },
                        { link -> trySend(mapLink(link, base)) },
                    )
                }
            }
            val event = result.fold(
                { ok -> LinkEvent.Done(ok) },
                { e -> if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                    Log.w(TAG, "links ${api.name}", e); LinkEvent.Done(false, OnlineErrors.friendly(e, "Couldn't get links")) },
            )
            trySend(event)
            channel.close()
        }
        awaitClose { job.cancel() }
    }

    internal fun mapLink(link: ExtractorLink, base: String? = null): LinkEvent {
        val url = if (link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET) link.url
            else UrlFix.normalize(link.url, link.referer.takeIf { UrlFix.isHttp(it) } ?: base)
        return mapLinkFixed(link, url)
    }

    private fun mapLinkFixed(link: ExtractorLink, url: String): LinkEvent = when {
        // Protected streams aren't played: this app doesn't bypass DRM.
        link is DrmExtractorLink -> LinkEvent.Skipped("DRM-protected source skipped")
        link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET -> LinkEvent.Skipped("Torrent source skipped")
        !UrlFix.isHttp(url) -> LinkEvent.Skipped("Unsupported link")
        else -> LinkEvent.Link(
            OnlineLink(
                name = link.name.ifBlank { link.source }, source = link.source, url = url,
                referer = link.referer.takeIf { it.isNotBlank() }, quality = link.quality,
                isHls = link.type == ExtractorLinkType.M3U8, isDash = link.type == ExtractorLinkType.DASH,
                headers = link.headers,
                raw = link,
                audioTracks = runCatching { link.audioTracks }.getOrNull().orEmpty()
                    .map { UrlFix.normalize(it.url, url) to it.headers.orEmpty() }.filter { UrlFix.isHttp(it.first) },
            )
        )
    }

    /**
     * Best first: real video before audio-only files, then quality (CloudStream's "unknown" is 400
     * or 0 — ranked like a typical 720p), adaptive formats, MP4 before other containers, then name.
     */
    fun sortLinks(links: List<OnlineLink>, preferAudio: String? = null, version: String = ""): List<OnlineLink> {
        // Round 13: the audio key is worked out once per link, not inside the comparator (which
        // ran the language regexes thousands of times per re-sort).
        val unique = links.distinctBy { it.url }
        val audioMiss = if (preferAudio == null) null else unique.associate { it.url to (AudioLanguages.filterKey(AudioLanguages.classify(it.name + " " + it.source, version)) != preferAudio) }
        return unique.sortedWith(
            compareBy<OnlineLink> { it.audioOnly }
                // Round 9: the user's remembered audio language plays first when a source has it.
                .thenBy { if (audioMiss?.get(it.url) == true) 1 else 0 }
                .thenByDescending { it.rank }
                .thenByDescending { it.isHls || it.isDash }
                .thenBy { if (it.container == "mp4" || it.container == "m3u8" || it.container == "mpd") 0 else 1 }
                .thenBy { it.name.lowercase() }
        )
    }

    // endregion

    // region mapping helpers

    /**
     * [owner] is the extension that returned this result. Extensions often copy each other's code
     * and leave a wrong `apiName`, so trusting it attributed results to another Hue (opening them
     * with the wrong extension, and colliding list keys that could crash the results grid).
     */
    internal fun SearchResponse.toItem(owner: String = apiName): OnlineItem? {
        val n = name.trim().ifBlank { return null }
        if (type == TvType.NSFW && !OnlineSettings.adult.value) return null
        return OnlineItem(
            name = n, url = url, api = owner.ifBlank { apiName }, posterUrl = posterUrl?.takeIf { it.isNotBlank() },
            posterHeaders = posterHeaders.orEmpty(), type = type?.let(::typeLabel),
            quality = quality?.name, score = runCatching { score?.toDouble(10) }.getOrNull(),
            dub = (this as? com.lagradost.cloudstream3.AnimeSearchResponse)?.dubStatus.orEmpty()
                .filter { it != DubStatus.None }.mapTo(HashSet()) { it.name },
        )
    }

    private fun com.lagradost.cloudstream3.Episode.toEpisode() = OnlineEpisode(
        data = data, name = name?.trim(), season = season, episode = episode, posterUrl = posterUrl,
        description = description?.let { cleanHtml(it) }, runtimeMin = runTime,
    )

    fun typeLabel(t: TvType?): String = when (t) {
        TvType.Movie -> "Movie"; TvType.AnimeMovie -> "Anime movie"; TvType.TvSeries -> "TV series"; TvType.Cartoon -> "Cartoon"
        TvType.Anime -> "Anime"; TvType.OVA -> "OVA"; TvType.Documentary -> "Documentary"; TvType.AsianDrama -> "Asian drama"
        TvType.Live -> "Live"; TvType.Music -> "Music"; TvType.AudioBook -> "Audiobook"; TvType.Audio -> "Audio"
        TvType.Podcast -> "Podcast"; TvType.Torrent -> "Torrent"; null -> "Video"; else -> t.name
    }

    fun typeLabel(name: String): String =
        TvType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { typeLabel(it) } ?: name

    private fun cleanHtml(s: String): String =
        s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()

    // endregion
}
