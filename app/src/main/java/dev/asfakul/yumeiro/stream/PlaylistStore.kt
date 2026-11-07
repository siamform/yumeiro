/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import dev.asfakul.yumeiro.security.TrustedSources
import android.content.Context
import android.net.Uri
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
import dev.asfakul.yumeiro.network.NetworkClient
import okhttp3.Request
import java.net.URL
import java.util.UUID

/**
 * A saved stream playlist. Remote playlists ([url] != null) are downloaded and cached on the device;
 * local playlists ([url] == null) are created by the user. Channels of both are stored as M3U files
 * in `filesDir/streams/`, so everything stays on the user's device.
 */
data class StreamPlaylist(
    val id: String,
    val name: String,
    val url: String? = null,
    /** Original source used when [url] (our mirror) cannot be reached. */
    val fallbackUrl: String? = null,
    val category: String? = null,
    val description: String? = null,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
    val channelCount: Int = 0,
    val lastUpdated: Long = 0L,
    val lastError: String? = null,
    /** True after the user renamed / changed a default, so Sync keeps their edits. */
    val userEdited: Boolean = false,
    /** Catalog id when installed from the Playlist Market. */
    val marketId: String? = null,
    /** sha256 of the market copy that is installed (compared with the catalog to find updates). */
    val marketSha: String? = null,
    /** Update automatically (stale refresh / market updates) or only when the user asks. */
    val autoUpdate: Boolean = true,
    /** GitHub repository the playlist comes from ("owner/name"). */
    val repo: String? = null,
) {
    val isLocal: Boolean get() = url == null
    val isMarket: Boolean get() = marketId != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); url?.let { put("url", it) }; fallbackUrl?.let { put("fallback", it) }
        category?.let { put("category", it) }; description?.let { put("description", it) }
        put("default", isDefault); put("enabled", enabled); put("count", channelCount); put("updated", lastUpdated)
        lastError?.let { put("error", it) }; put("edited", userEdited)
        marketId?.let { put("market", it) }; marketSha?.let { put("marketSha", it) }; put("auto", autoUpdate); repo?.let { put("repo", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = StreamPlaylist(
            id = o.getString("id"),
            name = o.optString("name").ifBlank { "Playlist" },
            url = o.optString("url").ifBlank { null },
            fallbackUrl = o.optString("fallback").ifBlank { null },
            category = o.optString("category").ifBlank { null },
            description = o.optString("description").ifBlank { null },
            isDefault = o.optBoolean("default"),
            enabled = o.optBoolean("enabled", true),
            channelCount = o.optInt("count"),
            lastUpdated = o.optLong("updated"),
            lastError = o.optString("error").ifBlank { null },
            userEdited = o.optBoolean("edited"),
            marketId = o.optString("market").ifBlank { null },
            marketSha = o.optString("marketSha").ifBlank { null },
            autoUpdate = o.optBoolean("auto", true),
            repo = o.optString("repo").ifBlank { null },
        )
    }
}

/** What a playlist URL returns: redirect chain, HTTP details and a summary of the channels. */
data class PlaylistAnalysis(
    val requestedUrl: String,
    val finalUrl: String,
    val redirects: List<String>,
    val status: Int,
    val contentType: String?,
    val bytes: Int,
    val isHls: Boolean,
    val channels: Int,
    val groups: List<Pair<String, Int>>,
    val drmChannels: Int,
    val headerChannels: Int,
    val epgUrl: String?,
    val lastUpdatedNote: String?,
)

/**
 * Default playlist (mirrored twice a week to the `streams` branch by .github/workflows/stream-sync.yml).
 * Only Mrgify BDIX ships pre-installed; everything else is available in the Playlist Market.
 */
object DefaultPlaylists {
    const val MIRROR_BASE = "https://raw.githubusercontent.com/siamform/yumeiro/streams"
    const val INDEX_URL = "$MIRROR_BASE/index.json"
    private const val UPSTREAM = "https://raw.githubusercontent.com/abusaeeidx"

    val all: List<StreamPlaylist> = listOf(
        d("mrgify_bdix", "Mrgify BDIX", "Bangladesh TV", "BDIX IPTV channels (Bangladesh)", "Mrgify-BDIX-IPTV/main/playlist.m3u"),
    )

    /** Former defaults (≤ v1.6) that now live in the Playlist Market. Removed on upgrade unless edited. */
    val retired: Set<String> = setOf("zilla_combined", "movies_bangla", "bdxtv_events", "yupptv", "tsports", "crichd", "crestsport")
    val ids: Set<String> get() = all.mapTo(mutableSetOf()) { it.id }

    private fun d(id: String, name: String, category: String, description: String, path: String) = StreamPlaylist(
        id = id, name = name, url = "$MIRROR_BASE/playlists/$id.m3u", fallbackUrl = "$UPSTREAM/$path",
        category = category, description = description, isDefault = true,
    )
}

/** On-device store of stream playlists. All I/O runs on [Dispatchers.IO]; every public call is safe to make from the UI. */
object PlaylistStore {
    private const val MAX_BYTES = 40 * 1024 * 1024
    private const val MAX_INDEX_BYTES = 1L * 1024 * 1024
    const val STALE_MS = 2 * 60 * 60 * 1000L
    private const val STORE_VERSION = 2

    private val mutex = Mutex()
    private val _playlists = MutableStateFlow<List<StreamPlaylist>>(emptyList())
    val playlists: StateFlow<List<StreamPlaylist>> = _playlists.asStateFlow()
    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()
    private var deletedDefaults = mutableSetOf<String>()
    @Volatile private var loadedDir: File? = null

    private fun dir(context: Context) = File(context.filesDir, "streams").apply { mkdirs() }
    private fun indexFile(context: Context) = File(dir(context), "playlists.json")
    private fun channelsFile(context: Context, id: String) = File(dir(context), "ch_${id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}.m3u")

    /** Loads saved playlists, seeding the defaults on first run. */
    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock { ensureLoaded(context) }
    }

    private fun ensureLoaded(context: Context) {
        val d = dir(context)
        if (loadedDir == d) return
        val file = indexFile(context)
        val saved = runCatching { JSONObject(file.readText()) }.getOrNull()
        if (saved == null) {
            _playlists.value = DefaultPlaylists.all
            deletedDefaults = mutableSetOf()
            _lastSync.value = 0L
        } else {
            val arr = saved.optJSONArray("playlists") ?: JSONArray()
            _playlists.value = (0 until arr.length()).mapNotNull { runCatching { StreamPlaylist.fromJson(arr.getJSONObject(it)) }.getOrNull() }
                .distinctBy { it.id }
            val del = saved.optJSONArray("deletedDefaults") ?: JSONArray()
            deletedDefaults = (0 until del.length()).map { del.optString(it) }.filter { it.isNotBlank() }.toMutableSet()
            _lastSync.value = saved.optLong("lastSync")
        }
        loadedDir = d
        val migrated = saved != null && saved.optInt("version", 1) < STORE_VERSION && migrateDefaults(context)
        if (saved == null || migrated || saved.optInt("version", 1) < STORE_VERSION) persist(context)
    }

    /**
     * v2: only one default ships. Unedited former defaults are removed (they are in the Market now);
     * ones the user renamed or re-pointed are kept as ordinary playlists.
     */
    private fun migrateDefaults(context: Context): Boolean {
        val keep = DefaultPlaylists.ids
        var changed = false
        _playlists.value = _playlists.value.mapNotNull { p ->
            when {
                !p.isDefault || p.id in keep -> p
                p.userEdited -> { changed = true; p.copy(isDefault = false, category = p.category ?: "Custom") }
                else -> { changed = true; channelsFile(context, p.id).delete(); null }
            }
        }
        deletedDefaults.retainAll(keep)
        return changed
    }

    private fun persist(context: Context) {
        val json = JSONObject().apply {
            put("version", STORE_VERSION)
            put("playlists", JSONArray().apply { _playlists.value.forEach { put(it.toJson()) } })
            put("deletedDefaults", JSONArray(deletedDefaults.toList()))
            put("lastSync", _lastSync.value)
        }
        writeAtomic(indexFile(context), json.toString())
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    private suspend fun <T> edit(context: Context, block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { ensureLoaded(context); block().also { persist(context) } }
    }

    private fun replace(id: String, f: (StreamPlaylist) -> StreamPlaylist) {
        _playlists.value = _playlists.value.map { if (it.id == id) f(it) else it }
    }

    fun get(id: String?): StreamPlaylist? = id?.let { i -> _playlists.value.firstOrNull { it.id == i } }

    // region CRUD

    /** Adds a remote playlist after downloading and validating it. Returns the new playlist. */
    suspend fun addRemote(context: Context, name: String, url: String): StreamPlaylist = withContext(Dispatchers.IO) {
        val text = fetchText(url)
        val channels = parseChannels(text)
        val p = StreamPlaylist(
            id = "u_" + UUID.randomUUID().toString().take(8), name = name.ifBlank { guessTitle(url) }, url = url,
            category = "Custom", channelCount = channels.size, lastUpdated = System.currentTimeMillis(),
        )
        edit(context) {
            writeAtomic(channelsFile(context, p.id), text)
            _playlists.value = _playlists.value + p
        }
        p
    }

    /** Creates a playlist stored only on this device, optionally pre-filled with [channels]. */
    suspend fun createLocal(context: Context, name: String, channels: List<StreamRequest> = emptyList()): StreamPlaylist {
        val p = StreamPlaylist(
            id = "l_" + UUID.randomUUID().toString().take(8), name = name.ifBlank { "My playlist" },
            category = "My playlist", channelCount = channels.size, lastUpdated = System.currentTimeMillis(),
        )
        edit(context) {
            writeAtomic(channelsFile(context, p.id), M3uWriter.write(channels, p.name))
            _playlists.value = _playlists.value + p
        }
        return p
    }

    /** Renames a playlist and/or changes its URL (remote playlists only). */
    suspend fun update(context: Context, id: String, name: String, url: String?) = edit(context) {
        replace(id) { p ->
            val newUrl = if (p.isLocal) null else url?.trim()?.ifBlank { null } ?: p.url
            val changedUrl = newUrl != p.url
            p.copy(
                name = name.ifBlank { p.name }, url = newUrl,
                // A user-chosen URL replaces the mirror; don't silently fall back to the old source.
                fallbackUrl = if (changedUrl) null else p.fallbackUrl,
                userEdited = p.userEdited || p.isDefault || (p.isMarket && changedUrl),
                lastUpdated = if (changedUrl) 0L else p.lastUpdated,
            )
        }
    }

    suspend fun setEnabled(context: Context, id: String, enabled: Boolean) = edit(context) { replace(id) { it.copy(enabled = enabled) } }

    suspend fun delete(context: Context, id: String) = edit(context) {
        val p = get(id) ?: return@edit
        if (p.isDefault) deletedDefaults += p.id
        _playlists.value = _playlists.value.filterNot { it.id == id }
        channelsFile(context, id).delete()
    }

    /** Brings back deleted defaults and re-enables all defaults. Returns how many were restored. */
    suspend fun restoreDefaults(context: Context): Int = edit(context) {
        val existing = _playlists.value.map { it.id }.toSet()
        val missing = DefaultPlaylists.all.filter { it.id !in existing }
        deletedDefaults.clear()
        _playlists.value = _playlists.value.map { if (it.isDefault) it.copy(enabled = true) else it } + missing
        missing.size
    }

    // endregion

    // region Channels

    /**
     * Channels of [id]. Remote playlists are downloaded when there is no cache, when [forceRefresh]
     * is set, or when the cache is older than 2 hours (the cache is used if the download fails).
     */
    suspend fun channels(context: Context, id: String, forceRefresh: Boolean = false): List<StreamRequest> = withContext(Dispatchers.IO) {
        mutex.withLock { ensureLoaded(context) }
        val p = get(id) ?: error("Playlist not found")
        val file = channelsFile(context, id)
        if (!p.isLocal) {
            // Market playlists update through the catalog (sha256), others when older than 2 h.
            val stale = p.autoUpdate && !p.isMarket && System.currentTimeMillis() - p.lastUpdated > STALE_MS
            if (forceRefresh || !file.exists() || stale) {
                val result = runCatching { refresh(context, id) }
                if (result.isFailure && !file.exists()) throw result.exceptionOrNull() ?: IllegalStateException("Download failed")
            }
        }
        if (!file.exists()) return@withContext emptyList()
        M3uParser.parse(file.readText())
    }

    /** Downloads a remote playlist (mirror first, then the original source) and caches it. */
    suspend fun refresh(context: Context, id: String, marketSha: String? = null): Int = withContext(Dispatchers.IO) {
        val p = mutex.withLock { ensureLoaded(context); get(id) } ?: error("Playlist not found")
        val urls = listOfNotNull(p.url, p.fallbackUrl).distinct()
        require(urls.isNotEmpty()) { "This playlist is stored on the device" }
        var lastError: Throwable? = null
        for (u in urls) {
            try {
                val text = fetchText(u)
                val count = parseChannels(text).size
                edit(context) {
                    writeAtomic(channelsFile(context, id), text)
                    replace(id) { it.copy(channelCount = count, lastUpdated = System.currentTimeMillis(), lastError = null, marketSha = marketSha ?: it.marketSha) }
                }
                return@withContext count
            } catch (e: Exception) {
                lastError = e
            }
        }
        val msg = lastError?.message ?: "Download failed"
        edit(context) { replace(id) { it.copy(lastError = msg) } }
        throw IllegalStateException(msg, lastError)
    }

    private fun parseChannels(text: String): List<StreamRequest> {
        require(!M3uParser.isHlsPlaylist(text)) { "This is a single HLS stream, not a channel list" }
        return M3uParser.parse(text).also { require(it.isNotEmpty()) { "No channels found in playlist" } }
    }

    private suspend fun mutateLocal(context: Context, id: String, f: (List<StreamRequest>) -> List<StreamRequest>) = withContext(Dispatchers.IO) {
        edit(context) {
            val p = get(id) ?: error("Playlist not found")
            require(p.isLocal) { "Only your own playlists can be edited channel by channel" }
            val file = channelsFile(context, id)
            val current = if (file.exists()) M3uParser.parse(file.readText()) else emptyList()
            val next = f(current)
            writeAtomic(file, M3uWriter.write(next, p.name))
            replace(id) { it.copy(channelCount = next.size, lastUpdated = System.currentTimeMillis()) }
            next
        }
    }

    suspend fun addChannels(context: Context, id: String, items: List<StreamRequest>) =
        mutateLocal(context, id) { cur -> cur + items.filter { n -> cur.none { it.url == n.url && it.title == n.title } } }

    suspend fun removeChannel(context: Context, id: String, index: Int) =
        mutateLocal(context, id) { cur -> cur.filterIndexed { i, _ -> i != index } }

    suspend fun updateChannel(context: Context, id: String, index: Int, item: StreamRequest) =
        mutateLocal(context, id) { cur -> cur.mapIndexed { i, c -> if (i == index) item else c } }

    /** Writes the playlist as an .m3u file to [target] (a SAF document). */
    suspend fun export(context: Context, id: String, target: Uri): Int = withContext(Dispatchers.IO) {
        val p = get(id) ?: error("Playlist not found")
        val list = channels(context, id)
        val text = M3uWriter.write(list, p.name)
        (context.contentResolver.openOutputStream(target, "wt") ?: error("Cannot write file")).use { it.write(text.toByteArray()) }
        list.size
    }

    /** Writes arbitrary channels (e.g. an opened, unsaved list) as an .m3u file. */
    suspend fun exportChannels(context: Context, name: String?, channels: List<StreamRequest>, target: Uri) = withContext(Dispatchers.IO) {
        (context.contentResolver.openOutputStream(target, "wt") ?: error("Cannot write file"))
            .use { it.write(M3uWriter.write(channels, name).toByteArray()) }
    }

    // endregion

    // region Sync

    data class SyncResult(val updated: Int, val failed: Int, val added: Int)

    /**
     * Reads the mirror index published by the stream-sync workflow, updates the default playlists
     * (new URLs, names, newly added defaults) and re-downloads every enabled default playlist.
     * Works without an app update; falls back to the built-in list when the index is unavailable.
     */
    suspend fun sync(context: Context): SyncResult = withContext(Dispatchers.IO) {
        mutex.withLock { ensureLoaded(context) }
        var added = 0
        val index = runCatching {
            JSONObject(TrustedSources.fetchCatalogText(DefaultPlaylists.INDEX_URL, MAX_INDEX_BYTES))
        }.getOrNull()
        val entries = index?.optJSONArray("playlists")
        if (entries != null) edit(context) {
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val id = e.optString("id").ifBlank { null } ?: continue
                if (id in deletedDefaults || id !in DefaultPlaylists.ids) continue
                // Defaults may only point at the official mirror; the upstream fallback must be HTTPS.
                val url = e.optString("url").trim().takeIf { TrustedSources.isOfficialCatalogUrl(it) } ?: continue
                val source = e.optString("source").trim().takeIf { it.startsWith("https://") }
                val existing = get(id)
                if (existing == null) {
                    _playlists.value = _playlists.value + StreamPlaylist(
                        id = id, name = e.optString("name").ifBlank { id }, url = url, fallbackUrl = source,
                        category = e.optString("category").ifBlank { null }, description = e.optString("description").ifBlank { null },
                        isDefault = true, channelCount = e.optInt("channels"),
                    )
                    added++
                } else if (existing.isDefault && !existing.userEdited) {
                    replace(id) {
                        it.copy(
                            name = e.optString("name").ifBlank { it.name }, url = url, fallbackUrl = source ?: it.fallbackUrl,
                            category = e.optString("category").ifBlank { it.category },
                            description = e.optString("description").ifBlank { it.description },
                        )
                    }
                }
            }
        }
        var updated = 0; var failed = 0
        for (p in _playlists.value.filter { it.isDefault && it.enabled && !it.isLocal }) {
            if (runCatching { refresh(context, p.id) }.isSuccess) updated++ else failed++
        }
        if (updated > 0 || index != null) edit(context) { _lastSync.value = System.currentTimeMillis() }
        if (updated == 0 && failed > 0) error("Could not reach the playlist servers")
        SyncResult(updated, failed, added)
    }

    // endregion

    // region Market

    /** Installed market playlist for catalog id [marketId], if any. */
    fun installed(marketId: String): StreamPlaylist? = _playlists.value.firstOrNull { it.marketId == marketId }

    /** Downloads [entry] (repaired mirror first, then its original source) and adds it to the user's playlists. */
    suspend fun installFromMarket(context: Context, entry: PlaylistMarket.Entry): StreamPlaylist = withContext(Dispatchers.IO) {
        mutex.withLock { ensureLoaded(context) }
        installed(entry.id)?.let { return@withContext it }
        // The pre-installed default is also listed in the market; never create a duplicate of it.
        get(entry.id)?.takeIf { it.isDefault }?.let { return@withContext it }
        var lastError: Throwable? = null
        for (u in listOfNotNull(entry.url, entry.source).distinct()) {
            try {
                val text = fetchText(u)
                val channels = parseChannels(text)
                val p = StreamPlaylist(
                    id = entry.installId, name = entry.name, url = entry.url, fallbackUrl = entry.source,
                    category = entry.category, description = entry.description, channelCount = channels.size,
                    lastUpdated = System.currentTimeMillis(), marketId = entry.id, marketSha = entry.sha256, repo = entry.repo,
                )
                edit(context) {
                    writeAtomic(channelsFile(context, p.id), text)
                    _playlists.value = _playlists.value.filterNot { it.id == p.id } + p
                }
                return@withContext p
            } catch (e: Exception) { lastError = e }
        }
        throw IllegalStateException(lastError?.message ?: "Download failed", lastError)
    }

    suspend fun setAutoUpdate(context: Context, id: String, auto: Boolean) = edit(context) { replace(id) { it.copy(autoUpdate = auto) } }

    data class UpdateResult(val updated: List<String>, val failed: List<String>)

    /**
     * Updates installed market playlists whose catalog sha256 changed. With [onlyAuto] only
     * playlists that have auto-update on are touched (background check); otherwise all of [ids]
     * (or every outdated one when null).
     */
    suspend fun applyMarketUpdates(
        context: Context, catalog: PlaylistMarket.Catalog, onlyAuto: Boolean, ids: Set<String>? = null,
    ): UpdateResult = withContext(Dispatchers.IO) {
        mutex.withLock { ensureLoaded(context) }
        val pending = PlaylistMarket.updatesFor(_playlists.value, catalog)
            .filter { (p, _) -> (!onlyAuto || (p.autoUpdate && p.enabled)) && (ids == null || p.id in ids) }
        val ok = mutableListOf<String>(); val bad = mutableListOf<String>()
        for ((p, e) in pending) {
            // Keep the catalog's mirror/source in sync too, unless the user changed the URL.
            if (!p.userEdited && (p.url != e.url || p.fallbackUrl != e.source)) edit(context) { replace(p.id) { it.copy(url = e.url, fallbackUrl = e.source) } }
            if (runCatching { refresh(context, p.id, marketSha = e.sha256) }.isSuccess) ok += p.name else bad += p.name
        }
        UpdateResult(ok, bad)
    }

    /** Background check used by the Stream tab: at most every 6 hours, silent on failure. */
    suspend fun autoUpdateMarket(context: Context): UpdateResult? {
        if (_playlists.value.none { it.isMarket && it.autoUpdate }) return null
        val cat = PlaylistMarket.load(context) ?: return null
        return runCatching { applyMarketUpdates(context, cat, onlyAuto = true) }.getOrNull()
    }

    // endregion

    // region Network

    private fun fetchText(url: String, requireM3u: Boolean = true): String {
        val (_, bytes, _) = download(url)
        val text = bytes.decodeToString()
        if (requireM3u) require(!text.trimStart().startsWith("<")) { "The link returned a web page, not a playlist" }
        return text
    }

    private data class Download(val finalUrl: String, val bytes: ByteArray, val meta: Triple<Int, String?, List<String>>)

    /**
     * GET with manual redirect handling (reports the redirect chain, follows http ⇄ https).
     * Uses the shared client, so the DNS fallback in Settings → Network also applies to playlists.
     */
    private fun download(start: String): Triple<String, ByteArray, Download> {
        var current = start
        val chain = mutableListOf<String>()
        val client = NetworkClient.noRedirects()
        repeat(6) {
            val request = Request.Builder().url(current)
                .header("User-Agent", UserAgentPreset.CHROME.value!!)
                .header("Accept", "*/*")
                .build()
            client.newCall(request).execute().use { response ->
                val code = response.code
                if (code in 300..399) {
                    val loc = response.header("Location") ?: error("Redirect without location")
                    chain += current
                    current = URL(URL(current), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) error("HTTP $code")
                val source = response.body.source()
                require(!source.request(MAX_BYTES.toLong() + 1)) { "Playlist too large" }
                val bytes = source.buffer.readByteArray()
                val d = Download(current, bytes, Triple(code, response.header("Content-Type"), chain.toList()))
                return Triple(current, bytes, d)
            }
        }
        error("Too many redirects")
    }

    /** Follows redirects and reports what [url] returns, without saving anything. */
    suspend fun analyze(url: String): PlaylistAnalysis = withContext(Dispatchers.IO) {
        val (finalUrl, bytes, d) = download(url.trim())
        val text = bytes.decodeToString()
        val isHls = M3uParser.isHlsPlaylist(text)
        val channels = if (isHls) emptyList() else M3uParser.parse(text)
        val groups = channels.groupingBy { it.group ?: "Ungrouped" }.eachCount().toList().sortedByDescending { it.second }
        val head = text.lineSequence().take(5).toList()
        PlaylistAnalysis(
            requestedUrl = url.trim(), finalUrl = finalUrl, redirects = d.meta.third, status = d.meta.first,
            contentType = d.meta.second, bytes = bytes.size, isHls = isHls, channels = channels.size,
            groups = groups.take(12),
            drmChannels = channels.count { it.drmScheme != DrmScheme.NONE },
            headerChannels = channels.count { it.userAgent != null || it.referer != null || it.origin != null || it.cookie != null || it.headers.isNotEmpty() },
            epgUrl = Regex("""(?:url-tvg|x-tvg-url)="([^"]+)"""").find(head.firstOrNull().orEmpty())?.groupValues?.get(1),
            lastUpdatedNote = head.firstOrNull { it.contains("updated", true) && it.startsWith("#") }?.removePrefix("#")?.trim(),
        )
    }

    // endregion

    /** For tests: forget the in-memory state so the next call reloads from disk. */
    internal fun resetForTests() { loadedDir = null; _playlists.value = emptyList(); deletedDefaults = mutableSetOf(); _lastSync.value = 0L }
}
