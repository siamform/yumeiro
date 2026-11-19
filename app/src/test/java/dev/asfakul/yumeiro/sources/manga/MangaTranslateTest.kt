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
            ocrCalls++
            return listOf(MangaTlPlan.Box(10, 10, 210, 110, "こんにちは"), MangaTlPlan.Box(5, 90, 25, 99, "12"))
        }
        override fun ready(src: MangaTlPlan.Src, dst: String) = true
        override fun prepare(src: MangaTlPlan.Src, dst: String, wifiOnly: Boolean) {}
        override fun translate(texts: List<String>, src: MangaTlPlan.Src, dst: String) = texts.map { if (it == "こんにちは") "Hello" else "?" }
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
        val k = MangaTlPlan.key("p1", ja, "en")
        assertEquals(k, MangaTlPlan.key("p1", ja, "en"))
        assertNotEquals(k, MangaTlPlan.key("p1", ja, "bn"))
        assertNotEquals(k, MangaTlPlan.key("p1", MangaTlPlan.Src.KO, "en"))
        val list = listOf(MangaTlPlan.Bubble(1, 2, 3, 4, "こんにちは", "Hello \"there\""))
        assertEquals(list, MangaTlPlan.decode(MangaTlPlan.encode(list)))
    }

    @Test fun pagePipelineOffsetsStripsTranslatesAndCaches() = runBlocking {
        val s = MangaTranslate.Settings(true, ja, "en", false)
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
        MangaTranslate.save(c, MangaTranslate.Settings(true, MangaTlPlan.Src.KO, "id", true))
        assertEquals(MangaTranslate.Settings(true, MangaTlPlan.Src.KO, "id", true), MangaTranslate.settings(c))
        MangaTranslate.save(c, MangaTranslate.Settings(false, ja, "en", false))
    }
}
