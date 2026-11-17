/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anime

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Anime extras for online playback: AniSkip intro/outro times for the playing episode, and
 * AniList / MyAnimeList progress updates when an episode is watched (85%).
 */
object AnimeSync {
    private const val TAG = "AnimeSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Skips(val key: String, val segments: List<AniSkip.Segment>)
    private val _skips = MutableStateFlow<Skips?>(null)
    val skips: StateFlow<Skips?> = _skips.asStateFlow()

    private val synced = Collections.synchronizedSet(HashSet<String>())

    /** Episode number used by AniSkip / trackers (episode, else its position in the list). */
    fun episodeNumber(episode: OnlineEpisode?, index: Int): Int? = episode?.episode ?: (index + 1).takeIf { episode != null }

    /** Loads skip times for the episode now playing. */
    fun load(context: Context, detail: OnlineDetail, episode: OnlineEpisode?, index: Int, durationMs: Long) {
        val num = episodeNumber(episode, index) ?: run { _skips.value = null; return }
        val key = "${detail.api}|${detail.url}|$num"
        if (_skips.value?.key == key) return
        _skips.value = Skips(key, emptyList())
        if (!OnlineSettings.skipIntro.value || !AnimeIds.isAnime(detail)) return
        val app = context.applicationContext
        scope.launch {
            val client = NetworkClient.get(app)
            val ids = runCatching { AnimeIds.of(client, detail) }.getOrNull() ?: return@launch
            val mal = ids.mal ?: return@launch
            val segs = AniSkip.segments(client, mal, num, durationMs)
            if (_skips.value?.key == key) _skips.value = Skips(key, segs)
        }
    }

    private val attempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private const val MAX_ATTEMPTS = 3
    private val ANIME_TRACKERS = listOf("anilist", "mal", "kitsu")

    /** A tracker that threw gets its key back (tried again on a later progress tick), at most [MAX_ATTEMPTS] times. */
    private fun failed(key: String, what: String, e: Throwable) {
        Log.w(TAG, what, e)
        val n = attempts.merge(key, 1) { a, b -> a + b } ?: 1
        if (n < MAX_ATTEMPTS) synced.remove(key)
    }

    /**
     * Called with player progress; updates each signed-in tracker once per episode.
     *
     * IG-2: each tracker has its own once-per-episode key and its own failure handling. Before, AniList,
     * MAL and Kitsu ran unguarded ahead of Trakt and Simkl inside one runCatching, so a Kitsu 401 (an
     * expired token) skipped Trakt and Simkl, and the whole episode was retried on every ~5 s progress
     * tick for the rest of playback.
     */
    fun onProgress(context: Context, detail: OnlineDetail, episode: OnlineEpisode?, index: Int, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0 || positionMs < durationMs * 0.85) return
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        val isAnime = AnimeIds.isAnime(detail)
        val trackable = detail.kind != OnlineKind.LIVE && detail.kind != OnlineKind.OTHER
        val wanted = buildList {
            if (isAnime && AniList.signedIn) add("anilist")
            if (isAnime && MyAnimeList.signedIn) add("mal")
            if (isAnime && dev.asfakul.yumeiro.tracking.Kitsu.signedIn) add("kitsu")
            if (trackable && dev.asfakul.yumeiro.tracking.Trakt.signedIn) add("trakt")
            if (trackable && dev.asfakul.yumeiro.tracking.Simkl.signedIn) add("simkl")
        }
        if (wanted.isEmpty()) return
        val num = episodeNumber(episode, index) ?: if (episode == null) 1 else return
        val key = "${detail.api}|${detail.url}|$num"
        val claimed = wanted.filter { synced.add("$key|$it") }.toSet()
        if (claimed.isEmpty()) return
        val app = context.applicationContext
        scope.launch {
            val client = NetworkClient.get(app)
            fun track(name: String, block: () -> Unit) {
                if (name in claimed) runCatching(block).onFailure { failed("$key|$name", name, it) }
            }
            val needIds = isAnime && (claimed.any { it in ANIME_TRACKERS } || "simkl" in claimed)
            val idsResult = if (needIds) runCatching { AnimeIds.of(client, detail) } else null
            val ids = idsResult?.getOrNull()
            for (name in ANIME_TRACKERS) track(name) {
                idsResult?.exceptionOrNull()?.let { throw it } // id lookup failed: retry this tracker later
                val i = ids ?: return@track
                when (name) {
                    "anilist" -> i.anilist?.let { AniList.watched(client, it, num, i.episodes) }
                    "mal" -> i.mal?.let { MyAnimeList.watched(app, client, it, num, i.episodes) }
                    "kitsu" -> i.mal?.let { dev.asfakul.yumeiro.tracking.Kitsu.watched(app, client, it, num, i.episodes) }
                }
            }
            val ep = if (episode != null) num else null
            track("trakt") { dev.asfakul.yumeiro.tracking.Trakt.watched(app, client, detail.name, detail.year, episode?.season, ep) }
            track("simkl") {
                val match = dev.asfakul.yumeiro.tracking.Simkl.Match(
                    title = detail.name, year = detail.year, mal = ids?.mal ?: detail.malId, anilist = ids?.anilist ?: detail.aniListId,
                    imdb = detail.imdbId, tmdb = detail.tmdbId,
                )
                dev.asfakul.yumeiro.tracking.Simkl.watched(app, client, match, episode?.season, ep)
            }
        }
    }

    /** OAuth redirects (yumeiro://anilist-login, yumeiro://mal-login). */
    fun handleRedirect(context: Context, uri: Uri, done: (String) -> Unit): Boolean {
        if (uri.scheme != "yumeiro") return false
        val app = context.applicationContext
        scope.launch {
            val client = NetworkClient.get(app)
            val msg = when (uri.host) {
                "anilist-login" -> { AniList.handleRedirect(app, uri, client); if (AniList.signedIn) "Signed in to AniList as ${AniList.user()}" else "AniList sign-in failed" }
                "mal-login" -> { MyAnimeList.handleRedirect(app, uri, client); if (MyAnimeList.signedIn) "Signed in to MyAnimeList as ${MyAnimeList.user()}" else "MyAnimeList sign-in failed" }
                else -> return@launch
            }
            done(msg)
        }
        return true
    }
}
