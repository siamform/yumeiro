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
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One Mihon / Tachiyomi manga source behind Yumeiro's [MangaProvider]. */
class MihonMangaProvider(private val source: CatalogueSource) : MangaProvider {
    override val id = "mihon:" + source.id
    override val name = source.name
    override val lang = source.lang
    override val ecosystem = "Mihon"
    override val nsfw = false
    override val supportsLatest = source.supportsLatest
    override val baseUrl = (source as? HttpSource)?.baseUrl

    private val http get() = source as? HttpSource

    override suspend fun popular(page: Int) = io { source.getPopularManga(page).toPage() }
    override suspend fun latest(page: Int) = io { source.getLatestUpdates(page).toPage() }
    override suspend fun search(query: String, page: Int) = io { source.getSearchManga(page, query, source.getFilterList()).toPage() }

    override suspend fun details(url: String): MangaDetails = io {
        val m = source.getMangaDetails(SManga.create().apply { this.url = url; title = "" })
        MangaDetails(id, url, m.title, m.thumbnail_url, m.author ?: m.artist, m.description,
            m.genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(), statusLabel(m.status))
    }

    override suspend fun chapters(url: String): List<MangaChapter> = io {
        source.getChapterList(SManga.create().apply { this.url = url; title = "" })
            .map { MangaChapter(it.url, it.name, it.chapter_number, it.date_upload, it.scanlator) }
    }

    override suspend fun pages(chapterUrl: String): List<MangaPageRef> = io {
        source.getPageList(SChapter.create().apply { url = chapterUrl; name = "" })
            .map { MangaPageRef(it.index, it.url, it.imageUrl) }
    }

    override suspend fun image(page: MangaPageRef): MangaImage = io {
        val src = http ?: error("This source can't load images")
        val p = Page(page.index, page.url, page.imageUrl)
        if (p.imageUrl.isNullOrBlank()) p.imageUrl = src.getImageUrl(p)
        src.getImage(p).use { r ->
            if (!r.isSuccessful) error("Image failed (HTTP ${r.code})")
            MangaImage(r.body.bytes(), r.body.contentType()?.toString())
        }
    }

    private fun MangasPage.toPage() = MangaListPage(mangas.map { MangaItem(id, it.url, it.title, it.thumbnail_url) }, hasNextPage)

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
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
