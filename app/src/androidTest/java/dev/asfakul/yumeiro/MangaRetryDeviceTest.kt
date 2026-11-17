/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import dev.asfakul.yumeiro.ui.screens.manga.MangaDetailScreen
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * G7 MG-1 (4cee17d): the manga detail page's load caught the cancellation of an older load with
 * runCatching, so a Retry tapped twice (the second tap lands before the page redraws) put
 * "Couldn't load" over the details that then loaded. A fake source (published to SourceHub) fails
 * the first load and answers slowly afterwards; the test taps Retry, steps frames until the first
 * retry's load is running while Retry is still on screen, taps again, and checks that the details
 * show with no error. On the CI emulators (44024309052, 44024330486) Retry is gone in the frame the
 * first retry starts, so no second tap can land: the race is not reachable from the screen and the
 * case is skipped (assumption). It stays as a guard in case Retry ever lingers.
 *
 * libraryLoadIsOffMain (MG-1, 4cee17d): opening the detail page with the library not loaded yet
 * must not read the library file on the main thread (StrictMode listener; only MangaLibrary stacks).
 *
 * Log tag YuMgRetry: MGR_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class MangaRetryDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val calls = AtomicInteger(0)
    @Volatile private var instant = false
    private val cancelled = AtomicInteger(0)

    private fun log(s: String) = Log.i("YuMgRetry", s)
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private val fake = object : MangaProvider {
        override val id = "yu_test_mg1"
        override val name = "YuMg test source"
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = false
        override val baseUrl: String? = null
        override suspend fun popular(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun latest(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false)
        override suspend fun details(url: String): MangaDetails {
            if (instant) return MangaDetails(id, url, "YuMg Loaded Title", null, author = "YuMg Author")
            val n = calls.incrementAndGet()
            log("MGR details call $n")
            if (n == 1) throw java.io.IOException("HTTP 503")
            try { delay(if (n == 2) 3_000 else 600) } catch (e: kotlinx.coroutines.CancellationException) { cancelled.incrementAndGet(); log("MGR call $n cancelled"); throw e }
            return MangaDetails(id, url, "YuMg Loaded Title", null, author = "YuMg Author")
        }
        override suspend fun chapters(url: String) = listOf(MangaChapter("$url/2", "YuMg Chapter 2", 2f), MangaChapter("$url/1", "YuMg Chapter 1", 1f))
        override suspend fun pages(chapterUrl: String) = emptyList<MangaPageRef>()
        override suspend fun image(page: MangaPageRef) = MangaImage(ByteArray(0), null)
    }

    @After fun tearDown() { runCatching { SourceHub.remove("yu_test_mg1_bridge") } }

    @Test fun doubleRetryDoesNotShowAFalseError() {
        SourceHub.publishManga("yu_test_mg1_bridge", listOf(fake))
        compose.setContent { Box(Modifier.fillMaxSize()) { MangaDetailScreen(providerId = fake.id, url = "yumg://t/1", title = "YuMg Title", onBack = {}, onRead = { _, _ -> }) } }
        compose.waitUntil(20_000) { hasText("Couldn't load") && hasText("Retry") }
        log("MGR first error shown")

        compose.mainClock.autoAdvance = false
        compose.onAllNodesWithText("Retry", useUnmergedTree = true)[0].performClick()
        var second = false
        for (f in 0 until 30) {
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(30)
            val retryShown = hasText("Retry")
            if (calls.get() >= 2 && retryShown) { compose.onAllNodesWithText("Retry", useUnmergedTree = true)[0].performClick(); second = true; log("MGR second tap at frame $f"); break }
            if (!retryShown) { log("MGR Retry gone at frame $f, calls=${calls.get()}"); break }
        }
        compose.mainClock.autoAdvance = true
        assumeTrue("could not tap Retry while the first retry was loading (calls=${calls.get()})", second)

        compose.waitUntil(20_000) { hasText("YuMg Chapter 1") }
        Thread.sleep(1_500); compose.waitForIdle()
        FirstRun.shot("mgr_after")
        val falseError = hasText("Couldn't load")
        log("MGR calls=${calls.get()} cancelled=${cancelled.get()} falseError=$falseError")
        assertTrue("MG-1: the second Retry must cancel the first retry's load (cancelled=${cancelled.get()})", cancelled.get() >= 1)
        assertTrue("MG-1: 'Couldn't load' shown over loaded details", !falseError)
        log("MGR_RESULT PASS")
    }

    @Test fun libraryLoadIsOffMain() {
        org.junit.Assume.assumeTrue("penaltyListener needs API 28", android.os.Build.VERSION.SDK_INT >= 28)
        instant = true
        SourceHub.publishManga("yu_test_mg1_bridge", listOf(fake))
        dev.asfakul.yumeiro.sources.manga.MangaLibrary.unloadForTests()
        val inst = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        var old: android.os.StrictMode.ThreadPolicy? = null
        inst.runOnMainSync {
            old = android.os.StrictMode.getThreadPolicy()
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                .penaltyListener({ it.run() }) { v: android.os.strictmode.Violation -> seen += Log.getStackTraceString(v) }.build())
        }
        try {
            compose.setContent { Box(Modifier.fillMaxSize()) { MangaDetailScreen(providerId = fake.id, url = "yumg://t/2", title = "YuMg Title", onBack = {}, onRead = { _, _ -> }) } }
            compose.waitUntil(20_000) { hasText("YuMg Chapter 1") }
            Thread.sleep(500); compose.waitForIdle()
        } finally { inst.runOnMainSync { old?.let { android.os.StrictMode.setThreadPolicy(it) } } }
        val ours = seen.filter { "MangaLibrary" in it }
        log("MGR library main-thread disk calls: ${ours.size} (all ${seen.size})")
        ours.take(2).forEach { log("MGR stack: " + it.lines().take(14).joinToString(" < ")) }
        assertTrue("MG-1: ${ours.size} main-thread disk calls from MangaLibrary on the detail page:\n" + ours.firstOrNull().orEmpty().take(2500), ours.isEmpty())
        log("MGR_RESULT PASS library")
    }
}
