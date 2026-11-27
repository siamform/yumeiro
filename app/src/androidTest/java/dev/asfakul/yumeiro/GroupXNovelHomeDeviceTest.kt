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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.novel.NovelSources
import dev.asfakul.yumeiro.ui.screens.novel.NovelHomeScreen
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Group X (B4): the Novel page with several sources. Three LNReader plugins run in the real engine, served by a
 * local test web server: "Yu River" and "Yu Stone" list 30 novels each, "Yu Broken" always fails (HTTP 500).
 *  a: no source chosen → the page opens on All sources with novels of both working sources, each cover naming its
 *     source, and the failing one named above the grid.
 *  b: choosing the failing source switches the page to All sources with a note; "Try Yu Broken again" shows that
 *     source's own error and stays there.
 *  c: the feed chips slide away while scrolling down the grid and come back when scrolling up.
 * Log tag YuGroupX: GX_… lines, GX_RESULT_D/E/F PASS. Shots gx_d_all, gx_e_fallback, gx_e_retry, gx_f_hidden, gx_f_back.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupXNovelHomeDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupX", s)
    private lateinit var server: MockWebServer
    private fun base() = "http://${server.hostName}:${server.port}/"
    private val ids = listOf("yuriver" to "Yu River", "yustone" to "Yu Stone", "yubroken" to "Yu Broken")

    private fun plugin(id: String, name: String) = """
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var fetch_1 = require("@libs/fetch");
var Yu = (function () {
  function Yu() { this.id = "ID"; this.name = "NAME"; this.site = "SITE/"; this.version = "1.0.0"; this.icon = ""; }
  Yu.prototype.popularNovels = async function (page, opts) {
    var r = await fetch_1.fetchApi(this.site + "ID/list?page=" + page);
    if (!r.ok) throw new Error("NAME is down (" + r.status + ")");
    return JSON.parse(await r.text());
  };
  Yu.prototype.searchNovels = function () { return Promise.resolve([]); };
  Yu.prototype.parseNovel = async function (path) { return { path: path, name: path, chapters: [] }; };
  Yu.prototype.parseChapter = async function () { return ""; };
  return Yu;
}());
exports.default = new Yu();
""".trimIndent().replace("ID", id).replace("NAME", name)

    private fun list(id: String, page: Int): String =
        if (page > 1) "[]" else (1..30).joinToString(",", "[", "]") { """{"name":"${id.removePrefix("yu").replaceFirstChar { c -> c.uppercase() }} Tale $it","path":"novel/$id-$it"}""" }

    @Before fun setUp() = runBlocking {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val json = { b: String -> MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(b).build() }
                return when {
                    path == "/plugins.min.json" -> json(ids.joinToString(",", "[", "]") { (id, name) ->
                        """{"id":"$id","name":"$name","site":"${base()}","lang":"English","version":"1.0.0","url":"${base()}$id.js","iconUrl":""}"""
                    })
                    path.endsWith(".js") -> { val id = path.removePrefix("/").removeSuffix(".js"); MockResponse.Builder().code(200).body(plugin(id, ids.first { it.first == id }.second).replace("SITE/", base())).build() }
                    path == "/yubroken/list" -> MockResponse.Builder().code(500).body("down").build()
                    path.endsWith("/list") -> json(list(path.removePrefix("/").removeSuffix("/list"), request.url.queryParameter("page")?.toIntOrNull() ?: 1))
                    else -> MockResponse.Builder().code(404).body("not here").build()
                }
            }
        }
        server.start()
        NovelSources.ensureLoaded(ctx)
        NovelSources.state.value.installed.forEach { NovelSources.uninstall(ctx, it.id) }
        NovelSources.addRepo(ctx, base() + "plugins.min.json").getOrThrow()
        val avail = NovelSources.available(ctx)
        ids.forEach { (id, _) -> NovelSources.install(ctx, avail.first { it.id == id }).getOrThrow() }
        ctx.getSharedPreferences("novel_reader", Context.MODE_PRIVATE).edit().remove("source").commit()
    }

    @After fun tearDown() {
        ids.forEach { runCatching { NovelSources.uninstall(ctx, it.first) } }
        runCatching { NovelSources.state.value.repos.filter { it.startsWith(base()) }.forEach { NovelSources.removeRepo(ctx, it) } }
        ctx.getSharedPreferences("novel_reader", Context.MODE_PRIVATE).edit().remove("source").commit()
        server.close()
    }

    private fun has(tag: String) = runCatching { compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun hasText(s: String) = runCatching { compose.onAllNodesWithText(s, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun open() {
        compose.setContent { FirstRun.AppBackground { Box(Modifier.fillMaxSize()) { NovelHomeScreen(onBack = {}, onOpenNovel = {}, onOpenSources = {}) } } }
        compose.waitUntil(30_000) { has("novel_feeds") }
    }

    @Test fun d_allSourcesIsTheDefault() {
        open()
        compose.waitUntil(60_000) { has("novel_all_grid") || has("novel_all_empty") || has("novel_browse_grid") || has("novel_browse_error") }
        compose.waitUntil(30_000) { hasText("River Tale 1") && hasText("Stone Tale 1") }
        SystemClock.sleep(800); compose.waitForIdle(); FirstRun.shot("gx_d_all")
        val sourceLine = runCatching { compose.onAllNodes(androidx.compose.ui.test.hasText("Yu Stone") and hasAnyAncestor(hasTestTag("novel_all_grid")), true).fetchSemanticsNodes().size }.getOrDefault(0)
        log("GX_D all=${has("novel_all_grid")} subtitleAll=${hasText("All sources")} river=${hasText("River Tale 1")} stone=${hasText("Stone Tale 1")} stoneLabels=$sourceLine failedNote=${has("novel_all_failed")} broken=${hasText("Yu Broken")}")
        assertTrue("B4: the page did not open on All sources", has("novel_all_grid") && hasText("All sources"))
        assertTrue("B4: All sources is missing a working source", hasText("River Tale 1") && hasText("Stone Tale 1"))
        assertTrue("B4: covers do not say their source", sourceLine > 0)
        assertTrue("B4: the failing source is not named", has("novel_all_failed") && hasText("Yu Broken"))
        compose.onNodeWithTag("novel_source_filter").performClick(); compose.waitForIdle()
        compose.waitUntil(10_000) { has("novel_source_all") }
        log("GX_D sheet all=${has("novel_source_all")}")
        log("GX_RESULT_D PASS")
    }

    @Test fun e_aFailingSourceSwitchesToAll() {
        ctx.getSharedPreferences("novel_reader", Context.MODE_PRIVATE).edit().putString("source", "yubroken").commit()
        open()
        compose.waitUntil(60_000) { has("novel_fallback_note") || has("novel_browse_error") }
        compose.waitUntil(30_000) { has("novel_fallback_note") && hasText("River Tale 1") }
        SystemClock.sleep(600); compose.waitForIdle(); FirstRun.shot("gx_e_fallback")
        log("GX_E note=${has("novel_fallback_note")} all=${has("novel_all_grid")} river=${hasText("River Tale 1")}")
        assertTrue("B4: a failing source did not switch to All sources", has("novel_fallback_note") && has("novel_all_grid"))
        compose.onNodeWithTag("novel_fallback_retry").performClick(); compose.waitForIdle()
        compose.waitUntil(60_000) { has("novel_browse_error") }
        SystemClock.sleep(2_000); compose.waitForIdle(); FirstRun.shot("gx_e_retry")
        log("GX_E retry error=${has("novel_browse_error")} note=${has("novel_fallback_note")} all=${has("novel_all_grid")}")
        assertTrue("B4: Try again did not stay on the chosen source", has("novel_browse_error") && !has("novel_fallback_note") && !has("novel_all_grid"))
        log("GX_RESULT_E PASS")
    }

    /** The visible height of the feed chips (a bar slid out of its clipped box has none left). */
    private fun feedsTop(): Float = compose.onNodeWithTag("novel_feeds", true).fetchSemanticsNode().boundsInRoot.height
    private fun gridTop(): Float = compose.onNodeWithTag("novel_all_grid", true).fetchSemanticsNode().boundsInRoot.top

    @Test fun f_feedBarHidesOnScroll() {
        open()
        compose.waitUntil(60_000) { hasText("River Tale 1") && hasText("Stone Tale 1") }
        SystemClock.sleep(800); compose.waitForIdle()
        val shownFeeds = feedsTop(); val shownGrid = gridTop()
        repeat(3) {
            compose.onNodeWithTag("novel_all_grid", true).performTouchInput { swipe(Offset(width / 2f, height * 0.8f), Offset(width / 2f, height * 0.3f), 500) }
            compose.waitForIdle()
        }
        SystemClock.sleep(600); compose.waitForIdle(); FirstRun.shot("gx_f_hidden")
        val hiddenFeeds = feedsTop(); val hiddenGrid = gridTop()
        compose.onNodeWithTag("novel_all_grid", true).performTouchInput { swipe(Offset(width / 2f, height * 0.4f), Offset(width / 2f, height * 0.6f), 400) }
        compose.waitForIdle(); SystemClock.sleep(600); compose.waitForIdle(); FirstRun.shot("gx_f_back")
        val backFeeds = feedsTop(); val backGrid = gridTop()
        log("GX_F feeds(visible height) shown=$shownFeeds hidden=$hiddenFeeds back=$backFeeds grid top shown=$shownGrid hidden=$hiddenGrid back=$backGrid")
        assertTrue("B4: the feed bar did not slide away on scroll down ($shownFeeds → $hiddenFeeds)", shownFeeds > 20f && hiddenFeeds < shownFeeds * 0.2f)
        assertTrue("B4: the grid did not take the bar's space ($shownGrid → $hiddenGrid)", hiddenGrid < shownGrid - 20f)
        assertTrue("B4: the feed bar did not come back on scroll up ($backFeeds, grid $backGrid)", backFeeds >= shownFeeds - 2f && backGrid >= shownGrid - 2f)
        log("GX_RESULT_F PASS")
    }
}
