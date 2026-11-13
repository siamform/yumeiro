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

/** Media handed to the app by other apps ("Open with" / "Share"), consumed by the UI once. */
object IncomingMedia {
    private val _pending = MutableStateFlow<VideoItem?>(null)
    val pending: StateFlow<VideoItem?> = _pending.asStateFlow()

    fun consume() { _pending.value = null }

    /** Opens a stream request in the player (also used by the Stream screen). */
    fun open(context: Context, request: StreamRequest, remember: Boolean = true, id: String? = null, positionMs: Long = 0L, folder: String? = null) {
        StreamRepository.register(request)
        // Online extension links are short-lived (signed URLs), so they aren't kept in stream history.
        if (remember) StreamRepository.addToHistory(context, request)
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
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { extractUrl(it) }?.let(Uri::parse)
                ?: @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            else -> null
        }
        uri ?: return false
        if (pointsIntoPrivateStorage(context, uri)) return false
        when (uri.scheme?.lowercase()) {
            "http", "https", "rtsp", "rtsps", "rtmp" -> open(context, StreamRequest(url = uri.toString()))
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

    private fun extractUrl(text: String): String? =
        Regex("""(https?|rtsps?)://\S+""").find(text)?.value?.trimEnd(')', ']', '"', '\'', '.', ',')

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
