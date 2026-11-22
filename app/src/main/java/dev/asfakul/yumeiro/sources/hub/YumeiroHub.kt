/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.hub

import android.content.Context
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.sources.SourceHub
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * OWN-13 (owner 2026-11-22: "it should provide everything without installing a single extension, all in one, not
 * different and separate installation"): the built-in sources are one source, "Yumeiro".
 *
 * It holds the official parts (today Yumeiro Drama: short dramas; Yumeiro Classics: public-domain films,
 * documentaries, cartoons, TV and films by country from the Internet Archive) and shows them as one extension
 * with one switch: one home with every shelf, one search over every part, and titles open and play through the
 * part they came from (ids keep their part's prefix, `drama:` / `classics:`).
 * Titles saved before (history, bookmarks with the old source names) still open: see [aliasOf].
 * Only legal sources: no piracy sites, no DRM or paywall bypass, no 18+.
 */
object YumeiroHub {
    const val NAME = "Yumeiro"
    const val BRIDGE = "yumeiro"

    /** The part names a saved title may carry from before OWN-13. */
    val PART_NAMES = setOf(dev.asfakul.yumeiro.sources.drama.YumeiroDrama.NAME, dev.asfakul.yumeiro.sources.classics.YumeiroClassics.NAME)

    private val _enabled = MutableStateFlow(true)
    /** On when any part is on (the one switch turns all of them on or off). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Volatile private var drama: MainAPI? = null
    @Volatile private var classics: MainAPI? = null

    fun on(): Boolean = FeatureFlags.isOn(FeatureFlags.Flag.YUMEIRO_HUB)

    /** A part publishes itself here instead of as its own source (null = the part is off). */
    @Synchronized fun setDrama(api: MainAPI?) { drama = api; publish() }
    @Synchronized fun setClassics(api: MainAPI?) { classics = api; publish() }

    private fun publish() {
        val parts = listOfNotNull(drama, classics)
        _enabled.value = parts.isNotEmpty()
        if (parts.isEmpty()) SourceHub.remove(BRIDGE)
        else SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.YUMEIRO, listOf(YumeiroHubProvider(parts)))
    }

    /** The one switch. */
    fun setEnabled(context: Context, on: Boolean) {
        dev.asfakul.yumeiro.sources.drama.YumeiroDrama.setEnabled(context, on)
        dev.asfakul.yumeiro.sources.classics.YumeiroClassics.setEnabled(context, on)
    }

    /** A saved title with an old part name opens with the hub. */
    fun aliasOf(name: String): String? = if (on() && name in PART_NAMES) NAME else null
}

/** The CloudStream-compatible face of [YumeiroHub]. Pure routing; the parts do the work. */
class YumeiroHubProvider(private val parts: List<MainAPI>) : MainAPI() {
    override var name = YumeiroHub.NAME
    override var mainUrl = "https://yumeiro.click"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = parts.flatMap { it.supportedTypes }.toSet()

    init { sourcePlugin = "yumeiro-builtin:hub" }

    override val mainPage: List<MainPageData> = parts.withIndex().flatMap { (i, p) -> p.mainPage.map { MainPageData(it.name, "$i$SEP${it.data}") } }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val (part, data) = shelf(request.data) ?: return null
        return part.getMainPage(page, MainPageRequest(request.name, data, request.horizontalImages))
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val lists = coroutineScope {
            parts.map { p -> async { withTimeoutOrNull(SEARCH_MS) { runCatching { p.search(query, page) }.getOrNull() } } }.awaitAll()
        }
        return newSearchResponseList(interleave(lists.map { it?.items.orEmpty() }).distinctBy { it.url }, lists.any { it?.hasNext == true })
    }

    override suspend fun search(query: String): List<SearchResponse> = search(query, 1).items

    override suspend fun load(url: String): LoadResponse? = partFor(url)?.load(url)

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean =
        partFor(data)?.loadLinks(data, isCasting, subtitleCallback, callback) ?: false

    /**
     * Which part a title id belongs to: by the part key in the id (`drama:`, `classics:`), else the only part.
     * Finds the key anywhere, because CloudStream's fixUrl() may have put a host in front of a saved id.
     */
    fun partFor(id: String): MainAPI? {
        val t = id.trim()
        val hit = parts.map { p -> p to t.indexOf(p.sourcePlugin.orEmpty().substringAfter("yumeiro-builtin:") + ":") }
            .filter { it.second >= 0 }.minByOrNull { it.second }?.first
        return hit ?: parts.singleOrNull()
    }

    private fun shelf(data: String): Pair<MainAPI, String>? {
        val i = data.substringBefore(SEP, "").toIntOrNull() ?: return null
        return parts.getOrNull(i)?.let { it to data.substringAfter(SEP) }
    }

    companion object {
        private const val SEP = "\u001F"
        private const val SEARCH_MS = 25_000L

        /** One result from each part in turn, so a slow or long part doesn't bury the others. */
        fun <T> interleave(lists: List<List<T>>): List<T> {
            val out = ArrayList<T>(lists.sumOf { it.size })
            val max = lists.maxOfOrNull { it.size } ?: 0
            for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
            return out
        }
    }
}
