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
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap

// Precompiled once (R15 audit): these ran a regex compile on every call.
private val RX_ONLINEREPOSITORY_HOT_1 = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val RX_ONLINEREPOSITORY_HOT_2 = Regex("<[^>]+>")

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
    /** DEDUP-1: the title's identity across extensions, worked out once per card (the feed re-checks on each update). */
    internal val titleKey: TitleDedupe.Key by lazy(LazyThreadSafetyMode.PUBLICATION) { TitleDedupe.compute(name, type) }
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
    /** META-1: cast and trailer addresses the extension gave (CloudStream `actors` / `trailers`). */
    val cast: List<OnlineCast> = emptyList(),
    val trailers: List<String> = emptyList(),
) {
    val versions: List<String> get() = episodes.keys.toList()
    fun seasons(version: String): List<OnlineSeason> =
        episodes[version].orEmpty().map { it.season }.distinct().sortedBy { it ?: Int.MAX_VALUE }.map { s ->
            OnlineSeason(s, s?.let { seasonNames[it] ?: "Season $it" } ?: "Episodes")
        }
}

/** META-1: one cast member: name, character (or role) and photo when the extension has them. */
data class OnlineCast(val name: String, val role: String? = null, val image: String? = null)

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
    /** B17: the other extension this link came from (null = the title's own extension). Not compared. */
    val via: String? = null,
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
    val audioOnly: Boolean get() = !isHls && !isDash && (container in AUDIO_EXT || AUDIO_NAME_RX.containsMatchIn(name))
    /** Comparable quality: "unknown" ranks like a typical 720p source. */
    internal val rank: Int get() = when { quality in 1..4320 && quality != 400 -> quality; else -> 720 }
}

// R15: was compiled on every `audioOnly` read (sorting, filters and list items called it per link).
private val AUDIO_NAME_RX = Regex("""\((mp3|m4a|aac|flac|ogg|opus|wav|vbr mp3)\)""", RegexOption.IGNORE_CASE)
private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "oga", "opus", "wav", "wma", "ogg")

data class OnlineSubtitle(
    val language: String, val url: String, val headers: Map<String, String>,
    /** B20 S6: made by Yumeiro (a translation); turned on when added and never offered as "more from the source". */
    val auto: Boolean = false,
)

/** Events while resolving links for one title / episode. */
sealed interface LinkEvent {
    data class Link(val link: OnlineLink) : LinkEvent
    data class Subtitle(val subtitle: OnlineSubtitle) : LinkEvent
    /** [url]: set for torrents, which the player skips but a forced download can fetch through TorrServer. */
    data class Skipped(val reason: String, val url: String? = null) : LinkEvent
    data class Done(val ok: Boolean, val error: String? = null) : LinkEvent
}

// endregion

/**
 * Calls into extension providers with timeouts, error isolation and caching, and maps their
 * results to the app's models. One slow or broken provider never blocks the others.
 */
object OnlineRepository {
    private const val TAG = "Online"
    private const val HOME_TIMEOUT_MS = 25_000L
    /** One home row call's limit; also the budget for asking more batches (FE-2b). Changed only by tests. */
    @androidx.annotation.VisibleForTesting @Volatile internal var homeTimeoutMs = HOME_TIMEOUT_MS
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
    /** At most this many batches of [ROWS_PER_PROVIDER] pages while earlier ones came back empty. */
    private const val MAX_PAGE_BATCHES = 3

    /** Round 2: extension calls at once follow the phone's heat (6 cool, 4 warm, 2 hot). */
    private object parallel { suspend fun <T> withPermit(block: suspend () -> T): T = dev.asfakul.yumeiro.player.HeatGuard.limited(block = block) }
    private const val MAX_ROW_ITEMS = 40
    private const val MAX_ROWS_PER_PROVIDER = 12
    private data class Timed<T>(val at: Long, val value: T)
    private val homeCache = ConcurrentHashMap<String, Timed<List<OnlineRow>>>()
    private val detailCache = ConcurrentHashMap<String, Timed<OnlineDetail>>()
    /** Search results the user tapped, so details can show a title + poster instantly. */
    val previews = ConcurrentHashMap<String, OnlineItem>()

    /** B9: searches kept for a quick reopen (memory and the phone's copies) go with the search history. */
    fun clearSearchCache() { searchCache.clear(); searchFails.clear(); runCatching { dev.asfakul.yumeiro.cache.DataCache.dropNamespace("search") } }

    /** Forgets loaded pages (memory and the copy saved on the phone): settings or extensions changed. */
    fun clearCaches() { homeCache.clear(); detailCache.clear(); searchCache.clear(); runCatching { dev.asfakul.yumeiro.cache.DataCache.invalidateAll() } }

    /**
     * SPD-2 (ADR-038): an extension was installed or updated in the background. Before, this used [clearCaches],
     * which deleted every saved page on the phone, so the next Yumix / Home visit asked every extension again
     * (slow feed after almost every launch). Now only the memory copies go; saved pages stay and are shown at
     * once, then refreshed in the background (stale-while-revalidate) because they predate the change.
     */
    fun refreshAfterExtensionChange() {
        staleBefore = System.currentTimeMillis()
        homeCache.clear(); detailCache.clear(); searchCache.clear()
    }
    @Volatile private var staleBefore = 0L

    /** Items a home row already showed (for an instant "See all"). */
    fun cachedRow(api: String, pageData: String): List<OnlineItem>? =
        homeCache[api]?.value?.filter { it.pageData == pageData }?.flatMap { it.items }?.distinctBy { it.url }?.takeIf { it.isNotEmpty() }

    // region home

    /** Home rows of [api] (its main page). Cached for a while unless [force]. */
    suspend fun home(api: MainAPI, force: Boolean = false, onRows: ((List<OnlineRow>) -> Unit)? = null): List<OnlineRow> = withContext(ExtensionGuard.dispatcher + ExtensionGuard.coroutineHandler) {
        if (!force) cachedHomeNow(api)?.let { return@withContext it }
        val diskKey = diskKey(api, "home")
        if (!runCatching { api.hasMainPage }.getOrDefault(false)) return@withContext emptyList()
        // VERIFY-2: a site whose check pass ran out is passed again in the background while the home loads.
        warmCheck(api)
        // E4: every live fetch is timed; failures count towards quarantine (ExtensionSpeed).
        // A fetch cancelled by a newer one (Retry, leaving the screen) is not the extension's fault.
        val started = System.currentTimeMillis()
        val live = try {
            homeLive(api, diskKey, onRows)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.E4_SPEED_ORDER)) ExtensionSpeed.record(api.name, System.currentTimeMillis() - started, ok = false)
            throw t
        }
        if (dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.E4_SPEED_ORDER)) ExtensionSpeed.record(api.name, System.currentTimeMillis() - started, ok = live.isNotEmpty())
        live
    }

    /**
     * SPEED-1: the home rows of [api] from memory or the phone, without asking the extension; null when there are
     * none. Runs on Dispatchers.IO, not the extension pool, so a saved page never waits behind live fetches (the
     * Yumix gate of three and the 8-thread extension pool used to queue cached pages behind slow sites).
     */
    suspend fun cachedHome(api: MainAPI): List<OnlineRow>? = withContext(Dispatchers.IO) { cachedHomeNow(api) }

    /**
     * FE-1 (docs/FEED-ENGINE-2.1.md, FE-RC2): what a feed shows at once for [api]. The fresh copy when there is one,
     * else the last saved page of any age up to keepDays, marked stale so the caller refreshes it live behind it
     * ([takeStaleHome]). Before, a saved page older than 6 h counted as nothing, so after a night away or an app
     * update every extension went back through the live queue of three (minutes of skeletons with pages on disk).
     * Disk read: Dispatchers.IO. The stale copy is not put in the memory cache, so [home] still fetches live.
     */
    suspend fun feedHome(api: MainAPI): List<OnlineRow>? = withContext(Dispatchers.IO) {
        cachedHomeNow(api) ?: savedHome(api)?.filter { it.items.isNotEmpty() }?.takeIf { it.isNotEmpty() }?.also { diskServed.add(api.name) }
    }

    private fun cachedHomeNow(api: MainAPI): List<OnlineRow>? {
        homeCache[api.name]?.takeIf { System.currentTimeMillis() - it.at < CACHE_MS }?.let { return it.value }
        // Round 12: saved on the phone too, so reopening Yumix doesn't ask every extension again.
        val diskKey = diskKey(api, "home")
        val rows = dev.asfakul.yumeiro.cache.DataCache.read("home", diskKey)?.let(dev.asfakul.yumeiro.cache.OnlineCodec::rows)?.takeIf { it.isNotEmpty() } ?: return null
        homeCache[api.name] = Timed(System.currentTimeMillis(), rows)
        // R15: a saved page is shown at once, but it may be hours old. The caller refreshes it
        // in the background (stale-while-revalidate) instead of leaving the old copy up.
        val age = dev.asfakul.yumeiro.cache.DataCache.ageOf("home", diskKey) ?: 0L
        if (age > CACHE_MS || System.currentTimeMillis() - age < staleBefore) diskServed.add(api.name)
        return rows
    }

    /**
     * [onRows] (FE-3, docs/FEED-ENGINE-2.1.md): called with the rows so far each time one row answers, so a screen can
     * show the extension at its first row. Before, the whole batch waited for its slowest row (up to 25 s) even
     * when the other rows were back in under a second. Called on the extension pool; may run concurrently.
     */
    private suspend fun homeLive(api: MainAPI, diskKey: String, onRows: ((List<OnlineRow>) -> Unit)? = null): List<OnlineRow> {
        val allPages = runCatching { api.mainPage }.getOrDefault(emptyList())
        // R15: the first ROWS_PER_PROVIDER pages are asked; when they come back empty or broken
        // (a studio that is down, a row that timed out) the next pages are asked too, up to
        // MAX_PAGE_BATCHES batches. Before, a provider whose first four rows were empty (Yumeiro
        // Drama with four busy studios) vanished from the feed although it had other rows.
        val rows = mutableListOf<OnlineRow>()
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        var asked = 0
        var batch = 0
        // FE-2b (docs/FEED-ENGINE-2.1.md FE-RC1): more batches only while this home is still within one row timeout.
        // Before, a dead site (every row timing out) was asked three batches in a row, 25 s each, and held one of
        // Yumix's three slots for 75 s; quickly failing rows (a busy studio answering an error) still get batch 2 and 3.
        val started = System.currentTimeMillis()
        while (batch < MAX_PAGE_BATCHES && rows.size < ROWS_PER_PROVIDER && (batch == 0 || System.currentTimeMillis() - started < homeTimeoutMs)) {
            val pages = allPages.drop(batch * ROWS_PER_PROVIDER).take(ROWS_PER_PROVIDER)
            if (pages.isEmpty()) break
            asked += pages.size
            val before = rows.toList()
            rows += homeBatch(api, pages, failures, onRows?.let { cb -> { soFar: List<OnlineRow> -> cb(shownRows(before + soFar)) } })
            batch++
        }
        val result = shownRows(rows)
        // G7-L1: when every page threw (QuickIPTV's sites sending a web page instead of a playlist),
        // the section said "Nothing came back from this extension" and Source Health "Home page is
        // empty". The first page error is passed on so both can show the reason.
        if (result.isEmpty() && asked > 0 && failures.size >= asked) {
            val first = failures.first()
            // A timeout is a CancellationException; callers must not mistake it for their own cancellation.
            throw if (first is kotlinx.coroutines.CancellationException) java.io.InterruptedIOException("Home page timed out").apply { initCause(first) } else first
        }
        if (result.isNotEmpty()) {
            homeCache[api.name] = Timed(System.currentTimeMillis(), result)
            dev.asfakul.yumeiro.cache.DataCache.write("home", diskKey, dev.asfakul.yumeiro.cache.OnlineCodec.rows(result))
        }
        return result
    }

    private fun shownRows(rows: List<OnlineRow>): List<OnlineRow> =
        rows.filter { it.items.isNotEmpty() }.distinctBy { it.title + "|" + it.pageData }.take(MAX_ROWS_PER_PROVIDER)

    private suspend fun homeBatch(
        api: MainAPI,
        pages: List<com.lagradost.cloudstream3.MainPageData>,
        failures: MutableList<Throwable>? = null,
        onRows: ((List<OnlineRow>) -> Unit)? = null,
    ): List<OnlineRow> {
        // FE-3: rows answered so far, by page position, handed to [onRows] as each one lands (page order kept).
        val landed = java.util.TreeMap<Int, List<OnlineRow>>()
        // supervisorScope: one broken row (or one extension bug) never cancels the others.
        return supervisorScope {
            pages.mapIndexed { i, page ->
                async {
                    parallel.withPermit {
                        runCatching {
                            ExtensionCall.bounded(homeTimeoutMs) { api.getMainPage(1, MainPageRequest(page.name, page.data, page.horizontalImages)) }
                        }.onFailure { Log.w(TAG, "home ${api.name}/${page.name}", it); if (it !is kotlinx.coroutines.CancellationException || it is kotlinx.coroutines.TimeoutCancellationException) failures?.add(it) }.getOrNull()?.let { pageRows(api, page, it) }
                    }?.also { rows ->
                        if (onRows != null && rows.isNotEmpty()) {
                            // Snapshot and hand-over under one lock: two rows landing together can't hand an older,
                            // shorter list after the newer one (CI 45902671041 saw [Fast, Slow 1, Slow 3] last).
                            synchronized(landed) { landed[i] = rows; runCatching { onRows(landed.values.flatten()) } }
                        }
                    }
                }
            }.mapNotNull { runCatching { it.await() }.getOrNull() }
        }.flatten()
    }

    private fun pageRows(api: MainAPI, page: com.lagradost.cloudstream3.MainPageData, resp: com.lagradost.cloudstream3.HomePageResponse): List<OnlineRow> =
        run {
            // Extension objects can break Kotlin's null-safety (reflection / Java), so mapping is guarded too.
            runCatching {
                resp.items.orEmpty().map { r ->
                    OnlineRow(
                        api.name, r.name.orEmpty(), r.list.orEmpty().mapNotNull { runCatching { it.toItem(api.name, feed = true) }.getOrNull() }.distinctBy { it.url }.take(MAX_ROW_ITEMS),
                        r.isHorizontalImages,
                        pageName = page.name, pageData = page.data, hasNext = resp.hasNext && resp.items.size == 1,
                    )
                }
            }.onFailure { Log.w(TAG, "home rows ${api.name}/${page.name}", it) }.getOrDefault(emptyList())
        }.filter { it.items.isNotEmpty() }

    /** FE-5: age of [api]'s saved home page in ms, or null when none is saved. Disk metadata: call off main. */
    fun homeAgeMs(api: MainAPI): Long? = dev.asfakul.yumeiro.cache.DataCache.ageOf("home", diskKey(api, "home"))

    private val diskServed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** True once after [home] answered [api] from an old saved copy (the caller should refresh it). */
    fun takeStaleHome(api: String): Boolean = diskServed.remove(api)

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
        // FE-2c: the next page under the finger goes before background feed rows.
        val resp = withContext(PriorityGate.Lane(PriorityGate.VISIBLE)) { parallel.withPermit {
            ExtensionCall.bounded(homeTimeoutMs) { api.getMainPage(page, MainPageRequest(pageName, pageData, req?.horizontalImages ?: false)) }
        } } ?: return@withContext OnlinePage(emptyList(), false)
        val items = resp.items.flatMap { r -> r.list.mapNotNull { it.toItem(api.name, feed = true) } }.distinctBy { it.url }
        OnlinePage(items, resp.hasNext && items.isNotEmpty())
    }

    // endregion

    // region search

    data class SearchUpdate(val api: String, val items: List<OnlineItem>, val error: String? = null)

    /** Searches every provider in parallel; emits each provider's results as soon as they arrive. */
    fun search(query: String, apis: List<MainAPI>, retry: Boolean = false): Flow<SearchUpdate> = channelFlow {
        val q = query.trim()
        if (q.isEmpty()) return@channelFlow
        val failMemory = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FE_S3_FAIL_MEMORY)
        val adaptive = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FE_S4_ADAPTIVE_TIMEOUT)
        val speed = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.E4_SPEED_ORDER)
        // SPEED-2: fastest measured searchers take the permits first; quarantined ones go last.
        val ordered = if (speed) ExtensionSpeed.order(apis, { it.name }, null, { ExtensionSpeed.stat(searchKey(it)) }, System.currentTimeMillis()) else apis
        for (api in ordered) {
            launch {
                // SPEED-2: a saved answer is sent at once, without waiting for a permit behind slow live searches.
                cachedSearch(api, q)?.let { send(SearchUpdate(api.name, it)); return@launch }
                // FE-S3 (lowram run 44884969625: a repeated search waited 15 s for a site that had just timed out): a site
                // that failed this query in the last 2 minutes answers with that failure at once; Retry asks again.
                val failKey = "${api.name}|${q.lowercase()}"
                if (failMemory && !retry) searchFails[failKey]?.takeIf { System.currentTimeMillis() - it.at < SEARCH_FAIL_MS }
                    ?.let { send(SearchUpdate(api.name, emptyList(), it.value)); return@launch }
                val update = parallel.withPermit {
                    val started = System.currentTimeMillis()
                    runCatching {
                        // FE-S4: measured sites get a timeout from their own speed; Retry and unknown sites the full 25 s.
                        val limit = if (speed && adaptive) ExtensionSpeed.searchTimeoutMs(ExtensionSpeed.stat(searchKey(api.name)), retry, started, SEARCH_TIMEOUT) else SEARCH_TIMEOUT
                        withContext(ProviderBase(runCatching { api.mainUrl }.getOrDefault(""))) { ExtensionCall.bounded(limit) { searchOne(api, q) } }
                    }.fold(
                        { if (speed) ExtensionSpeed.record(searchKey(api.name), System.currentTimeMillis() - started, ok = true)
                          searchFails.remove(failKey)
                          SearchUpdate(api.name, it) },
                        { if (it is kotlinx.coroutines.CancellationException && it !is kotlinx.coroutines.TimeoutCancellationException) throw it
                          if (speed) ExtensionSpeed.record(searchKey(api.name), System.currentTimeMillis() - started, ok = false)
                          val msg = OnlineErrors.friendly(it)
                          if (failMemory) { if (searchFails.size > 256) searchFails.clear(); searchFails[failKey] = Timed(System.currentTimeMillis(), msg) }
                          SearchUpdate(api.name, emptyList(), msg) },
                    )
                }
                send(update)
            }
        }
    }.flowOn(Dispatchers.IO + PriorityGate.Lane(PriorityGate.PICKED)) // FE-2c: the user is waiting for a search.

    private val searchCache = ConcurrentHashMap<String, Timed<List<OnlineItem>>>()
    /** FE-S3: memory only, 2 minutes: a site that just failed a query is not asked again until Retry. */
    private val searchFails = ConcurrentHashMap<String, Timed<String>>()
    private const val SEARCH_FAIL_MS = 2 * 60_000L

    private fun searchKey(api: String) = "search|$api"

    /** SPEED-2: this query's results for [api] from memory or the phone (fresh enough), or null. Disk read: call off main. */
    fun cachedSearch(api: MainAPI, q: String): List<OnlineItem>? {
        val key = "${api.name}|${q.lowercase()}"
        searchCache[key]?.takeIf { System.currentTimeMillis() - it.at < SEARCH_CACHE_MS }?.let { return it.value }
        val dk = diskKey(api, "search", q.lowercase())
        return dev.asfakul.yumeiro.cache.DataCache.read("search", dk, maxAgeMs = minOf(dev.asfakul.yumeiro.cache.DataCache.Settings.freshMs, SEARCH_DISK_MS))
            // FE-S1: an empty answer is saved too (a site that has nothing for this query), so it isn't asked again.
            ?.let { raw -> runCatching { dev.asfakul.yumeiro.cache.OnlineCodec.items(raw) }.getOrNull() }?.also { saved ->
                searchCache[key] = Timed(System.currentTimeMillis(), saved)
            }
    }

    private val warmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** VERIFY-2: see BrowserCheck.warm. Never waits; never shows a page. */
    private fun warmCheck(api: MainAPI) {
        val url = runCatching { api.mainUrl }.getOrNull()?.takeIf { it.startsWith("http") } ?: return
        noteSite(api.name, url)
        warmScope.launch { runCatching { dev.asfakul.yumeiro.online.web.BrowserCheck.warm(url) } }
    }

    /** B4: the site address of every extension asked so far (to find the ones a passed check belongs to). */
    private val siteOf = ConcurrentHashMap<String, String>()

    private fun noteSite(name: String, url: String) { if (siteOf.size < 1024 || siteOf.containsKey(name)) siteOf[name] = url }

    /** B4: names of the extensions asked so far whose site is [host]. Pure on the map; unit-tested. */
    internal fun extensionsOf(host: String): Set<String> =
        siteOf.entries.filter { dev.asfakul.yumeiro.online.web.SitePass.matches(it.value, host) }.mapTo(HashSet()) { it.key }

    /**
     * B4 (Group M): [host]'s check was just passed. What the failed attempt left in memory for its extensions goes:
     * the 2-minute search failure memory (FE-S3 answered every search with the old failure), empty search answers
     * and home / title pages, and the failure count that quarantined it (E4: a quarantined site got the shortest
     * search timeout and went last), so the reload that follows asks the site again with the pass. Cheap: maps only.
     */
    fun onSitePassed(host: String) {
        val names = extensionsOf(host)
        if (names.isEmpty()) return
        fun mine(key: String) = names.any { key == it || key.startsWith("$it|") }
        searchFails.keys.removeAll(::mine)
        val emptyAnswers = searchCache.entries.filter { mine(it.key) && it.value.value.isEmpty() }.map { it.key }
        emptyAnswers.forEach { k ->
            searchCache.remove(k)
            val name = k.substringBefore('|'); val q = k.substringAfter('|')
            // The phone's copy too (same key as diskKey(api, "search", q)); DataCache writes off this thread.
            siteOf[name]?.let { site -> runCatching { dev.asfakul.yumeiro.cache.DataCache.remove("search", "search|$name|$site|$q") } }
        }
        homeCache.keys.removeAll(::mine)
        detailCache.keys.removeAll(::mine)
        names.forEach { ExtensionSpeed.forgive(it); ExtensionSpeed.forgive(searchKey(it)) }
    }

    /** Live search of [api]; the caller already checked [cachedSearch] (FE-S1: the second disk read on a miss is gone). */
    private suspend fun searchOne(api: MainAPI, q: String): List<OnlineItem> {
        warmCheck(api)
        val key = "${api.name}|${q.lowercase()}"
        val dk = diskKey(api, "search", q.lowercase())
        val asked = System.currentTimeMillis()
        val results = try {
            api.search(q, 1)?.items
        } catch (e: NotImplementedError) { null } ?: api.search(q).orEmpty()
        val items = results.mapNotNull { runCatching { it.toItem(api.name) }.getOrNull() }.distinctBy { it.url }
        // FE-S1 (SpeedDeviceTest run 44883372732: a repeated search took 15.1 s): empty answers are kept as well
        // (memory 10 min, phone 1 h like any search); errors and timeouts are not, so Retry still asks again.
        // B4: an empty answer made while the site showed its check page is the check page, not "nothing found".
        if (items.isEmpty() && dev.asfakul.yumeiro.online.web.BrowserCheck.challengedSince(runCatching { api.mainUrl }.getOrNull(), asked)) return items
        if (searchCache.size > 64) searchCache.clear()
        searchCache[key] = Timed(System.currentTimeMillis(), items)
        dev.asfakul.yumeiro.cache.DataCache.write("search", dk, dev.asfakul.yumeiro.cache.OnlineCodec.items(items))
        return items
    }

    // endregion

    // region details

    /**
     * OWN-6: one extension `load` per title at a time. The tap (OnlineSession.open) and the title page both asked
     * the extension for the same details at once, so the page waited for a second, slower network call.
     */
    private val loadsInFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<OnlineDetail>>()
    private val loadScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    suspend fun load(api: MainAPI, url: String, force: Boolean = false): OnlineDetail {
        runCatching { api.mainUrl }.getOrNull()?.let { noteSite(api.name, it) }
        if (force || !dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FAST_TITLE_OPEN)) return loadOnce(api, url, force)
        val key = "${api.name}|$url"
        detailCache[key]?.takeIf { System.currentTimeMillis() - it.at < CACHE_MS }?.let { return it.value }
        val running = loadsInFlight[key]
        if (running != null && running.isActive) return running.await()
        val d = loadScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) { loadOnce(api, url, false) }
        val prev = loadsInFlight.putIfAbsent(key, d)
        if (prev != null && prev.isActive) { d.cancel(); return prev.await() }
        if (prev != null) loadsInFlight[key] = d
        d.invokeOnCompletion { loadsInFlight.remove(key, d) }
        d.start()
        return d.await()
    }

    internal fun loadsRunning(): Int = loadsInFlight.size

    private suspend fun loadOnce(api: MainAPI, url: String, force: Boolean): OnlineDetail = withContext(Dispatchers.IO) {
        val key = "${api.name}|$url"
        detailCache[key]?.takeIf { !force && System.currentTimeMillis() - it.at < CACHE_MS }?.let { return@withContext it.value }
        val dk = diskKey(api, "detail", url)
        if (!force) dev.asfakul.yumeiro.cache.DataCache.read("detail", dk)?.let(dev.asfakul.yumeiro.cache.OnlineCodec::detail)?.let { saved ->
            detailCache[key] = Timed(System.currentTimeMillis(), saved)
            return@withContext saved
        }
        val resp = withContext(ProviderBase(runCatching { api.mainUrl }.getOrDefault(""))) { ExtensionCall.bounded(LOAD_TIMEOUT) { api.load(url) } } ?: throw IllegalStateException("This title isn't available anymore")
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
            recommendations = r.recommendations.orEmpty().mapNotNull { it.toItem(api.name, feed = true) }.distinctBy { it.url }.take(24),
            unsupportedReason = unsupported,
            malId = runCatching { with(LoadResponse) { r.getMalId() }?.toIntOrNull() }.getOrNull(),
            aniListId = runCatching { with(LoadResponse) { r.getAniListId() }?.toIntOrNull() }.getOrNull(),
            imdbId = runCatching { with(LoadResponse) { r.getImdbId() } }.getOrNull()?.takeIf { it.startsWith("tt") },
            tmdbId = runCatching { with(LoadResponse) { r.getTMDbId() }?.toIntOrNull() }.getOrNull(),
            cast = runCatching {
                r.actors.orEmpty().mapNotNull { a ->
                    a.actor.name.trim().takeIf { it.isNotEmpty() }?.let { OnlineCast(it, a.roleString?.trim()?.ifBlank { null }, a.actor.image?.takeIf { u -> u.startsWith("http") }) }
                }.distinctBy { it.name }.take(30)
            }.getOrDefault(emptyList()),
            trailers = runCatching { r.trailers.map { it.extractorUrl.trim() }.filter { it.startsWith("http") }.distinct().take(6) }.getOrDefault(emptyList()),
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
                ExtensionCall.bounded(LINKS_TIMEOUT) {
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
        link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET -> LinkEvent.Skipped("Torrent source skipped", link.url)
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
    fun sortLinks(links: List<OnlineLink>, preferAudio: String? = null, version: String = "", maxHeight: Int = 0): List<OnlineLink> {
        // Round 13: the audio key is worked out once per link, not inside the comparator (which
        // ran the language regexes thousands of times per re-sort).
        val unique = links.distinctBy { it.url }
        val audioMiss = if (preferAudio == null) null else unique.associate { l -> l.url to AudioLanguages.filterKey(AudioLanguages.classify(l.name + " " + l.source, version)).let { k -> k.isNotEmpty() && k != preferAudio } }
        return unique.sortedWith(
            compareBy<OnlineLink> { it.audioOnly }
                // Round 9: the user's remembered audio language plays first when a source has it.
                .thenBy { if (audioMiss?.get(it.url) == true) 1 else 0 }
                // Round 13: Settings → Quality and audio → Default quality. Links above it go last
                // (a link with no known quality is never pushed down).
                .thenBy { if (maxHeight > 0 && it.quality > maxHeight) 1 else 0 }
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
    internal fun SearchResponse.toItem(owner: String = apiName, feed: Boolean = false): OnlineItem? {
        val n = name.trim().ifBlank { return null }
        if (AdultContent.dropNsfw(type == TvType.NSFW, feed, OnlineSettings.adult.value, OnlineSettings.adultPrivate.value)) return null
        return OnlineItem(
            name = n, url = url, api = owner.ifBlank { apiName }, posterUrl = posterUrl?.takeIf { it.isNotBlank() },
            posterHeaders = posterHeaders.orEmpty(), type = type?.let(::typeLabel),
            quality = quality?.name, score = runCatching { score?.toDouble(10) }.getOrNull(),
            dub = (this as? com.lagradost.cloudstream3.AnimeSearchResponse)?.dubStatus.orEmpty()
                .filter { it != DubStatus.None }.mapTo(HashSet()) { it.name },
        ).also { it.tags } // R15: poster tags are computed here (extension thread), never while a card composes.
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
        s.replace(RX_ONLINEREPOSITORY_HOT_1, "\n").replace(RX_ONLINEREPOSITORY_HOT_2, "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim()

    // endregion
}
