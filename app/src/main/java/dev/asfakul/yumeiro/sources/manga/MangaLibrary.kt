/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Saved manga plus reading progress, stored on the device (files/manga/library.json).
 * Incognito mode skips progress writes.
 */
object MangaLibrary {
    @Serializable
    data class Entry(
        val providerId: String,
        val url: String,
        val title: String,
        val cover: String? = null,
        val saved: Boolean = false,
        val lastChapterUrl: String? = null,
        val lastChapterName: String? = null,
        val lastPage: Int = 0,
        val readChapters: List<String> = emptyList(),
        val updatedAt: Long = 0,
    ) { val key get() = "$providerId|$url" }

    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()
    @Volatile private var loaded = false

    /**
     * PROF-1: the library (saved titles and Continue reading) belongs to the current profile, like online history
     * (Profiles.file: the old path for the default profile, profiles/<id>/manga/library.json otherwise). It was one
     * file for everyone, so another profile's reading showed up in Continue reading.
     */
    private fun file(context: Context) = dev.asfakul.yumeiro.profiles.Profiles.file(context, "manga/library.json").also { it.parentFile?.mkdirs() }

    /** File of the profile that was loaded; writes go there even if the profile changes before the restart. */
    @Volatile private var loadedFile: File? = null

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val f = file(context).also { loadedFile = it }
        // R16 (G5): an unreadable file is kept as .bad and a leftover .tmp is used (SafeJsonFile).
        _entries.value = dev.asfakul.yumeiro.data.SafeJsonFile.read(f) { json.decodeFromString<List<Entry>>(it) }.value.orEmpty()
        loaded = true
    }

    // R16 (G5): the reader saves on every page turn. This used to encode the whole library and write
    // it straight over the file on the main thread (a kill mid-write truncated it, and the next start
    // then lost everything). One background thread now writes the latest copy through a temp file;
    // a newer copy replaces one not yet written.
    // G5 LB-2: the file (filesDir + mkdirs) is resolved on the writer thread too; doing it here hit
    // the disk on main on every page turn (MangaLibraryMainThreadDeviceTest).
    private val pending = java.util.concurrent.atomic.AtomicReference<Pair<Context, List<Entry>>?>(null)
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "yu-manga-library").apply { isDaemon = true } }

    private fun save(context: Context) {
        if (pending.getAndSet((context.applicationContext ?: context) to _entries.value) != null) return
        writer.execute {
            pending.getAndSet(null)?.let { (c, list) ->
                runCatching { dev.asfakul.yumeiro.data.SafeJsonFile.write(loadedFile ?: file(c), json.encodeToString(list)) }
                    .onFailure { runCatching { android.util.Log.w("MangaLibrary", "save", it) } }
            }
        }
    }

    /** Blocks until queued writes are on disk (backup, tests). */
    fun flush() { runCatching { writer.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS) } }

    /** For tests: forget the loaded state, so the next call reads the file again. */
    @Synchronized internal fun unloadForTests() { flush(); _entries.value = emptyList(); loaded = false; loadedFile = null }

    fun get(providerId: String, url: String): Entry? = _entries.value.firstOrNull { it.providerId == providerId && it.url == url }

    private fun upsert(context: Context, base: Entry, change: (Entry) -> Entry) {
        load(context)
        val cur = get(base.providerId, base.url) ?: base
        val next = change(cur).copy(updatedAt = System.currentTimeMillis())
        _entries.value = listOf(next) + _entries.value.filterNot { it.key == next.key }
        save(context)
    }

    fun toggleSaved(context: Context, item: MangaItem) =
        upsert(context, Entry(item.providerId, item.url, item.title, item.cover)) { it.copy(saved = !it.saved, title = item.title, cover = item.cover ?: it.cover) }

    /** Keeps a stored entry's title / cover current once the title page has loaded (no reorder). */
    fun refreshMeta(context: Context, providerId: String, url: String, title: String?, cover: String?) {
        load(context)
        val cur = get(providerId, url) ?: return
        val t = title?.takeIf { it.isNotBlank() } ?: cur.title
        val c = cover?.takeIf { it.isNotBlank() } ?: cur.cover
        if (t == cur.title && c == cur.cover) return
        _entries.value = _entries.value.map { if (it.key == cur.key) it.copy(title = t, cover = c) else it }
        save(context)
    }

    fun progress(context: Context, item: MangaItem, chapter: MangaChapter, page: Int, finished: Boolean) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        upsert(context, Entry(item.providerId, item.url, item.title, item.cover)) {
            it.copy(
                lastChapterUrl = chapter.url, lastChapterName = chapter.name, lastPage = page,
                readChapters = if (finished && chapter.url !in it.readChapters) (it.readChapters + chapter.url).takeLast(5000) else it.readChapters,
            )
        }
        // TRACK-2: a chapter read to its last page goes to the trackers (Tracking V2 only; no-op when off).
        if (finished) dev.asfakul.yumeiro.tracking.v2.TrackV2.onMangaChapter(
            context, item.providerId, item.url, item.title, chapter.name, chapter.number.toDouble().takeIf { it >= 1.0 }, null,
        )
    }

    fun remove(context: Context, key: String) { load(context); _entries.value = _entries.value.filterNot { it.key == key }; save(context) }

    /** For backups. */
    fun export(context: Context): String { load(context); flush(); return json.encodeToString(_entries.value) }
    fun import(context: Context, text: String) {
        val incoming = json.decodeFromString<List<Entry>>(text)
        load(context)
        _entries.value = (incoming + _entries.value).distinctBy { it.key }
        save(context)
    }
}
