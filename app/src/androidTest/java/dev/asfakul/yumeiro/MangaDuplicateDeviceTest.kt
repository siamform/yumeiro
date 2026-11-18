/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import dev.asfakul.yumeiro.ui.screens.manga.MangaHomeScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * G7-MB1: manga browse removed titles already in the grid, but not a title repeated inside the same
 * page. Sources do repeat one (a pinned or featured title listed twice); the grid is keyed by URL,
 * and a key used twice throws ("Key … was already used") and closes the app. Each title must show once.
 * The title page's chapter list is keyed by chapter URL the same way. Emulator twin of the
 * Robolectric MangaBrowseDuplicateTest; log tag YuMangaDup (MD_… lines). Before the fix the
 * instrumentation process dies ("Process crashed").
 */
@RunWith(AndroidJUnit4::class)
class MangaDuplicateDeviceTest {
    @get:Rule val rule = createComposeRule()

    private fun item(n: Int) = MangaItem("yu_test_mb1", "yumb://t/$n", "YuMb Title $n", null)
    private val fake = object : MangaProvider {
        override val id = "yu_test_mb1"
        override val name = "AAA YuMb test source"
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = false
        override val baseUrl: String? = null
        override suspend fun popular(page: Int) = MangaListPage(listOf(item(1), item(2), item(1), item(3), item(2)), false)
        override suspend fun latest(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false)
        override suspend fun details(url: String) = MangaDetails(id, url, "YuMb", null)
        override suspend fun chapters(url: String) = listOf(MangaChapter("$url/2", "YuMb Chapter 2", 2f), MangaChapter("$url/1", "YuMb Chapter 1", 1f), MangaChapter("$url/1", "YuMb Chapter 1", 1f))
        override suspend fun pages(chapterUrl: String) = emptyList<MangaPageRef>()
        override suspend fun image(page: MangaPageRef) = MangaImage(ByteArray(0), null)
    }

    @After fun tearDown() { SourceHub.remove("yu_test_mb1_bridge") }

    private fun count(t: String) = rule.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test fun titleRepeatedInOnePageShowsOnce() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("manga_home", 0).edit().putString("source", fake.id).commit()
        SourceHub.publishManga("yu_test_mb1_bridge", listOf(fake))
        rule.setContent { MangaHomeScreen(onBack = {}, onOpenTitle = {}, onOpenSources = {}) }
        rule.waitUntil(20_000) { count("YuMb Title 3") > 0 }
        Thread.sleep(1_000); rule.waitForIdle()
        val c = (1..3).map { count("YuMb Title $it") }
        Log.i("YuMangaDup", "MD_GRID counts=$c"); FirstRun.shot("md_grid")
        for (n in 1..3) assertEquals("YuMb Title $n shown once", 1, c[n - 1])
        Log.i("YuMangaDup", "MD_RESULT grid PASS")
    }

    @Test fun chapterRepeatedInTheListShowsOnce() {
        SourceHub.publishManga("yu_test_mb1_bridge", listOf(fake))
        rule.setContent { dev.asfakul.yumeiro.ui.screens.manga.MangaDetailScreen(providerId = fake.id, url = "yumb://t/1", title = "YuMb", onBack = {}, onRead = { _, _ -> }) }
        rule.waitUntil(20_000) { count("YuMb Chapter 2") > 0 }
        Thread.sleep(1_000); rule.waitForIdle()
        val c = count("YuMb Chapter 1")
        Log.i("YuMangaDup", "MD_CHAPTERS chapter1=$c"); FirstRun.shot("md_chapters")
        assertEquals("YuMb Chapter 1 shown once", 1, c)
        Log.i("YuMangaDup", "MD_RESULT chapters PASS")
    }
}
