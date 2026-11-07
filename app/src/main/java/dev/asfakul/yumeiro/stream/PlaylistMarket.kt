/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import dev.asfakul.yumeiro.security.TrustedSources
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * Playlist Market: a catalog of IPTV playlists collected from GitHub by the
 * `Playlist market` workflow (.github/workflows/playlist-market.yml). Each list is repaired and
 * validated there and published with a sha256, so the app can tell when an installed copy is out of
 * date. The catalog is cached on the device and works offline.
 */
object PlaylistMarket {
    const val BASE = "https://raw.githubusercontent.com/siamform/yumeiro/market"
    const val INDEX_URL = "$BASE/index.json"
    /** Installed market playlists use this id prefix. */
    const val ID_PREFIX = "m_"
    /** How often the Stream tab checks for playlist updates in the background. */
    const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_INDEX_BYTES = 8L * 1024 * 1024

    /** Which playlist URLs a catalog entry may use. Tests swap this to point at a local server. */
    internal var entryUrlAllowed: (String) -> Boolean = DEFAULT_ENTRY_URL_ALLOWED
    private val DEFAULT_ENTRY_URL_ALLOWED: (String) -> Boolean get() = { TrustedSources.isOfficialCatalogUrl(it) }

    data class Entry(
        val id: String,
        val name: String,
        val category: String,
        val description: String?,
        val repo: String?,
        val stars: Int?,
        val country: String?,
        val url: String,
        val source: String?,
        val channels: Int,
        val groups: Int,
        val topGroups: List<String>,
        val sha256: String?,
        val updated: Long,
        val alive: Int?,
        val sampled: Int?,
        val ok: Boolean,
        val origin: String?,
    ) {
        val installId: String get() = ID_PREFIX + id
        /** Share of sampled streams that answered from the build server (null = not sampled). */
        val health: Float? get() = if (sampled != null && sampled > 0 && alive != null) alive.toFloat() / sampled else null
    }

    data class Catalog(val generated: Long, val categories: List<String>, val entries: List<Entry>, val fetchedAt: Long, val fromCache: Boolean)

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Ready(val catalog: Catalog, val refreshing: Boolean = false, val warning: String? = null) : State
        data class Error(val message: String) : State
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val catalog: Catalog? get() = (_state.value as? State.Ready)?.catalog

    private fun cacheFile(context: Context) = File(File(context.filesDir, "streams").apply { mkdirs() }, "market.json")

    /** Parses index.json. Invalid entries are skipped; a broken file throws. */
    fun parse(text: String, fetchedAt: Long = System.currentTimeMillis(), fromCache: Boolean = false): Catalog {
        val root = JSONObject(text)
        val arr = root.optJSONArray("playlists") ?: JSONArray()
        val entries = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").trim().takeIf { it.matches(Regex("[A-Za-z0-9_.-]{1,80}")) } ?: return@mapNotNull null
            // Market playlists are always served from the bot-only market branch over HTTPS.
            val url = o.optString("url").trim().takeIf { entryUrlAllowed(it) } ?: return@mapNotNull null
            Entry(
                id = id,
                name = o.optString("name").trim().ifBlank { id },
                category = o.optString("category").trim().ifBlank { "Community" },
                description = o.optString("description").trim().ifBlank { null },
                repo = o.optString("repo").trim().ifBlank { null },
                stars = if (o.has("stars") && !o.isNull("stars")) o.optInt("stars") else null,
                country = o.optString("country").trim().ifBlank { null },
                url = url,
                source = o.optString("source").trim().ifBlank { null },
                channels = o.optInt("channels"),
                groups = o.optInt("groups"),
                topGroups = o.optJSONArray("topGroups")?.let { g -> (0 until g.length()).mapNotNull { g.optString(it).ifBlank { null } } }.orEmpty(),
                sha256 = o.optString("sha256").trim().ifBlank { null },
                updated = parseTime(o.optString("updated")),
                alive = if (o.has("alive")) o.optInt("alive") else null,
                sampled = if (o.has("sampled")) o.optInt("sampled") else null,
                ok = o.optBoolean("ok", true),
                origin = o.optString("origin").ifBlank { null },
            )
        }.distinctBy { it.id }
        val cats = root.optJSONArray("categories")?.let { c -> (0 until c.length()).mapNotNull { c.optString(it).ifBlank { null } } }
            ?: entries.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        return Catalog(parseTime(root.optString("generated")), cats.filter { c -> entries.any { it.category == c } }, entries, fetchedAt, fromCache)
    }

    private fun parseTime(s: String?): Long = runCatching { Instant.parse(s!!.trim()).toEpochMilli() }.getOrDefault(0L)

    /**
     * Loads the catalog: shows the cached copy at once, then downloads a fresh one when [force] is
     * set or the cache is older than [CHECK_INTERVAL_MS]. Never throws; failures end in
     * [State.Error] (no cache) or a [State.Ready] with a warning (cache shown, offline).
     */
    suspend fun load(context: Context, force: Boolean = false): Catalog? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = cacheFile(context)
            val cached = (_state.value as? State.Ready)?.catalog ?: runCatching {
                if (file.exists()) parse(file.readText(), fetchedAt = file.lastModified(), fromCache = true) else null
            }.getOrNull()
            if (cached != null) _state.value = State.Ready(cached, refreshing = true) else _state.value = State.Loading
            val fresh = force || cached == null || System.currentTimeMillis() - cached.fetchedAt > CHECK_INTERVAL_MS
            if (!fresh) { _state.value = State.Ready(cached!!); return@withLock cached }
            try {
                val text = TrustedSources.fetchCatalogText(INDEX_URL, MAX_INDEX_BYTES)
                val cat = parse(text)
                require(cat.entries.isNotEmpty()) { "The market is empty right now" }
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                _state.value = State.Ready(cat)
                cat
            } catch (e: Exception) {
                val msg = friendlyError(e)
                _state.value = if (cached != null) State.Ready(cached, warning = "Offline — showing saved catalog ($msg)") else State.Error(msg)
                cached
            }
        }
    }

    internal fun friendlyError(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is java.net.UnknownHostException -> "No internet connection"
            e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException -> "The server took too long to answer"
            m.startsWith("HTTP 404") -> "The market is being prepared. Try again in a few minutes"
            m.startsWith("HTTP") -> "Server error ($m)"
            e is org.json.JSONException -> "The market catalog is damaged"
            m.isNotBlank() -> m
            else -> "Could not load the market"
        }
    }


    /** Installed market playlists whose catalog copy changed since they were downloaded. */
    fun updatesFor(installed: List<StreamPlaylist>, catalog: Catalog): List<Pair<StreamPlaylist, Entry>> {
        val byId = catalog.entries.associateBy { it.id }
        return installed.mapNotNull { p ->
            val e = p.marketId?.let(byId::get) ?: return@mapNotNull null
            if (e.sha256 != null && e.sha256 != p.marketSha) p to e else null
        }
    }

    /** For tests. */
    internal fun resetForTests() { _state.value = State.Idle; entryUrlAllowed = DEFAULT_ENTRY_URL_ALLOWED }
}
