/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * B17 (Beta chapter, Group N): play from whichever installed extension answers first.
 *
 * When a title plays, its own extension is asked for links as before. At the same time (or, in
 * "When needed" mode, only once the own extension is slow or empty) the other installed video
 * extensions are searched for the same title. A match must have the same cleaned name, a compatible
 * year and the same movie / series kind; its matching episode (same season and number, same Sub /
 * Dub version) is loaded and its links join the same source list, marked with [OnlineLink.via].
 *
 * The own extension's links always stay first in the list (a same-named video from elsewhere never
 * jumps ahead of the right one), but when the own extension has no playable link after
 * [OWN_HOLD_MS], or finishes with none, the first working link from anywhere starts. Failover then
 * walks the whole list, so a dead own link moves on to another extension's link.
 *
 * Bounded for low-end phones and 200 000 users: at most [MAX_SOURCES] extensions, [LOADS] title pages
 * loaded at once, one sweep of [SWEEP_MS] per episode, and it stops with the session.
 */
object OtherSources {
    private const val TAG = "OtherSources"
    const val MAX_SOURCES = 12
    const val LOADS = 3
    const val OWN_HOLD_MS = 3_000L
    const val SWEEP_MS = 45_000L
    private const val LOAD_MS = 20_000L
    private const val PREFS = "yu_other_sources"

    enum class Mode(val label: String, val detail: String) {
        PARALLEL("At the same time", "Other extensions are searched while the title's own one loads. The first working source plays"),
        WHEN_NEEDED("When needed", "Other extensions are searched only when the title's own one is slow, empty or fails"),
        OFF("Off", "Only the title's own extension is used"),
    }

    private val _mode = MutableStateFlow(Mode.PARALLEL)
    val mode: StateFlow<Mode> = _mode.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = p.getString("mode", null)?.let { s -> Mode.entries.firstOrNull { it.name == s } }
        _mode.value = saved ?: defaultMode(dev.asfakul.yumeiro.DeviceClass.lowEnd)
        loaded = true
    }

    /** Low-end phones search other extensions only when it is needed (less network, heat and memory). */
    fun defaultMode(lowEnd: Boolean): Mode = if (lowEnd) Mode.WHEN_NEEDED else Mode.PARALLEL

    fun setMode(context: Context, m: Mode) {
        _mode.value = m; loaded = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", m.name).apply()
    }

    // region pure (OtherSourcesTest)

    private val VIDEO = setOf(TvType.Movie, TvType.AnimeMovie, TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.OVA,
        TvType.Documentary, TvType.AsianDrama, TvType.Others)
    private val URLISH = Regex("""^\s*(https?://|www\.)|[?&][a-z_]+=|/search\b""", RegexOption.IGNORE_CASE)

    /** true = a series, false = a film, null = unknown, from the type label [OnlineRepository.typeLabel] gives. */
    fun seriesOf(typeLabel: String?): Boolean? = when (typeLabel?.trim()?.lowercase()) {
        "movie", "anime movie", "animemovie", "torrent" -> false
        "tv series", "tvseries", "anime", "ova", "asian drama", "asiandrama", "cartoon", "documentary" -> true
        else -> null
    }

    /**
     * The other extensions worth asking for a title of [series] kind from [own]: video sources only, not
     * the same site (its clones read the same catalogue), not stream-lookup add-ons (they join through
     * [StreamAddons]), no 18+ source for a normal title, at most [cap].
     */
    fun candidates(own: String, series: Boolean?, adult: Boolean, apis: List<MainAPI>, isLookup: (MainAPI) -> Boolean, cap: Int = MAX_SOURCES): List<MainAPI> =
        apis.asSequence()
            .filter { it.name != own && !it.name.startsWith("$own · ") && !own.startsWith("${it.name} · ") }
            .filter { !isLookup(it) }
            .filter { a ->
                val t = runCatching { a.supportedTypes }.getOrDefault(emptySet())
                if (TvType.NSFW in t && !adult) return@filter false
                if (t.isEmpty()) return@filter true
                if (t.none { it in VIDEO }) return@filter false
                when (series) {
                    true -> t.any { it != TvType.Movie && it != TvType.AnimeMovie && it in VIDEO }
                    false -> t.any { it == TvType.Movie || it == TvType.AnimeMovie || it == TvType.Others || it == TvType.Anime }
                    null -> true
                }
            }
            .distinctBy { it.name }
            .take(cap)
            .toList()

    /** The search result that is the same title as [name] / [year] / [series], or null. Same cleaned name only. */
    fun pick(name: String, year: Int?, series: Boolean?, items: List<OnlineItem>): OnlineItem? {
        val want = TitleDedupe.compute(name, null)
        val wantYear = year ?: want.year
        val ok = items.filter { i ->
            if (URLISH.containsMatchIn(i.name)) return@filter false
            val k = TitleDedupe.compute(i.name, null)
            if (k.base != want.base) return@filter false
            if (wantYear != null && k.year != null && k.year != wantYear) return@filter false
            val s = seriesOf(i.type)
            series == null || s == null || s == series
        }
        return ok.firstOrNull { wantYear != null && TitleDedupe.compute(it.name, null).year == wantYear } ?: ok.firstOrNull()
    }

    /**
     * The other title's version for the playing [want] version (\"Sub\", \"Dub\", or \"\"): the same name, else the
     * same Sub / Dub kind, else its only version. Null when it has the other kind only (never Sub for a Dub).
     */
    fun versionFor(want: String, versions: List<String>): String? {
        if (versions.isEmpty()) return ""
        versions.firstOrNull { it.equals(want, ignoreCase = true) }?.let { return it }
        val dub = want.contains("dub", true); val sub = want.contains("sub", true)
        if (dub) return versions.firstOrNull { it.contains("dub", true) } ?: versions.singleOrNull()?.takeIf { !it.contains("sub", true) }
        if (sub) return versions.firstOrNull { it.contains("sub", true) } ?: versions.singleOrNull()?.takeIf { !it.contains("dub", true) }
        return versions.firstOrNull { !it.contains("dub", true) } ?: versions.first()
    }

    /** What to ask [other]'s loadLinks for: its film, or its copy of [episode] in the matching version. */
    fun dataFor(other: OnlineDetail, version: String, episode: OnlineEpisode?): String? {
        if (episode == null) return other.playData ?: other.episodes.values.singleOrNull()?.singleOrNull()?.data
        val v = versionFor(version, other.versions) ?: return null
        val eps = OnlineSession.episodesOf(other, v)
        return eps.getOrNull(EpisodeMatcher.match(episode, eps))?.data
    }

    /** \"Solo Leveling (2024) [Hindi]\" → \"Solo Leveling\": what is typed into the other sites' search. */
    fun queryFor(name: String): String = name.replace(QUERY_NOISE, " ").replace(SPACES, " ").trim().ifBlank { name.trim() }
    private val QUERY_NOISE = Regex("""[\[(（【{][^\])）】}]*[\])）】}]""")
    private val SPACES = Regex("""\s+""")

    // endregion

    /** One event from another extension: the link events of its matching episode, or that it matched. */
    sealed interface Hit {
        val api: String
        data class Matched(override val api: String, val title: String) : Hit
        data class Event(override val api: String, val event: LinkEvent) : Hit
    }

    /**
     * The sweep for [detail]'s [episode] in [version]: searches [apis] together, loads the matching titles
     * ([LOADS] at a time) and streams their links. Waits for [start] first ("When needed" mode): true searches, false skips.
     */
    fun sweep(detail: OnlineDetail, version: String, episode: OnlineEpisode?, apis: List<MainAPI>, start: CompletableDeferred<Boolean>? = null): Flow<Hit> = channelFlow {
        if (start?.await() == false) return@channelFlow
        if (apis.isEmpty()) return@channelFlow
        val series = seriesOf(detail.type) ?: when (detail.kind) { OnlineKind.MOVIE -> false; OnlineKind.SERIES -> true; else -> if (episode == null) false else null }
        val loads = Semaphore(LOADS)
        val byName = apis.associateBy { it.name }
        val q = queryFor(detail.name)
        Log.i(TAG, "sweep \"$q\" in ${apis.size}: ${apis.joinToString { it.name }}")
        withTimeoutOrNull(SWEEP_MS) {
            OnlineRepository.search(q, apis).collect { u ->
                val api = byName[u.api] ?: return@collect
                val m = pick(detail.name, detail.year, series, u.items)
                if (m == null) { Log.i(TAG, "${u.api}: no match in ${u.items.size} (${u.error ?: "ok"})"); return@collect }
                launch {
                    val other = loads.withPermit { withTimeoutOrNull(LOAD_MS) { runCatching { OnlineRepository.load(api, m.url) }.getOrNull() } }
                    val data = other?.let { dataFor(it, version, episode) }
                    if (data == null) { Log.i(TAG, "${u.api}: \"${m.name}\" has no matching episode"); return@launch }
                    Log.i(TAG, "${u.api}: matched \"${m.name}\"")
                    send(Hit.Matched(u.api, m.name))
                    OnlineRepository.links(api, data).collect { send(Hit.Event(u.api, it)) }
                }
            }
        }
    }
}
