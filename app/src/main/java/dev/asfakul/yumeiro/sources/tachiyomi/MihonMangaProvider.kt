/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.tachiyomi

import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * One Mihon / Tachiyomi manga source behind Yumeiro's [MangaProvider].
 *
 * Details and chapters go through [eu.kanade.tachiyomi.source.Source.getMangaUpdate] (extensions-lib
 * 1.6; older extensions fall back to getMangaDetails / getChapterList), so a 1.6 source that loads
 * both in one request is asked once: the chapters from the details call are kept for a minute.
 * Calls for the same manga are serialised (1.6 sources refuse concurrent ones), and the sources'
 * hidden memo data is kept in [memos] and handed back.
 */
class MihonMangaProvider(private val source: CatalogueSource, private val memos: MemoStore = MemoStore(null)) : MangaProvider {
    override val id = "mihon:" + source.id
    override val name = source.name
    override val lang = source.lang
    override val ecosystem = "Mihon"
    override val nsfw = false
    override val supportsLatest = source.supportsLatest
    override val baseUrl = (source as? HttpSource)?.let { runCatching { it.getHomeUrl() }.getOrNull() ?: it.baseUrl }

    private val http get() = source as? HttpSource

    private val locks = ConcurrentHashMap<String, Mutex>()
    private class Cached(val at: Long, val chapters: List<SChapter>)
    private val chapterCache = ConcurrentHashMap<String, Cached>()

    override suspend fun popular(page: Int) = io { source.getPopularManga(page).toPage() }
    override suspend fun latest(page: Int) = io { source.getLatestUpdates(page).toPage() }
    override suspend fun search(query: String, page: Int) = io { source.getSearchManga(page, query, source.getFilterList()).toPage() }

    override suspend fun details(url: String): MangaDetails = io {
        val update = update(url, details = true, chapters = true)
        val m = update.manga
        chapterCache[url] = Cached(System.currentTimeMillis(), update.chapters)
        MangaDetails(id, url, m.title, m.thumbnail_url, m.author ?: m.artist, m.description,
            m.genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(), statusLabel(m.status))
    }

    override suspend fun chapters(url: String): List<MangaChapter> = io {
        val cached = chapterCache.remove(url)?.takeIf { System.currentTimeMillis() - it.at < CHAPTER_CACHE_MS }
        val list = cached?.chapters ?: update(url, details = false, chapters = true).chapters
        list.map { MangaChapter(it.url, it.name, it.chapter_number, it.date_upload, it.scanlator) }
    }

    override suspend fun pages(chapterUrl: String): List<MangaPageRef> = io {
        source.getPageList(chapter(chapterUrl)).map { MangaPageRef(it.index, it.url, it.imageUrl) }
    }

    override suspend fun image(page: MangaPageRef): MangaImage = io {
        val src = http ?: error("This source can't load images")
        val p = Page(page.index, page.url, page.imageUrl)
        if (p.imageUrl == null) p.imageUrl = src.getImageUrl(p)
        src.getImage(p).use { r ->
            if (!r.isSuccessful) error("Image failed (HTTP ${r.code})")
            MangaImage(r.body.bytes(), r.body.contentType()?.toString())
        }
    }

    override fun coverHeaders(): Map<String, String> =
        runCatching { http?.headers?.let { h -> h.names().associateWith { n -> h[n].orEmpty() } } }.getOrNull().orEmpty()

    private suspend fun update(url: String, details: Boolean, chapters: Boolean): SMangaUpdate =
        locks.getOrPut(url) { Mutex() }.withLock {
            val known = chapterCache[url]?.chapters.orEmpty()
            val u = source.getMangaUpdate(manga(url), known, details, chapters)
            memos.putAll(listOf(MemoStore.mangaKey(url) to u.manga.memo) +
                u.chapters.map { MemoStore.chapterKey(it.url) to it.memo })
            u
        }

    private fun manga(url: String) = SManga.create().apply {
        this.url = url
        title = ""
        memos.get(MemoStore.mangaKey(url))?.let { memo = it }
    }

    private fun chapter(url: String) = SChapter.create().apply {
        this.url = url
        name = ""
        memos.get(MemoStore.chapterKey(url))?.let { memo = it }
    }

    private fun MangasPage.toPage(): MangaListPage {
        memos.putAll(mangas.map { MemoStore.mangaKey(it.url) to it.memo })
        return MangaListPage(mangas.map { MangaItem(id, it.url, it.title, it.thumbnail_url) }, hasNextPage)
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val CHAPTER_CACHE_MS = 60_000L

        fun statusLabel(status: Int): String? = when (status) {
            SManga.ONGOING -> "Ongoing"
            SManga.COMPLETED -> "Completed"
            SManga.PUBLISHING_FINISHED -> "Finished"
            SManga.CANCELLED -> "Cancelled"
            SManga.ON_HIATUS -> "On hiatus"
            else -> null
        }
    }
}
