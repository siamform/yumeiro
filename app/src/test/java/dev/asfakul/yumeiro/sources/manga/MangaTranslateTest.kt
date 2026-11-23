/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** F-11 (ADR-044): the reader had no translation at all. These cover the pure plan and the page pipeline with a fake engine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MangaTranslateTest {
    private lateinit var c: Context
    private val ja = MangaTlPlan.Src.JA

    private class Fake : MangaTlEngine {
        var ocrCalls = 0; var closed = 0
        override fun ocr(bitmap: Bitmap, src: MangaTlPlan.Src): List<MangaTlPlan.Box> {
            ocrCalls++; scripts += src
            return boxes
        }
        var boxes = listOf(MangaTlPlan.Box(10, 10, 210, 110, "こんにちは"), MangaTlPlan.Box(5, 90, 25, 99, "12"))
        val scripts = ArrayList<MangaTlPlan.Src>(); val pairs = ArrayList<String>(); var fail = false
        override fun ready(from: String, dst: String) = true
        override fun prepare(from: String, dst: String, wifiOnly: Boolean) { pairs += "prepare $from>$dst" }
        override fun translate(texts: List<String>, from: String, dst: String): List<String> {
            pairs += "$from>$dst"
            return texts.map { if (fail) "" else if (it == "こんにちは") "Hello" else "[$it]" }
        }
        override fun close() { closed++ }
    }
    private val fake = Fake()

    @Before fun setUp() {
        c = ApplicationProvider.getApplicationContext()
        MangaTranslate.release(); MangaTranslate.clear(c)
        MangaTranslate.engineFactory = { fake }
    }
    @After fun tearDown() { MangaTranslate.release(); MangaTranslate.clear(c); MangaTranslate.engineFactory = { MlKitTlEngine(it.applicationContext) } }

    @Test fun linesJoinPerScript() {
        assertEquals("こんにちは元気", MangaTlPlan.join(listOf("こんにちは", " 元気 "), ja))
        assertEquals("I can't believe this", MangaTlPlan.join(listOf("I can't be-", "lieve this"), MangaTlPlan.Src.LATIN))
        assertEquals("WAIT - NO", MangaTlPlan.join(listOf("WAIT -", "NO"), MangaTlPlan.Src.LATIN))
    }

    @Test fun pageNumbersPunctuationSpecksAndSfxLettersAreDropped() {
        fun box(t: String, w: Int = 200, h: Int = 100) = MangaTlPlan.Box(0, 0, w, h, t)
        assertTrue(MangaTlPlan.keep(box("こんにちは"), 800, 1100, ja))
        assertFalse(MangaTlPlan.keep(box("12"), 800, 1100, ja))
        assertFalse(MangaTlPlan.keep(box("ーー"), 800, 1100, ja))
        assertFalse(MangaTlPlan.keep(box("…!?"), 800, 1100, ja))
        assertFalse("speck", MangaTlPlan.keep(box("あ", 10, 10), 800, 1100, ja))
        assertFalse("single Latin letter", MangaTlPlan.keep(box("K"), 800, 1100, MangaTlPlan.Src.LATIN))
        assertTrue(MangaTlPlan.keep(box("NO WAY"), 800, 1100, MangaTlPlan.Src.LATIN))
    }

    @Test fun overlayUsesTheImageScaling() {
        assertEquals(Triple(0.5f, 0f, 0f), MangaTlPlan.fit(400f, 5000f, 800, 1100, fillWidth = true))
        val (k, dx, dy) = MangaTlPlan.fit(1000f, 1100f, 800, 1100, fillWidth = false) // letterboxed left/right
        assertEquals(1f, k); assertEquals(100f, dx); assertEquals(0f, dy)
        assertEquals(Triple(0f, 0f, 0f), MangaTlPlan.fit(0f, 0f, 800, 1100, false))
    }

    @Test fun tallBubblesWidenAroundTheirCentreAndStayOnThePage() {
        val tall = MangaTlPlan.Bubble(700, 100, 760, 500, "s", "t") // vertical Japanese bubble at the right edge
        val r = MangaTlPlan.layout(tall, 800)
        assertTrue("wider", r[2] - r[0] > 60)
        assertTrue("on the page", r[0] >= 0 && r[2] <= 800)
        assertEquals(100, r[1]); assertEquals(500, r[3])
        val wide = MangaTlPlan.Bubble(10, 10, 210, 110, "s", "t")
        assertEquals(listOf(10, 10, 210, 110), MangaTlPlan.layout(wide, 800).toList())
    }

    @Test fun cacheKeyAndStorage() {
        val k = MangaTlPlan.key("p1", "ja", "en")
        assertEquals(k, MangaTlPlan.key("p1", "ja", "en"))
        assertNotEquals(k, MangaTlPlan.key("p1", "ja", "bn"))
        assertNotEquals(k, MangaTlPlan.key("p1", "ko", "en"))
        val list = listOf(MangaTlPlan.Bubble(1, 2, 3, 4, "こんにちは", "Hello \"there\""))
        assertEquals(list, MangaTlPlan.decode(MangaTlPlan.encode(list)))
    }

    @Test fun pagePipelineOffsetsStripsTranslatesAndCaches() = runBlocking {
        val s = MangaTranslate.Settings(true, "ja", "en", false)
        val strips = listOf(Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888), Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888))
        val out = MangaTranslate.page(c, "src|p0|0", strips, s)
        assertEquals("page number dropped, both strips read", 2, out.size)
        assertEquals("Hello", out[0].text)
        assertEquals(10, out[0].t); assertEquals(1010, out[1].t) // second strip offset by the first's height
        assertEquals(2, fake.ocrCalls)
        MangaTranslate.release()
        assertEquals("models released", 1, fake.closed)
        assertEquals(out, MangaTranslate.page(c, "src|p0|0", strips, s))
        assertEquals("served from cache, no new OCR", 2, fake.ocrCalls)
        MangaTranslate.clear(c)
        MangaTranslate.page(c, "src|p0|0", strips, s)
        assertEquals("Clear caches really clears", 4, fake.ocrCalls)
    }

    @Test fun settingsRoundTripAndDefaultTarget() {
        assertEquals("bn", MangaTlPlan.defaultTarget("bn"))
        assertEquals("en", MangaTlPlan.defaultTarget("xx"))
        MangaTranslate.save(c, MangaTranslate.Settings(true, "ko", "id", true))
        assertEquals(MangaTranslate.Settings(true, "ko", "id", true), MangaTranslate.settings(c))
        // Old saved values ("ja", "zh", "ko", "en") stay valid; nothing saved means Auto.
        c.getSharedPreferences("manga_reader", 0).edit().remove("tl_src").commit()
        assertEquals(MangaTlPlan.AUTO, MangaTranslate.settings(c).from)
        MangaTranslate.save(c, MangaTranslate.Settings(false, "ja", "en", false))
    }

    // TL-2 -------------------------------------------------------------------------------------------------

    @Test fun autoFollowsTheSourceLanguageAndLatinLanguagesKeepTheirOwnCode() {
        assertEquals("pt", MangaTlPlan.fromProvider("pt-BR"))
        assertEquals("es", MangaTlPlan.fromProvider("es-419"))
        assertEquals("zh", MangaTlPlan.fromProvider("zh-Hans"))
        assertEquals("ja", MangaTlPlan.fromProvider("JA"))
        assertEquals(null, MangaTlPlan.fromProvider("all"))
        assertEquals(null, MangaTlPlan.fromProvider(null))
        val auto = MangaTranslate.Settings(true, MangaTlPlan.AUTO, "bn", false)
        assertEquals("es", MangaTranslate.resolve(auto, "es").from)
        assertEquals(MangaTlPlan.Src.LATIN, MangaTranslate.resolve(auto, "es").src)
        assertEquals("a fixed choice wins over the source", "ko", MangaTranslate.resolve(auto.copy(from = "ko"), "es").from)
        assertEquals(MangaTlPlan.AUTO, MangaTranslate.resolve(auto, "multi").from)
    }

    @Test fun detectReadsTheScript() {
        assertEquals("ja", MangaTlPlan.detect("こんにちは 元気ですか"))
        assertEquals("ja", MangaTlPlan.detect("東京都の天気は晴れです"))
        assertEquals("ko", MangaTlPlan.detect("안녕하세요"))
        assertEquals("zh", MangaTlPlan.detect("你好我们走吧"))
        assertEquals("en", MangaTlPlan.detect("WHERE ARE YOU GOING?"))
        assertEquals("en", MangaTlPlan.detect(""))
    }

    @Test fun bubblesCutByAStripSeamAreJoined() {
        val top = MangaTlPlan.Box(100, 1900, 400, 2046, "WHERE ARE YOU")
        val bottom = MangaTlPlan.Box(110, 2050, 390, 2120, "GOING?")
        val other = MangaTlPlan.Box(500, 2048, 700, 2200, "NO")
        val far = MangaTlPlan.Box(100, 300, 300, 400, "HEY")
        val out = MangaTlPlan.mergeSeams(listOf(far, top, other, bottom), listOf(2048), MangaTlPlan.Src.LATIN)
        assertEquals(3, out.size)
        assertTrue(out.contains(MangaTlPlan.Box(100, 1900, 400, 2120, "WHERE ARE YOU GOING?")))
        assertTrue("a box beside it is not swallowed", out.contains(other))
        assertTrue(out.contains(far))
        val ja = MangaTlPlan.mergeSeams(listOf(MangaTlPlan.Box(0, 2000, 50, 2048, "こんに"), MangaTlPlan.Box(0, 2048, 50, 2100, "ちは")), listOf(2048), MangaTlPlan.Src.JA)
        assertEquals("こんにちは", ja.single().text)
        assertEquals("no seam, no change", listOf(top, bottom), MangaTlPlan.mergeSeams(listOf(top, bottom), emptyList(), MangaTlPlan.Src.LATIN))
    }

    @Test fun tallPagePipelineJoinsTheSeamBubbleAndTranslatesFromSpanish() = runBlocking {
        // Each strip returns the same boxes; strip 1's bubble ends on the seam, strip 2's starts on it.
        fake.boxes = listOf(MangaTlPlan.Box(100, 0, 400, 60, "¿A DÓNDE"), MangaTlPlan.Box(100, 940, 400, 1000, "¿A DÓNDE"))
        val s = MangaTranslate.Settings(true, "es", "en", false)
        val strips = listOf(Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888), Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888))
        val out = MangaTranslate.page(c, "src|tall|0", strips, s)
        assertEquals("strip 1 bottom + strip 2 top joined: ${out.map { it.source }}", 3, out.size)
        assertTrue(out.any { it.t == 940 && it.b == 1060 && it.source == "¿A DÓNDE ¿A DÓNDE" })
        assertEquals("read with the Latin reader", listOf(MangaTlPlan.Src.LATIN, MangaTlPlan.Src.LATIN), fake.scripts)
        assertEquals("translated from Spanish, not as English", listOf("es>en"), fake.pairs)
    }

    @Test fun sameLanguageAndFailedTranslationsAreReportedNotHidden() = runBlocking {
        val strip = listOf(Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888))
        val same = runCatching { MangaTranslate.page(c, "src|p|0", strip, MangaTranslate.Settings(true, "en", "en", false)) }.exceptionOrNull()
        assertTrue("English → English: $same", same is MangaTranslate.SameLanguage)
        assertEquals("no OCR for nothing", 0, fake.ocrCalls)
        fake.fail = true
        val failed = runCatching { MangaTranslate.page(c, "src|p|1", strip, MangaTranslate.Settings(true, "ja", "en", false)) }.exceptionOrNull()
        assertTrue("every bubble failed: $failed", failed is MangaTranslate.TranslateFailed)
        fake.fail = false
        assertEquals("a failure is not cached; the retry translates", "Hello", MangaTranslate.page(c, "src|p|1", strip, MangaTranslate.Settings(true, "ja", "en", false)).first().text)
    }

    @Test fun autoWithAnUnknownSourceReadsTheLanguageFromThePage() = runBlocking {
        val strip = listOf(Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888))
        val out = MangaTranslate.page(c, "src|auto|0", strip, MangaTranslate.Settings(true, MangaTlPlan.AUTO, "bn", false))
        assertEquals("pack already there: no download, translated from Japanese", listOf("ja>bn"), fake.pairs)
        assertEquals(1, out.size)
        fake.boxes = listOf(MangaTlPlan.Box(10, 10, 310, 110, "WHERE ARE YOU GOING?"))
        val en = runCatching { MangaTranslate.page(c, "src|auto|1", strip, MangaTranslate.Settings(true, MangaTlPlan.AUTO, "en", false)) }.exceptionOrNull()
        assertTrue("English page, English target: $en", en is MangaTranslate.SameLanguage)
    }
}
