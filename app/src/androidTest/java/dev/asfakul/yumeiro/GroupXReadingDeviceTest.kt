/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import dev.asfakul.yumeiro.ui.screens.manga.MangaHomeScreen
import dev.asfakul.yumeiro.ui.screens.novel.NovelHomeScreen
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaDetails
import dev.asfakul.yumeiro.sources.manga.MangaImage
import dev.asfakul.yumeiro.sources.manga.MangaLibrary
import dev.asfakul.yumeiro.sources.manga.MangaListPage
import dev.asfakul.yumeiro.sources.manga.MangaPageRef
import dev.asfakul.yumeiro.sources.manga.MangaProvider
import dev.asfakul.yumeiro.sources.novel.NovelLibrary
import dev.asfakul.yumeiro.sources.novel.NovelSources
import dev.asfakul.yumeiro.ui.screens.manga.MangaReaderScreen
import dev.asfakul.yumeiro.ui.screens.novel.NovelReaderScreen
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Group X (B5): manga and novel chapters re-open at the exact spot, not at the top of the page / paragraph.
 * The real readers run; the manga pages come from a test source made here (tall solid PNGs, one colour per page),
 * the novel through the real LNReader engine with a plugin served by a local test web server.
 *  a: webtoon — scroll to the middle of page 2, leave 0.3 s later, open again → same page, same line.
 *  b: novel — scroll into the middle of a long paragraph, leave 0.3 s later, open again → same paragraph, same line.
 *  c: novel — a bigger text size keeps the same paragraph and the same share into it at the top.
 *  g: manga — read into page 2, go to the Manga home: Continue reading lists the title, a tap opens the chapter at the same line.
 *  h: novel — read into a paragraph, go to the Novels home: Continue reading lists it, a tap opens the chapter at the same line.
 * Log tag YuGroupX: GX_… lines, GX_RESULT_A/B/C/G/H PASS. Shots gx_a_before, gx_a_after, gx_b_before, gx_b_after, gx_c.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupXReadingDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupX", s)
    private var show by mutableStateOf(true)

    /** Where the top edge of [root] is: the item crossing it and the share of that item above the edge. */
    private data class Spot(val label: String, val frac: Float, val heightPx: Float)

    private fun spot(rootTag: String, items: () -> List<androidx.compose.ui.semantics.SemanticsNode>, label: (androidx.compose.ui.semantics.SemanticsNode) -> String?): Spot? {
        val top = runCatching { compose.onNodeWithTag(rootTag, true).fetchSemanticsNode().boundsInRoot.top }.getOrNull() ?: return null
        // Unclipped position and size: boundsInRoot is cut to the screen, so a tall item never shows its share.
        return items().mapNotNull { n -> label(n)?.let { Triple(it, n.positionInRoot.y, n.size.height.toFloat()) } }
            .firstOrNull { (_, y, h) -> h > 0f && y <= top + 1f && y + h > top + 1f }
            ?.let { (l, y, h) -> Spot(l, (top - y) / h, h) }
    }

    private fun slowSwipe(tag: String, up: Boolean) = compose.onNodeWithTag(tag, true).performTouchInput {
        val x = width / 2f
        val a = Offset(x, height * 0.75f); val b = Offset(x, height * 0.45f)
        swipe(if (up) a else b, if (up) b else a, durationMillis = 700)
    }

    /** Scrolls until the top edge is well inside an item after the first ([good]), slowly so it does not fling. */
    private fun scrollInto(tag: String, read: () -> Spot?, good: (Spot) -> Boolean): Spot {
        repeat(80) {
            val s = read()
            if (s != null && good(s)) return s
            slowSwipe(tag, up = true); compose.waitForIdle(); SystemClock.sleep(150)
        }
        error("never reached the middle of an item: ${read()}")
    }

    private fun reopen() {
        show = false; compose.waitForIdle(); SystemClock.sleep(1_200)
        show = true; compose.waitForIdle()
    }

    // ---- manga ---------------------------------------------------------------------------------------------
    private val colours = listOf(intArrayOf(0xE0, 0x30, 0x30), intArrayOf(0x30, 0xC0, 0x40), intArrayOf(0x30, 0x50, 0xE0), intArrayOf(0xE0, 0xC0, 0x30))
    private fun mangaSource(id: String) = object : MangaProvider {
        override val id = id
        override val name = "YuSpot test source"
        override val lang = "en"
        override val ecosystem = "test"
        override val nsfw = false
        override val supportsLatest = false
        override val baseUrl: String? = null
        override suspend fun popular(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun latest(page: Int) = MangaListPage(emptyList(), false)
        override suspend fun search(query: String, page: Int) = MangaListPage(emptyList(), false)
        override suspend fun details(url: String) = MangaDetails(id, url, "YuSpot", null)
        override suspend fun chapters(url: String) = listOf(MangaChapter("$url/1", "YuSpot Chapter 1", 1f))
        override suspend fun pages(chapterUrl: String) = colours.indices.map { MangaPageRef(it, "$chapterUrl/p$it", "yuspot://$it") }
        override suspend fun image(page: MangaPageRef) = MangaImage(png(720, 4_800, colours[page.index]), "image/png")
    }

    private fun png(w: Int, h: Int, rgb: IntArray): ByteArray {
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
        // A darker band every 400 rows, so a screenshot shows where in the page the screen is.
        val row = ByteArray(1 + w * 3).also { r -> for (x in 0 until w) for (c in 0..2) r[1 + x * 3 + c] = rgb[c].toByte() }
        val band = ByteArray(1 + w * 3).also { r -> for (x in 0 until w) for (c in 0..2) r[1 + x * 3 + c] = (rgb[c] / 3).toByte() }
        val idat = ByteArrayOutputStream()
        DeflaterOutputStream(idat).use { d -> for (y in 0 until h) d.write(if (y % 400 < 24) band else row) }
        chunk("IDAT", idat.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun mangaSpot() = spot("manga_reader", {
        runCatching { compose.onAllNodesWithContentDescription("Page ", substring = true, useUnmergedTree = true).fetchSemanticsNodes() }.getOrDefault(emptyList())
    }) { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.takeIf { it.startsWith("Page ") } }

    private fun pagesShown() = runCatching { compose.onAllNodesWithContentDescription("Page ", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    @Test fun a_webtoonReopensAtTheSameLine() {
        val p = mangaSource("yu_test_spot")
        ctx.getSharedPreferences("manga_reader", 0).edit().putBoolean("vertical", true).commit()
        MangaLibrary.load(ctx)
        SourceHub.publishManga("yu_test_spot_bridge", listOf(p))
        compose.setContent { FirstRun.AppBackground { Box(Modifier.fillMaxSize()) { if (show) MangaReaderScreen(p.id, "yuspot://m", "YuSpot", "yuspot://m/1", "YuSpot Chapter 1", onBack = {}) } } }
        compose.waitUntil(60_000) { pagesShown() }
        SystemClock.sleep(1_500); compose.waitForIdle()
        val before = scrollInto("manga_reader", ::mangaSpot) { it.label != "Page 1" && it.frac in 0.3f..0.7f }
        SystemClock.sleep(300); FirstRun.shot("gx_a_before")
        log("GX_A before=$before saved=${MangaLibrary.get(p.id, "yuspot://m")?.lastPage}")
        reopen()
        compose.waitUntil(60_000) { pagesShown() }
        SystemClock.sleep(3_000); compose.waitForIdle()
        val after = mangaSpot(); FirstRun.shot("gx_a_after")
        val saved = MangaLibrary.get(p.id, "yuspot://m")
        log("GX_A after=$after saved=${saved?.lastPage}")
        assertEquals("re-opened on another page", before.label, after?.label)
        val offPx = kotlin.math.abs((after!!.frac - before.frac) * before.heightPx)
        log("GX_A off=${offPx}px of ${before.heightPx}px")
        assertTrue("re-opened ${offPx.toInt()} px away from the spot (page top is ${(before.frac * before.heightPx).toInt()} px away)", offPx <= 24f)
        log("GX_RESULT_A PASS")
    }

    // ---- novel ---------------------------------------------------------------------------------------------
    private lateinit var server: MockWebServer
    private fun base() = "http://${server.hostName}:${server.port}/"
    private fun html(b: String) = MockResponse.Builder().code(200).setHeader("Content-Type", "text/html; charset=utf-8").body(b).build()
    private val words = "the river kept its own counsel while the lanterns of the old town flickered one after another in the wind".split(" ")
    /** Twelve long paragraphs, each several screens tall on a phone, each starting with its marker "P<n>". */
    private val chapter: String by lazy {
        (1..12).joinToString("") { n -> "<p>P$n " + (0 until 900).joinToString(" ") { words[(it * 7 + n) % words.size] } + ".</p>" }
    }
    private val plugin = """
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var fetch_1 = require("@libs/fetch");
var cheerio_1 = require("cheerio");
var Yu = (function () {
  function Yu() { this.id = "yuspot"; this.name = "Yu Spot Test"; this.site = "SITE/"; this.version = "1.0.0"; this.icon = ""; }
  Yu.prototype.popularNovels = function () { return Promise.resolve([{ name: "Long River", path: "novel/long" }]); };
  Yu.prototype.searchNovels = function () { return Promise.resolve([]); };
  Yu.prototype.parseNovel = async function (path) {
    return { path: path, name: "Long River", chapters: [{ name: "Chapter 1", path: "novel/long/1", chapterNumber: 1 }] };
  };
  Yu.prototype.parseChapter = async function (path) {
    var ${"$"} = (0, cheerio_1.load)(await fetch_1.fetchText(this.site + path));
    return ${"$"}("#content").html() || "";
  };
  return Yu;
}());
exports.default = new Yu();
""".trimIndent()

    private fun startNovel() = runBlocking {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/plugins.min.json" -> MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(
                    """[{"id":"yuspot","name":"Yu Spot Test","site":"${base()}","lang":"English","version":"1.0.0","url":"${base()}yuspot.js","iconUrl":""}]""",
                ).build()
                "/yuspot.js" -> MockResponse.Builder().code(200).body(plugin.replace("SITE/", base())).build()
                "/novel/long/1" -> html("""<div id="content">$chapter</div>""")
                else -> MockResponse.Builder().code(404).body("not here").build()
            }
        }
        server.start()
        NovelSources.ensureLoaded(ctx); NovelLibrary.ensureLoaded(ctx)
        NovelLibrary.forget(ctx, "yuspot", "novel/long")
        NovelSources.addRepo(ctx, base() + "plugins.min.json").getOrThrow()
        NovelSources.install(ctx, NovelSources.available(ctx).first { it.id == "yuspot" }).getOrThrow()
        ctx.getSharedPreferences("novel_reader", Context.MODE_PRIVATE).edit().putInt("size", 18).commit()
    }

    private fun novelSpot() = spot("novel_text", {
        runCatching { compose.onAllNodes(hasAnyAncestor(hasTestTag("novel_text")), true).fetchSemanticsNodes() }.getOrDefault(emptyList())
    }) { n -> n.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text?.let { Regex("^P(\\d+) ").find(it)?.value?.trim() } }

    private fun novelShown() = runCatching { compose.onAllNodesWithTag("novel_text", true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    private fun openNovel() {
        compose.setContent { FirstRun.AppBackground { Box(Modifier.fillMaxSize()) { if (show) NovelReaderScreen("yuspot", "novel/long", "Long River", "novel/long/1", onBack = {}) } } }
        compose.waitUntil(60_000) { novelShown() }
        SystemClock.sleep(1_000); compose.waitForIdle()
    }

    @Test fun b_novelReopensAtTheSameLine() {
        startNovel(); openNovel()
        val before = scrollInto("novel_text", ::novelSpot) { it.label.isNotEmpty() && it.label != "P1" && it.frac in 0.3f..0.7f }
        SystemClock.sleep(300); FirstRun.shot("gx_b_before")
        log("GX_B before=$before")
        reopen()
        compose.waitUntil(60_000) { novelShown() }
        SystemClock.sleep(2_000); compose.waitForIdle()
        val after = novelSpot(); FirstRun.shot("gx_b_after")
        log("GX_B after=$after saved=${NovelLibrary.get("yuspot", "novel/long")?.position}")
        assertEquals("re-opened at another paragraph", before.label, after?.label)
        val offPx = kotlin.math.abs((after!!.frac - before.frac) * before.heightPx)
        log("GX_B off=${offPx}px of ${before.heightPx}px")
        assertTrue("re-opened ${offPx.toInt()} px away from the line (paragraph top is ${(before.frac * before.heightPx).toInt()} px away)", offPx <= 24f)
        log("GX_RESULT_B PASS")
    }

    @Test fun c_novelTextSizeKeepsTheLine() {
        startNovel(); openNovel()
        val before = scrollInto("novel_text", ::novelSpot) { it.label.isNotEmpty() && it.label != "P1" && it.frac in 0.3f..0.7f }
        SystemClock.sleep(800)
        compose.onNodeWithTag("novel_text_size").performClick(); compose.waitForIdle()
        repeat(4) { compose.onNodeWithTag("novel_size_up").performClick(); compose.waitForIdle(); SystemClock.sleep(250) }
        SystemClock.sleep(1_500); compose.waitForIdle()
        val after = novelSpot(); FirstRun.shot("gx_c")
        log("GX_C before=$before after=$after")
        assertEquals("a new text size moved to another paragraph", before.label, after?.label)
        assertTrue("a new text size moved the line: share ${before.frac} → ${after!!.frac}", kotlin.math.abs(after.frac - before.frac) <= 0.03f)
        assertTrue("the text did not grow", after.heightPx > before.heightPx * 1.1f)
        log("GX_RESULT_C PASS")
    }

    // ---- Continue reading (B5) -------------------------------------------------------------------------------
    private var reading by mutableStateOf(true)
    private fun shown(tag: String) = runCatching { compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    @Test fun g_mangaContinueOpensTheChapterAtTheSpot() {
        val p = mangaSource("yu_test_spot")
        ctx.getSharedPreferences("manga_reader", 0).edit().putBoolean("vertical", true).commit()
        ctx.getSharedPreferences("manga_home", 0).edit().putString("source", p.id).commit()
        MangaLibrary.load(ctx)
        SourceHub.publishManga("yu_test_spot_bridge", listOf(p))
        var resumed: MangaLibrary.Entry? = null
        var openedTitle = false
        compose.setContent { FirstRun.AppBackground { Box(Modifier.fillMaxSize()) {
            if (reading) MangaReaderScreen(p.id, "yuspot://g", "YuSpot G", resumed?.lastChapterUrl ?: "yuspot://g/1", resumed?.lastChapterName ?: "YuSpot Chapter 1", onBack = {})
            else MangaHomeScreen(onBack = {}, onOpenTitle = { openedTitle = true }, onOpenSources = {}, onResume = { e -> resumed = e; reading = true })
        } } }
        compose.waitUntil(60_000) { pagesShown() }
        SystemClock.sleep(1_500); compose.waitForIdle()
        val before = scrollInto("manga_reader", ::mangaSpot) { it.label != "Page 1" && it.frac in 0.3f..0.7f }
        SystemClock.sleep(300)
        reading = false; compose.waitForIdle()
        compose.waitUntil(30_000) { shown("manga_continue_yuspot://g") }
        SystemClock.sleep(500); FirstRun.shot("gx_g_home")
        log("GX_G before=$before saved=${MangaLibrary.get(p.id, "yuspot://g")?.lastPage}")
        compose.onNodeWithTag("manga_continue_yuspot://g", true).performClick(); compose.waitForIdle()
        assertTrue("Continue reading opened the title page, not the chapter", !openedTitle)
        assertEquals("Continue reading did not hand over the chapter", "yuspot://g/1", resumed?.lastChapterUrl)
        compose.waitUntil(60_000) { pagesShown() }
        SystemClock.sleep(3_000); compose.waitForIdle()
        val after = mangaSpot(); FirstRun.shot("gx_g_after")
        log("GX_G after=$after")
        assertEquals("Continue reading opened another page", before.label, after?.label)
        val offPx = kotlin.math.abs((after!!.frac - before.frac) * before.heightPx)
        assertTrue("Continue reading opened ${offPx.toInt()} px away from the spot", offPx <= 24f)
        log("GX_RESULT_G PASS")
    }

    @Test fun h_novelContinueOpensTheChapterAtTheSpot() {
        startNovel()
        var resumed: NovelLibrary.Entry? = null
        var openedTitle = false
        compose.setContent { FirstRun.AppBackground { Box(Modifier.fillMaxSize()) {
            if (reading) NovelReaderScreen("yuspot", "novel/long", "Long River", resumed?.chapterPath ?: "novel/long/1", onBack = {})
            else NovelHomeScreen(onBack = {}, onOpenNovel = { openedTitle = true }, onOpenSources = {}, onResume = { e -> resumed = e; reading = true })
        } } }
        compose.waitUntil(60_000) { novelShown() }
        SystemClock.sleep(1_000); compose.waitForIdle()
        val before = scrollInto("novel_text", ::novelSpot) { it.label.isNotEmpty() && it.label != "P1" && it.frac in 0.3f..0.7f }
        SystemClock.sleep(300)
        reading = false; compose.waitForIdle()
        compose.waitUntil(60_000) { shown("novel_all_grid") || shown("novel_browse_grid") }
        val grid = if (shown("novel_all_grid")) "novel_all_grid" else "novel_browse_grid"
        val tag = "novel_continue_novel/long"
        runCatching { compose.onNodeWithTag(grid, true).performScrollToNode(hasTestTag(tag)) }
        compose.waitUntil(30_000) { shown(tag) }
        SystemClock.sleep(500); FirstRun.shot("gx_h_home")
        log("GX_H before=$before saved=${NovelLibrary.get("yuspot", "novel/long")?.position}")
        compose.onNodeWithTag(tag, true).performClick(); compose.waitForIdle()
        assertTrue("Continue reading opened the novel page, not the chapter", !openedTitle)
        assertEquals("Continue reading did not hand over the chapter", "novel/long/1", resumed?.chapterPath)
        compose.waitUntil(60_000) { novelShown() }
        SystemClock.sleep(2_000); compose.waitForIdle()
        val after = novelSpot(); FirstRun.shot("gx_h_after")
        log("GX_H after=$after")
        assertEquals("Continue reading opened another paragraph", before.label, after?.label)
        val offPx = kotlin.math.abs((after!!.frac - before.frac) * before.heightPx)
        assertTrue("Continue reading opened ${offPx.toInt()} px away from the line", offPx <= 24f)
        log("GX_RESULT_H PASS")
    }

    @After fun tearDown() {
        runCatching { SourceHub.remove("yu_test_spot_bridge") }
        runCatching { MangaLibrary.remove(ctx, "yu_test_spot|yuspot://g") }
        if (::server.isInitialized) {
            runCatching { NovelSources.uninstall(ctx, "yuspot") }
            runCatching { NovelSources.state.value.repos.filter { it.startsWith(base()) }.forEach { NovelSources.removeRepo(ctx, it) } }
            runCatching { NovelLibrary.forget(ctx, "yuspot", "novel/long") }
            server.close()
        }
        ctx.getSharedPreferences("novel_reader", Context.MODE_PRIVATE).edit().putInt("size", 18).commit()
    }
}
