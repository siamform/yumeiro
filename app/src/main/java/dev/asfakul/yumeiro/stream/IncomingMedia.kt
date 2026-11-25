/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import dev.asfakul.yumeiro.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Precompiled once (R15 audit): these ran a regex compile on every call.
private val RX_INCOMINGMEDIA_HOT_1 = Regex("""(https?|rtsps?)://\S+|magnet:\?\S+""", RegexOption.IGNORE_CASE)

/** Media handed to the app by other apps ("Open with" / "Share"), consumed by the UI once. */
object IncomingMedia {
    private val _pending = MutableStateFlow<VideoItem?>(null)
    val pending: StateFlow<VideoItem?> = _pending.asStateFlow()

    fun consume() { _pending.value = null }

    /** Which recents list a stream opened with [open] goes into (ADR-027). */
    enum class Recent {
        /** Not recorded (websites, extensions, YouTube, the player's own re-opens). */
        NONE,
        /** Play Link "Recent streams": typed / pasted / shared links. */
        LINK,
        /** Live TV "Recently played": channels picked from a Live TV list. */
        LIVE,
        /** Refreshes the entry in whichever list already holds the URL; adds nothing new. */
        KEEP,
    }

    /**
     * Opens a stream request in the player (also used by the Stream screen). Callers say where the stream
     * came from with [recent]; the default records nothing (NB15D-1).
     */
    fun open(context: Context, request: StreamRequest, recent: Recent = Recent.NONE, id: String? = null, positionMs: Long = 0L, folder: String? = null) {
        StreamRepository.register(request)
        when (recent) {
            Recent.NONE -> Unit
            Recent.LINK -> StreamRepository.addToHistory(context, request)
            Recent.LIVE -> StreamRepository.addLive(context, request)
            Recent.KEEP -> StreamRepository.touch(context, request)
        }
        val item = request.toVideoItem()
        _pending.value = item.copy(
            id = id ?: item.id,
            playbackPositionMs = positionMs.coerceAtLeast(0L),
            folderName = folder ?: item.folderName,
        )
    }

    fun handle(context: Context, intent: Intent?): Boolean {
        intent ?: return false
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { linkFromText(it) }?.let(Uri::parse)
                ?: @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            else -> null
        }
        uri ?: return false
        if (pointsIntoPrivateStorage(context, uri)) return false
        // BUG-2 (E4): a shared YouTube channel link opens the channel, not the player.
        val scheme = uri.scheme?.lowercase()
        if ((scheme == "http" || scheme == "https") && dev.asfakul.yumeiro.youtube.YouTube.isChannelUrl(uri.toString())) {
            dev.asfakul.yumeiro.youtube.YtChannelLaunch.open(uri.toString(), "")
            return true
        }
        when (scheme) {
            // TOR-1 (owner list 12): magnet links (a browser's "Open with", or a shared info-hash) play through the
            // built-in torrent engine like a pasted one; before, Yumeiro wasn't offered for magnet: at all.
            "http", "https", "rtsp", "rtsps", "rtmp", "magnet" -> open(context, StreamRequest(url = uri.toString()), recent = Recent.LINK)
            "content", "file" -> _pending.value = VideoItem(
                id = "external:$uri",
                title = displayName(context, uri) ?: uri.lastPathSegment ?: "Video",
                durationMs = 0L,
                uri = uri.toString(),
                resolution = "",
                folderName = "External",
            )
            else -> return false
        }
        return true
    }

    /**
     * Another app must not be able to make us open our own private files (file:///data/data/<us>/…
     * or our own content provider) — the classic "file theft via VIEW intent" pattern, since the
     * player can save or share what it opens.
     */
    internal fun pointsIntoPrivateStorage(context: Context, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "content") return uri.authority?.startsWith(context.packageName) == true
        if (scheme != "file") return false
        val path = runCatching { java.io.File(uri.path ?: return true).canonicalPath }.getOrNull() ?: return true
        val roots = listOfNotNull(context.applicationInfo.dataDir, context.filesDir?.parent, context.cacheDir?.parent,
            "/data/data/${context.packageName}", "/data/user/0/${context.packageName}")
            .mapNotNull { runCatching { java.io.File(it).canonicalPath }.getOrNull() }
        return roots.any { path == it || path.startsWith("$it/") }
    }

    /** TOR-1: the first link in shared text, or a bare torrent info-hash as its magnet link. */
    internal fun linkFromText(text: String): String? = extractUrl(text) ?: LinkHosts.magnetFromHash(text.trim())

    private fun extractUrl(text: String): String? =
        RX_INCOMINGMEDIA_HOT_1.find(text)?.value?.trimEnd(')', ']', '"', '\'', '.', ',')

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()
}

fun StreamRequest.toVideoItem() = VideoItem(
    id = "stream:" + url.hashCode().toUInt().toString(16),
    title = displayTitle,
    durationMs = 0L,
    uri = url,
    thumbnailUri = logoUrl,
    resolution = resolvedFormat().label.substringBefore(' '),
    folderName = group ?: "Stream",
)
