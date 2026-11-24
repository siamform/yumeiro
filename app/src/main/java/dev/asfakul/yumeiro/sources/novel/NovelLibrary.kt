/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.novel

import android.content.Context
import dev.asfakul.yumeiro.data.SafeJsonFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * NOVEL-1 (owner item 26): the reader's library and progress. One entry per novel (plugin id + path): saved in
 * the library or not, the last chapter read, where in it (0..1 of its height), and when. Written atomically to
 * filesDir/novel/library.json; read once, off the main thread.
 */
object NovelLibrary {
    data class Entry(
        val pluginId: String,
        val path: String,
        val name: String,
        val cover: String?,
        val saved: Boolean = false,
        val chapterPath: String? = null,
        val chapterName: String? = null,
        val chapterIndex: Int = -1,
        val position: Float = 0f,
        val readAt: Long = 0L,
        val addedAt: Long = 0L,
    ) {
        val key get() = key(pluginId, path)
    }

    private val _entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val entries: StateFlow<Map<String, Entry>> = _entries.asStateFlow()
    @Volatile private var loaded = false
    private val lock = Any()

    fun key(pluginId: String, path: String) = "$pluginId\u0001$path"

    private fun file(c: Context) = File(File(c.applicationContext.filesDir, "novel").apply { mkdirs() }, "library.json")

    fun ensureLoaded(c: Context) = synchronized(lock) {
        if (loaded) return@synchronized
        val list = SafeJsonFile.read(file(c)) { t -> decode(t) }.value.orEmpty()
        _entries.value = list.associateBy { it.key } + _entries.value
        loaded = true
    }

    fun get(pluginId: String, path: String): Entry? = _entries.value[key(pluginId, path)]

    /** Saved novels, newest activity first. */
    fun saved(): List<Entry> = _entries.value.values.filter { it.saved }.sortedByDescending { maxOf(it.readAt, it.addedAt) }

    /** Novels with reading progress, last read first. */
    fun history(): List<Entry> = _entries.value.values.filter { it.readAt > 0 }.sortedByDescending { it.readAt }

    fun setSaved(c: Context, item: NovelItem, saved: Boolean, now: Long = System.currentTimeMillis()) = update(c, item) {
        it.copy(saved = saved, addedAt = if (saved && !it.saved) now else it.addedAt, name = item.name, cover = item.cover ?: it.cover)
    }

    /** Reading progress: [position] is how far down the chapter (0..1). */
    fun progress(c: Context, item: NovelItem, chapter: NovelChapter, index: Int, position: Float, now: Long = System.currentTimeMillis()) = update(c, item) {
        it.copy(chapterPath = chapter.path, chapterName = chapter.name, chapterIndex = index, position = position.coerceIn(0f, 1f), readAt = now)
    }

    fun forget(c: Context, pluginId: String, path: String) = synchronized(lock) {
        ensureLoaded(c)
        _entries.value = _entries.value - key(pluginId, path)
        write(c)
    }

    private fun update(c: Context, item: NovelItem, f: (Entry) -> Entry) = synchronized(lock) {
        ensureLoaded(c)
        val k = key(item.pluginId, item.path)
        val old = _entries.value[k] ?: Entry(item.pluginId, item.path, item.name, item.cover)
        _entries.value = _entries.value + (k to f(old))
        write(c)
    }

    private fun write(c: Context) { SafeJsonFile.write(file(c), encode(_entries.value.values.toList())) }

    @androidx.annotation.VisibleForTesting
    internal fun resetForTests() = synchronized(lock) { loaded = false; _entries.value = emptyMap() }

    fun encode(list: List<Entry>): String = JSONArray().apply {
        for (e in list) put(JSONObject().apply {
            put("p", e.pluginId); put("u", e.path); put("n", e.name); e.cover?.let { put("i", it) }; put("s", e.saved)
            e.chapterPath?.let { put("cu", it) }; e.chapterName?.let { put("cn", it) }; put("ci", e.chapterIndex)
            put("pos", e.position.toDouble()); put("r", e.readAt); put("a", e.addedAt)
        })
    }.toString()

    fun decode(t: String): List<Entry> {
        val a = JSONArray(t)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val p = o.optString("p"); val u = o.optString("u")
            if (p.isBlank() || u.isBlank()) return@mapNotNull null
            Entry(
                p, u, o.optString("n", u), o.optString("i").takeIf { it.isNotBlank() }, o.optBoolean("s"),
                o.optString("cu").takeIf { it.isNotBlank() }, o.optString("cn").takeIf { it.isNotBlank() }, o.optInt("ci", -1),
                o.optDouble("pos", 0.0).toFloat(), o.optLong("r"), o.optLong("a"),
            )
        }
    }
}
