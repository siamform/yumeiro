/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaOffline
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import dev.asfakul.yumeiro.ui.screens.manga.MangaReaderScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * F-13 offline manga chapters (ADR-040) on the device: a chapter is saved page by page from a source
 * (no ".part" folder left behind), then read with the source unreachable (every call throws, as with
 * no network): the reader opens the saved copy. A source that fails half way leaves nothing saved.
 * Log tag YuMangaOffline.
 */
@RunWith(AndroidJUnit4::class)
class MangaOfflineDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuMangaOffline", s)
    private val pid = "yu_test_offline"
    private val manga = MangaItem(pid, "yuoff://m", "YuOffline", null)
    private val chapter = MangaChapter("yuoff://m/1", "YuOffline Chapter 1", 1f)
    private val imageCalls = AtomicInteger()

    private fun redPng(): ByteArray {
        val b = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(0xE0, 0, 0)) }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it); b.recycle() }.toByteArray()
    }

    private fun provider(online: Boolean, failAt: Int = -1) = object : MangaProvider {
        override val id = pid
        override val name = "YuOffline test source"
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = false
        override val baseUrl: String? = null
        private fun net() { if (!online) throw IOException("Unable to resolve host (offline)") }
        override suspend fun popular(page: Int) = MangaListPage(emptyList(), false).also { net() }
        override suspend fun latest(page: Int) = MangaListPage(emptyList(), false).also { net() }
        override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false).also { net() }
        override suspend fun details(url: String) = MangaDetails(pid, url, "YuOffline", null).also { net() }
        override suspend fun chapters(url: String) = listOf(chapter).also { net() }
        override suspend fun pages(chapterUrl: String): List<MangaPageRef> { net(); return (0 until 3).map { MangaPageRef(it, "$chapterUrl/p$it", "yuoff://img/$it") } }
        override suspend fun image(page: MangaPageRef): MangaImage {
            net(); imageCalls.incrementAndGet()
            if (page.index == failAt) throw IOException("connection reset on page ${page.index}")
            return MangaImage(redPng(), "image/png")
        }
    }

    private fun shown() = runCatching { compose.onAllNodesWithContentDescription("Page 1", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun failed() = runCatching { compose.onAllNodesWithText("failed", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun parts(): List<String> = File(ctx.filesDir, "manga_offline").listFiles()?.map { it.name }?.filter { it.endsWith(".part") }.orEmpty()

    @After fun tearDown() {
        runCatching { MangaOffline.delete(ctx, pid, chapter.url) }
        runCatching { SourceHub.remove("yu_test_offline_bridge") }
    }

    @Test fun a_halfFailedSaveLeavesNothing() {
        MangaOffline.delete(ctx, pid, chapter.url)
        val r = runCatching { runBlocking { MangaOffline.save(ctx, provider(online = true, failAt = 1), manga, chapter) } }
        log("half save: ${r.exceptionOrNull()?.message} parts=${parts()}")
        assertTrue("a failing page did not fail the save", r.isFailure)
        assertNull("a half chapter shows as saved", MangaOffline.saved(ctx, pid, chapter.url))
        assertTrue("a .part folder was left behind: ${parts()}", parts().isEmpty())
    }

    @Test fun b_savedChapterOpensWithTheSourceOffline() {
        MangaOffline.delete(ctx, pid, chapter.url)
        val saved = runBlocking { MangaOffline.save(ctx, provider(online = true), manga, chapter) }
        log("saved ${saved.pages} pages, ${saved.bytes} bytes; list=${MangaOffline.list(ctx).map { it.chapterName }}")
        assertEquals(3, saved.pages)
        assertTrue(saved.bytes > 0)
        assertNotNull(MangaOffline.saved(ctx, pid, chapter.url))
        assertTrue(MangaOffline.list(ctx).any { it.chapterUrl == chapter.url })
        assertTrue("a .part folder was left behind: ${parts()}", parts().isEmpty())

        // Now the source is unreachable: the reader must use the saved copy.
        val offline = provider(online = false)
        imageCalls.set(0)
        SourceHub.publishManga("yu_test_offline_bridge", listOf(offline))
        compose.setContent { Box(Modifier.fillMaxSize()) { MangaReaderScreen(pid, manga.url, manga.title, chapter.url, chapter.name, onBack = {}) } }
        val ok = runCatching { compose.waitUntil(60_000) { shown() || failed() }; true }.getOrDefault(false)
        SystemClock.sleep(1_000); compose.waitForIdle()
        FirstRun.shot("manga_offline_b")
        log("MO_B shown=${shown()} failed=${failed()} sourceImageCalls=${imageCalls.get()} waited=$ok")
        assertTrue("saved chapter did not open offline", shown() && !failed())
        assertEquals("the reader asked the offline source for pages", 0, imageCalls.get())
    }
}
