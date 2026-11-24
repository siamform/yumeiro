/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.sources.novel.NovelLibrary
import dev.asfakul.yumeiro.sources.novel.NovelSources
import dev.asfakul.yumeiro.sources.novel.NovelText
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NOVEL-1 (item 26) on the device: the real QuickJS engine with the LNReader harness and cheerio bundle, the real
 * OkHttp stack, storage and library. A local server publishes an LNReader-format repository (plugins.min.json), a
 * CommonJS plugin written the way LNReader's compiled plugins are (require('@libs/fetch'), require('cheerio'),
 * async methods), and the site it reads. Add repository → install → popular / latest / search / details /
 * more chapters / chapter text → reader paragraphs → library progress; a failing chapter gives a readable error;
 * uninstall removes it.
 */
@RunWith(AndroidJUnit4::class)
class NovelEngineDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.w("YuNovelTest", s)
    private lateinit var server: MockWebServer
    private val seen = java.util.Collections.synchronizedList(ArrayList<String>())
    private fun base() = "http://${server.hostName}:${server.port}/"

    private fun html(b: String) = MockResponse.Builder().code(200).setHeader("Content-Type", "text/html; charset=utf-8").body(b).build()

    private val plugin = """
"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var fetch_1 = require("@libs/fetch");
var cheerio_1 = require("cheerio");
var novelStatus_1 = require("@libs/novelStatus");
var YuTest = (function () {
  function YuTest() { this.id = "yutest"; this.name = "Yu Novel Test"; this.site = "SITE/"; this.version = "1.0.0"; this.icon = ""; }
  YuTest.prototype.list = function (url) {
    return fetch_1.fetchApi(url).then(function (r) { return r.text(); }).then(function (b) {
      var ${"$"} = (0, cheerio_1.load)(b); var out = [];
      ${"$"}("a.novel").each(function (i, e) { out.push({ name: ${"$"}(e).text().trim(), path: ${"$"}(e).attr("href"), cover: ${"$"}(e).attr("data-cover") }); });
      return out;
    });
  };
  YuTest.prototype.popularNovels = function (page, opts) { return this.list(this.site + (opts && opts.showLatestNovels ? "latest" : "popular") + "?page=" + page); };
  YuTest.prototype.searchNovels = function (q, page) { return this.list(this.site + "search?q=" + encodeURIComponent(q) + "&page=" + page); };
  YuTest.prototype.parseNovel = async function (path) {
    var b = await fetch_1.fetchText(this.site + path);
    var ${"$"} = (0, cheerio_1.load)(b);
    var chapters = [];
    ${"$"}("li.ch a").each(function (i, e) { chapters.push({ name: ${"$"}(e).text().trim(), path: ${"$"}(e).attr("href"), chapterNumber: Number(${"$"}(e).attr("data-n")), releaseTime: ${"$"}(e).attr("data-t") }); });
    return { path: path, name: ${"$"}("h1").text().trim(), cover: ${"$"}("img.cover").attr("src"), author: ${"$"}(".author").text().trim(), status: novelStatus_1.NovelStatus.Ongoing,
      summary: ${"$"}(".summary").text().trim(), genres: "Fantasy, Adventure", chapters: chapters, totalPages: 2 };
  };
  YuTest.prototype.parsePage = async function (path, page) {
    var b = await fetch_1.fetchText(this.site + path + "?page=" + page);
    var ${"$"} = (0, cheerio_1.load)(b); var chapters = [];
    ${"$"}("li.ch a").each(function (i, e) { chapters.push({ name: ${"$"}(e).text().trim(), path: ${"$"}(e).attr("href") }); });
    return { chapters: chapters };
  };
  YuTest.prototype.parseChapter = async function (path) {
    var r = await fetch_1.fetchApi(this.site + path, { headers: { "X-Yu": "1" } });
    if (!r.ok) throw new Error("Could not reach the chapter (" + r.status + ")");
    var ${"$"} = (0, cheerio_1.load)(await r.text());
    return ${"$"}("#content").html() || "";
  };
  return YuTest;
}());
exports.default = new YuTest();
""".trimIndent()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                seen += path + (request.url.encodedQuery?.let { "?$it" } ?: "") + " ua=" + (request.headers["User-Agent"]?.take(24) ?: "-") + " x=" + (request.headers["X-Yu"] ?: "-")
                return when (path) {
                    "/plugins.min.json" -> MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(
                        """[{"id":"yutest","name":"Yu Novel Test","site":"${base()}","lang":"\u200eEnglish","version":"1.0.0","url":"${base()}yutest.js","iconUrl":""}]""",
                    ).build()
                    "/yutest.js" -> MockResponse.Builder().code(200).body(plugin.replace("SITE/", base())).build()
                    "/popular" -> html("""<a class="novel" href="novel/night" data-cover="/c/night.jpg">Night Walker</a><a class="novel" href="novel/day">Day Runner</a>""")
                    "/latest" -> html("""<a class="novel" href="novel/new">Fresh Tale</a>""")
                    "/search" -> if (request.url.queryParameter("q") == "night") html("""<a class="novel" href="novel/night">Night Walker</a>""") else html("<p>none</p>")
                    "/novel/night" -> if (request.url.queryParameter("page") == "2")
                        html("""<ul><li class="ch"><a href="novel/night/3">Chapter 3</a></li></ul>""")
                    else html(
                        """<h1>Night Walker</h1><img class="cover" src="/c/night.jpg"><span class="author">A. Writer</span><p class="summary">A long night.</p>""" +
                            """<ul><li class="ch"><a href="novel/night/2" data-n="2" data-t="2026-11-02">Chapter 2</a></li><li class="ch"><a href="novel/night/1" data-n="1">Chapter 1</a></li></ul>""",
                    )
                    "/novel/night/1" -> html("""<div id="content"><h3>Chapter 1</h3><p>First line.</p>Second<br>line<br><br>Third para<script>ad()</script><div class="ads">BUY</div></div>""")
                    else -> MockResponse.Builder().code(404).body("not here").build()
                }
            }
        }
        server.start()
        NovelSources.ensureLoaded(ctx)
        NovelSources.state.value.installed.filter { it.id == "yutest" }.forEach { NovelSources.uninstall(ctx, it.id) }
    }

    @After fun tearDown() {
        NovelSources.uninstall(ctx, "yutest")
        NovelSources.state.value.repos.filter { it.startsWith(base()) }.forEach { NovelSources.removeRepo(ctx, it) }
        NovelLibrary.forget(ctx, "yutest", "novel/night")
        server.close()
    }

    @Test fun lnreaderPluginRunsEndToEnd() = runBlocking {
        val added = NovelSources.addRepo(ctx, base() + "plugins.min.json")
        log("NOVEL addRepo=$added")
        assertEquals(1, added.getOrThrow())
        val available = NovelSources.available(ctx)
        val p = available.first { it.id == "yutest" }
        assertEquals("English", p.language); assertEquals("en", p.languageCode)
        NovelSources.install(ctx, p).getOrThrow()
        assertTrue(NovelSources.state.value.installed.any { it.id == "yutest" })

        val popular = NovelSources.popular(ctx, "yutest", 1)
        log("NOVEL popular=$popular")
        assertEquals(listOf("Night Walker", "Day Runner"), popular.map { it.name })
        assertEquals(base() + "c/night.jpg", popular[0].cover)
        assertEquals(listOf("Fresh Tale"), NovelSources.popular(ctx, "yutest", 1, latest = true).map { it.name })

        val groups = NovelSources.searchAll(ctx, "night")
        log("NOVEL searchAll=${groups.map { it.plugin.id to it.items.size to it.error }}")
        assertEquals(listOf("novel/night"), groups.first { it.plugin.id == "yutest" }.items.map { it.path })

        val d = NovelSources.details(ctx, "yutest", "novel/night")
        log("NOVEL details=${d.name} ${d.author} ${d.status} ${d.genres} ${d.chapters.map { it.name }} pages=${d.totalPages}")
        assertEquals("Night Walker", d.name); assertEquals("A. Writer", d.author); assertEquals(listOf("Fantasy", "Adventure"), d.genres)
        assertEquals("newest-first list is put in reading order", listOf("Chapter 1", "Chapter 2"), d.chapters.map { it.name })
        assertEquals(2, d.totalPages)
        assertEquals(listOf("Chapter 3"), NovelSources.chapterPage(ctx, "yutest", "novel/night", 2).map { it.name })

        val chHtml = NovelSources.chapterHtml(ctx, "yutest", "novel/night/1")
        val paras = NovelText.paragraphs(chHtml)
        log("NOVEL paras=$paras")
        assertEquals(listOf("Chapter 1", "First line.", "Second\nline", "Third para"), paras.map { it.text })
        assertTrue(paras[0].heading)
        assertTrue("plugin headers reach the site", seen.any { it.startsWith("/novel/night/1") && it.endsWith("x=1") })
        assertTrue("a browser user agent is sent", seen.any { it.startsWith("/popular") && "Mozilla" in it })

        val missing = runCatching { NovelSources.chapterHtml(ctx, "yutest", "novel/night/9") }
        log("NOVEL missing=${missing.exceptionOrNull()?.message}")
        assertTrue(missing.isFailure)
        assertTrue("readable error: ${missing.exceptionOrNull()?.message}", missing.exceptionOrNull()?.message.orEmpty().contains("404"))
        assertEquals("engine works after a failure", 2, NovelSources.popular(ctx, "yutest", 1).size)

        NovelLibrary.ensureLoaded(ctx)
        val item = popular[0]
        NovelLibrary.setSaved(ctx, item, true)
        NovelLibrary.progress(ctx, item, d.chapters[1], 1, 0.4f)
        NovelLibrary.resetForTests(); NovelLibrary.ensureLoaded(ctx)
        val e = NovelLibrary.get("yutest", "novel/night")!!
        log("NOVEL library=$e")
        assertTrue(e.saved); assertEquals("novel/night/2", e.chapterPath); assertEquals(0.4f, e.position, 0.001f)

        NovelSources.uninstall(ctx, "yutest")
        assertTrue(NovelSources.state.value.installed.none { it.id == "yutest" })
        log("NOVEL seen=$seen")
    }
}
