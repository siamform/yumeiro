/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * F-05-N (ADR-057): what a stream-only add-on is asked for. Nuvio scrapers take a TMDB id, a media
 * type ("movie" or "tv") and, for a series, the season and episode; Stremio stream add-ons and E1
 * Layer B take the IMDb id the same way, so one request serves them all.
 */
data class StreamRequest(
    val tmdbId: Int?,
    val imdbId: String?,
    /** "movie" or "tv" (Nuvio's words). */
    val mediaType: String,
    val season: Int?,
    val episode: Int?,
    val title: String,
    val year: Int?,
)

/**
 * F-05-N (ADR-057): a source of extra streams for a title another source opened. It never
 * publishes a catalog; its links join the opened source's links in the same list, marked with
 * their own [OnlineLink.source] badge. Nuvio scrapers are the first; SkyStream and Stremio
 * stream-only add-ons and E1 Layer B use the same seam.
 */
interface StreamAddon {
    val id: String
    val label: String
    /** Cheap: flags and whether anything is installed. Called off the main thread. */
    fun enabled(context: Context): Boolean
    /** Link / Subtitle / Skipped events; a final Done is optional (the merge emits its own). */
    fun streams(context: Context, request: StreamRequest): Flow<LinkEvent>
}

object StreamAddons {
    private const val TAG = "StreamAddons"

    /** Registered add-ons. Each one checks its own flag in [StreamAddon.enabled]. */
    @Volatile var registered: List<StreamAddon> = listOf(
        dev.asfakul.yumeiro.sources.ecosystem.NuvioStreams,
        // F-05-STREMIO: stream-only Stremio add-ons.
        dev.asfakul.yumeiro.sources.stremio.StremioStreams,
    )
        @androidx.annotation.VisibleForTesting internal set

    /**
     * What to ask add-ons for this title and episode, or null when there is nothing they could
     * look up (no TMDB or IMDb id, or a live channel).
     */
    fun requestFor(detail: OnlineDetail, episode: OnlineEpisode?): StreamRequest? {
        if (detail.kind == OnlineKind.LIVE) return null
        if (detail.tmdbId == null && detail.imdbId.isNullOrBlank()) return null
        val series = episode != null && (episode.episode != null || episode.season != null)
        return StreamRequest(
            tmdbId = detail.tmdbId,
            imdbId = detail.imdbId?.takeIf { it.isNotBlank() },
            mediaType = if (series) "tv" else "movie",
            season = if (series) episode?.season ?: 1 else null,
            episode = if (series) episode?.episode else null,
            title = detail.name,
            year = detail.year,
        )
    }

    /** The add-ons that would run now (flags on, something installed). */
    fun active(context: Context): List<StreamAddon> = registered.filter { a ->
        runCatching { a.enabled(context) }.onFailure { Log.w(TAG, "${a.id}: ${it.message}") }.getOrDefault(false)
    }

    /**
     * [main]'s events plus every add-on's, as they come. Inner Done events are swallowed and one Done
     * is sent after all of them finished: ok when any link came in; otherwise [main]'s error (an
     * add-on's error only when [main] had none). An add-on that throws ends just that add-on; [main]
     * throwing ends the add-ons too, like before. Collecting less (GatherCap) cancels everything.
     */
    fun merge(main: Flow<LinkEvent>, extras: List<Flow<LinkEvent>>): Flow<LinkEvent> {
        if (extras.isEmpty()) return main
        return channelFlow {
            var anyLink = false
            var mainError: String? = null
            var extraError: String? = null
            val lock = Any()
            fun note(ev: LinkEvent): Boolean {
                if (ev is LinkEvent.Link) synchronized(lock) { anyLink = true }
                return ev !is LinkEvent.Done
            }
            val jobs = ArrayList<kotlinx.coroutines.Job>()
            jobs += launch {
                main.collect { ev ->
                    if (ev is LinkEvent.Done && !ev.ok) synchronized(lock) { mainError = ev.error }
                    if (note(ev)) send(ev)
                }
            }
            for ((i, flow) in extras.withIndex()) {
                jobs += launch {
                    flow.catch { e ->
                        if (e is CancellationException) throw e
                        Log.w(TAG, "add-on $i: ${e.message}")
                        synchronized(lock) { if (extraError == null) extraError = e.message }
                    }.collect { ev ->
                        if (ev is LinkEvent.Done && !ev.ok) synchronized(lock) { if (extraError == null) extraError = ev.error }
                        if (note(ev)) send(ev)
                    }
                }
            }
            jobs.forEach { it.join() }
            val ok = synchronized(lock) { anyLink }
            send(LinkEvent.Done(ok, if (ok) null else mainError ?: extraError))
        }
    }

    /**
     * [main] merged with the active add-ons for [detail] / [episode]; [main] alone when add-ons are
     * off, none is installed, or the title has no id they could use.
     */
    fun withAddons(context: Context, detail: OnlineDetail, episode: OnlineEpisode?, main: Flow<LinkEvent>): Flow<LinkEvent> {
        if (!dev.asfakul.yumeiro.FeatureFlags.on(context, dev.asfakul.yumeiro.FeatureFlags.Flag.STREAM_ADDONS)) return main
        val request = requestFor(detail, episode) ?: return main
        val addons = active(context)
        if (addons.isEmpty()) return main
        return merge(main, addons.map { it.streams(context, request) })
    }
}
