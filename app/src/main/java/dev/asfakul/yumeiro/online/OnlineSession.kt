/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.util.Log
import dev.asfakul.yumeiro.data.model.VideoItem
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.online.web.HeadlessCapture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What's playing from an extension: the title, its episode list (current Sub/Dub version), the
 * resolved links and which one is in use. Drives direct play (no "pick a source" step), the
 * in-player Episodes / Sources panels, next episode, automatic fallback to the next source when
 * one fails, and watch progress.
 */
object OnlineSession {
    private const val TAG = "OnlineSession"
    /**
     * After the first playable link arrives, wait this long for a better one in the same burst
     * before starting. Round 13: was 2 s; playback now starts almost the moment the user taps.
     */
    private const val GATHER_MS = 350L
    /** Sources are shown (and handed to the UI) ten at a time; "Load more" adds ten. */
    const val PAGE = 10
    /** Hard cap on waiting for any link. */
    private const val FIRST_LINK_TIMEOUT_MS = 80_000L

    enum class Phase { RESOLVING, PLAYING, FAILED }

    data class State(
        val api: String,
        val detail: OnlineDetail,
        val version: String,
        /** Every episode of [version] in order (all seasons); empty for movies / live. */
        val episodes: List<OnlineEpisode>,
        /** Index into [episodes]; -1 for movies / live. */
        val index: Int,
        val phase: Phase = Phase.RESOLVING,
        val links: List<OnlineLink> = emptyList(),
        val subtitles: List<OnlineSubtitle> = emptyList(),
        val linkIndex: Int = 0,
        val linksDone: Boolean = false,
        val skipped: Int = 0,
        val message: String? = null,
        val videoId: String? = null,
        /** Links that already failed in this session (skipped by automatic fallback). */
        val failed: Set<String> = emptySet(),
        /** Fresh `loadLinks` already done after every link was refused (tokens may have expired). */
        val refreshed: Boolean = false,
        /** Headless page capture already tried for this episode. */
        val captured: Boolean = false,
        /** Round 13: how many sources the lists show ([PAGE] at a time, "Load more" adds a page). */
        val visible: Int = PAGE,
        /** B17: other extensions that have this title and gave links (their links carry [OnlineLink.via]). */
        val others: List<String> = emptyList(),
        /** B17: every link so far failed while sources were still arriving; the next one that arrives plays (at [awaitPos]). */
        val awaiting: Boolean = false,
        val awaitPos: Long = 0L,
    ) {
        /** Sources the lists show right now (the playing one always included). */
        val shownLinks: Int get() = maxOf(visible, linkIndex + 1).coerceAtMost(links.size)
        val hiddenLinks: Int get() = (links.size - shownLinks).coerceAtLeast(0)
        val episode: OnlineEpisode? get() = episodes.getOrNull(index)
        val data: String? get() = episode?.data ?: detail.playData
        val hasNext: Boolean get() = index >= 0 && index < episodes.lastIndex
        val hasPrevious: Boolean get() = index > 0
        val title: String get() = titleFor(detail, episode)
        val link: OnlineLink? get() = links.getOrNull(linkIndex)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /**
     * Round 13: source gathering, de-duplication and sorting run here, one event at a time, so
     * the main thread only receives finished state (it used to do all of it, a cause of ANRs).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val gather = Dispatchers.Default.limitedParallelism(1)
    private var lastFailoverAt = 0L
    private var failoverJob: kotlinx.coroutines.Job? = null
    private const val FAILOVER_GAP_MS = 900L
    private const val PUBLISH_GAP_MS = 700L
    private val _state = MutableStateFlow<State?>(null)
    /** BUG-1 (D4): the session that just closed, for the player's final position save. */
    @Volatile private var ended: State? = null
    val state: StateFlow<State?> = _state.asStateFlow()
    private var job: Job? = null

    private val _opening = MutableStateFlow<OnlineItem?>(null)
    /** A tapped title whose details are loading (before playback or the episode list). */
    val opening: StateFlow<OnlineItem?> = _opening.asStateFlow()
    private val _handoff = MutableStateFlow(false)
    /** OWN-1: the title page is already pushed under the loading layer; Back belongs to the page. */
    val handoff: StateFlow<Boolean> = _handoff.asStateFlow()

    /**
     * Tap on a poster: movies and live streams start playing straight away; series (or anything
     * that can't play directly) go to [onShowTitle] — the episode list.
     */
    fun open(context: Context, item: OnlineItem, onShowTitle: (OnlineItem) -> Unit) {
        val app = context.applicationContext
        job?.cancel()
        // OWN-6: a source known to open a title page goes there at once; the page shows the card while the
        // details load (CloudStream / Hikari open the details shell first too).
        if (OpenFlow.pageFirst(dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FAST_TITLE_OPEN), OpeningKinds.opensPage(app, item))) {
            _opening.value = null
            onShowTitle(item)
            job = scope.launch {
                val provider = kotlinx.coroutines.withContext(Dispatchers.Default) { ExtensionManager.provider(item.api) }
                val detail = provider?.let { p -> runCatching { OnlineRepository.load(p, item.url) }.getOrNull() }
                if (detail != null) OpeningKinds.learn(app, item, opensPage = !OpenFlow.direct(detail))
            }
            return
        }
        _opening.value = item
        job = scope.launch {
            val provider = kotlinx.coroutines.withContext(Dispatchers.Default) { ExtensionManager.provider(item.api) }
            val detail = provider?.let { p -> runCatching { OnlineRepository.load(p, item.url) }.onFailure { Log.w(TAG, "open ${item.name}", it) }.getOrNull() }
            if (_opening.value != item) return@launch
            val direct = detail != null && OpenFlow.direct(detail)
            if (!direct && dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FAST_TITLE_OPEN)) {
                // OWN-1: the page slides in under the loading layer, which goes only after the slide. Before, the
                // layer vanished first, the feed flashed, then the page came in: it looked like a page closing.
                if (detail != null) OpeningKinds.learn(app, item, opensPage = true)
                onShowTitle(item)
                _handoff.value = true
                try { kotlinx.coroutines.delay(OpenFlow.HANDOFF_MS) } finally { _handoff.value = false }
                if (_opening.value == item) _opening.value = null
                return@launch
            }
            _opening.value = null
            // Round 13: remembered so the next tap on this source shows the right skeleton.
            if (detail != null) OpeningKinds.learn(app, item, opensPage = !direct)
            when {
                direct -> play(app, detail!!)
                else -> onShowTitle(item) // episodes to choose, or the title page explains the problem
            }
        }
    }

    /**
     * Round 13: the version that plays when the user didn't pick one, from Settings → Audio →
     * Quality and audio → Default audio (Sub unless the user chose Dub).
     */
    fun defaultVersion(versions: List<String>): String {
        val dub = dev.asfakul.yumeiro.player.VideoPrefs.audio.value == dev.asfakul.yumeiro.player.VideoPrefs.AudioDefault.DUB
        val want = if (dub) "dub" else "sub"
        return versions.firstOrNull { it.contains(want, ignoreCase = true) } ?: versions.firstOrNull().orEmpty()
    }

    private fun defaultAudioKey(): String =
        if (dev.asfakul.yumeiro.player.VideoPrefs.audio.value == dev.asfakul.yumeiro.player.VideoPrefs.AudioDefault.DUB) AudioLanguages.DUB_OTHER else AudioLanguages.ORIG

    fun cancelOpening() { _opening.value = null; _handoff.value = false; job?.cancel() }

    fun titleFor(d: OnlineDetail, e: OnlineEpisode?): String = buildString {
        append(d.name)
        if (e == null) return@buildString
        if (e.season != null) append(" · S").append(e.season)
        if (e.episode != null) append(if (e.season != null) "E" else " · E").append(e.episode)
        e.name?.takeIf { it.isNotBlank() && !it.equals("Episode ${e.episode}", true) }?.let { append(" · ").append(it) }
    }

    fun videoIdFor(api: String, data: String) = "online:" + "$api|$data".hashCode().toUInt().toString(16)

    fun isOnline(video: VideoItem?) = video != null && video.id.startsWith("online:") && _state.value?.videoId == video.id

    /** All episodes of [version] in watch order. */
    fun episodesOf(detail: OnlineDetail, version: String): List<OnlineEpisode> = labelledEpisodes(detail, version).map { it.first }

    private val watchOrder = compareBy<OnlineEpisode>({ it.season ?: 0 }, { it.episode ?: Int.MAX_VALUE })
    private fun inWatchOrder(list: List<OnlineEpisode>): List<OnlineEpisode> =
        list.sortedWith(watchOrder).let { sorted -> if (sorted.all { it.episode == null }) list else sorted }

    /** NBUG-5: language marker an extension appends to an episode name, e.g. "The Journey's End (Spanish)" → "Spanish". */
    internal fun languageTag(e: OnlineEpisode): String? =
        e.name?.let { EPISODE_LANGUAGE_TAG.find(it) }?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Episodes of [version] in watch order, each with the label of its language run (null when the list is
     * a single language — the usual case, unchanged).
     *
     * NBUG-5 (signal D): some extensions put several languages in one Sub/Dub list, one run per language
     * ("… (Spanish)" 1..12, then "… (Dub)" 1..12). Sorting the whole list by number interleaved the runs, so
     * "next" went from Dub 1 to Spanish 1 and the grid read 1, 1, 1, 2, 2, 2. When the list has repeated
     * numbers AND at least two different name tags, each run (tag + how many times that number was already
     * seen under the tag) is kept together and sorted on its own; the run named like the version comes
     * first, the rest keep the extension's order. Nothing is dropped or merged.
     */
    fun labelledEpisodes(detail: OnlineDetail, version: String): List<Pair<OnlineEpisode, String?>> {
        val raw = detail.episodes[version].orEmpty()
        val keys = raw.map { it.season to it.episode }
        val repeats = keys.size != keys.toSet().size
        val tags = if (repeats) raw.map(::languageTag) else emptyList()
        if (!repeats || tags.filterNotNull().distinct().size < 2) return inWatchOrder(raw).map { it to null }
        val seen = HashMap<Triple<String?, Int?, Int?>, Int>()
        val runs = LinkedHashMap<Pair<String?, Int>, MutableList<OnlineEpisode>>()
        raw.forEachIndexed { i, e ->
            val n = seen.merge(Triple(tags[i], e.season, e.episode), 1) { a, b -> a + b }!!
            runs.getOrPut(tags[i] to n) { ArrayList() }.add(e)
        }
        val order = runs.keys.sortedBy { (tag, n) -> if (n == 1 && tag != null && tag.equals(version, ignoreCase = true)) 0 else 1 } // stable
        return order.flatMap { key ->
            val label = key.first?.let { if (key.second == 1) it else "$it ${key.second}" }
            inWatchOrder(runs.getValue(key)).map { it to label }
        }
    }

    /** Number of different episodes (season + number) in [list]; the header count when runs repeat numbers. */
    fun distinctEpisodeCount(list: List<OnlineEpisode>): Int =
        if (list.any { it.episode == null }) list.size else list.distinctBy { it.season to it.episode }.size

    /**
     * Resolves and plays [episodeIndex] of [version] (or the movie when there are no episodes).
     * [resumeMs] null → resume from watch history.
     */
    fun play(context: Context, detail: OnlineDetail, version: String = defaultVersion(detail.versions), episodeIndex: Int = -1, resumeMs: Long? = null) {
        val app = context.applicationContext
        OnlineHistory.ensureLoaded(app)
        val episodes = episodesOf(detail, version)
        val index = if (detail.playData != null && episodes.isEmpty()) -1 else episodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
        val s = State(detail.api, detail, version, episodes, if (episodes.isEmpty()) -1 else index)
        if (s.data == null) {
            _state.value = s.copy(phase = Phase.FAILED, message = detail.unsupportedReason ?: "There's nothing to play for this title.")
            return
        }
        _state.value = s
        dev.asfakul.yumeiro.player.StartTimeline.begin("${detail.api}: ${detail.name}")
        dev.asfakul.yumeiro.data.PlayCounts.record(
            app, dev.asfakul.yumeiro.data.PlayCounts.Kind.ONLINE, "${detail.api}|${detail.url}", detail.name, detail.api,
            detail.posterUrl, detail.posterHeaders, api = detail.api, url = detail.url,
            adult = AdultContent.isAdult(detail.api, detail.type), // NBUG-11 (ADR-023)
        )
        resolve(app, resumeMs)
    }

    private fun resolve(context: Context, resumeMs: Long?) {
        job?.cancel()
        val start = _state.value ?: return
        val data = start.data ?: return
        job = scope.launch(gather) {
            AudioLanguages.init(context)
            OtherSources.init(context)
            val provider = ExtensionManager.provider(start.api)
            if (provider == null) {
                _state.update { it?.copy(phase = Phase.FAILED, message = "The extension “${start.api}” isn't installed or is turned off.") }
                return@launch
            }
            dev.asfakul.yumeiro.player.StartTimeline.mark("provider")
            var started = false
            val begun = System.currentTimeMillis()
            var firstAt = 0L
            var firstOwnAt = 0L
            var ownDone = false
            var ownError: String? = null
            val links = LinkedHashMap<String, OnlineLink>()
            val subs = LinkedHashMap<String, OnlineSubtitle>()
            val matched = LinkedHashSet<String>()
            var skipped = 0
            // B17: other extensions join at once (PARALLEL), or once the own one is slow, empty or refused (WHEN_NEEDED).
            val mode = OtherSources.mode.value
            val need = CompletableDeferred<Boolean>()
            if (mode == OtherSources.Mode.PARALLEL || start.refreshed) need.complete(true)
            val others = if (mode == OtherSources.Mode.OFF) emptyList() else OtherSources.candidates(
                start.api, OtherSources.seriesOf(start.detail.type) ?: (start.episode != null).takeIf { it },
                AdultContent.isAdult(start.detail.api, start.detail.type),
                ExtensionManager.providers(), { it is dev.asfakul.yumeiro.sources.lookup.StreamLookupProvider },
            )
            // Extensions can emit dozens of links/subtitles in a burst. They are gathered silently
            // here and handed to the UI in staggered batches, never one recomposition per link.
            var lastPublish = 0L
            var pendingPublish = false
            fun publish(done: Boolean, error: String? = null) {
                val t = System.currentTimeMillis()
                if (!done && t - lastPublish < PUBLISH_GAP_MS) { pendingPublish = true; return }
                lastPublish = t; pendingPublish = false
                val audio = AudioLanguages.preferred.value ?: defaultAudioKey()
                val v = _state.value?.version.orEmpty()
                val h = dev.asfakul.yumeiro.player.VideoPrefs.quality.value.height
                // B17: the title's own extension stays first (Hikari learned that a same-named video from elsewhere must never jump ahead).
                val (own, other) = links.values.partition { it.via == null }
                val sorted = OnlineRepository.sortLinks(own, audio, v, h) + OnlineRepository.sortLinks(other, audio, v, h)
                val subList = subs.values.toList()
                _state.update { cur ->
                    if (cur == null || cur.data != data) return@update cur
                    // Keep the link in use at the same position when the list re-sorts.
                    val keep = cur.link?.url
                    val idx = keep?.let { u -> sorted.indexOfFirst { it.url == u } }?.takeIf { it >= 0 } ?: cur.linkIndex
                    cur.copy(links = sorted, subtitles = subList, linkIndex = idx, linksDone = done, skipped = skipped, others = matched.toList(),
                        message = if (done && sorted.isEmpty()) error ?: "No playable sources were found." else cur.message)
                }
                resumeAwaiting(context, data, done)
            }
            suspend fun startIfReady(force: Boolean) {
                if (started || links.isEmpty()) return
                val now = System.currentTimeMillis()
                val ownPlayable = links.values.any { it.via == null && !it.audioOnly }
                val anyPlayable = links.values.any { !it.audioOnly }
                val ready = force || when {
                    // Starts on the first link that has a picture (an audio-only link waits for the burst).
                    ownPlayable -> now - firstOwnAt >= GATHER_MS
                    // B17: the fastest working source wins once the own extension is slow or has nothing.
                    anyPlayable -> ownDone || now - begun >= OtherSources.OWN_HOLD_MS
                    else -> false
                }
                if (!ready) return
                started = true
                lastPublish = 0L
                publish(false)
                kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                    if (_state.value?.data == data) playCurrent(context, 0, resumeMs)
                }
            }
            // Round 2 (heat): 300 ms instead of 150 ms, slower again when the phone is warm.
            val ticker = launch {
                while (true) {
                    delay(dev.asfakul.yumeiro.player.HeatGuard.interval(300))
                    if (!need.isCompleted && System.currentTimeMillis() - begun >= OtherSources.OWN_HOLD_MS && links.values.none { it.via == null && !it.audioOnly }) need.complete(true)
                    startIfReady(false); if (pendingPublish && started) publish(false)
                }
            }
            // F-05-N (ADR-057): stream-only add-ons (Nuvio scrapers) join with their own links when on.
            val ownFlow = StreamAddons.withAddons(context, start.detail, start.episode, OnlineRepository.links(provider, data))
                .map { OtherSources.Hit.Event("", it) as OtherSources.Hit }
            val sweep = OtherSources.sweep(start.detail, start.version, start.episode, others, need)
            val ok = withTimeoutOrNull(FIRST_LINK_TIMEOUT_MS + 60_000L) {
                merge(ownFlow, sweep).transformWhile { ev ->
                    emit(ev)
                    // Round 2 (heat): once a video plays and enough backups are in, stop asking the
                    // extensions for more mirrors (each one is more network, parsing and battery).
                    !GatherCap.enough(started, links.values.count { !it.audioOnly }, subs.size, System.currentTimeMillis() - firstAt)
                }.collect { hit ->
                    val via = hit.api.ifEmpty { null }
                    if (hit is OtherSources.Hit.Matched) { matched += hit.api; return@collect }
                    when (val ev = (hit as OtherSources.Hit.Event).event) {
                        is LinkEvent.Link -> {
                            val now = System.currentTimeMillis()
                            if (links.isEmpty()) { firstAt = now; dev.asfakul.yumeiro.player.StartTimeline.mark("first link", ev.link.name) }
                            if (via == null && firstOwnAt == 0L) firstOwnAt = now
                            links.putIfAbsent(ev.link.url, if (via == null) ev.link else ev.link.copy(via = via))
                            if (started) publish(false) else startIfReady(false)
                        }
                        is LinkEvent.Subtitle -> { subs.putIfAbsent(ev.subtitle.url, ev.subtitle); if (started) publish(false) }
                        is LinkEvent.Skipped -> skipped++
                        is LinkEvent.Done -> if (via == null && !ownDone) {
                            ownDone = true; ownError = ev.error
                            // WHEN_NEEDED: an own extension that gave a picture skips the sweep; one that gave nothing starts it.
                            need.complete(links.values.none { it.via == null && !it.audioOnly })
                            startIfReady(false)
                        }
                    }
                }
                true
            }
            ticker.cancel()
            need.complete(false)
            if (ok == true && _state.value?.let { it.data == data && !it.linksDone } == true) publish(true, ownError)
            if (ok == null) publish(true, "The source took too long to answer.")
            startIfReady(true)
            if (!started) _state.update { it?.copy(phase = Phase.FAILED, message = it.message ?: "No playable sources were found.") }
        }
    }

    /**
     * B17: failover found every link so far dead while other extensions were still answering. The first new
     * link that hasn't failed plays; when the search ends with none, the usual last steps run.
     */
    private fun resumeAwaiting(context: Context, data: String, done: Boolean) {
        val cur = _state.value ?: return
        if (!cur.awaiting || cur.data != data) return
        val i = cur.links.indexOfFirst { it.url !in cur.failed }
        if (i < 0 && !done) return
        _state.update { it?.copy(awaiting = false, phase = Phase.PLAYING) }
        scope.launch {
            if (_state.value?.data != data) return@launch
            if (i >= 0) { Log.w(TAG, "failover (late source) → ${cur.links[i].name}"); playCurrent(context, i, cur.awaitPos) }
            else if (!failover(context, cur.awaitPos)) _state.update { it?.copy(phase = Phase.FAILED, message = it.message ?: "Every source failed to play.") }
        }
    }

    /** "Load more" in the source lists: shows the next [PAGE] sources (already gathered in the background). */
    fun loadMore() {
        _state.update { it?.copy(visible = (it.shownLinks + PAGE).coerceAtMost(it.links.size.coerceAtLeast(PAGE))) }
    }

    private fun playCurrent(context: Context, linkIndex: Int, resumeMs: Long?) {
        val s = _state.value ?: return
        val link = s.links.getOrNull(linkIndex) ?: return
        val data = s.data ?: return
        // NBUG-12 NB12-4: after the fresh loadLinks the same link must really play again. With the same
        // id IncomingMedia publishes an equal item, its StateFlow drops it, the player never reloads
        // and the session sat on the old error forever (the last resort was never reached).
        val id = videoIdFor(s.api, data) + ":" + link.url.hashCode().toUInt().toString(16) + (if (s.refreshed) "r" else "")
        val resume = resumeMs ?: OnlineHistory.progressOf(s.api, data)?.takeIf { !it.finished }?.positionMs ?: 0L
        _state.update { cur -> (cur ?: s).copy(phase = Phase.PLAYING, linkIndex = linkIndex, videoId = id, message = null, visible = maxOf(s.visible, linkIndex + 1)) }
        dev.asfakul.yumeiro.player.StartTimeline.mark("player start", link.qualityLabel)
        Log.i(TAG, "play ${s.title} via ${link.name}${link.via?.let { " from $it" }.orEmpty()} (${link.qualityLabel}, ${link.formatLabel}) ${link.url.take(120)}")
        // B17: a link from another extension uses that extension's interceptors and site address.
        val provider = ExtensionManager.provider(link.via ?: s.api)
        OnlinePlayback.registerInterceptors(provider, link)
        val request = OnlinePlayback.request(s.title, link, s.subtitles, s.detail.posterUrl)
        // ANIME-3: a source that links a YouTube watch page (Yumeiro Anime's official channels) plays
        // through the app's own YouTube streams, as the YouTube tab does; the stream resolver only
        // hands YouTube links to the YouTube app, so the episode never played in Yumeiro.
        val ytId = if (OnlinePlayback.isYouTubePage(link.url)) dev.asfakul.yumeiro.youtube.YouTube.idFrom(link.url) else null
        if (ytId != null) { playYouTube(context, ytId, request, id, resume, s.detail.api); return }
        val site = runCatching { provider?.mainUrl }.getOrNull()
        AccessLadder.reset(request.url); AccessLadder.hint(request.url, site)
        IncomingMedia.open(context, request, id = id, positionMs = resume, folder = s.detail.api)
    }

    private var ytJob: Job? = null

    private fun playYouTube(context: Context, ytId: String, base: dev.asfakul.yumeiro.stream.StreamRequest, id: String, resume: Long, folder: String) {
        ytJob?.cancel()
        val app = context.applicationContext
        ytJob = scope.launch {
            runCatching { dev.asfakul.yumeiro.youtube.YouTubeStreams.videoRequest(app, ytId) }
                .onSuccess { yt ->
                    if (_state.value?.videoId != id) return@onSuccess // another episode or source was picked meanwhile
                    Log.i(TAG, "youtube $ytId -> ${yt.format}")
                    // Group YouTube: YouTubePlayback's refresher renews an expired or refused link (403).
                    IncomingMedia.open(app, yt.copy(title = base.title, logoUrl = base.logoUrl ?: yt.logoUrl,
                        subtitles = base.subtitles + yt.subtitles), id = id, positionMs = resume, folder = folder)
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) return@onFailure
                    Log.w(TAG, "youtube $ytId failed: ${e.javaClass.simpleName}: ${e.message}")
                    if (_state.value?.videoId != id) return@onFailure
                    _state.update { it?.copy(phase = Phase.FAILED, message = dev.asfakul.yumeiro.youtube.YtAccount.refusalMessage(e) ?: "YouTube couldn't play this episode: ${OnlineErrors.friendly(e)}") }
                }
        }
    }

    /** Switches to another link of the same episode, keeping the position. */
    /** Adds a subtitle found by search and reloads the current source at [positionMs]. */
    fun addSubtitle(context: Context, subtitle: OnlineSubtitle, positionMs: Long) {
        val s = _state.value ?: return
        _state.value = s.copy(subtitles = (listOf(subtitle) + s.subtitles).distinctBy { it.url })
        if (s.link != null) playCurrent(context.applicationContext, s.linkIndex, positionMs)
    }

    fun switchSource(context: Context, linkIndex: Int, positionMs: Long) {
        val s = _state.value ?: return
        if (linkIndex !in s.links.indices) return
        // R15: a manual pick wins. A failover that was still waiting for its gap used to fire after
        // the pick and replace it; and the picked link gets a fresh chance (it may have failed before),
        // so when it is dead the normal rotation moves on from it again.
        failoverJob?.cancel(); failoverJob = null
        val picked = s.links[linkIndex].url
        if (picked in s.failed) _state.update { cur -> cur?.copy(failed = cur.failed - picked) }
        playCurrent(context.applicationContext, linkIndex, positionMs)
    }

    /**
     * Device tests only: adds [link] (for example a dead one) to the current episode's sources and
     * returns its index, so a test can pick it by hand through [switchSource].
     */
    /** Device tests only (Group Y): show [s] as the current session without resolving or playing anything. */
    @androidx.annotation.VisibleForTesting
    internal fun setStateForTest(s: State?) { _state.value = s }

    @androidx.annotation.VisibleForTesting
    internal fun addLinkForTest(link: OnlineLink): Int {
        var index = -1
        _state.update { cur -> cur?.let { index = it.links.size; it.copy(links = it.links + link) } }
        return index
    }

    /**
     * The current link failed: tries the next one automatically. Returns false when there's
     * nothing left to try (the player then shows its error).
     */
    fun failover(context: Context, positionMs: Long, fromVideoId: String? = null): Boolean {
        val s = _state.value ?: return false
        // Single owner: only the player that is playing the current link may fail it over. A late
        // error from a player that was already replaced (or a second error for the same link while
        // a refresh / page capture runs) must not skip a link or tear the new one down.
        if (fromVideoId != null && s.videoId != null && fromVideoId != s.videoId) return true
        if (s.phase == Phase.RESOLVING) return true
        val app = context.applicationContext
        val failed = s.failed + (s.link?.url ?: "")
        val next = s.links.indices.firstOrNull { it != s.linkIndex && s.links[it].url !in failed }
        if (next != null) {
            _state.update { cur -> (cur ?: s).copy(failed = failed) }
            Log.w(TAG, "failover → ${s.links[next].name}")
            // Dead links can fail within milliseconds of each other; tearing players down and up
            // that fast starved the main thread (ANR / force close). Space failovers out.
            val now = System.currentTimeMillis()
            val wait = (lastFailoverAt + FAILOVER_GAP_MS - now).coerceIn(0L, FAILOVER_GAP_MS)
            lastFailoverAt = now + wait
            failoverJob?.cancel()
            val nextUrl = s.links[next].url
            val go = { val cur = _state.value; val i = cur?.links?.indexOfFirst { it.url == nextUrl } ?: -1; if (cur?.data == s.data && i >= 0) playCurrent(app, i, positionMs) }
            if (wait == 0L) go()
            else failoverJob = scope.launch { delay(wait); go() }
            return true
        }
        // B17: other extensions are still answering; the next link that arrives plays (see resumeAwaiting).
        if (!s.linksDone) {
            Log.w(TAG, "failover: no link left yet, waiting for the sources still answering")
            _state.update { cur -> (cur ?: s).copy(failed = failed, awaiting = true, awaitPos = positionMs, phase = Phase.RESOLVING, message = "Trying other sources…") }
            return true
        }
        // Every link refused. Signed links expire / bind to the extraction session, so extract
        // a fresh set once before giving up (CloudStream users do this by hand with "reload").
        if (!s.refreshed && s.linksDone) {
            Log.w(TAG, "all links failed → fresh loadLinks")
            _state.value = s.copy(phase = Phase.RESOLVING, links = emptyList(), linkIndex = 0, linksDone = false,
                message = "Refreshing sources…", failed = emptySet(), refreshed = true)
            resolve(app, positionMs)
            return true
        }
        // Last resort: play what the site's own page plays. First the stream its HTML publishes
        // (NBUG-12: KVS player config, <video>, JSON-LD; one request through the app's DNS), then the
        // page in a headless WebView (cookies, UA and referer of a real browser).
        val page = pageFor(s)
        if (!s.captured && page != null) {
            Log.w(TAG, "fresh links failed too → source page $page")
            _state.value = s.copy(phase = Phase.RESOLVING, message = "Opening the source page…", failed = failed, captured = true)
            job?.cancel()
            job = scope.launch {
                val site = runCatching { ExtensionManager.provider(s.api)?.mainUrl }.getOrNull()
                val fromHtml = fromPageHtml(app, page, s.title, site)
                Log.w(TAG, if (fromHtml != null) "source page HTML gave a stream" else "source page HTML gave nothing → headless capture")
                val req = fromHtml ?: HeadlessCapture.capture(app, page, s.title, referer = site)
                val cur = _state.value ?: return@launch
                if (cur.data != s.data) return@launch
                if (req == null) {
                    _state.value = cur.copy(phase = Phase.FAILED, message = "Every source refused playback, even from the site's own page.")
                    dev.asfakul.yumeiro.ui.components.AppMessages.error(app, "Every source refused playback (HTTP 403), even from the site's own page. Try another episode or update the extension.")
                    return@launch
                }
                val id = videoIdFor(cur.api, cur.data ?: "") + ":web"
                _state.value = cur.copy(phase = Phase.PLAYING, videoId = id, message = null)
                IncomingMedia.open(app, req, id = id, positionMs = positionMs, folder = cur.detail.api)
            }
            return true
        }
        _state.value = s.copy(failed = failed)
        return false
    }

    /**
     * NBUG-12: the stream [page]'s HTML publishes for its own player, checked reachable by
     * [dev.asfakul.yumeiro.stream.StreamResolver] (it probes each candidate), or null.
     */
    private suspend fun fromPageHtml(app: Context, page: String, title: String, site: String?): dev.asfakul.yumeiro.stream.StreamRequest? = runCatching {
        val referer = site?.takeIf { it.isNotBlank() }?.let { if (it.endsWith("/")) it else "$it/" }
        val r = dev.asfakul.yumeiro.stream.StreamResolver.resolve(app,
            dev.asfakul.yumeiro.stream.StreamRequest(url = page, title = title, referer = referer, userAgent = com.lagradost.cloudstream3.USER_AGENT))
        (r as? dev.asfakul.yumeiro.stream.Resolution.Playable)?.request?.takeIf { it.url != page }
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Log.w(TAG, "source page HTML", it) }.getOrNull()

    /** The web page that plays this episode (NBUG-12: never a refused stream file), see [CapturePage]. */
    private fun pageFor(s: State): String? =
        CapturePage.pick(s.links.map { it.referer }, s.links.map { it.url }, s.data, s.detail.url)

    fun playEpisode(context: Context, index: Int) {
        val s = _state.value ?: return
        if (index !in s.episodes.indices) return
        _state.value = s.copy(index = index, videoId = null, phase = Phase.RESOLVING, links = emptyList(), subtitles = emptyList(), linkIndex = 0, linksDone = false, skipped = 0, message = null, failed = emptySet(), visible = PAGE,
            // NBUG-12 NB12-5: each episode gets its own refresh and page step.
            refreshed = false, captured = false)
        resolve(context.applicationContext, null)
    }

    fun next(context: Context) { _state.value?.let { if (it.hasNext) playEpisode(context, it.index + 1) } }
    fun previous(context: Context) { _state.value?.let { if (it.hasPrevious) playEpisode(context, it.index - 1) } }

    /** Switches Sub ↔ Dub, staying on the same episode number when it exists. */
    /** True when [version] (e.g. "Dub") has the episode that is playing right now. */
    fun hasEpisodeIn(version: String): Boolean {
        val s = _state.value ?: return false
        return EpisodeMatcher.match(s.episode, episodesOf(s.detail, version)) >= 0
    }

    fun switchVersion(context: Context, version: String) {
        val s = _state.value ?: return
        if (version == s.version || version !in s.detail.versions) return
        val eps = episodesOf(s.detail, version)
        val cur = s.episode
        val idx = EpisodeMatcher.match(cur, eps)
        if (idx < 0) {
            // The other version doesn't have this episode: keep playing instead of jumping to a
            // different episode that happens to sit at the same list position.
            val what = cur?.episode?.let { "episode $it" } ?: "this episode"
            dev.asfakul.yumeiro.ui.components.AppMessages.show(context, "${version.ifBlank { "That version" }} isn't available for $what yet. Still playing ${s.version.ifBlank { "the current version" }}.", dev.asfakul.yumeiro.ui.components.AppMessages.Kind.WARNING)
            return
        }
        _state.value = s.copy(version = version, episodes = eps, index = idx, videoId = null, phase = Phase.RESOLVING, links = emptyList(), subtitles = emptyList(), linkIndex = 0, linksDone = false, message = null, failed = emptySet(), visible = PAGE,
            refreshed = false, captured = false) // NBUG-12 NB12-5
        resolve(context.applicationContext, null)
    }

    fun retry(context: Context) {
        val s = _state.value ?: return
        _state.value = s.copy(phase = Phase.RESOLVING, links = emptyList(), linkIndex = 0, linksDone = false, message = null, failed = emptySet(), visible = PAGE,
            refreshed = false, captured = false) // NBUG-12 NB12-5
        resolve(context.applicationContext, null)
    }

    /** Progress callback from the player. */
    fun onProgress(context: Context, videoId: String, positionMs: Long, durationMs: Long) {
        // BUG-1 (D4): Close runs end() before the player's last save; that save still belongs here.
        val s = _state.value?.takeIf { it.videoId == videoId } ?: ended?.takeIf { it.videoId == videoId } ?: return
        OnlineHistory.record(context.applicationContext, s.detail, s.version, s.episode, positionMs, durationMs)
        // B20: the same progress feeds this profile's taste (start / substantial / complete / rewatch marks).
        dev.asfakul.yumeiro.intel.TasteRefs.online(s.detail)?.let { ref ->
            runCatching { dev.asfakul.yumeiro.intel.TasteSignals.played(context.applicationContext, ref, s.episode?.data, positionMs, durationMs, s.index, s.episodes.size) }
        }
        dev.asfakul.yumeiro.online.anime.AnimeSync.onProgress(context, s.detail, s.episode, s.index, positionMs, durationMs)
    }

    /** Placeholder for the player's "Up next" card. */
    fun nextItem(): VideoItem? {
        val s = _state.value ?: return null
        if (!s.hasNext) return null
        val e = s.episodes[s.index + 1]
        return VideoItem(id = "online-next:" + e.data.hashCode(), title = titleFor(s.detail, e), durationMs = 0L, uri = "", thumbnailUri = e.posterUrl ?: s.detail.posterUrl, resolution = "", folderName = s.detail.api)
    }

    /** The player closed, or the user cancelled resolving. */
    fun end() {
        job?.cancel(); job = null; failoverJob?.cancel(); failoverJob = null
        ended = _state.value?.takeIf { it.videoId != null }
        _state.value = null
    }

    /** Cancels resolving but keeps the session if something is already playing. */
    fun cancelResolving() {
        val s = _state.value ?: return
        if (s.phase != Phase.PLAYING) end()
    }
}

/** Round 2 (heat): when collecting more backup links stops being worth it. */
internal object GatherCap {
    /** Subtitles often arrive late, so without any we keep listening for [SUB_WAIT_MS]. */
    const val SUB_WAIT_MS = 20_000L
    fun enough(started: Boolean, playable: Int, subtitles: Int, sinceFirstMs: Long, cap: Int = dev.asfakul.yumeiro.player.HeatGuard.linkCap()): Boolean =
        started && playable >= cap && (subtitles > 0 || sinceFirstMs >= SUB_WAIT_MS)
}

/** NBUG-5: trailing "(Spanish)"-style language marker; top level so RegexWarmup can load it off main (OnlineSessionKt). */
private val EPISODE_LANGUAGE_TAG = Regex("""\((\p{L}[\p{L} .\-]{1,23})\)\s*$""")
