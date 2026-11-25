/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.sources.novel.NovelSources
import dev.asfakul.yumeiro.ui.screens.novel.NovelHomeScreen
import dev.asfakul.yumeiro.ui.screens.online.PosterCard
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GROUP G1 and G3 on the device (phones and TV), with the real screens:
 *  b) a poster whose kind shows as a chip on the art does not repeat that kind in the line under the name (G1);
 *  c) the Novel home opens on a banner (G3): a real LNReader-format plugin, served by a local server and run by the
 *     QuickJS engine, lists novels and the page draws `novel_banner` above the grid with a top pick.
 * Log tag YuGroupG.
 */
@RunWith(AndroidJUnit4::class)
class GroupGScreensDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupG", s)
    private fun n(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private lateinit var server: MockWebServer
    private fun base() = "http://${server.hostName}:${server.port}/"
    private fun html(b: String) = MockResponse.Builder().code(200).setHeader("Content-Type", "text/html; charset=utf-8").body(b).build()

    private val plugin = """
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var fetch_1 = require("@libs/fetch");
var cheerio_1 = require("cheerio");
var YuG = (function () {
  function YuG() { this.id = "yugbanner"; this.name = "Yu Banner Test"; this.site = "SITE/"; this.version = "1.0.0"; this.icon = ""; }
  YuG.prototype.list = function (url) {
    return fetch_1.fetchApi(url).then(function (r) { return r.text(); }).then(function (b) {
      var q = (0, cheerio_1.load)(b); var out = [];
      q("a.novel").each(function (i, e) { out.push({ name: q(e).text().trim(), path: q(e).attr("href"), cover: q(e).attr("data-cover") }); });
      return out;
    });
  };
  YuG.prototype.popularNovels = function (page, opts) { return page > 1 ? Promise.resolve([]) : this.list(this.site + "popular"); };
  YuG.prototype.searchNovels = function (q, page) { return Promise.resolve([]); };
  YuG.prototype.parseNovel = async function (path) { return { path: path, name: "x", chapters: [] }; };
  YuG.prototype.parseChapter = async function (path) { return ""; };
  return YuG;
}());
exports.default = new YuG();
""".trimIndent()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/plugins.min.json" -> MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(
                    """[{"id":"yugbanner","name":"Yu Banner Test","site":"${base()}","lang":"English","version":"1.0.0","url":"${base()}yug.js","iconUrl":""}]""",
                ).build()
                "/yug.js" -> MockResponse.Builder().code(200).body(plugin.replace("SITE/", base())).build()
                "/popular" -> html((1..8).joinToString("") { """<a class="novel" href="novel/$it" data-cover="${base()}c/$it.png">Banner Novel $it</a>""" })
                else -> MockResponse.Builder().code(404).body("not here").build()
            }
        }
        server.start()
        NovelSources.ensureLoaded(ctx)
        NovelSources.state.value.installed.forEach { NovelSources.uninstall(ctx, it.id) }
    }

    @After fun tearDown() {
        NovelSources.uninstall(ctx, "yugbanner")
        NovelSources.state.value.repos.filter { it.startsWith(base()) }.forEach { NovelSources.removeRepo(ctx, it) }
        server.close()
    }

    @Test fun b_typeShownOnceOnAPoster() {
        val movie = OnlineItem("Device Film", "https://s.example/1", "Site", null, type = "Movie")
        rule.setContent { MTPlayerTheme { PosterCard(movie, onClick = {}) } }
        rule.waitForIdle()
        val chips = rule.onAllNodesWithText("Movie", useUnmergedTree = true).fetchSemanticsNodes().size
        val sub = rule.onAllNodesWithTag("online_poster_sub", true).fetchSemanticsNodes()
            .map { it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString { t -> t.text } }
        log("RESULT_B chips=$chips sub=$sub tags=${n("poster_tags")}")
        assertEquals("the kind shows once (the chip): chips=$chips sub=$sub", 1, chips)
        assertTrue("no line repeating the kind: $sub", sub.none { it.contains("Movie") })
    }

    @Test fun c_novelHomeOpensOnABanner(): Unit = runBlocking {
        assertEquals(1, NovelSources.addRepo(ctx, base() + "plugins.min.json").getOrThrow())
        NovelSources.install(ctx, NovelSources.available(ctx).first { it.id == "yugbanner" }).getOrThrow()
        rule.setContent { MTPlayerTheme { NovelHomeScreen(onBack = {}, onOpenNovel = {}, onOpenSources = {}) } }
        val ok = runCatching { rule.waitUntil(60_000) { n("novel_banner") > 0 }; true }.getOrDefault(false)
        val grid = n("novel_browse_grid")
        val popular = rule.onAllNodesWithText("Popular", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size
        val h = rule.onAllNodesWithTag("novel_banner", true).fetchSemanticsNodes().firstOrNull()?.size?.height ?: 0
        log("RESULT_C banner=$ok grid=$grid popularLabels=$popular height=$h err=${n("novel_browse_error")} empty=${n("novel_browse_empty")}")
        FirstRun.shot("groupg_novel_banner")
        assertTrue("the Novel home shows a banner (grid=$grid)", ok)
        assertTrue("the banner is drawn, height $h", h > 100)
    }
}
