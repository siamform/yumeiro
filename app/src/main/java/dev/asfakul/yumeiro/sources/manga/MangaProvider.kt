/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

/** A manga / manhwa / comic / webtoon item in a list. */
data class MangaItem(val providerId: String, val url: String, val title: String, val cover: String?)

data class MangaListPage(val items: List<MangaItem>, val hasNext: Boolean)

data class MangaDetails(
    val providerId: String,
    val url: String,
    val title: String,
    val cover: String?,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
)

data class MangaChapter(
    val url: String,
    val name: String,
    val number: Float = -1f,
    val dateUpload: Long = 0,
    val scanlator: String? = null,
)

/** One page of a chapter. [imageUrl] may be null until [MangaProvider.resolve] is called. */
data class MangaPageRef(val index: Int, val url: String, val imageUrl: String?)

/** Image bytes for a page, plus its MIME type when known. */
class MangaImage(val bytes: ByteArray, val mime: String?)

/**
 * Reading source used by the manga reader. Mihon extensions and JavaScript sources implement it;
 * the UI never knows which one it is talking to.
 */
interface MangaProvider {
    /** Stable id, unique across ecosystems. */
    val id: String
    val name: String
    val lang: String
    val ecosystem: String
    val nsfw: Boolean
    val supportsLatest: Boolean
    val baseUrl: String?

    suspend fun popular(page: Int): MangaListPage
    suspend fun latest(page: Int): MangaListPage
    suspend fun search(query: String, page: Int): MangaListPage
    suspend fun details(url: String): MangaDetails
    suspend fun chapters(url: String): List<MangaChapter>
    suspend fun pages(chapterUrl: String): List<MangaPageRef>
    /** Downloads one page image (with the headers and cookies the source needs). */
    suspend fun image(page: MangaPageRef): MangaImage
}
