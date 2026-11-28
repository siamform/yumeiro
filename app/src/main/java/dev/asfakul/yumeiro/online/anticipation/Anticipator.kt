/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anticipation

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.online.OnlineSubtitle
import dev.asfakul.yumeiro.online.OtherSources
import dev.asfakul.yumeiro.online.PriorityGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * B23 — the Anticipation Engine (docs: audits/AUDIT_B23.md). While an episode plays steadily, the next episodes
 * (3 by default, see [AnticipationPolicy]) are prepared one at a time in the background through the same pipeline
 * the player uses: the title's own extension, then the other installed ones (B17), then Black Source names (F14,
 * only when opted in). A prepared episode starts at once on Next or auto-advance ([take]); anything missing or stale
 * falls back to the normal resolve. Links stay in memory only; [PrepStore] keeps misses, matches and activity.
 *
 * Isolation: its own supervisor scope, one control coroutine, every step in runCatching (cancellation rethrown),
 * extension calls under timeouts and in the BACKGROUND lane. It never changes what is playing.
 */
object Anticipator {
    private const val TAG = "Anticipation"
    /** Prepared sets kept at most (the window is 3; one more for the episode just taken back by Previous). */
    private const val MAX_PREPARED = 4
    /** Links of this many playable sources are enough for one prepared episode. */
    private const val ENOUGH = 3
    /** Only this many top links are probed (one light request each). */
    private const val PROBES = 3
    /** Memory is cleared when no session was seen for this long. */
    private const val IDLE_CLEAR_MS = 10 * 60_000L

    data class Prepared(
        val key: String,
        val links: List<OnlineLink>,
        val subtitles: List<OnlineSubtitle>,
        val others: List<String>,
        /** The title's own extension had a playable link (false: an alternative from [via]). */
        val ownHad: Boolean,
        val at: Long,
    ) {
        val via: String? get() = if (ownHad) null else links.firstOrNull()?.via
    }

    /** One upcoming episode to prepare. [preferName]: the server playing now (the next one prefers it, like a binge group). */
    data class Target(val api: String, val detail: OnlineDetail, val version: String, val episode: OnlineEpisode, val preferName: String? = null) {
        val series: String get() = seriesKey(api, detail.url)
        val key: String get() = episodeKey(api, detail.url, version, episode.data)
    }

    sealed interface Outcome {
        data class Ready(val prepared: Prepared) : Outcome
        /** Every source answered and none has this episode: remembered as a miss. */
        data object Miss : Outcome
        /** Timed out, not installed, or every probed link was dead: tried again later. */
        data class Failed(val why: String) : Outcome
    }

    /** Where links come from. [LiveSources] in the app; fakes in tests. */
    interface Sources {
        fun own(t: Target): Flow<LinkEvent>?
        fun others(t: Target, memo: List<PrepData.Match>): Flow<OtherSources.Hit>
        fun black(t: Target): Flow<OtherSources.Hit>
        suspend fun alive(link: OnlineLink): Boolean
    }

    fun seriesKey(api: String, url: String) = "$api|$url"
    fun episodeKey(api: String, url: String, version: String, data: String) = "$api|$url|$version|$data"

    // region preparation (pure flow logic, unit-tested with fake sources)

    suspend fun prepare(t: Target, src: Sources, memo: List<PrepData.Match>, onMatch: (api: String, url: String) -> Unit, now: () -> Long = System::currentTimeMillis): Outcome {
        val own = src.own(t) ?: return Outcome.Failed("extension not installed")
        val ownLinks = LinkedHashMap<String, OnlineLink>()
        val subs = LinkedHashMap<String, OnlineSubtitle>()
        var ownDone = false
        var ownClean = false
        withTimeoutOrNull(AnticipationPolicy.OWN_MS) {
            own.transformWhile { ev -> emit(ev); ev !is LinkEvent.Done }.collect { ev ->
                when (ev) {
                    is LinkEvent.Link -> ownLinks.putIfAbsent(ev.link.url, ev.link)
                    is LinkEvent.Subtitle -> subs.putIfAbsent(ev.subtitle.url, ev.subtitle)
                    is LinkEvent.Done -> { ownDone = true; ownClean = ev.error == null }
                    is LinkEvent.Skipped -> Unit
                }
            }
        }
        val ownPlayable = ownLinks.values.filter { !it.audioOnly }
        if (ownPlayable.isNotEmpty()) {
            val alive = probe(order(ownPlayable, t.preferName), src)
            if (alive.isNotEmpty()) return Outcome.Ready(Prepared(t.key, alive + ownLinks.values.filter { it.audioOnly }, subs.values.toList(), emptyList(), ownHad = true, at = now()))
        }
        // The own extension has nothing playable (or only dead links): the other providers, then Black Source.
        val matched = LinkedHashSet<String>()
        val altLinks = LinkedHashMap<String, OnlineLink>()
        var searched = true
        suspend fun gather(flow: Flow<OtherSources.Hit>) {
            val done = withTimeoutOrNull(AnticipationPolicy.OTHERS_MS) {
                flow.transformWhile { h -> emit(h); altLinks.values.count { !it.audioOnly } < ENOUGH }.collect { h ->
                    when (h) {
                        is OtherSources.Hit.Matched -> { matched += h.api; if (h.url.isNotEmpty()) onMatch(h.api, h.url) }
                        is OtherSources.Hit.Event -> when (val ev = h.event) {
                            is LinkEvent.Link -> altLinks.putIfAbsent(ev.link.url, ev.link.copy(via = h.api))
                            is LinkEvent.Subtitle -> subs.putIfAbsent(ev.subtitle.url, ev.subtitle)
                            else -> Unit
                        }
                    }
                }
                true
            }
            if (done == null) searched = false
        }
        gather(src.others(t, memo))
        if (altLinks.values.none { !it.audioOnly }) gather(src.black(t))
        val altPlayable = altLinks.values.filter { !it.audioOnly }
        if (altPlayable.isNotEmpty()) {
            val alive = probe(order(altPlayable, null), src)
            if (alive.isNotEmpty()) return Outcome.Ready(Prepared(t.key, alive, subs.values.toList(), matched.toList(), ownHad = false, at = now()))
            return Outcome.Failed("every prepared link was dead")
        }
        return if (ownDone && ownClean && searched && ownLinks.isEmpty()) Outcome.Miss else Outcome.Failed(if (!ownDone) "own extension timed out" else if (!ownClean) "own extension failed" else "nothing playable yet")
    }

    /** The same server as the episode playing comes first (Stremio's binge-group idea), the rest keep their order. */
    internal fun order(links: List<OnlineLink>, preferName: String?): List<OnlineLink> {
        if (preferName.isNullOrBlank()) return links
        val (same, rest) = links.partition { it.name == preferName || it.source == preferName }
        return same + rest
    }

    /** Probes the first [PROBES] links; a dead one (404 / 410) is dropped, the others keep their place. */
    private suspend fun probe(links: List<OnlineLink>, src: Sources): List<OnlineLink> {
        val dead = HashSet<String>()
        for (l in links.take(PROBES)) {
            if (runCatching { src.alive(l) }.getOrElse { if (it is CancellationException) throw it; true }) break
            dead += l.url
        }
        return links.filter { it.url !in dead }
    }

    // endregion

    // region engine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prepared = ConcurrentHashMap<String, Prepared>()
    private data class Tries(val count: Int, val at: Long)
    private val tries = ConcurrentHashMap<String, Tries>()
    @Volatile private var loop: Job? = null
    @Volatile private var app: Context? = null
    @Volatile private var lastSeen = 0L
    @Volatile private var touched: String? = null
    /** For tests and the log: the episode keys asked so far in this process, in order. */
    internal val asked: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile internal var sourcesForTest: Sources? = null

    /** Starts the engine once (the first online playback). Cheap: a tick every [AnticipationPolicy.TICK_MS]. */
    fun start(context: Context) {
        val c = context.applicationContext
        app = c
        if (loop?.isActive == true) return
        loop = scope.launch {
            runCatching { AnticipationPrefs.init(c); PrepStore.cleanup(c) }
            // Profile isolation: nothing prepared for one profile is offered in another.
            launch { dev.asfakul.yumeiro.profiles.Profiles.currentFlow.drop(1).collect { clear(); touched = null } }
            while (true) {
                delay(AnticipationPolicy.TICK_MS)
                try { tick(c) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Log.w(TAG, "tick", e) }
            }
        }
    }

    /** The prepared set for this episode when it is still fresh (taken: used once). */
    fun take(api: String, url: String, version: String, data: String, now: Long = System.currentTimeMillis()): Prepared? =
        prepared.remove(episodeKey(api, url, version, data))?.takeIf { AnticipationPolicy.fresh(it.at, now) }

    /** Peek without taking (UI: "ready" marks). */
    fun isReady(api: String, url: String, version: String, data: String, now: Long = System.currentTimeMillis()): Boolean =
        prepared[episodeKey(api, url, version, data)]?.let { AnticipationPolicy.fresh(it.at, now) } == true

    fun clear() { prepared.clear(); tries.clear() }

    @Volatile private var progressVideo: String? = null
    @Volatile private var progressPos = 0L
    @Volatile private var progressDur = 0L

    /** The player's progress (OnlineSession.onProgress, every few seconds): when the playing episode is steady. */
    fun onProgress(videoId: String, positionMs: Long, durationMs: Long) { progressVideo = videoId; progressPos = positionMs; progressDur = durationMs }

    internal fun putForTest(p: Prepared) { prepared[p.key] = p }

    private fun network(c: Context): Pair<Boolean, Boolean> = runCatching {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        (cm?.activeNetwork != null) to (cm?.isActiveNetworkMetered == true)
    }.getOrDefault(true to false)

    private fun signals(c: Context): AnticipationPolicy.Signals {
        val d = dev.asfakul.yumeiro.player.PlaybackDiagnostics.state.value
        val (online, metered) = network(c)
        return AnticipationPolicy.Signals(
            enabled = AnticipationPrefs.enabled.value,
            heat = dev.asfakul.yumeiro.player.HeatGuard.level.value,
            memoryLow = dev.asfakul.yumeiro.online.FeedThrottle.isMemoryLow(),
            online = online, metered = metered, lowEnd = dev.asfakul.yumeiro.DeviceClass.lowEnd,
            playbackSteady = progressVideo != null && progressVideo == OnlineSession.state.value?.videoId &&
                AnticipationPolicy.steady(d.firstFrame, d.state == "BUFFERING", progressPos, progressDur),
        )
    }

    private suspend fun tick(c: Context) {
        val now = System.currentTimeMillis()
        val s = OnlineSession.state.value
        if (s == null) { if (lastSeen > 0 && now - lastSeen > IDLE_CLEAR_MS) { clear(); lastSeen = 0 }; return }
        lastSeen = now
        if (s.phase != OnlineSession.Phase.PLAYING || s.index < 0 || s.episodes.size < 2) return
        val series = seriesKey(s.api, s.detail.url)
        // Active series first: watching it marks it (once per series per session), so its data outlives the 7 days.
        if (touched != series) { touched = series; withContext(Dispatchers.IO) { PrepStore.update(c) { it.touch(series, now) } } }
        val sig = signals(c)
        val depth = AnticipationPolicy.depth(sig)
        val window = AnticipationPolicy.window(s.index, s.episodes.size, depth).map { s.episodes[it] }
        val keep = window.map { episodeKey(s.api, s.detail.url, s.version, it.data) }.toSet()
        // Out of the window, stale, or memory short: dropped (the next one stays while it is fresh).
        val next = s.episodes.getOrNull(s.index + 1)?.let { episodeKey(s.api, s.detail.url, s.version, it.data) }
        prepared.entries.removeIf { (k, p) -> !AnticipationPolicy.fresh(p.at, now) || (k !in keep && k != next) || (sig.memoryLow && k != next) }
        if (depth == 0) return
        val data = withContext(Dispatchers.IO) { PrepStore.get(c) }
        for (ep in window) {
            val key = episodeKey(s.api, s.detail.url, s.version, ep.data)
            if (prepared.containsKey(key)) continue
            if (data.isMiss(key, now)) continue
            val tr = tries[key]
            val at = AnticipationPolicy.retryAt(tr?.count ?: 0, tr?.at ?: 0L) ?: continue
            if (now < at) continue
            run(c, s, ep, key, data)
            return // one episode per tick: the next tick re-checks heat, memory and playback first
        }
    }

    private suspend fun run(c: Context, s: OnlineSession.State, ep: OnlineEpisode, key: String, data: PrepData) {
        val t = Target(s.api, s.detail, s.version, ep, s.link?.name)
        val series = t.series
        asked += key
        Log.i(TAG, "prepare ${s.detail.name} ${ep.label} (${s.version})")
        var outcome: Outcome? = null
        val work = scope.launch(PriorityGate.Lane(PriorityGate.BACKGROUND)) {
            outcome = runCatching {
                prepare(t, sourcesForTest ?: LiveSources(c), data.matchesFor(series), { api, url -> runCatching { PrepStore.update(c) { it.match(series, api, url, System.currentTimeMillis()) } } })
            }.getOrElse { if (it is CancellationException) throw it; Outcome.Failed(it.message ?: it.javaClass.simpleName) }
        }
        // Playback first: a stall, a hot phone, memory pressure or another title cancels the job at once.
        while (work.isActive) {
            delay(1_000)
            val cur = OnlineSession.state.value
            val sig = signals(c)
            val sameTitle = cur != null && cur.api == s.api && cur.detail.url == s.detail.url && cur.version == s.version
            val playing = cur?.phase == OnlineSession.Phase.PLAYING
            val buffering = dev.asfakul.yumeiro.player.PlaybackDiagnostics.state.value.state == "BUFFERING"
            if (!sameTitle || !playing || buffering || AnticipationPolicy.why(sig.copy(playbackSteady = true)) != AnticipationPolicy.Why.OK) {
                Log.i(TAG, "paused (${if (!sameTitle) "title changed" else if (!playing || buffering) "playback first" else AnticipationPolicy.why(sig)})")
                work.cancel(); break
            }
        }
        work.join()
        val now = System.currentTimeMillis()
        when (val o = outcome) {
            is Outcome.Ready -> {
                val cur = OnlineSession.state.value
                if (cur != null && cur.api == s.api && cur.detail.url == s.detail.url && cur.version == s.version) {
                    prepared[key] = o.prepared
                    while (prepared.size > MAX_PREPARED) prepared.entries.minByOrNull { it.value.at }?.let { prepared.remove(it.key) } ?: break
                    tries.remove(key)
                    Log.i(TAG, "ready ${ep.label}: ${o.prepared.links.size} links, ${o.prepared.subtitles.size} subtitles${o.prepared.via?.let { " (alternative from $it)" }.orEmpty()}")
                }
            }
            Outcome.Miss -> { withContext(Dispatchers.IO) { PrepStore.update(c) { it.miss(series, key, now) } }; Log.i(TAG, "miss ${ep.label}: no source has it") }
            is Outcome.Failed -> { tries[key] = Tries((tries[key]?.count ?: 0) + 1, now); Log.i(TAG, "not ready ${ep.label}: ${o.why}") }
            null -> Unit // cancelled: not counted, tried again when playback is steady
        }
    }

    // endregion
}
