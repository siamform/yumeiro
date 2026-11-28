/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anticipation

import android.content.Context
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.online.AdultContent
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OtherSources
import dev.asfakul.yumeiro.online.PriorityGate
import dev.asfakul.yumeiro.online.SearchRanker
import dev.asfakul.yumeiro.online.StreamAddons
import dev.asfakul.yumeiro.online.blacksource.BlackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * B23: the app's real sources for [Anticipator.prepare]. Everything goes through the existing pipeline: the
 * extension's `loadLinks` (with stream add-ons, as the player), the B17 candidates and sweep, Black Source's AniList
 * names. No second resolver, no new network stack.
 */
internal class LiveSources(private val c: Context) : Anticipator.Sources {
    override fun own(t: Anticipator.Target): Flow<LinkEvent>? {
        val api = ExtensionManager.provider(t.api) ?: return null
        return StreamAddons.withAddons(c, t.detail, t.episode, OnlineRepository.links(api, t.episode.data))
    }

    private fun candidates(t: Anticipator.Target): List<MainAPI> {
        if (OtherSources.mode.value == OtherSources.Mode.OFF) return emptyList()
        return OtherSources.candidates(
            t.api, OtherSources.seriesOf(t.detail.type) ?: true, AdultContent.isAdult(t.detail.api, t.detail.type),
            ExtensionManager.providers(), { it is dev.asfakul.yumeiro.sources.lookup.StreamLookupProvider },
        )
    }

    /** Remembered matches first (no search at all), then the B17 sweep over the rest in the BACKGROUND lane. */
    override fun others(t: Anticipator.Target, memo: List<PrepData.Match>): Flow<OtherSources.Hit> = channelFlow {
        val apis = candidates(t)
        if (apis.isEmpty()) return@channelFlow
        val byName = apis.associateBy { it.name }
        val used = HashSet<String>()
        for (m in memo) {
            val api = byName[m.api] ?: continue
            used += m.api
            if (episodeFrom(api, m.url, t) { send(it) }) return@channelFlow
        }
        val rest = apis.filter { it.name !in used }
        if (rest.isNotEmpty()) OtherSources.sweep(t.detail, t.version, t.episode, rest, null, PriorityGate.BACKGROUND).collect { send(it) }
    }

    /**
     * Black Source (only when the person opted in, ADR-B23-4): the title's other names from AniList (romaji, English,
     * native, synonyms), each searched in the chosen sources; only a result that is the same title by that name and
     * year joins (never a sequel or another season).
     */
    override fun black(t: Anticipator.Target): Flow<OtherSources.Hit> = channelFlow {
        if (!BlackSource.optedIn(c)) return@channelFlow
        val title = runCatching { BlackSource.search(BlackSource.Filter(words = OtherSources.queryFor(t.detail.name))) }.getOrNull()
            ?.items?.firstOrNull { BlackSource.matches(t.detail.name, it) } ?: return@channelFlow
        val own = SearchRanker.normalize(t.detail.name)
        val names = title.names.filter { SearchRanker.normalize(it).let { n -> n.length >= 3 && n != own } }.distinctBy { SearchRanker.normalize(it) }.take(3)
        val apis = BlackSource.chosen(candidates(t), BlackSource.sourcesOff(c)) { it.name }
        if (names.isEmpty() || apis.isEmpty()) return@channelFlow
        val byName = apis.associateBy { it.name }
        val series = OtherSources.seriesOf(t.detail.type) ?: true
        for (n in names) {
            val updates = withTimeoutOrNull(OtherSources.SWEEP_MS) { OnlineRepository.search(OtherSources.queryFor(n), apis, lane = PriorityGate.BACKGROUND).toList() }.orEmpty()
            for (u in updates) {
                val api = byName[u.api] ?: continue
                val m = OtherSources.pick(n, t.detail.year ?: title.year, series, u.items) ?: continue
                if (episodeFrom(api, m.url, t) { send(it) }) return@channelFlow
            }
        }
    }

    /** Loads [url] on [api], finds the same episode and version, and streams its links. True when one was playable. */
    private suspend fun episodeFrom(api: MainAPI, url: String, t: Anticipator.Target, send: suspend (OtherSources.Hit) -> Unit): Boolean {
        val d = withTimeoutOrNull(20_000) { runCatching { OnlineRepository.load(api, url) }.getOrNull() } ?: return false
        val data = OtherSources.dataFor(d, t.version, t.episode) ?: return false
        send(OtherSources.Hit.Matched(api.name, d.name, url))
        var got = false
        OnlineRepository.links(api, data).collect { ev -> if (ev is LinkEvent.Link && !ev.link.audioOnly) got = true; send(OtherSources.Hit.Event(api.name, ev)) }
        return got
    }

    /** One light request (first byte); only 404 / 410 count as dead, anything else (403 to bots, timeouts) is kept. */
    override suspend fun alive(link: OnlineLink): Boolean = withContext(Dispatchers.IO) {
        if (!link.url.startsWith("http")) return@withContext true
        val client = probeClient ?: dev.asfakul.yumeiro.network.NetworkClient.get(c).newBuilder().callTimeout(8, TimeUnit.SECONDS).build().also { probeClient = it }
        val req = Request.Builder().url(link.url).header("Range", "bytes=0-0").apply {
            link.headers.forEach { (k, v) -> runCatching { header(k, v) } }
            link.referer?.takeIf { it.startsWith("http") && link.headers.keys.none { k -> k.equals("Referer", true) } }?.let { header("Referer", it) }
        }.build()
        runCatching { client.newCall(req).execute().use { it.code != 404 && it.code != 410 } }.getOrDefault(true)
    }

    private companion object { @Volatile var probeClient: okhttp3.OkHttpClient? = null }
}
