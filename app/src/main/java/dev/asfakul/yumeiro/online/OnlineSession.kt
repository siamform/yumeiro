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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** After the first link arrives, wait this long for better ones before starting. */
    private const val GATHER_MS = 2_000L
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
    ) {
        val episode: OnlineEpisode? get() = episodes.getOrNull(index)
        val data: String? get() = episode?.data ?: detail.playData
        val hasNext: Boolean get() = index >= 0 && index < episodes.lastIndex
        val hasPrevious: Boolean get() = index > 0
        val title: String get() = titleFor(detail, episode)
        val link: OnlineLink? get() = links.getOrNull(linkIndex)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastFailoverAt = 0L
    private var failoverJob: kotlinx.coroutines.Job? = null
    private const val FAILOVER_GAP_MS = 900L
    private const val PUBLISH_GAP_MS = 300L
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()
    private var job: Job? = null

    private val _opening = MutableStateFlow<OnlineItem?>(null)
    /** A tapped title whose details are loading (before playback or the episode list). */
    val opening: StateFlow<OnlineItem?> = _opening.asStateFlow()

    /**
     * Tap on a poster: movies and live streams start playing straight away; series (or anything
     * that can't play directly) go to [onShowTitle] — the episode list.
     */
    fun open(context: Context, item: OnlineItem, onShowTitle: (OnlineItem) -> Unit) {
        val app = context.applicationContext
        job?.cancel()
        _opening.value = item
        job = scope.launch {
            val provider = ExtensionManager.provider(item.api)
            val detail = provider?.let { p -> runCatching { OnlineRepository.load(p, item.url) }.onFailure { Log.w(TAG, "open ${item.name}", it) }.getOrNull() }
            if (_opening.value != item) return@launch
            _opening.value = null
            when {
                detail != null && detail.episodes.values.all { it.isEmpty() } && detail.playData != null && detail.unsupportedReason == null -> play(app, detail)
                else -> onShowTitle(item) // episodes to choose, or the title page explains the problem
            }
        }
    }

    fun cancelOpening() { _opening.value = null; job?.cancel() }

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
    fun episodesOf(detail: OnlineDetail, version: String): List<OnlineEpisode> =
        detail.episodes[version].orEmpty().sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: Int.MAX_VALUE }))
            .let { sorted -> if (sorted.all { it.episode == null }) detail.episodes[version].orEmpty() else sorted }

    /**
     * Resolves and plays [episodeIndex] of [version] (or the movie when there are no episodes).
     * [resumeMs] null → resume from watch history.
     */
    fun play(context: Context, detail: OnlineDetail, version: String = detail.versions.firstOrNull().orEmpty(), episodeIndex: Int = -1, resumeMs: Long? = null) {
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
        dev.asfakul.yumeiro.data.PlayCounts.record(
            app, dev.asfakul.yumeiro.data.PlayCounts.Kind.ONLINE, "${detail.api}|${detail.url}", detail.name, detail.api,
            detail.posterUrl, detail.posterHeaders, api = detail.api, url = detail.url,
        )
        resolve(app, resumeMs)
    }

    private fun resolve(context: Context, resumeMs: Long?) {
        job?.cancel()
        val start = _state.value ?: return
        val data = start.data ?: return
        job = scope.launch {
            AudioLanguages.init(context)
            val provider = ExtensionManager.provider(start.api)
            if (provider == null) {
                _state.update { it?.copy(phase = Phase.FAILED, message = "The extension “${start.api}” isn't installed or is turned off.") }
                return@launch
            }
            var started = false
            var firstAt = 0L
            val links = mutableListOf<OnlineLink>()
            val subs = mutableListOf<OnlineSubtitle>()
            var skipped = 0
            // Extensions can emit dozens of links/subtitles in a burst; re-sorting and pushing state
            // for each one recomposed the player on every event and made the UI stutter or freeze.
            var lastPublish = 0L
            var pendingPublish = false
            fun publish(done: Boolean, error: String? = null) {
                val t = System.currentTimeMillis()
                if (!done && t - lastPublish < PUBLISH_GAP_MS) { pendingPublish = true; return }
                lastPublish = t; pendingPublish = false
                val sorted = OnlineRepository.sortLinks(links.distinctBy { it.url }, AudioLanguages.preferred.value, _state.value?.version.orEmpty())
                _state.update { cur ->
                    if (cur == null || cur.data != data) return@update cur
                    // Keep the link in use at the same position when the list re-sorts.
                    val keep = cur.link?.url
                    val idx = keep?.let { u -> sorted.indexOfFirst { it.url == u } }?.takeIf { it >= 0 } ?: cur.linkIndex
                    cur.copy(links = sorted, subtitles = subs.distinctBy { it.url }, linkIndex = idx, linksDone = done, skipped = skipped,
                        message = if (done && sorted.isEmpty()) error ?: "No playable sources were found." else cur.message)
                }
            }
            fun startIfReady(force: Boolean) {
                if (started || links.isEmpty()) return
                if (!force && System.currentTimeMillis() - firstAt < GATHER_MS) return
                started = true
                publish(false)
                playCurrent(context, 0, resumeMs)
            }
            val ticker = launch { while (true) { delay(500); startIfReady(false); if (pendingPublish && started) publish(false) } }
            val ok = withTimeoutOrNull(FIRST_LINK_TIMEOUT_MS + 60_000L) {
                OnlineRepository.links(provider, data).collect { ev ->
                    when (ev) {
                        is LinkEvent.Link -> { if (links.isEmpty()) firstAt = System.currentTimeMillis(); links += ev.link; if (started) publish(false) }
                        is LinkEvent.Subtitle -> { subs += ev.subtitle; if (started) publish(false) }
                        is LinkEvent.Skipped -> skipped++
                        is LinkEvent.Done -> { publish(true, ev.error); startIfReady(true) }
                    }
                }
                true
            }
            ticker.cancel()
            if (ok == null) publish(true, "The source took too long to answer.")
            startIfReady(true)
            if (!started) _state.update { it?.copy(phase = Phase.FAILED, message = it.message ?: "No playable sources were found.") }
        }
    }

    private fun playCurrent(context: Context, linkIndex: Int, resumeMs: Long?) {
        val s = _state.value ?: return
        val link = s.links.getOrNull(linkIndex) ?: return
        val data = s.data ?: return
        val id = videoIdFor(s.api, data) + ":" + link.url.hashCode().toUInt().toString(16)
        val resume = resumeMs ?: OnlineHistory.progressOf(s.api, data)?.takeIf { !it.finished }?.positionMs ?: 0L
        _state.value = s.copy(phase = Phase.PLAYING, linkIndex = linkIndex, videoId = id, message = null)
        Log.i(TAG, "play ${s.title} via ${link.name} (${link.qualityLabel}, ${link.formatLabel}) ${link.url.take(120)}")
        OnlinePlayback.registerInterceptors(ExtensionManager.provider(s.api), link)
        val request = OnlinePlayback.request(s.title, link, s.subtitles, s.detail.posterUrl)
        val site = runCatching { ExtensionManager.provider(s.api)?.mainUrl }.getOrNull()
        AccessLadder.reset(request.url); AccessLadder.hint(request.url, site)
        IncomingMedia.open(context, request, remember = false, id = id, positionMs = resume, folder = s.detail.api)
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
        playCurrent(context.applicationContext, linkIndex, positionMs)
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
            _state.value = s.copy(failed = failed)
            Log.w(TAG, "failover → ${s.links[next].name}")
            // Dead links can fail within milliseconds of each other; tearing players down and up
            // that fast starved the main thread (ANR / force close). Space failovers out.
            val now = System.currentTimeMillis()
            val wait = (lastFailoverAt + FAILOVER_GAP_MS - now).coerceIn(0L, FAILOVER_GAP_MS)
            lastFailoverAt = now + wait
            failoverJob?.cancel()
            if (wait == 0L) playCurrent(app, next, positionMs)
            else failoverJob = scope.launch { delay(wait); if (_state.value?.data == s.data) playCurrent(app, next, positionMs) }
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
        // Last resort: play what the site's own page plays (cookies, UA and referer of a real browser).
        val page = pageFor(s)
        if (!s.captured && page != null) {
            Log.w(TAG, "fresh links failed too → headless capture of $page")
            _state.value = s.copy(phase = Phase.RESOLVING, message = "Opening the source page…", failed = failed, captured = true)
            job?.cancel()
            job = scope.launch {
                val req = HeadlessCapture.capture(app, page, s.title, referer = runCatching { ExtensionManager.provider(s.api)?.mainUrl }.getOrNull())
                val cur = _state.value ?: return@launch
                if (cur.data != s.data) return@launch
                if (req == null) {
                    _state.value = cur.copy(phase = Phase.FAILED, message = "Every source refused playback, even from the site's own page.")
                    dev.asfakul.yumeiro.ui.components.AppMessages.error(app, "Every source refused playback (HTTP 403), even from the site's own page. Try another episode or update the extension.")
                    return@launch
                }
                val id = videoIdFor(cur.api, cur.data ?: "") + ":web"
                _state.value = cur.copy(phase = Phase.PLAYING, videoId = id, message = null)
                IncomingMedia.open(app, req, remember = false, id = id, positionMs = positionMs, folder = cur.detail.api)
            }
            return true
        }
        _state.value = s.copy(failed = failed)
        return false
    }

    /** The web page that plays this episode: the embed a link came from, else the episode URL. */
    private fun pageFor(s: State): String? {
        fun web(u: String?) = u?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        // A referer with a path is an embed page; a bare origin ("https://host/") is not worth loading.
        return s.links.firstNotNullOfOrNull { l -> web(l.referer)?.takeIf { r -> r.substringAfter("://").substringAfter('/', "").isNotBlank() } }
            ?: web(s.data)
            ?: web(s.detail.url)
    }

    fun playEpisode(context: Context, index: Int) {
        val s = _state.value ?: return
        if (index !in s.episodes.indices) return
        _state.value = s.copy(index = index, videoId = null, phase = Phase.RESOLVING, links = emptyList(), subtitles = emptyList(), linkIndex = 0, linksDone = false, skipped = 0, message = null, failed = emptySet())
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
        _state.value = s.copy(version = version, episodes = eps, index = idx, videoId = null, phase = Phase.RESOLVING, links = emptyList(), subtitles = emptyList(), linkIndex = 0, linksDone = false, message = null, failed = emptySet())
        resolve(context.applicationContext, null)
    }

    fun retry(context: Context) {
        val s = _state.value ?: return
        _state.value = s.copy(phase = Phase.RESOLVING, links = emptyList(), linkIndex = 0, linksDone = false, message = null, failed = emptySet())
        resolve(context.applicationContext, null)
    }

    /** Progress callback from the player. */
    fun onProgress(context: Context, videoId: String, positionMs: Long, durationMs: Long) {
        val s = _state.value ?: return
        if (s.videoId != videoId) return
        OnlineHistory.record(context.applicationContext, s.detail, s.version, s.episode, positionMs, durationMs)
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
        _state.value = null
    }

    /** Cancels resolving but keeps the session if something is already playing. */
    fun cancelResolving() {
        val s = _state.value ?: return
        if (s.phase != Phase.PLAYING) end()
    }
}
