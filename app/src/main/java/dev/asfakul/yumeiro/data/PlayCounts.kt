/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * How often each video was started — device files, Yumix titles and YouTube videos — so Home's
 * "Most played" can rank videos next to songs (songs already carry a play count in the media
 * database). Stored only on this phone (prefs "play_counts"), capped, and never written while
 * incognito is on.
 */
object PlayCounts {
    enum class Kind { DEVICE, ONLINE, YOUTUBE }

    data class Entry(
        val kind: Kind,
        /** Device video id, Yumix "api|url" key, or YouTube video id. */
        val id: String,
        val title: String,
        val subtitle: String,
        val thumbnail: String?,
        val headers: Map<String, String> = emptyMap(),
        val count: Int,
        val lastAt: Long,
        /** Yumix only: what is needed to reopen the title. */
        val api: String? = null,
        val url: String? = null,
        /**
         * NBUG-11 (ADR-023): the title is 18+, decided when the play was counted, as Continue
         * watching does. Null = not known (saved before the mark existed); [learnAdult] fills it in.
         */
        val adult: Boolean? = null,
    ) {
        val key: String get() = "${kind.name}:$id"
    }

    private const val PREFS = "play_counts"
    private const val KEY = "entries"
    private const val MAX = 200

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    @Volatile private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loaded = true
            val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
            _entries.value = runCatching { parse(JSONArray(raw)) }.getOrDefault(emptyList())
        }
    }

    /** Counts one start of a video. Ignored while incognito is on. */
    fun record(
        context: Context, kind: Kind, id: String, title: String, subtitle: String, thumbnail: String?,
        headers: Map<String, String> = emptyMap(), api: String? = null, url: String? = null, now: Long = System.currentTimeMillis(),
        adult: Boolean? = null,
    ) {
        if (id.isBlank() || dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        dev.asfakul.yumeiro.stats.ActivityLog.addPlay(context, when (kind) {
            Kind.DEVICE -> dev.asfakul.yumeiro.stats.StatsMath.Kind.DEVICE
            Kind.ONLINE -> dev.asfakul.yumeiro.stats.StatsMath.Kind.ONLINE
            Kind.YOUTUBE -> dev.asfakul.yumeiro.stats.StatsMath.Kind.YOUTUBE
        })
        ensureLoaded(context)
        synchronized(this) {
            val list = _entries.value
            val old = list.firstOrNull { it.kind == kind && it.id == id }
            val next = Entry(kind, id, title, subtitle, thumbnail, headers, (old?.count ?: 0) + 1, now, api ?: old?.api, url ?: old?.url, adult ?: old?.adult)
            _entries.value = rank(listOf(next) + list.filterNot { it.kind == kind && it.id == id }).take(MAX)
            save(context)
        }
    }

    /**
     * NBUG-11 (ADR-023): entries saved before the 18+ mark get it once [resolve] can tell (the
     * extension has loaded, or Continue watching knows the title). Saved only if something changed.
     * Call off the main thread.
     */
    fun learnAdult(context: Context, resolve: (Entry) -> Boolean?) {
        ensureLoaded(context)
        synchronized(this) {
            var changed = false
            val next = _entries.value.map { e ->
                if (e.kind != Kind.ONLINE || e.adult != null) e
                else resolve(e)?.let { a -> changed = true; e.copy(adult = a) } ?: e
            }
            if (changed) { _entries.value = next; save(context) }
        }
    }

    /** Most played first; ties go to the most recent. */
    fun rank(list: List<Entry>): List<Entry> = list.sortedWith(compareByDescending<Entry> { it.count }.thenByDescending { it.lastAt })

    fun clear(context: Context) {
        synchronized(this) { _entries.value = emptyList(); save(context) }
    }

    private fun save(context: Context) {
        val a = JSONArray()
        _entries.value.forEach { e ->
            a.put(JSONObject().apply {
                put("k", e.kind.name); put("id", e.id); put("t", e.title); put("s", e.subtitle)
                e.thumbnail?.let { put("th", it) }
                if (e.headers.isNotEmpty()) put("h", JSONObject(e.headers))
                put("c", e.count); put("at", e.lastAt)
                e.api?.let { put("api", it) }; e.url?.let { put("url", it) }
                e.adult?.let { put("ad", it) }
            })
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    internal fun parse(a: JSONArray): List<Entry> = (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val kind = runCatching { Kind.valueOf(o.optString("k")) }.getOrNull() ?: return@mapNotNull null
        val h = o.optJSONObject("h")?.let { j -> j.keys().asSequence().associateWith { j.optString(it) } }.orEmpty()
        Entry(
            kind, o.optString("id"), o.optString("t"), o.optString("s"), o.optString("th").takeIf { it.isNotBlank() }, h,
            o.optInt("c", 1), o.optLong("at"), o.optString("api").takeIf { it.isNotBlank() }, o.optString("url").takeIf { it.isNotBlank() },
            if (o.has("ad")) o.optBoolean("ad") else null,
        )
    }.filter { it.id.isNotBlank() }

    internal fun resetForTests() { _entries.value = emptyList(); loaded = false }
}
