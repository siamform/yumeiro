/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import dev.asfakul.yumeiro.sources.manga.MangaTlPlan
import dev.asfakul.yumeiro.sources.manga.MangaTranslate
import dev.asfakul.yumeiro.ui.screens.manga.MangaReaderScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * F-11 (ADR-044), real ML Kit on the emulator. Fixture assets/manga/tl_page.png: two speech bubbles,
 * こんにちは and ありがとう, plus a page number and a face (must not be "translated").
 *  a: the engine reads both bubbles on the device and translates them (Japanese → English pack
 *     downloaded once); page time logged against the budget.
 *  b: in the reader (fake source), Translate pages on shows the translations over the page; turning it
 *     off in the sheet removes them.
 * Needs network once for the language pack. Log tag YuMangaTl: TL_… lines, TL_RESULT_A / TL_RESULT_B PASS.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MangaTranslateDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private fun log(s: String) = Log.i("YuMangaTl", s)
    private val bytes by lazy { InstrumentationRegistry.getInstrumentation().context.assets.open("manga/tl_page.png").use { it.readBytes() } }
    private val s = MangaTranslate.Settings(true, MangaTlPlan.Src.JA, "en", false)

    @After fun tearDown() {
        runCatching { SourceHub.remove("yu_test_tl_bridge") }
        MangaTranslate.save(ctx, s.copy(on = false))
        MangaTranslate.release()
        MangaTranslate.clear(ctx)
    }

    // FINAL-4: JUnit needs a void test method; the last line (log) returned Int, so the class was invalid.
    @Test fun a_engineReadsAndTranslatesBubblesOnDevice() = runBlocking<Unit> {
        MangaTranslate.clear(ctx)
        val t0 = SystemClock.elapsedRealtime()
        val prep = MangaTranslate.prepare(ctx, s)
        log("TL_A prepare=${prep.isSuccess} ${prep.exceptionOrNull()?.javaClass?.simpleName} ms=${SystemClock.elapsedRealtime() - t0}")
        assertTrue("language pack not ready: ${prep.exceptionOrNull()}", prep.isSuccess)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val t1 = SystemClock.elapsedRealtime()
        val out = MangaTranslate.page(ctx, "fixture|tl_page", listOf(bmp), s)
        val ms = SystemClock.elapsedRealtime() - t1
        log("TL_A page_ms=$ms bubbles=${out.map { it.source + " -> " + it.text }}")
        assertTrue("both bubbles expected, got ${out.size}: $out", out.size == 2)
        val all = out.joinToString(" | ") { it.source + "=" + it.text.lowercase() }
        assertTrue("こんにちは not read/translated: $all", out.any { "にちは" in it.source && "hello" in it.text.lowercase() })
        assertTrue("ありがとう not read/translated: $all", out.any { "がとう" in it.source && "thank" in it.text.lowercase() })
        assertTrue("page took $ms ms (emulator budget 20 s)", ms < 20_000)
        log("TL_RESULT_A PASS")
    }

    private val fake = object : MangaProvider {
        override val id = "yu_test_tl"
        override val name = "YuTl test source"
        override val lang = "ja"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = false
        override val baseUrl: String? = null
        override suspend fun popular(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun latest(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false)
        override suspend fun details(url: String) = MangaDetails(id, url, "YuTl", null)
        override suspend fun chapters(url: String) = listOf(MangaChapter("$url/1", "YuTl Chapter 1", 1f))
        override suspend fun pages(chapterUrl: String) = listOf(MangaPageRef(0, "$chapterUrl/p0", "yutl://tl_page"))
        override suspend fun image(page: MangaPageRef) = MangaImage(bytes, "image/png")
    }

    private fun boxes() = runCatching { compose.onAllNodes(SemanticsMatcher("tag starts with tl_box_0_") { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("tl_box_0_") == true }, useUnmergedTree = true).fetchSemanticsNodes() }.getOrDefault(emptyList())

    @Test fun b_readerShowsTranslationsAndTurnsThemOff() {
        ctx.getSharedPreferences("manga_reader", 0).edit().putBoolean("vertical", false).commit()
        MangaTranslate.save(ctx, s) // on, Japanese → English
        SourceHub.publishManga("yu_test_tl_bridge", listOf(fake))
        compose.setContent { Box(Modifier.fillMaxSize()) { MangaReaderScreen(fake.id, "yutl://m", "YuTl", "yutl://m/1", "YuTl Chapter 1", onBack = {}) } }
        compose.waitUntil(240_000) { boxes().size >= 2 }
        val texts = boxes().map { it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString() }
        FirstRun.shot("tl_on")
        log("TL_B on boxes=$texts")
        assertTrue("translations not shown over the page: $texts", texts.any { "hello" in it.lowercase() })
        compose.onNodeWithTag("reader_translate").performClick()
        compose.onNodeWithTag("tl_switch").performClick()
        compose.onNodeWithTag("tl_done").performClick()
        compose.waitUntil(10_000) { boxes().isEmpty() }
        FirstRun.shot("tl_off")
        log("TL_B off boxes=${boxes().size}")
        assertTrue("translations still shown after turning it off", boxes().isEmpty())
        log("TL_RESULT_B PASS")
    }
}
