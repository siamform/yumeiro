/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import android.content.Context
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.sources.manga.MangaLibrary
import dev.asfakul.yumeiro.sources.novel.NovelLibrary

/**
 * B20 (plan "Migration"): the first time a profile's taste store is used, what that profile already watched, saved
 * and read becomes its starting signals, so For You is useful at once instead of after weeks. Runs once per
 * profile (the store remembers it) and only reads the profile's own history and libraries. 18+ titles stay out.
 */
object TasteImport {
    /** Imports when not done yet for the open profile. Call off the main thread. */
    fun ensure(c: Context) {
        if (TasteSignals.imported(c)) return
        TasteSignals.markImported(c, collect(c, System.currentTimeMillis()))
    }

    /** Pure-ish: the entries the existing stores imply (no network). */
    internal fun collect(c: Context, now: Long): List<TasteSignals.Entry> {
        val out = LinkedHashMap<String, TasteSignals.Entry>()
        fun put(e: TasteSignals.Entry) { out[e.key] = out[e.key]?.let { merge(it, e) } ?: e }

        OnlineHistory.ensureLoaded(c)
        for (h in OnlineHistory.entries.value) {
            if (h.adult) continue
            val ref = TasteRefs.online(h.api, h.url, h.name, h.posterUrl, h.type) ?: continue
            if (ref.adult) continue
            val frac = h.fraction
            val engaged = h.positionMs >= 120_000 || frac >= 0.05f
            val part = TasteSignals.partKey(h.episodeData)
            put(
                TasteSignals.Entry(
                    ref.key, ref.category, h.name, h.posterUrl, opens = 1, lastOpenAt = h.updatedAt,
                    parts = mapOf(part to TasteSignals.Part(frac, engaged, if (frac >= 0.9f) h.updatedAt else 0L)),
                    firstAt = h.updatedAt, lastAt = h.updatedAt,
                ),
            )
        }

        OnlineBookmarks.ensureLoaded(c)
        for (b in OnlineBookmarks.items.value) {
            val ref = TasteRefs.online(b.api, b.url, b.name, b.posterUrl, b.type) ?: continue
            if (ref.adult) continue
            val done = b.status == OnlineBookmarks.Status.COMPLETED
            put(
                TasteSignals.Entry(
                    ref.key, ref.category, b.name, b.posterUrl, saved = b.listed || b.favorite, favorite = b.favorite,
                    completions = if (done) 1 else 0, completedAt = if (done) b.addedAt else 0L, firstAt = b.addedAt, lastAt = b.addedAt,
                ),
            )
        }

        MangaLibrary.load(c)
        for (m in MangaLibrary.entries.value) {
            val ref = TasteRefs.manga(m.providerId, m.url, m.title, m.cover)
            val parts = m.readChapters.takeLast(400).associate { TasteSignals.partKey(it) to TasteSignals.Part(1f, true, m.updatedAt) }
                .ifEmpty { m.lastChapterUrl?.let { mapOf(TasteSignals.partKey(it) to TasteSignals.Part(0f, m.lastPage >= 3, 0L)) }.orEmpty() }
            put(TasteSignals.Entry(ref.key, ref.category, m.title, m.cover, parts = parts, saved = m.saved, firstAt = m.updatedAt, lastAt = m.updatedAt))
        }

        NovelLibrary.ensureLoaded(c)
        for (n in NovelLibrary.entries.value.values) {
            val ref = TasteRefs.novel(n.pluginId, n.path, n.name, n.cover)
            val at = maxOf(n.readAt, n.addedAt)
            // Chapters before the current one were read; the current one as far as the spot.
            val parts = if (n.chapterIndex < 0) emptyMap() else buildMap {
                for (i in 0 until minOf(n.chapterIndex, 400)) put("i$i", TasteSignals.Part(1f, true, n.readAt))
                n.chapterPath?.let { put(TasteSignals.partKey(it), TasteSignals.Part(n.position, n.position >= 0.2f, if (n.position >= 0.98f) n.readAt else 0L)) }
            }
            put(TasteSignals.Entry(ref.key, ref.category, n.name, n.cover, parts = parts, saved = n.saved, firstAt = at, lastAt = at))
        }
        return out.values.toList()
    }

    private fun merge(a: TasteSignals.Entry, b: TasteSignals.Entry) = a.copy(
        cover = a.cover ?: b.cover, opens = maxOf(a.opens, b.opens), lastOpenAt = maxOf(a.lastOpenAt, b.lastOpenAt),
        parts = b.parts + a.parts, saved = a.saved || b.saved, favorite = a.favorite || b.favorite,
        completions = maxOf(a.completions, b.completions), completedAt = maxOf(a.completedAt, b.completedAt),
        firstAt = listOf(a.firstAt, b.firstAt).filter { it > 0 }.minOrNull() ?: 0L, lastAt = maxOf(a.lastAt, b.lastAt),
    )
}
