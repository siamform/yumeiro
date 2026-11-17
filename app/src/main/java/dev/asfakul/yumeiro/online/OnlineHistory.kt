/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Watch history for online titles: "Continue watching" and per-episode progress.
 * Small JSON file in app storage; never holds stream links (they expire).
 */
object OnlineHistory {
    private const val TAG = "OnlineHistory"
    private const val MAX_TITLES = 120
    private const val MAX_PROGRESS = 3000
    /** Watched to here (or more) counts as finished. */
    private const val DONE_FRACTION = 0.92

    data class Entry(
        val api: String,
        val url: String,
        val name: String,
        val posterUrl: String?,
        val posterHeaders: Map<String, String>,
        val type: String?,
        val version: String,
        /** Episode being watched (null for movies / live). */
        val episodeData: String?,
        val episodeLabel: String?,
        val positionMs: Long,
        val durationMs: Long,
        val updatedAt: Long,
        /** From an 18+ extension or an 18+ title (kept out of feeds when 18+ activity is private). */
        val adult: Boolean = false,
    ) {
        val key: String get() = "$api|$url"
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        fun toItem() = OnlineItem(name, url, api, posterUrl, posterHeaders, type)
    }

    data class Progress(val positionMs: Long, val durationMs: Long, val at: Long) {
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs * DONE_FRACTION
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    /** Most recent first. */
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    private val progress = LinkedHashMap<String, Progress>()
    private val _version = MutableStateFlow(0)
    /** Bumps whenever progress changes (for recomposition). */
    val version: StateFlow<Int> = _version.asStateFlow()
    @Volatile private var loaded = false
    private var file: File? = null

    fun progressKey(api: String, data: String) = "$api|$data"

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val f = File(context.applicationContext.filesDir, "online_history.json").also { file = it }
        // R16 (G5): an unreadable file is kept as .bad and a leftover .tmp is used (SafeJsonFile).
        val o = dev.asfakul.yumeiro.data.SafeJsonFile.read(f) { JSONObject(it) }.value ?: return
        val list = o.optJSONArray("titles")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::entryFrom) } }.orEmpty()
        _entries.value = list.sortedByDescending { it.updatedAt }
        o.optJSONObject("progress")?.let { p ->
            p.keys().forEach { k -> p.optJSONArray(k)?.let { v -> progress[k] = Progress(v.optLong(0), v.optLong(1), v.optLong(2)) } }
            looseIndex = null
        }
        _version.value++
    }

    fun progressOf(api: String, data: String): Progress? = synchronized(this) {
        progress[progressKey(api, data)] ?: run {
            // BUG-1 (D5): extension data often embeds the site's domain, which rotates. When the exact
            // string misses, the same api + the data without scheme/host still finds the spot.
            val loose = hostFree(data)
            // N4-2: Aniyomi and JS ids are now kept as the source gave them ("/anime/1"); spots saved
            // before under CloudStream's "https://site/anime/1" are found through the same host-free index.
            // Plain ids ("ep-1", "rk:12") never borrow.
            if (loose == data && !data.contains('/')) null
            else hostIndex().get(progressKey(api, loose))
                ?: if (loose == data && !loose.startsWith("/")) hostIndex().get(progressKey(api, "/$loose")) else null
        }
    }

    /** hostFree(key) → newest progress (rebuilt after a change; guarded by `this`). */
    private var looseIndex: HashMap<String, Progress>? = null
    private fun hostIndex(): HashMap<String, Progress> = looseIndex ?: HashMap<String, Progress>(progress.size * 2).also { m ->
        for ((k, p) in progress) {
            val lk = hostFree(k)
            val old = m[lk]
            if (old == null || p.at >= old.at) m[lk] = p
        }
        looseIndex = m
    }

    fun entry(api: String, url: String): Entry? = _entries.value.let { list ->
        list.firstOrNull { it.api == api && it.url == url }
            ?: hostFree(url).takeIf { it != url || url.contains('/') }?.let { loose ->
                list.firstOrNull { it.api == api && hostFree(it.url).let { h -> h == loose || (loose == url && h == "/$loose") } }
            }
    }

    /**
     * BUG-1 (D5): [s] with every `http(s)://host` (also JSON-escaped `https:\\/\\/host`) removed (path, query and JSON around it kept), so
     * `https://a.example/ep/1` and `https://b.example/ep/1` match. No regex (main-thread safe). Pure.
     */
    internal fun hostFree(s: String): String {
        if (!s.contains("://") && !s.contains(":\\/\\/")) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val skip = when {
                s.startsWith("https://", i, ignoreCase = true) -> 8
                s.startsWith("http://", i, ignoreCase = true) -> 7
                s.startsWith("https:\\/\\/", i, ignoreCase = true) -> 10 // JSON-escaped
                s.startsWith("http:\\/\\/", i, ignoreCase = true) -> 9
                else -> 0
            }
            if (skip == 0) { sb.append(s[i]); i++; continue }
            var j = i + skip
            while (j < s.length && s[j] !in HOST_END) j++
            i = j
        }
        return sb.toString()
    }
    private const val HOST_END = "/\\\"'?# ,}]"

    /** Records progress of what's playing. Finished movies leave "Continue watching". */
    fun record(context: Context, detail: OnlineDetail, version: String, episode: OnlineEpisode?, positionMs: Long, durationMs: Long) {
        ensureLoaded(context)
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        // BUG-1 (D2): position 0 is a player that has not loaded or not started; it never replaces a saved spot.
        if (positionMs <= 0L) return
        if (detail.kind == OnlineKind.LIVE) return
        val now = System.currentTimeMillis()
        val data = episode?.data ?: detail.playData ?: return
        synchronized(this) {
            progress.remove(progressKey(detail.api, data))
            progress[progressKey(detail.api, data)] = Progress(positionMs, durationMs, now)
            while (progress.size > MAX_PROGRESS) progress.remove(progress.keys.first())
            looseIndex = null
        }
        val finishedMovie = episode == null && durationMs > 0 && positionMs >= durationMs * DONE_FRACTION
        val entry = Entry(
            detail.api, detail.url, detail.name, detail.posterUrl, detail.posterHeaders, detail.type, version,
            episode?.data, episode?.label, positionMs, durationMs, now,
            adult = AdultContent.isAdult(detail.api, detail.type),
        )
        _entries.value = (if (finishedMovie) emptyList() else listOf(entry)) + _entries.value.filterNot { it.key == entry.key }.take(MAX_TITLES - 1)
        _version.value++
        save()
        dev.asfakul.yumeiro.widget.ContinueWidget.maybeRefresh(context, "${entry.key}|${entry.episodeData}")
    }

    fun remove(context: Context, api: String, url: String) {
        ensureLoaded(context)
        _entries.value = _entries.value.filterNot { it.api == api && it.url == url }
        save()
        dev.asfakul.yumeiro.widget.ContinueWidget.refresh(context)
    }

    /** Removes only 18+ titles (and their resume positions). */
    fun clearAdult(context: Context) {
        ensureLoaded(context)
        val gone = _entries.value.filter { isAdult(it) }
        if (gone.isEmpty()) return
        _entries.value = _entries.value.filterNot { isAdult(it) }
        synchronized(this) { progress.keys.removeAll { k -> AdultContent.isAdultApi(k.substringBefore('|')) }; looseIndex = null }
        _version.value++
        save()
    }

    fun isAdult(e: Entry): Boolean = e.adult || AdultContent.isAdult(e.api, e.type)

    /**
     * What feeds, Home and Continue watching may show. 18+ titles are left out when 18+ content is
     * off, or when it is on but kept private; they stay saved and findable through search.
     */
    fun forFeed(list: List<Entry>, adultOn: Boolean = OnlineSettings.adult.value, private: Boolean = OnlineSettings.adultPrivate.value): List<Entry> =
        if (adultOn && !private) list else list.filterNot { isAdult(it) }

    fun clear(context: Context) {
        ensureLoaded(context)
        _entries.value = emptyList()
        synchronized(this) { progress.clear(); looseIndex = null }
        _version.value++
        save()
        dev.asfakul.yumeiro.widget.ContinueWidget.refresh(context)
    }

    // R16 (ST-7): the history was turned into JSON and written to disk on the caller's thread.
    // The player calls [record] on main every few seconds and again while it closes, so with up to
    // 120 titles and 3,000 progress marks that write ran inside the player's dispose (run 43671209244).
    // The caller now only copies the lists; one background thread builds and writes the file, and a
    // newer copy replaces one that has not been written yet.
    private val pending = java.util.concurrent.atomic.AtomicReference<Pair<List<Entry>, Map<String, Progress>>?>(null)
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "yu-history").apply { isDaemon = true } }

    private fun save() {
        val f = file ?: return
        val snap = _entries.value to synchronized(this) { LinkedHashMap(progress) }
        if (pending.getAndSet(snap) != null) return // a write is already queued; it will take this copy
        writer.execute { pending.getAndSet(null)?.let { write(f, it.first, it.second) } }
    }

    /** Blocks until queued writes are on disk (backup, tests). */
    fun flush() { runCatching { writer.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS) } }

    private fun write(f: File, entries: List<Entry>, marks: Map<String, Progress>) {
        val o = JSONObject()
            .put("version", 1)
            .put("titles", JSONArray().apply { entries.forEach { put(entryJson(it)) } })
            .put("progress", JSONObject().apply { marks.forEach { (k, p) -> put(k, JSONArray().put(p.positionMs).put(p.durationMs).put(p.at)) } })
        runCatching { dev.asfakul.yumeiro.data.SafeJsonFile.write(f, o.toString()) }.onFailure { Log.w(TAG, "save", it) }
    }

    private fun entryJson(e: Entry) = JSONObject()
        .put("api", e.api).put("url", e.url).put("name", e.name).put("poster", e.posterUrl)
        .put("posterHeaders", JSONObject(e.posterHeaders)).put("type", e.type).put("version", e.version)
        .put("episodeData", e.episodeData).put("episodeLabel", e.episodeLabel)
        .put("position", e.positionMs).put("duration", e.durationMs).put("at", e.updatedAt).put("adult", e.adult)

    private fun entryFrom(o: JSONObject): Entry? {
        val api = o.optString("api").ifBlank { return null }
        val url = o.optString("url").ifBlank { return null }
        val headers = o.optJSONObject("posterHeaders")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }.orEmpty()
        return Entry(
            api, url, o.optString("name").ifBlank { "Untitled" }, o.optString("poster").ifBlank { null }, headers,
            o.optString("type").ifBlank { null }, o.optString("version"), o.optString("episodeData").ifBlank { null },
            o.optString("episodeLabel").ifBlank { null }, o.optLong("position"), o.optLong("duration"), o.optLong("at"),
            adult = o.optBoolean("adult", false),
        )
    }

    internal fun resetForTests() { flush(); loaded = false; file = null; _entries.value = emptyList(); synchronized(this) { progress.clear(); looseIndex = null } }

    /** For tests: forget the loaded state, so the next call reads the file again. */
    @Synchronized internal fun unloadForTests() { _entries.value = emptyList(); progress.clear(); looseIndex = null; loaded = false; file = null }
}
