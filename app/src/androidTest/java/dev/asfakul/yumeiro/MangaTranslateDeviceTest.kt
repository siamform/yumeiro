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
    private val s = MangaTranslate.Settings(true, "ja", "en", false)

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
        assertTrue("こんにちは not read/translated: $all", out.any { "にちは" in it.source && greeting(it.text) })
        assertTrue("ありがとう not read/translated: $all", out.any { "がとう" in it.source && "thank" in it.text.lowercase() })
        assertTrue("page took $ms ms (emulator budget 20 s)", ms < 20_000)
        log("TL_RESULT_A PASS")
    }

    /**
     * FINAL-6: こんにちは is a greeting; ML Kit's ja→en model on the CI image says "Good afternoon" (run 44595993499),
     * earlier models said "Hello". Both are correct translations; anything else (or the Japanese left as is) fails.
     */
    private fun greeting(t: String) = t.lowercase().let { l -> listOf("hello", "good afternoon", "good day", "hi").any { g -> Regex("\\b$g\\b").containsMatchIn(l) } }

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
        assertTrue("translations not shown over the page: $texts", texts.any { greeting(it) })
        compose.onNodeWithTag("reader_translate").performClick()
        compose.onNodeWithTag("tl_switch").performClick()
        compose.onNodeWithTag("tl_done").performClick()
        compose.waitUntil(10_000) { boxes().isEmpty() }
        FirstRun.shot("tl_off")
        log("TL_B off boxes=${boxes().size}")
        assertTrue("translations still shown after turning it off", boxes().isEmpty())
        log("TL_RESULT_B PASS")
    }

    // TL-2 ------------------------------------------------------------------------------------------------

    /** A drawn page: white bubbles with black Latin text on grey "art" (real OCR reads the pixels). */
    private fun drawnPage(w: Int, h: Int, vararg bubbles: Pair<Int, String>): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(b)
        cv.drawColor(android.graphics.Color.rgb(170, 170, 170))
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; textSize = 46f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        for ((top, text) in bubbles) {
            cv.drawOval(60f, top.toFloat(), w - 60f, top + 220f, fill)
            val tw = ink.measureText(text)
            cv.drawText(text, (w - tw) / 2f, top + 125f, ink)
        }
        return b
    }

    /** TL-2: a Spanish page is translated from Spanish (the first version sent all Latin text as English). */
    @Test fun c_spanishPageIsTranslatedFromSpanishOnDevice() = runBlocking<Unit> {
        MangaTranslate.clear(ctx)
        val es = MangaTranslate.resolve(MangaTranslate.Settings(true, MangaTlPlan.AUTO, "en", false), "es-419")
        log("TL_C resolved from=${es.from} script=${es.src}")
        assertTrue("Auto + es-419 source → es: ${es.from}", es.from == "es")
        val t0 = SystemClock.elapsedRealtime()
        val prep = MangaTranslate.prepare(ctx, es)
        log("TL_C prepare=${prep.isSuccess} ms=${SystemClock.elapsedRealtime() - t0}")
        assertTrue("es pack: ${prep.exceptionOrNull()}", prep.isSuccess)
        val page = drawnPage(900, 1300, 200 to "¿DÓNDE ESTÁ MI GATO?", 800 to "MUCHAS GRACIAS")
        val out = MangaTranslate.page(ctx, "fixture|es_page", listOf(page), es)
        log("TL_C bubbles=${out.map { it.source + " -> " + it.text }}")
        val all = out.joinToString(" | ") { it.text.lowercase() }
        assertTrue("cat not in $all", "cat" in all)
        assertTrue("thank not in $all", "thank" in all)
        log("TL_RESULT_C PASS")
    }

    /** TL-2: a bubble cut by the strip seam of a tall (webtoon) page comes back as one sentence. */
    @Test fun d_bubbleOnAStripSeamIsReadAsOne() = runBlocking<Unit> {
        MangaTranslate.clear(ctx)
        val es = MangaTranslate.Settings(true, "es", "en", false)
        assertTrue(MangaTranslate.prepare(ctx, es).isSuccess)
        // One bubble drawn across y = 1000, then cut there like the reader's strips.
        val whole = drawnPage(900, 2000, 890 to "MUCHAS GRACIAS")
        val a = android.graphics.Bitmap.createBitmap(whole, 0, 0, 900, 1000)
        val b = android.graphics.Bitmap.createBitmap(whole, 0, 1000, 900, 1000)
        val out = MangaTranslate.page(ctx, "fixture|seam", listOf(a, b), es)
        log("TL_D bubbles=${out.map { "[" + it.t + "-" + it.b + "] " + it.source + " -> " + it.text }}")
        assertTrue("one bubble expected, got ${out.size}: $out", out.size == 1)
        assertTrue("translated: $out", "thank" in out.single().text.lowercase())
        log("TL_RESULT_D PASS")
    }

    /** TL-2: English chapter, English target: the reader says so instead of doing nothing; packs are listed. */
    @Test fun e_sameLanguageIsExplainedAndPacksAreListed() {
        val en = object : MangaProvider by fake { override val id = "yu_test_tl_en"; override val lang = "en" }
        MangaTranslate.save(ctx, MangaTranslate.Settings(true, MangaTlPlan.AUTO, "en", false))
        SourceHub.publishManga("yu_test_tl_bridge", listOf(en))
        compose.setContent { Box(Modifier.fillMaxSize()) { MangaReaderScreen(en.id, "yutl://m", "YuTl", "yutl://m/1", "YuTl Chapter 1", onBack = {}) } }
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithTag("tl_status").fetchSemanticsNode() }.isSuccess }
        val msg = compose.onNodeWithTag("tl_status").fetchSemanticsNode().config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString()
        log("TL_E status=$msg")
        assertTrue("same-language note: $msg", "already in English" in msg)
        FirstRun.shot("tl_same")
        compose.onNodeWithTag("tl_status").performClick()
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithTag("tl_pack_es").fetchSemanticsNode() }.isSuccess || runCatching { compose.onNodeWithTag("tl_pack_ja").fetchSemanticsNode() }.isSuccess }
        FirstRun.shot("tl_packs")
        val packs = runBlocking { MangaTranslate.packs(ctx) }
        log("TL_E packs=$packs")
        assertTrue("downloaded packs listed: $packs", packs.isNotEmpty())
        log("TL_RESULT_E PASS")
    }
}
