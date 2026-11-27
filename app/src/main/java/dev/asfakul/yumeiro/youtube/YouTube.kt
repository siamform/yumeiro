/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import dev.asfakul.yumeiro.l10n.tr
import dev.asfakul.yumeiro.R
import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** One YouTube / YouTube Music video or song. Plain data → safe to keep in Compose state. */
/** BUG-2 (E2): a channel found by search. [subscribers] < 0 when unknown. */
data class YtChannelHit(val url: String, val name: String, val avatar: String?, val subscribers: Long)

data class YtItem(
    val id: String,
    val url: String,
    val title: String,
    val channel: String,
    val channelUrl: String? = null,
    val thumbnail: String? = null,
    val channelAvatar: String? = null,
    /** Seconds; <= 0 for live / unknown. */
    val durationSec: Long = 0,
    val views: Long = -1,
    val uploaded: String? = null,
    val live: Boolean = false,
    /** Came from YouTube Music (song) rather than regular YouTube. */
    val music: Boolean = false,
    /** F-12: YouTube marks it a Short (vertical, up to 3 min), whatever its length. */
    val vertical: Boolean = false,
) {
    val key: String get() = "yt|$id"
}

/** A page of results plus the token for the next one. */
data class YtPage(val items: List<YtItem>, val next: Page?)

/**
 * A YouTube channel header plus the tabs it offers (Videos, Shorts, Live). Tabs are opened with
 * [YouTube.channelTab] using the channel url and one of [tabs].
 */
data class YtChannel(
    val url: String,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val subscribers: Long,
    val description: String,
    val tabs: List<String>,
)

/** Built-in shelves shown on the YouTube and YouTube Music home pages. */
enum class YtShelf(val title: String, val kioskId: String?, val query: String?, val music: Boolean) {
    TRENDING_MUSIC(tr(R.string.trending_music, "Trending music"), "trending_music", null, true),
    TRENDING_GAMING(tr(R.string.trending_in_gaming, "Trending in gaming"), "trending_gaming", null, false),
    TRENDING_MOVIES(tr(R.string.movies_shows, "Movies & shows"), "trending_movies_and_shows", null, false),
    LIVE(tr(R.string.live_now, "Live now"), "live", null, false),
    ANIME(tr(R.string.anime_picks, "Anime picks"), null, "anime opening 4k", false),
    AMV(tr(R.string.amv_edits, "AMV & edits"), null, "amv edit", false),
    LOFI(tr(R.string.lo_fi_chill, "Lo-fi & chill"), null, "lofi hip hop", true),
    JPOP(tr(R.string.j_pop_anime_songs, "J-Pop & anime songs"), null, "anime songs jpop", true),
    TOP_HITS(tr(R.string.top_hits, "Top hits"), null, "top hits this week", true),
    WORKOUT(tr(R.string.energy_boost, "Energy boost"), null, "workout music mix", true),
}

/**
 * YouTube + YouTube Music without any login, through NewPipeExtractor (the same anonymous approach
 * NewPipe, ArchiveTune and AuraMusic use). Everything is suspend + IO and cached briefly.
 *
 * Login-only features (your subscriptions, liked songs, history sync) are intentionally absent;
 * see docs/HANDOVER.md → "Login-only features" for how to plug them in later.
 */
object YouTube {
    private const val TAG = "YouTube"
    private const val INFO_TTL_MS = 4 * 60 * 60 * 1000L // stream URLs live ~6 h; refresh well before
    private const val LIST_TTL_MS = 20 * 60 * 1000L

    @Volatile private var ready = false
    private val service get() = ServiceList.YouTube

    private data class Timed<T>(val at: Long, val value: T)
    private val infoCache = ConcurrentHashMap<String, Timed<StreamInfo>>()
    private val listCache = ConcurrentHashMap<String, Timed<List<YtItem>>>()

    /** Initialises NewPipe once with the device language / country. Cheap, idempotent. */
    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val locale = Locale.getDefault()
            YtAccount.init(context)
            NewPipe.init(
                NewPipeDownloader(NetworkClient.get(context)),
                Localization.fromLocale(locale),
                ContentCountry(locale.country.ifBlank { "US" }),
            )
            ready = true
        }
    }

    // region lists

    suspend fun shelf(context: Context, shelf: YtShelf, force: Boolean = false): List<YtItem> = withContext(Dispatchers.IO) {
        init(context)
        val key = "shelf:${shelf.name}"
        listCache[key]?.takeIf { !force && fresh(it.at, LIST_TTL_MS) }?.let { return@withContext it.value }
        val items = when {
            shelf.kioskId != null -> runCatching { kiosk(shelf.kioskId, shelf.music) }
                .getOrElse { Log.w(TAG, "kiosk ${shelf.kioskId}", it); fallbackSearch(shelf) }
            else -> fallbackSearch(shelf)
        }
        if (items.isNotEmpty()) listCache[key] = Timed(System.currentTimeMillis(), items)
        items
    }

    /**
     * One page of a planned [YtFeed]. Searches page forever (pass the returned `next`); kiosks and
     * related lists are a single page. First pages are cached like shelves.
     */
    suspend fun feed(context: Context, feed: YtFeed, page: Page? = null, force: Boolean = false): YtPage = withContext(Dispatchers.IO) {
        init(context)
        val key = "feed:${feed.id}"
        if (page == null) listCache[key]?.takeIf { !force && fresh(it.at, LIST_TTL_MS) }?.let { return@withContext YtPage(it.value, pageTokens[key]) }
        val result = when (feed.kind) {
            YtFeed.Kind.KIOSK -> YtPage(
                runCatching { kiosk(feed.arg, feed.music) }.getOrElse {
                    Log.w(TAG, "kiosk ${feed.arg}", it)
                    searchBlocking(feed.title, if (feed.music) Filter.SONGS else Filter.VIDEOS, null).items
                },
                null,
            )
            YtFeed.Kind.RELATED -> YtPage(infoBlocking(feed.arg, false).relatedItems.mapNotNull { it.toYt(feed.music) }, null)
            YtFeed.Kind.QUERY -> searchBlocking(feed.arg, if (feed.music) Filter.SONGS else Filter.VIDEOS, page)
        }
        if (page == null && result.items.isNotEmpty()) {
            listCache[key] = Timed(System.currentTimeMillis(), result.items)
            rememberFeed(feed, result.items)
            if (result.next != null) pageTokens[key] = result.next else pageTokens.remove(key)
        }
        result
    }
    private val pageTokens = ConcurrentHashMap<String, Page>()

    /**
     * FE-7a (docs/FEED-ENGINE-2.1.md): the first page of every shelf is saved on the phone, so the next cold start
     * shows it at once (any age up to keepDays) while the live copy loads. Before, shelves lived only in memory for
     * 20 min: every app start showed skeletons and fetched every shelf live, two at a time.
     */
    internal fun rememberFeed(feed: YtFeed, items: List<YtItem>) {
        if (items.isEmpty()) return
        runCatching { dev.asfakul.yumeiro.cache.DataCache.write(FEED_NS, feed.id, YtHistory.encode(items.take(60))) }
    }

    /** The saved first page of [feed] (any age up to keepDays), or null. Disk read on Dispatchers.IO. */
    suspend fun savedFeed(feed: YtFeed): List<YtItem>? = withContext(Dispatchers.IO) {
        runCatching {
            dev.asfakul.yumeiro.cache.DataCache.read(FEED_NS, feed.id, maxAgeMs = dev.asfakul.yumeiro.cache.DataCache.Settings.keepDays * 86_400_000L)
        }.getOrNull()?.let(YtHistory::decode)?.takeIf { it.isNotEmpty() }
    }
    private const val FEED_NS = "ytfeed"

    private fun fallbackSearch(shelf: YtShelf): List<YtItem> {
        val q = shelf.query ?: shelf.title
        return searchBlocking(q, if (shelf.music) Filter.SONGS else Filter.VIDEOS, null).items
    }

    private fun kiosk(id: String, music: Boolean): List<YtItem> {
        val url = service.kioskList.getListLinkHandlerFactoryByType(id).fromId(id).url
        return KioskInfo.getInfo(service, url).relatedItems.mapNotNull { it.toYt(music) }
    }

    enum class Filter(val id: String, val music: Boolean) {
        VIDEOS("videos", false), SONGS("music_songs", true), MUSIC_VIDEOS("music_videos", true),
    }

    suspend fun search(context: Context, query: String, filter: Filter, page: Page? = null): YtPage = withContext(Dispatchers.IO) {
        init(context)
        searchBlocking(query, filter, page)
    }

    private fun searchBlocking(query: String, filter: Filter, page: Page?): YtPage {
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter.id), "")
        return if (page == null) {
            val info = SearchInfo.getInfo(service, handler)
            YtPage(info.relatedItems.mapNotNull { it.toYt(filter.music) }, info.nextPage?.takeIf { Page.isValid(it) })
        } else {
            val more = SearchInfo.getMoreItems(service, handler, page)
            YtPage(more.items.mapNotNull { it.toYt(filter.music) }, more.nextPage?.takeIf { Page.isValid(it) })
        }
    }

    /**
     * BUG-2 (E2): channels for a search. The video search filter returns videos only and
     * [toYt] keeps streams only, so a channel could never be found. Errors give an empty row
     * (the video results are the main answer).
     */
    suspend fun searchChannels(context: Context, query: String, limit: Int = 4): List<YtChannelHit> = withContext(Dispatchers.IO) {
        init(context)
        runCatching {
            val handler = service.searchQHFactory.fromQuery(query, listOf(CHANNELS_FILTER), "")
            SearchInfo.getInfo(service, handler).relatedItems.mapNotNull { channelHit(it) }.distinctBy { it.url }.take(limit)
        }.onFailure { Log.w(TAG, "channels '$query'", it) }.getOrDefault(emptyList())
    }

    internal const val CHANNELS_FILTER = "channels"

    internal fun channelHit(item: InfoItem): YtChannelHit? {
        val c = item as? ChannelInfoItem ?: return null
        val url = c.url?.takeIf { it.isNotBlank() } ?: return null
        return YtChannelHit(url, c.name.orEmpty(), bestImage(runCatching { c.thumbnails }.getOrNull()), c.subscriberCount)
    }

    suspend fun suggestions(context: Context, query: String): List<String> = withContext(Dispatchers.IO) {
        init(context)
        if (query.isBlank()) emptyList() else runCatching { service.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList()).take(8)
    }

    /** "Up next" / radio: videos related to [id] (songs when [music]). */
    suspend fun related(context: Context, id: String, music: Boolean): List<YtItem> = withContext(Dispatchers.IO) {
        init(context)
        runCatching { infoBlocking(id, false).relatedItems.mapNotNull { it.toYt(music) } }
            .onFailure { Log.w(TAG, "related $id", it) }.getOrDefault(emptyList())
    }

    // endregion

    // region streams

    /** Full stream info (cached for [INFO_TTL_MS]). Throws on extraction failure. */
    suspend fun info(context: Context, id: String, force: Boolean = false): StreamInfo = withContext(Dispatchers.IO) {
        init(context)
        infoBlocking(id, force)
    }

    /** Blocking variant for ExoPlayer loader threads (ResolvingDataSource). */
    fun infoBlocking(id: String, force: Boolean): StreamInfo {
        infoCache[id]?.takeIf { !force && fresh(it.at, INFO_TTL_MS) }?.let { return it.value }
        // Anonymous first; the optional account is used only if YouTube puts up a sign-in wall.
        val info = YtAccount.retryWithAccount { StreamInfo.getInfo(service, watchUrl(id)) }
        infoCache[id] = Timed(System.currentTimeMillis(), info)
        return info
    }

    fun invalidate(id: String) { infoCache.remove(id) }

    /**
     * Test seam (BUG-4): device tests on CI cannot reach YouTube ("confirm you're not a bot" for
     * data-centre IPs), so they put a built [StreamInfo] in the cache to drive the player UI.
     */
    @androidx.annotation.VisibleForTesting
    fun seedForTests(id: String, info: StreamInfo) { infoCache[id] = Timed(System.currentTimeMillis(), info) }

    /** Drops every cached stream (after signing in or out, links made with the old session are stale). */
    fun invalidateAll() { infoCache.clear() }

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    /** Video id from any YouTube / YouTube Music URL, or null. */
    fun idFrom(url: String): String? = runCatching { service.streamLHFactory.getId(url) }.getOrNull()

    fun isYouTubeUrl(url: String): Boolean = idFrom(url) != null

    /** BUG-2 (E4): /@handle, /channel/UC…, /c/…, /user/… links (not videos). */
    fun isChannelUrl(url: String): Boolean =
        idFrom(url) == null && runCatching { service.channelLHFactory.acceptUrl(url) }.getOrDefault(false)

    // endregion

    // region channels
    private val channelTabs = ConcurrentHashMap<String, org.schabi.newpipe.extractor.linkhandler.ListLinkHandler>()
    private val channelCache = ConcurrentHashMap<String, Timed<YtChannel>>()

    /** Channel header + the tabs we can list (videos / shorts / live, in that order). */
    suspend fun channel(context: Context, url: String): YtChannel = withContext(Dispatchers.IO) {
        init(context)
        channelCache[url]?.takeIf { fresh(it.at, LIST_TTL_MS) }?.let { return@withContext it.value }
        val info = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(service, url)
        val wanted = listOf(
            org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS,
            org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.SHORTS,
            org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.LIVESTREAMS,
        )
        val tabs = info.tabs.mapNotNull { h -> h.contentFilters.firstOrNull()?.takeIf { it in wanted }?.let { it to h } }
            .sortedBy { wanted.indexOf(it.first) }
        tabs.forEach { (name, h) -> channelTabs["$url|$name"] = h }
        YtChannel(
            url = url, name = info.name.orEmpty(), avatar = bestImage(info.avatars), banner = bestImage(info.banners),
            subscribers = info.subscriberCount, description = info.description.orEmpty(), tabs = tabs.map { it.first },
        ).also { channelCache[url] = Timed(System.currentTimeMillis(), it) }
    }

    /** One page of a channel tab opened by [channel]. */
    suspend fun channelTab(context: Context, url: String, tab: String, page: Page? = null): YtPage = withContext(Dispatchers.IO) {
        init(context)
        val handler = channelTabs["$url|$tab"] ?: run { channel(context, url); channelTabs["$url|$tab"] }
            ?: return@withContext YtPage(emptyList(), null)
        if (page == null) {
            val info = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, handler)
            YtPage(info.relatedItems.mapNotNull { it.toYt(false) }, info.nextPage?.takeIf { Page.isValid(it) })
        } else {
            val more = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getMoreItems(service, handler, page)
            YtPage(more.items.mapNotNull { it.toYt(false) }, more.nextPage?.takeIf { Page.isValid(it) })
        }
    }
    // endregion

    // region playlists (ANIME-1: official anime channels list their series as playlists)
    /** One playlist on a channel's Playlists tab. */
    data class YtPlaylistCard(val url: String, val title: String, val thumbnail: String?, val count: Long, val channel: String)

    /** The first page of [channelUrl]'s Playlists tab (empty when the channel has none or YouTube refuses). */
    suspend fun channelPlaylists(context: Context, channelUrl: String): List<YtPlaylistCard> = withContext(Dispatchers.IO) {
        init(context)
        val info = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(service, channelUrl)
        val tab = info.tabs.firstOrNull { it.contentFilters.firstOrNull() == org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS }
            ?: return@withContext emptyList()
        val page = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(service, tab)
        page.relatedItems.mapNotNull { it as? org.schabi.newpipe.extractor.playlist.PlaylistInfoItem }.map { p ->
            YtPlaylistCard(p.url, p.name.orEmpty(), bestImage(p.thumbnails), p.streamCount, p.uploaderName ?: info.name.orEmpty())
        }
    }

    /** A playlist's videos in order, up to [max] (pages are followed until then). */
    suspend fun playlist(context: Context, url: String, max: Int = 400): Pair<String, List<YtItem>> = withContext(Dispatchers.IO) {
        init(context)
        val info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(service, url)
        val out = ArrayList<YtItem>(info.relatedItems.mapNotNull { it.toYt(false) })
        var next = info.nextPage
        while (out.size < max && next != null && Page.isValid(next)) {
            val more = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(service, url, next)
            out += more.items.mapNotNull { it.toYt(false) }
            next = more.nextPage
        }
        info.name.orEmpty() to out.take(max)
    }
    // endregion

    private fun fresh(at: Long, ttl: Long) = System.currentTimeMillis() - at < ttl

    internal fun bestImage(images: List<Image>?): String? =
        images.orEmpty().maxByOrNull { if (it.height > 0) it.height else 0 }?.url

    internal fun InfoItem.toYt(music: Boolean): YtItem? {
        val s = this as? StreamInfoItem ?: return null
        val id = idFrom(s.url) ?: return null
        val live = s.streamType == StreamType.LIVE_STREAM || s.streamType == StreamType.AUDIO_LIVE_STREAM
        return YtItem(
            id = id, url = s.url, title = s.name.orEmpty(), channel = s.uploaderName.orEmpty(),
            channelUrl = s.uploaderUrl, thumbnail = bestImage(s.thumbnails) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            channelAvatar = bestImage(runCatching { s.uploaderAvatars }.getOrNull()),
            durationSec = s.duration, views = s.viewCount, uploaded = s.textualUploadDate, live = live, music = music,
            vertical = !music && runCatching { s.isShortFormContent }.getOrDefault(false),
        )
    }
}

/** "3:45" / "1:02:03"; empty when unknown. */
fun formatYtDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.US, h, m, s) else "%d:%02d".format(java.util.Locale.US, m, s)
}

/** "1.2M subscribers"; empty when hidden. */
fun formatYtSubscribers(count: Long): String =
    if (count < 0) "" else formatYtViews(count).replace("views", "subscribers")

/**
 * YouTube-style home order: take items from every ready feed in turn (first feed leads), dropping
 * repeats, so the page reads as one mixed stream instead of separate shelves.
 */
fun interleaveFeeds(lists: List<List<YtItem>>, limit: Int = Int.MAX_VALUE): List<YtItem> {
    val out = ArrayList<YtItem>()
    val seen = HashSet<String>()
    val its = lists.map { it.iterator() }
    var progressed = true
    while (progressed && out.size < limit) {
        progressed = false
        for (it in its) {
            while (it.hasNext()) {
                val x = it.next()
                if (seen.add(x.id)) { out += x; progressed = true; break }
            }
            if (out.size >= limit) break
        }
    }
    return out
}

/**
 * Short vertical clips for the Shorts shelf: under a minute (not live), a /shorts/ link, or (F-12, flag
 * f12_vertical_shorts) YouTube's own Short mark, which covers the 61 s to 3 min Shorts the length rule missed.
 */
fun YtItem.isShort(vertical3min: Boolean = dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.SHORTS_VERTICAL)): Boolean =
    !live && (durationSec in 1..60 || vertical3min && vertical) || url.contains("/shorts/")

/** "1.2M views" style. */
fun formatYtViews(views: Long): String = when {
    views < 0 -> ""
    views >= 1_000_000_000 -> "%.1fB views".format(java.util.Locale.US, views / 1e9)
    views >= 1_000_000 -> "%.1fM views".format(java.util.Locale.US, views / 1e6)
    views >= 1_000 -> "%.1fK views".format(java.util.Locale.US, views / 1e3)
    else -> "$views views"
}.replace(".0", "")
