package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 */
interface Source {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Whether the source has support for latest updates.
     *
     * @since extensions-lib 1.6 (declared here; [CatalogueSource] declared it before)
     */
    val supportsLatest: Boolean
        get() = false

    /**
     * Returns the list of filters for the source.
     *
     * @since extensions-lib 1.6 (declared here)
     */
    fun getFilterList(): FilterList = FilterList()

    /** @since extensions-lib 1.6 (declared here; implemented by [CatalogueSource]) */
    suspend fun getPopularManga(page: Int): MangasPage =
        throw UnsupportedOperationException("This source has no catalogue")

    /** @since extensions-lib 1.6 (declared here; implemented by [CatalogueSource]) */
    suspend fun getLatestUpdates(page: Int): MangasPage =
        throw UnsupportedOperationException("This source has no catalogue")

    /** @since extensions-lib 1.6 (declared here; implemented by [CatalogueSource]) */
    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw UnsupportedOperationException("This source has no catalogue")

    /**
     * Gets the manga details and/or its chapters in one call, so a source can share requests.
     * The existing [chapters] are passed so the source can keep their data (e.g. [SChapter.memo]);
     * the parts not requested may be returned as they were given.
     *
     * The default bridges extensions built before lib 1.6 to [getMangaDetails] / [getChapterList].
     *
     * @since extensions-lib 1.6
     */
    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) getMangaDetails(manga) else manga
        val list = if (fetchChapters) getChapterList(manga) else chapters
        return SMangaUpdate(details, list)
    }

    /**
     * Get the updated details for a manga.
     *
     * @since extensions-lib 1.5
     * @param manga the manga to update.
     * @return the updated manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga {
        return fetchMangaDetails(manga).awaitSingle()
    }

    /**
     * Get all the available chapters for a manga.
     *
     * @since extensions-lib 1.5
     * @param manga the manga to update.
     * @return the chapters for the manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> {
        return fetchChapterList(manga).awaitSingle()
    }

    /**
     * Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @since extensions-lib 1.5
     * @param chapter the chapter.
     * @return the pages for the chapter.
     */
    @Suppress("DEPRECATION")
    suspend fun getPageList(chapter: SChapter): List<Page> {
        return fetchPageList(chapter).awaitSingle()
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getMangaDetails"),
    )
    fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getChapterList"),
    )
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPageList"),
    )
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        throw IllegalStateException("Not used")
}
