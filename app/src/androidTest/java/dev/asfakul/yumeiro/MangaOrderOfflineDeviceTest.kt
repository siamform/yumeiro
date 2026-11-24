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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * MANGA-ORDER-1 (owner item 27, parts 2-3) on the device: chapters saved in a random order (15, 12, 14, 12.5, 13,
 * the source gives no numbers) are listed in chapter order, and the reader's Previous / Next move between the saved
 * chapters with the source unreachable (every call throws, as with no network). Log tag YuMangaOrder.
 */
@RunWith(AndroidJUnit4::class)
class MangaOrderOfflineDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuMangaOrder", s)
    private val pid = "yu_test_order"
    private val manga = MangaItem(pid, "yuord://m", "YuOrder", null)
    private val saveOrder = listOf("15", "12", "14", "12.5", "13")
    private val chapters = saveOrder.map { MangaChapter("yuord://m/$it", "Chapter $it") }
    private val netCalls = AtomicInteger()

    private fun png(): ByteArray {
        val b = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(0, 0x60, 0xE0)) }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it); b.recycle() }.toByteArray()
    }

    private fun provider(online: Boolean) = object : MangaProvider {
        override val id = pid
        override val name = "YuOrder test source"
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = false
        override val baseUrl: String? = null
        private fun net() { netCalls.incrementAndGet(); if (!online) throw IOException("Unable to resolve host (offline)") }
        override suspend fun popular(page: Int) = MangaListPage(emptyList(), false).also { net() }
        override suspend fun latest(page: Int) = MangaListPage(emptyList(), false).also { net() }
        override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false).also { net() }
        override suspend fun details(url: String) = MangaDetails(pid, url, "YuOrder", null).also { net() }
        override suspend fun chapters(url: String): List<MangaChapter> { net(); return chapters }
        override suspend fun pages(chapterUrl: String): List<MangaPageRef> { net(); return (0 until 2).map { MangaPageRef(it, "$chapterUrl/p$it", "yuord://img/$it") } }
        override suspend fun image(page: MangaPageRef): MangaImage { net(); return MangaImage(png(), "image/png") }
    }

    private fun has(text: String) = runCatching {
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)
    private fun pageShown() = runCatching {
        compose.onAllNodesWithContentDescription("Page 1", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)
    private fun waitChapter(name: String) {
        compose.waitUntil(30_000) { has(name) && pageShown() }
        compose.waitForIdle()
    }

    @After fun tearDown() {
        chapters.forEach { runCatching { MangaOffline.delete(ctx, pid, it.url) } }
        runCatching { SourceHub.remove("yu_test_order_bridge") }
    }

    @Test fun savedChaptersKeepChapterOrderAndNavigateOffline() {
        chapters.forEach { MangaOffline.delete(ctx, pid, it.url) }
        // Saved one by one in a random order, as a user who downloads 15 first and fills the gaps later.
        chapters.forEach { c -> runBlocking { MangaOffline.save(ctx, provider(online = true), manga, c) }; SystemClock.sleep(20) }

        val listed = MangaOffline.chapters(ctx, pid, manga.url).map { it.name }
        log("saved order=$saveOrder listed=$listed")
        assertEquals(listOf("Chapter 15", "Chapter 14", "Chapter 13", "Chapter 12.5", "Chapter 12"), listed)

        // No network from here on: the source answers every call with an error.
        SourceHub.publishManga("yu_test_order_bridge", listOf(provider(online = false)))
        netCalls.set(0)
        compose.setContent {
            Box(Modifier.fillMaxSize()) { MangaReaderScreen(pid, manga.url, manga.title, "yuord://m/12.5", "Chapter 12.5", onBack = {}) }
        }
        waitChapter("Chapter 12.5")
        FirstRun.shot("manga_order_12_5")
        compose.onNodeWithTag("reader_prev", useUnmergedTree = true).assertIsEnabled()
        compose.onNodeWithTag("reader_next", useUnmergedTree = true).assertIsEnabled()

        compose.onNodeWithTag("reader_next", useUnmergedTree = true).performClick()
        waitChapter("Chapter 13")
        log("next from 12.5 -> 13")
        compose.onNodeWithTag("reader_prev", useUnmergedTree = true).performClick()
        waitChapter("Chapter 12.5")
        compose.onNodeWithTag("reader_prev", useUnmergedTree = true).performClick()
        waitChapter("Chapter 12")
        FirstRun.shot("manga_order_12")
        compose.onNodeWithTag("reader_prev", useUnmergedTree = true).assertIsNotEnabled()
        log("prev from 12.5 -> 12, no chapter before 12")

        repeat(4) { compose.onNodeWithTag("reader_next", useUnmergedTree = true).performClick(); SystemClock.sleep(400); compose.waitForIdle() }
        waitChapter("Chapter 15")
        FirstRun.shot("manga_order_15")
        compose.onNodeWithTag("reader_next", useUnmergedTree = true).assertIsNotEnabled()
        log("walked 12 -> 15 offline; source calls with the network off=${netCalls.get()} (chapter list only, pages from disk)")
        // The only allowed calls are the reader asking the (failing) source for its chapter list; pages come from disk.
        assertTrue("pages were asked from the offline source: ${netCalls.get()} calls", netCalls.get() <= 6)
    }
}
