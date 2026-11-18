/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * G7-MR1: webtoon pages are tall strips. The reader capped only the width (1600 px), so a tall
 * page became one huge bitmap: a 107 MB page (over Android's 100 MB draw limit) was blank on
 * api33-lowram in 44238006080 (drawn on api36), and any tall page is one oversized GPU texture.
 * A fake source (SourceHub) serves solid-colour PNGs made here without a Bitmap (top half red,
 * bottom half blue), so the test itself stays small on the low-RAM phone.
 *  a: 1000 × 12000 (48 MB decoded): red on screen at the top, blue after scrolling down.
 *  b: 1440 × 18500 (107 MB decoded, over the draw limit): must be drawn (red at the top).
 * Log tag YuMangaTall: MT_… lines, MT_RESULT_A / MT_RESULT_B PASS. Shots mt_a_top, mt_a_low, mt_b.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MangaTallPageDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun log(s: String) = Log.i("YuMangaTall", s)

    private fun fake(id: String, w: Int, h: Int) = object : MangaProvider {
        override val id = id
        override val name = "YuTall test source"
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = false
        override val baseUrl: String? = null
        override suspend fun popular(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun latest(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false)
        override suspend fun details(url: String) = MangaDetails(id, url, "YuTall", null)
        override suspend fun chapters(url: String) = listOf(MangaChapter("$url/1", "YuTall Chapter 1", 1f))
        override suspend fun pages(chapterUrl: String) = listOf(MangaPageRef(0, "$chapterUrl/p0", "yutall://$w x $h"))
        override suspend fun image(page: MangaPageRef) = MangaImage(png(w, h), "image/png")
    }

    /** Solid PNG, top half red and bottom half blue, written row by row (no Bitmap). */
    private fun png(w: Int, h: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            val len = data.size
            out.write(byteArrayOf((len ushr 24).toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()))
            val t = type.toByteArray(Charsets.US_ASCII)
            out.write(t); out.write(data)
            val crc = CRC32().apply { update(t); update(data) }.value
            out.write(byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte()))
        }
        val ihdr = ByteArrayOutputStream().apply {
            fun i32(v: Int) = write(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()))
            i32(w); i32(h); write(8); write(2); write(0); write(0); write(0)
        }.toByteArray()
        chunk("IHDR", ihdr)
        val red = ByteArray(1 + w * 3).also { r -> for (x in 0 until w) { r[1 + x * 3] = 0xE0.toByte() } }
        val blue = ByteArray(1 + w * 3).also { r -> for (x in 0 until w) { r[3 + x * 3] = 0xE0.toByte() } }
        val idat = ByteArrayOutputStream()
        DeflaterOutputStream(idat).use { d -> for (y in 0 until h) d.write(if (y < h / 2) red else blue) }
        chunk("IDAT", idat.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun screen(): Bitmap? = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    private fun share(b: Bitmap, pick: (Int) -> Boolean): Float {
        var hit = 0; var n = 0
        for (y in b.height / 4 until b.height * 3 / 4 step 16) for (x in b.width / 8 until b.width * 7 / 8 step 16) { n++; if (pick(b.getPixel(x, y))) hit++ }
        return if (n == 0) 0f else hit.toFloat() / n
    }
    private fun isRed(c: Int) = android.graphics.Color.red(c) > 170 && android.graphics.Color.green(c) < 80 && android.graphics.Color.blue(c) < 80
    private fun isBlue(c: Int) = android.graphics.Color.blue(c) > 170 && android.graphics.Color.red(c) < 80 && android.graphics.Color.green(c) < 80
    private fun shown() = runCatching { compose.onAllNodesWithContentDescription("Page 1", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun failed() = runCatching { compose.onAllNodesWithText("failed", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    private fun open(p: MangaProvider) {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("manga_reader", 0).edit().putBoolean("vertical", true).commit()
        SourceHub.publishManga("yu_test_tall_bridge", listOf(p))
        compose.setContent { Box(Modifier.fillMaxSize()) { MangaReaderScreen(p.id, "yutall://m", "YuTall", "yutall://m/1", "YuTall Chapter 1", onBack = {}) } }
        compose.waitUntil(60_000) { shown() || failed() }
        SystemClock.sleep(1_500); compose.waitForIdle()
    }

    @After fun tearDown() { runCatching { SourceHub.remove("yu_test_tall_bridge") } }

    @Test fun a_tallPageDrawsTopAndBottom() {
        open(fake("yu_test_tall_a", 1000, 12_000))
        val top = screen()!!; FirstRun.shot("mt_a_top")
        val red = share(top, ::isRed)
        repeat(12) { compose.onNodeWithTag("manga_reader").performTouchInput { swipeUp() }; compose.waitForIdle() }
        SystemClock.sleep(800); compose.waitForIdle()
        val low = screen()!!; FirstRun.shot("mt_a_low")
        val blue = share(low, ::isBlue)
        log("MT_A shown=${shown()} failed=${failed()} redTop=$red blueLow=$blue")
        assertTrue("page 1 not shown", shown() && !failed())
        assertTrue("top of the tall page not drawn (red share $red)", red > 0.5f)
        assertTrue("bottom of the tall page not drawn (blue share $blue)", blue > 0.5f)
        log("MT_RESULT_A PASS")
    }

    @Test fun b_pageOverTheDrawLimitDoesNotCrash() {
        open(fake("yu_test_tall_b", 1440, 18_500))
        val s = screen()!!; FirstRun.shot("mt_b")
        val red = share(s, ::isRed)
        log("MT_B shown=${shown()} failed=${failed()} redTop=$red")
        assertTrue("page 1 not shown", shown() && !failed())
        assertTrue("page over 100 MB not drawn (red share $red)", red > 0.5f)
        log("MT_RESULT_B PASS")
    }
}
