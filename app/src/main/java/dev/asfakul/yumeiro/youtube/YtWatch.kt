/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of the YouTube watch page (player on top, details + "Up next" below, like the YouTube
 * app). Opens instantly from the tapped [YtItem] (so the page shows a skeleton right away), then
 * fills in channel, views, likes, description and related videos from NewPipe's StreamInfo —
 * the same cached info the stream resolver uses, so nothing is fetched twice.
 */
object YtWatch {
    private const val TAG = "YtWatch"

    data class Info(
        val title: String,
        val channel: String,
        val channelUrl: String?,
        val channelAvatar: String?,
        val subscribers: Long,
        val views: Long,
        val likes: Long,
        val uploaded: String?,
        val description: String,
        val tags: List<String>,
        val category: String?,
    )

    data class State(
        val item: YtItem,
        val info: Info? = null,
        val related: List<YtItem> = emptyList(),
        val loading: Boolean = true,
        /** The stream itself is still being resolved (player not open yet). */
        val resolving: Boolean = true,
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()
    private var job: Job? = null

    fun open(context: Context, item: YtItem) {
        val app = context.applicationContext
        job?.cancel()
        _state.value = State(item)
        job = scope.launch {
            val info = runCatching { YouTube.info(app, item.id) }.onFailure { Log.w(TAG, "info ${item.id}", it) }.getOrNull()
            if (_state.value?.item?.id != item.id) return@launch
            val mapped = info?.let { i ->
                Info(
                    title = i.name ?: item.title,
                    channel = i.uploaderName ?: item.channel,
                    channelUrl = i.uploaderUrl ?: item.channelUrl,
                    channelAvatar = YouTube.bestImage(i.uploaderAvatars) ?: item.channelAvatar,
                    subscribers = i.uploaderSubscriberCount,
                    views = i.viewCount,
                    likes = i.likeCount,
                    uploaded = i.textualUploadDate ?: item.uploaded,
                    description = runCatching { i.description?.content.orEmpty() }.getOrDefault("").let(::stripHtml),
                    tags = runCatching { i.tags.orEmpty() }.getOrDefault(emptyList()).take(12),
                    category = i.category?.takeIf { it.isNotBlank() },
                )
            }
            val related = runCatching { YouTube.related(app, item.id, item.music) }.getOrDefault(emptyList()).filter { it.id != item.id }
            _state.value = _state.value?.takeIf { it.item.id == item.id }?.copy(info = mapped, related = related, loading = false)
        }
    }

    fun onResolved(id: String) { _state.value = _state.value?.takeIf { it.item.id == id }?.copy(resolving = false, error = null) }

    fun onFailed(id: String, message: String) { _state.value = _state.value?.takeIf { it.item.id == id }?.copy(resolving = false, error = message) }

    fun end() { job?.cancel(); job = null; _state.value = null }

    internal fun stripHtml(s: String): String =
        s.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").trim()
}
