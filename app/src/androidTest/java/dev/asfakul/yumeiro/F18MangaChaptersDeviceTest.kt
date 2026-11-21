/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import dev.asfakul.yumeiro.ui.screens.manga.MangaReaderScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * F-18 on a device: the manga reader's top bar has Previous / Next chapter, and the end of a chapter
 * (vertical mode) shows a "Read next chapter" card that opens the next one. A fake source with 3 chapters of
 * 2 small pages each (source order newest first, like real sources). Lines: F18_CASE ….
 */
@RunWith(AndroidJUnit4::class)
class F18MangaChaptersDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun log(s: String) = Log.i("YuF18", s)
    private fun hasText(t: String) = compose.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private val png: ByteArray by lazy {
        val b = Bitmap.createBitmap(300, 420, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(200, 180, 120)) }
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
    private val fake = object : MangaProvider {
        override val id = "yu_test_f18"
        override val name = "YuF18 test source"
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = false
        override val baseUrl: String? = null
        override suspend fun popular(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun latest(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false)
        override suspend fun details(url: String) = MangaDetails(id, url, "YuF18 Title", null)
        override suspend fun chapters(url: String) = (3 downTo 1).map { MangaChapter("$url/c$it", "YuF18 Chapter $it", it.toFloat()) }
        override suspend fun pages(chapterUrl: String) = (0 until 2).map { MangaPageRef(it, "$chapterUrl/p$it", null) }
        override suspend fun image(page: MangaPageRef) = MangaImage(png, "image/png")
    }

    @After fun tearDown() { runCatching { SourceHub.remove("yu_test_f18_bridge") } }

    @Test fun previousNextAndTheEndCardMoveBetweenChapters() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("manga_reader", 0).edit().putBoolean("vertical", true).commit()
        SourceHub.publishManga("yu_test_f18_bridge", listOf(fake))
        compose.setContent { MTPlayerTheme { Box(Modifier.fillMaxSize()) { MangaReaderScreen(fake.id, "yuf18://m", "YuF18 Title", "yuf18://m/c1", "YuF18 Chapter 1", onBack = {}) } } }
        compose.waitUntil(20_000) { has("manga_reader") }
        // The top bar shows with the reader's controls; a tap on the page toggles them.
        if (!has("reader_next")) { compose.onNodeWithTag("manga_reader").performClick(); compose.waitForIdle() }
        compose.waitUntil(20_000) { hasText("YuF18 Chapter 1") && has("reader_next") }
        // Chapters load in the background; Next turns on when they are in.
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithTag("reader_next").assertIsEnabled(); true }.getOrDefault(false) }
        val firstHasNoPrev = runCatching { compose.onNodeWithTag("reader_prev").assertIsNotEnabled(); true }.getOrDefault(false)
        compose.onNodeWithTag("reader_next").performClick()
        val toTwo = runCatching { compose.waitUntil(15_000) { hasText("YuF18 Chapter 2") }; true }.getOrDefault(false)
        val prevOn = runCatching { compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("reader_prev").assertIsEnabled(); true }.getOrDefault(false) }; true }.getOrDefault(false)
        FirstRun.shot("f18_ch2")
        // End of chapter 2: the "Read next chapter" card.
        var card = false; var toThree = false
        runCatching {
            compose.waitUntil(15_000) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag("reader_next_card"))
            card = has("reader_next_card")
            compose.onNodeWithTag("reader_next_card").performClick()
            toThree = runCatching { compose.waitUntil(15_000) { hasText("YuF18 Chapter 3") }; true }.getOrDefault(false)
        }
        if (!has("reader_next")) { compose.onNodeWithTag("manga_reader").performClick(); compose.waitForIdle() }
        val lastHasNoNext = runCatching { compose.onNodeWithTag("reader_next").assertIsNotEnabled(); true }.getOrDefault(false)
        compose.onNodeWithTag("reader_prev").performClick()
        val backToTwo = runCatching { compose.waitUntil(15_000) { hasText("YuF18 Chapter 2") }; true }.getOrDefault(false)
        FirstRun.shot("f18_back")
        log("F18_CASE firstHasNoPrev=$firstHasNoPrev toTwo=$toTwo prevOn=$prevOn card=$card toThree=$toThree lastHasNoNext=$lastHasNoNext backToTwo=$backToTwo")
        assertTrue("F-18: chapter 1 offers Previous", firstHasNoPrev)
        assertTrue("F-18: Next chapter did not open chapter 2", toTwo && prevOn)
        assertTrue("F-18: no Read next chapter card at the end of chapter 2 (or it did not open 3)", card && toThree)
        assertTrue("F-18: the last chapter still offers Next", lastHasNoNext)
        assertTrue("F-18: Previous chapter did not go back to 2", backToTwo)
    }
}
