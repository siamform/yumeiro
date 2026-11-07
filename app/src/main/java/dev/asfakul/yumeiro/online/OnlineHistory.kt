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
        val o = runCatching { if (f.exists()) JSONObject(f.readText()) else null }.getOrNull() ?: return
        val list = o.optJSONArray("titles")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::entryFrom) } }.orEmpty()
        _entries.value = list.sortedByDescending { it.updatedAt }
        o.optJSONObject("progress")?.let { p ->
            p.keys().forEach { k -> p.optJSONArray(k)?.let { v -> progress[k] = Progress(v.optLong(0), v.optLong(1), v.optLong(2)) } }
        }
        _version.value++
    }

    fun progressOf(api: String, data: String): Progress? = synchronized(this) { progress[progressKey(api, data)] }

    fun entry(api: String, url: String): Entry? = _entries.value.firstOrNull { it.api == api && it.url == url }

    /** Records progress of what's playing. Finished movies leave "Continue watching". */
    fun record(context: Context, detail: OnlineDetail, version: String, episode: OnlineEpisode?, positionMs: Long, durationMs: Long) {
        ensureLoaded(context)
        if (positionMs <= 0L && durationMs <= 0L) return
        if (detail.kind == OnlineKind.LIVE) return
        val now = System.currentTimeMillis()
        val data = episode?.data ?: detail.playData ?: return
        synchronized(this) {
            progress.remove(progressKey(detail.api, data))
            progress[progressKey(detail.api, data)] = Progress(positionMs, durationMs, now)
            while (progress.size > MAX_PROGRESS) progress.remove(progress.keys.first())
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
    }

    fun remove(context: Context, api: String, url: String) {
        ensureLoaded(context)
        _entries.value = _entries.value.filterNot { it.api == api && it.url == url }
        save()
    }

    /** Removes only 18+ titles (and their resume positions). */
    fun clearAdult(context: Context) {
        ensureLoaded(context)
        val gone = _entries.value.filter { isAdult(it) }
        if (gone.isEmpty()) return
        _entries.value = _entries.value.filterNot { isAdult(it) }
        synchronized(this) { progress.keys.removeAll { k -> AdultContent.isAdultApi(k.substringBefore('|')) } }
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
        synchronized(this) { progress.clear() }
        _version.value++
        save()
    }

    private fun save() {
        val f = file ?: return
        val o = JSONObject()
            .put("version", 1)
            .put("titles", JSONArray().apply { _entries.value.forEach { put(entryJson(it)) } })
            .put("progress", JSONObject().apply { synchronized(this@OnlineHistory) { progress.forEach { (k, p) -> put(k, JSONArray().put(p.positionMs).put(p.durationMs).put(p.at)) } } })
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }.onFailure { Log.w(TAG, "save", it) }
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

    internal fun resetForTests() { loaded = false; file = null; _entries.value = emptyList(); synchronized(this) { progress.clear() } }
}
