/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** F-13 (ADR-040): a chapter saved on the phone reads without the network and without its source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MangaOfflineTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private var failAt = -1

    private val fake = object : MangaProvider {
        override val id = "yu_test_f13"
        override val name = "F-13 test source"
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = false
        override val baseUrl: String? = null
        override suspend fun popular(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun latest(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false)
        override suspend fun details(url: String) = MangaDetails(id, url, "T", null)
        override suspend fun chapters(url: String) = emptyList<MangaChapter>()
        override suspend fun pages(chapterUrl: String) = (0 until 3).map { MangaPageRef(it, "$chapterUrl/p$it", "https://img/$it.png") }
        override suspend fun image(page: MangaPageRef): MangaImage {
            if (page.index == failAt) error("network down")
            return MangaImage(byteArrayOf(1, 2, page.index.toByte()), "image/png")
        }
    }
    private val item = MangaItem(fake.id, "https://m/1", "Title", null)
    private val ch = MangaChapter("https://m/1/ch1", "Chapter 1")

    @After fun tearDown() { MangaOffline.delete(ctx, fake.id, ch.url) }

    @Test fun savedChapterReadsBackWithoutTheSource() = runBlocking {
        MangaOffline.save(ctx, fake, item, ch)
        val saved = MangaOffline.saved(ctx, fake.id, ch.url)
        assertNotNull(saved)
        assertEquals(3, saved!!.pages)
        val reader = MangaOffline.provider(ctx, fake.id, ch.url, fallbackName = "gone")!!
        val pages = reader.pages(ch.url)
        assertEquals(3, pages.size)
        assertArrayEquals(byteArrayOf(1, 2, 2), reader.image(pages[2]).bytes)
        assertTrue(MangaOffline.list(ctx).any { it.chapterUrl == ch.url && it.mangaTitle == "Title" })
    }

    @Test fun aFailedPageLeavesNothingHalfSaved() = runBlocking {
        failAt = 1
        val r = runCatching { MangaOffline.save(ctx, fake, item, ch) }
        assertTrue(r.isFailure)
        assertNull(MangaOffline.saved(ctx, fake.id, ch.url))
        assertFalse(MangaOffline.list(ctx).any { it.chapterUrl == ch.url })
    }

    @Test fun deleteRemovesTheFiles() = runBlocking {
        MangaOffline.save(ctx, fake, item, ch)
        assertTrue(MangaOffline.delete(ctx, fake.id, ch.url))
        assertNull(MangaOffline.saved(ctx, fake.id, ch.url))
        assertNull(MangaOffline.provider(ctx, fake.id, ch.url, fallbackName = "x"))
    }

    @Test fun folderNameIsSafeAndStable() {
        val a = MangaOffline.folderName("src/../x", "https://a/b?c=1")
        assertEquals(a, MangaOffline.folderName("src/../x", "https://a/b?c=1"))
        assertTrue(a.all { it.isLetterOrDigit() || it == '_' })
    }
}
