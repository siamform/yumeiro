/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import dev.asfakul.yumeiro.network.NetworkClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** Stream history + playlist loading. Also remembers the request (headers/DRM) for each URL being played. */
object StreamRepository {
    private const val PREFS = "mt_streams"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY = 40
    private const val KEY_LIVE = "live_history"
    private const val KEY_SPLIT = "recents_split_v1"
    private const val MAX_LIVE = 30

    private val _history = MutableStateFlow<List<StreamRequest>>(emptyList())
    /** Play Link recents: only streams launched from the Play Link page or shared to the app (ADR-027). */
    val history: StateFlow<List<StreamRequest>> = _history.asStateFlow()
    private val _liveHistory = MutableStateFlow<List<StreamRequest>>(emptyList())
    /** Live TV "Recently played": channels played from Live TV lists (ADR-027). */
    val liveHistory: StateFlow<List<StreamRequest>> = _liveHistory.asStateFlow()
    private val active = ConcurrentHashMap<String, StreamRequest>()
    private val _playlist = MutableStateFlow<List<StreamRequest>>(emptyList())
    /** Channels of the currently opened M3U playlist (kept while the app runs). */
    val playlist: StateFlow<List<StreamRequest>> = _playlist.asStateFlow()
    private val _playlistName = MutableStateFlow<String?>(null)
    val playlistName: StateFlow<String?> = _playlistName.asStateFlow()

    private val _playlistId = MutableStateFlow<String?>(null)
    /** Id of the saved [StreamPlaylist] currently opened, or null for a temporary (unsaved) list. */
    val playlistId: StateFlow<String?> = _playlistId.asStateFlow()

    fun setPlaylist(name: String?, channels: List<StreamRequest>, id: String? = null) {
        _playlistName.value = name; _playlist.value = channels; _playlistId.value = id
    }
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = prefs(context)
        var link = StreamRequest.listFromJson(p.getString(KEY_HISTORY, null))
        var live = StreamRequest.listFromJson(p.getString(KEY_LIVE, null))
        if (!p.getBoolean(KEY_SPLIT, false)) {
            // NB15D-1 one-time split: before ADR-027 every played channel went into Play Link recents.
            // Entries carrying a channel group or logo came from a playlist, so they move to Live TV.
            val (fromList, typed) = link.partition(::fromPlaylist)
            link = typed
            live = (live + fromList).distinctBy { it.url }.take(MAX_LIVE)
            p.edit().putString(KEY_HISTORY, StreamRequest.listToJson(link))
                .putString(KEY_LIVE, StreamRequest.listToJson(live)).putBoolean(KEY_SPLIT, true).apply()
        }
        _history.value = link
        _liveHistory.value = live
        loaded = true
    }

    /** True when [request] carries playlist metadata (channel group or logo), i.e. it came from a list. */
    internal fun fromPlaylist(request: StreamRequest): Boolean =
        !request.group.isNullOrBlank() || !request.logoUrl.isNullOrBlank()

    /** Registers [request] so the player can find its headers / DRM by URL. */
    fun register(request: StreamRequest) { active[request.url] = request }
    fun requestFor(url: String): StreamRequest? = active[url]

    fun addToHistory(context: Context, request: StreamRequest) {
        // NB15D-OB1: incognito records nothing, as every other history does.
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        init(context)
        val list = listOf(request) + _history.value.filterNot { it.url == request.url }
        _history.value = list.take(MAX_HISTORY)
        save(context)
    }

    fun remove(context: Context, request: StreamRequest) {
        _history.value = _history.value.filterNot { it.url == request.url }
        save(context)
    }

    fun clear(context: Context) { _history.value = emptyList(); save(context) }

    /** Records a channel played from a Live TV list in Live TV "Recently played". */
    fun addLive(context: Context, request: StreamRequest) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        init(context)
        _liveHistory.value = (listOf(request) + _liveHistory.value.filterNot { it.url == request.url }).take(MAX_LIVE)
        saveLive(context)
    }

    fun removeLive(context: Context, request: StreamRequest) {
        init(context)
        _liveHistory.value = _liveHistory.value.filterNot { it.url == request.url }
        saveLive(context)
    }

    fun clearLive(context: Context) { init(context); _liveHistory.value = emptyList(); saveLive(context) }

    /**
     * Moves [request] to the front of whichever recents list already holds its URL, without adding it to
     * the other one (used when the watch panel switches back to a stream's own link).
     */
    fun touch(context: Context, request: StreamRequest) {
        init(context)
        when {
            _liveHistory.value.any { it.url == request.url } -> addLive(context, request)
            _history.value.any { it.url == request.url } -> addToHistory(context, request)
        }
    }

    private fun save(context: Context) =
        prefs(context).edit().putString(KEY_HISTORY, StreamRequest.listToJson(_history.value)).apply()

    private fun saveLive(context: Context) =
        prefs(context).edit().putString(KEY_LIVE, StreamRequest.listToJson(_liveHistory.value)).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE)

    /** Loads an M3U channel list from a URL (using [base] headers) or a content:// document. */
    suspend fun loadPlaylist(context: Context, source: String, base: StreamRequest? = null): List<StreamRequest> =
        withContext(Dispatchers.IO) {
            val text = if (source.startsWith("content://") || source.startsWith("file://")) {
                context.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes().decodeToString() }
                    ?: error("Cannot open playlist")
            } else {
                fetchText(source, base)
            }
            require(!M3uParser.isHlsPlaylist(text)) { "This is a single HLS stream, not a channel list" }
            M3uParser.parse(text, base, baseUrl = source.takeIf { it.startsWith("http") }).also { require(it.isNotEmpty()) { "No channels found in playlist" } }
        }

    private fun fetchText(url: String, base: StreamRequest?): String {
        val b = Request.Builder().url(url).header("User-Agent", base?.userAgent ?: UserAgentPreset.CHROME.value!!)
        base?.requestHeaders()?.forEach { (k, v) -> runCatching { b.header(k, v) } }
        NetworkClient.get().newCall(b.build()).execute().use { response ->
            if (response.code !in 200..299) error("HTTP ${response.code}")
            val source = response.body.source()
            require(!source.request(30L * 1024 * 1024)) { "Playlist too large" }
            return source.buffer.readUtf8()
        }
    }

    /** Remembers [request] under [key] (the URL the player was opened with) after it was resolved. */
    fun register(key: String, request: StreamRequest) { active[key] = request }
}
