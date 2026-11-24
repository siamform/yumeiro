/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.anime

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
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.youtube.YouTube
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * ANIME-1 (owner list item 20, "Yumeiro Mega Extension": the legal free-anime part).
 *
 * "Yumeiro Anime": full episodes that the rights holders and their licensed distributors publish free on their own
 * official YouTube channels (Muse Asia, Ani-One Asia, GUNDAM CHANNEL, VIZ Media, RetroCrush, Tezuka Productions).
 * A series is one of the channel's playlists; its episodes are the playlist's videos. Playback is YouTube's own
 * stream through the app's login-free YouTube player, so YouTube's region rules apply as they are: a video the
 * distributor didn't license for the viewer's country stays unavailable (nothing is bypassed). No piracy sites,
 * no re-uploads, no 18+ (these are all-ages official channels; adult words are dropped again on titles).
 * It is one part of the built-in "Yumeiro" source and follows its one switch.
 */
object YumeiroAnime {
    const val NAME = "Yumeiro Anime"
    const val PLUGIN = "yumeiro-builtin:anime"

    @Volatile internal var app: Context? = null

    fun start(context: Context) {
        app = context.applicationContext
        val hub = dev.asfakul.yumeiro.sources.hub.YumeiroHub
        val on = FeatureFlags.on(context, FeatureFlags.Flag.OFFICIAL_ANIME) && hub.on() &&
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)
        if (hub.on()) hub.setAnime(if (on) YumeiroAnimeProvider() else null)
    }

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
        start(context)
    }

    private const val PREFS = "yumeiro_anime"
}

/** One official channel: its YouTube id and the name shown on its shelf. */
internal data class AnimeChannel(val id: String, val shelf: String) {
    val url get() = "https://www.youtube.com/channel/$id"
}

class YumeiroAnimeProvider(
    private val playlists: suspend (String) -> List<YouTube.YtPlaylistCard> = { url -> YouTube.channelPlaylists(ctx(), url) },
    private val episodes: suspend (String) -> Pair<String, List<dev.asfakul.yumeiro.youtube.YtItem>> = { url -> YouTube.playlist(ctx(), url) },
) : MainAPI() {
    override var name = YumeiroAnime.NAME
    override var mainUrl = "https://www.youtube.com"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    init { sourcePlugin = YumeiroAnime.PLUGIN }

    override val mainPage: List<MainPageData> = CHANNELS.map { MainPageData(it.shelf, it.id) }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList(), false)
        val ch = CHANNELS.firstOrNull { it.id == request.data } ?: return newHomePageResponse(request.name, emptyList(), false)
        return newHomePageResponse(request.name, series(ch).map { it.toResult() }, false)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val words = words(query)
        if (words.isEmpty() || page > 1) return newSearchResponseList(emptyList(), false)
        val all = coroutineScope { CHANNELS.map { ch -> async { withTimeoutOrNull(SEARCH_MS) { series(ch) }.orEmpty() } }.awaitAll() }.flatten()
        return newSearchResponseList(all.filter { s -> matches(s.title, words) }.map { it.toResult() }, false)
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse {
        val list = playlistOf(url) ?: error("Not a Yumeiro Anime title")
        val card = known[list]
        val (rawTitle, videos) = episodes("https://www.youtube.com/playlist?list=$list")
        val full = fullEpisodes(videos)
        if (full.isEmpty()) error("This playlist has no full episodes")
        val title = cleanTitle(card?.title ?: rawTitle).ifBlank { rawTitle }
        if (adult(title)) error("This title isn't available in Yumeiro Anime")
        val eps = full.mapIndexed { i, v ->
            // fix = false: the id is ours, not a page path (newEpisode would turn it into "https://www.youtube.com/anime:v:…").
            newEpisode("anime:v:${v.id}", {
                name = v.title
                episode = i + 1
                posterUrl = v.thumbnail
                runTime = (v.durationSec / 60).toInt().takeIf { it > 0 }
            }, false)
        }
        return newTvSeriesLoadResponse(title, url, TvType.Anime, eps) {
            posterUrl = card?.thumbnail ?: full.first().thumbnail
            backgroundPosterUrl = full.first().thumbnail
            plot = "Full episodes published free by ${card?.channel ?: full.first().channel} on its official YouTube channel. " +
                "Some episodes may only play in the countries the distributor licensed them for."
            tags = listOf("Official", "Free")
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val id = videoOf(data) ?: return false
        // The player resolves a YouTube watch link through the app's YouTube extractor (best quality, DASH),
        // like a shared YouTube link: see StreamResolver.resolve and OnlinePlayback.formatOf.
        callback(newExtractorLink(name, "$name · YouTube", YouTube.watchUrl(id), ExtractorLinkType.VIDEO))
        return true
    }

    private data class Series(val list: String, val title: String, val poster: String?, val count: Long)

    private fun Series.toResult(): SearchResponse = newTvSeriesSearchResponse(title, "anime:pl:$list", TvType.Anime, fix = false) {
        posterUrl = poster
    }

    /** A channel's series: its playlists that look like a show (not clips, trailers, music or talk). Cached. */
    private suspend fun series(ch: AnimeChannel): List<Series> {
        cache[ch.id]?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.let { return it.second }
        val cards = runCatching { playlists(ch.url) }.getOrNull() ?: return cache[ch.id]?.second.orEmpty()
        val out = cards.mapNotNull { c ->
            val list = playlistOf("anime:pl:" + c.url.substringAfter("list=", "").substringBefore('&')) ?: return@mapNotNull null
            if (!looksLikeSeries(c.title, c.count)) return@mapNotNull null
            known[list] = c
            Series(list, cleanTitle(c.title).ifBlank { c.title }, c.thumbnail, c.count)
        }
        if (out.isNotEmpty()) cache[ch.id] = System.currentTimeMillis() to out
        return out
    }

    companion object {
        private const val TTL_MS = 6 * 60 * 60 * 1000L
        private const val SEARCH_MS = 20_000L
        /** A video shorter than this is a clip, preview or trailer, not an episode. */
        const val MIN_EPISODE_SEC = 8 * 60L

        /** Official channels of rights holders / licensed distributors (checked on youtube.com 2026-11-24). */
        internal val CHANNELS = listOf(
            AnimeChannel("UCGbshtvS9t-8CW11W7TooQg", "Muse Asia · new and seasonal anime"),
            AnimeChannel("UC0wNSTMWIL3qaorLx0jie6A", "Ani-One Asia · full series"),
            AnimeChannel("UCejtUitnpnf8Be-v5NuDSLw", "Gundam · official full series"),
            AnimeChannel("UCV1da9peoqEwqr45bpTJsbQ", "VIZ Media · free anime"),
            AnimeChannel("UCPZkYpT5XIUou8sV8zk-3jQ", "RetroCrush · classic anime"),
            AnimeChannel("UCLGBzNvdJmFFVUlhRkv9L4A", "Tezuka Productions · classics"),
        )

        private val cache = ConcurrentHashMap<String, Pair<Long, List<Series>>>()
        private val known = ConcurrentHashMap<String, YouTube.YtPlaylistCard>()


        private fun ctx(): Context = YumeiroAnime.app ?: error("Yumeiro Anime isn't started")

        /** "anime:pl:<id>" (anywhere in the string, since fixUrl may put a host in front) → playlist id. */
        fun playlistOf(s: String): String? = s.substringAfter("anime:pl:", "").substringBefore('/').substringBefore('?').takeIf { AnimeRx.RX_LIST.matches(it) }

        /** "anime:v:<id>" → YouTube video id. */
        fun videoOf(s: String): String? = s.substringAfter("anime:v:", "").take(11).takeIf { AnimeRx.RX_VIDEO.matches(it) }

        fun looksLikeSeries(title: String, count: Long): Boolean =
            title.isNotBlank() && (count < 0 || count >= 2) && !AnimeRx.RX_NOT_SERIES.containsMatchIn(title) && !adult(title)

        fun adult(title: String): Boolean = AnimeRx.RX_ADULT.containsMatchIn(title)

        /** "AOASHI｜青之蘆葦【Ani-One Asia】" → "AOASHI"; channel tags and the second-language half go. */
        fun cleanTitle(t: String): String {
            val noTag = t.replace(AnimeRx.RX_CHANNEL_TAG, " ")
            val first = noTag.substringBefore('｜').trim()
            return first.ifBlank { noTag.trim() }.replace(AnimeRx.RX_SPACE, " ").trim()
        }

        /** Episodes only: clips, trailers and live streams are dropped; order kept. */
        fun fullEpisodes(videos: List<dev.asfakul.yumeiro.youtube.YtItem>): List<dev.asfakul.yumeiro.youtube.YtItem> =
            videos.filter { !it.live && !it.vertical && it.durationSec >= MIN_EPISODE_SEC && !adult(it.title) }.distinctBy { it.id }

        fun words(q: String): List<String> = q.lowercase().split(AnimeRx.RX_WORD).filter { it.length >= 2 }.take(8)

        fun matches(title: String, words: List<String>): Boolean {
            val t = title.lowercase()
            return words.all { it in t }
        }
    }
}

/** ANIME-1: the patterns, in their own holder so RegexWarmup can initialise it off the main thread. */
private object AnimeRx {
    val RX_LIST = Regex("[A-Za-z0-9_-]{10,64}")
    val RX_VIDEO = Regex("[A-Za-z0-9_-]{11}")
    val RX_NOT_SERIES = Regex(
        """\b(chat|podcast|moments?|spotlight|trailers?|teasers?|pv|cm|promo|clips?|shorts|music|mv|songs?|opening|ending|op|ed|highlights?|interviews?|events?|live|stream|asmr|behind|making|gp\d{2,4}|seedgp\d*|recap|preview|news|announcements?)\b""",
        RegexOption.IGNORE_CASE,
    )
    val RX_ADULT = Regex("""\b(sex|sexy|nude|nudity|erotic|erotica|hentai|ecchi|porn|xxx|18\+)""", RegexOption.IGNORE_CASE)
    val RX_CHANNEL_TAG = Regex("""【[^】]*】""")
    val RX_WORD = Regex("""[^\p{L}\p{N}]+""")
    val RX_SPACE = Regex("""\s+""")
}
