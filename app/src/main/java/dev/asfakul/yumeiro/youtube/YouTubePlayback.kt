/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import android.content.Context
import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRefreshers
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.components.AppMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Starts YouTube videos in the video player and maps YouTube Music songs to music-player tracks. */
object YouTubePlayback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null
    @Volatile private var registered = false
    private val refreshes = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Registers the 403 / expiry refresher once (called from Application.onCreate). */
    fun install(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        StreamRefreshers.register(object : StreamRefreshers.Refresher {
            override fun handles(request: StreamRequest) = request.group == GROUP && request.pageUrl?.let { YouTube.idFrom(it) } != null
            override suspend fun refresh(context: Context, request: StreamRequest): StreamRequest? {
                val id = request.pageUrl?.let { YouTube.idFrom(it) } ?: return null
                // 1st refusal: a fresh link. Again: YouTube's safer HLS / muxed path.
                val n = refreshes.merge(id, 1, Int::plus) ?: 1
                return YouTubeStreams.videoRequest(context, id, force = true, safe = n > 1)
            }
        })
        YouTube.init(app)
    }

    /** Resolves [item] and opens it in the full video player. */
    fun playVideo(context: Context, item: YtItem, maxHeight: Int = YouTubeStreams.defaultHeight()) {
        pending?.cancel()
        refreshes.remove(item.id)
        // The watch page opens instantly (skeleton + details) while the stream resolves.
        YtWatch.open(context, item)
        pending = scope.launch {
            runCatching { YouTubeStreams.videoRequest(context, item.id, maxHeight) }
                .onSuccess { req ->
                    YtWatch.onResolved(item.id)
                    YtHistory.add(context, item)
                    dev.asfakul.yumeiro.data.PlayCounts.record(context, dev.asfakul.yumeiro.data.PlayCounts.Kind.YOUTUBE, item.id, item.title, item.channel, item.thumbnail)
                    IncomingMedia.open(context, req.copy(logoUrl = req.logoUrl ?: item.thumbnail), remember = false, id = "yt:${item.id}", folder = GROUP)
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) return@onFailure
                    YtWatch.onFailed(item.id, e.message ?: e.javaClass.simpleName)
                    AppMessages.error(context, "YouTube couldn't play this video: ${e.message ?: e.javaClass.simpleName}", "Retry") {
                        playVideo(context, item, maxHeight)
                    }
                }
        }
    }

    /** Stops resolving (watch page closed before the player opened). */
    fun cancel() { pending?.cancel(); pending = null }

    /** YouTube Music song → a music-player track (resolved lazily by [YouTubeAudioDataSource]). */
    fun toTrack(item: YtItem): AudioTrack = AudioTrack(
        id = "yt:${item.id}",
        title = item.title,
        artist = item.channel.removeSuffix(" - Topic").ifBlank { "YouTube Music" },
        album = "YouTube Music",
        durationMs = (item.durationSec.coerceAtLeast(0)) * 1000L,
        uri = YouTubeAudioDataSource.uriFor(item.id),
        albumArtUri = squareArt(item),
        folderName = "YouTube Music",
        folderPath = item.url,
    )

    /** Square, high-res cover when YouTube offers one (lh3 / music thumbnails resize via the URL). */
    private fun squareArt(item: YtItem): String? {
        val t = item.thumbnail ?: return null
        return if ("googleusercontent.com" in t || "ggpht.com" in t) t.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj") else t
    }

    fun isYouTubeTrack(track: AudioTrack?): Boolean = track?.uri?.startsWith(YouTubeAudioDataSource.SCHEME + "://") == true

    const val GROUP = "YouTube"
}
