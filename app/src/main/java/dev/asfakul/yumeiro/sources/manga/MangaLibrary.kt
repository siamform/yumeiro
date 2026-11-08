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

    private fun file(context: Context) = File(context.filesDir, "manga/library.json").also { it.parentFile?.mkdirs() }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _entries.value = runCatching { json.decodeFromString<List<Entry>>(file(context).readText()) }.getOrDefault(emptyList())
    }

    private fun save(context: Context) = runCatching { file(context).writeText(json.encodeToString(_entries.value)) }

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

    fun progress(context: Context, item: MangaItem, chapter: MangaChapter, page: Int, finished: Boolean) {
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        upsert(context, Entry(item.providerId, item.url, item.title, item.cover)) {
            it.copy(
                lastChapterUrl = chapter.url, lastChapterName = chapter.name, lastPage = page,
                readChapters = if (finished && chapter.url !in it.readChapters) (it.readChapters + chapter.url).takeLast(5000) else it.readChapters,
            )
        }
    }

    fun remove(context: Context, key: String) { load(context); _entries.value = _entries.value.filterNot { it.key == key }; save(context) }

    /** For backups. */
    fun export(context: Context): String { load(context); return json.encodeToString(_entries.value) }
    fun import(context: Context, text: String) {
        val incoming = json.decodeFromString<List<Entry>>(text)
        load(context)
        _entries.value = (incoming + _entries.value).distinctBy { it.key }
        save(context)
    }
}
