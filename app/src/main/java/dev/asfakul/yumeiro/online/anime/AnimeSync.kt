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

    /** Called with player progress; updates trackers once per episode. */
    fun onProgress(context: Context, detail: OnlineDetail, episode: OnlineEpisode?, index: Int, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0 || positionMs < durationMs * 0.85) return
        if (!AniList.signedIn && !MyAnimeList.signedIn) return
        if (!AnimeIds.isAnime(detail)) return
        val num = episodeNumber(episode, index) ?: if (episode == null) 1 else return
        val key = "${detail.api}|${detail.url}|$num"
        if (!synced.add(key)) return
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val client = NetworkClient.get(app)
                val ids = AnimeIds.of(client, detail) ?: return@runCatching
                ids.anilist?.let { if (AniList.signedIn) AniList.watched(client, it, num, ids.episodes) }
                ids.mal?.let { if (MyAnimeList.signedIn) MyAnimeList.watched(client, it, num, ids.episodes) }
            }.onFailure { Log.w(TAG, "sync", it); synced.remove(key) }
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
