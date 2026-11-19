/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.ecosystem.ScriptRuntime
import dev.asfakul.yumeiro.sources.ecosystem.VegaAdapter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Vega step 4 (ADR-047) on the real QuickJS: a fixture provider in the Vega file layout (written
 * for this test, CommonJS like an esbuild `dist/`) runs through the shared runtime, its DOM calls
 * go to jsoup, its streams map to links, a DRM stream is dropped, private addresses are refused,
 * a busy loop is stopped, and no state leaks from one call to the next.
 */
@RunWith(AndroidJUnit4::class)
class VegaProviderDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuVega", s)

    private val html = "<div class='blog-items'><article><a href='/one/'><img src='https://img.example/1.jpg'></a><h3 class='entry-title'>One (2024)</h3></article>" +
        "<article><a href='/two/'><img data-src='https://img.example/2.jpg'></a><h3 class='entry-title'>Two</h3></article></div>"

    private val files = mapOf(
        "catalog" to """"use strict";module.exports={catalog:[{title:"New",filter:""},{title:"Drama",filter:"drama"}],genres:[]};""",
        "posts" to """
            const PAGE = ${JsonPrimitive(html)};
            async function getPosts({ filter, page, providerValue, providerContext }) {
              const ${'$'} = providerContext.cheerio.load(PAGE);
              const out = [];
              ${'$'}(".blog-items").children("article").each((i, el) => {
                const a = ${'$'}(el).find("a");
                const img = ${'$'}(el).find("img");
                out.push({ title: ${'$'}(el).find(".entry-title").text().trim() + (filter ? " [" + filter + "]" : ""),
                  link: new URL(a.attr("href"), "https://site.example/").pathname, image: img.attr("src") || img.attr("data-src") });
              });
              globalThis.__leak = "set in posts";
              return out;
            }
            async function getSearchPosts({ searchQuery }) { return [{ title: "Found " + searchQuery, link: "/s/", image: "" }]; }
            module.exports = { getPosts, getSearchPosts };
        """.trimIndent(),
        "meta" to """
            exports.getMeta = async ({ link }) => ({ title: "Show " + link, image: "https://img.example/p.jpg", synopsis: "Plot",
              imdbId: "", type: "series", linkList: [{ title: "Season 1", episodesLink: "https://site.example/eps1" },
              { title: "Season 2", directLinks: [{ title: "Episode 1", link: "https://site.example/s2e1" }] }] });
        """.trimIndent(),
        "episodes" to """exports.getEpisodes = async ({ url }) => [{ title: "Episode 1", link: url + "/e1" }, { title: "Episode 2", link: url + "/e2" }];""",
        "stream" to """
            exports.getStream = async ({ link, type }) => {
              if (link === "leak") return [{ server: String(globalThis.__leak), link: "https://cdn.example/x.mp4", type: "mp4" }];
              if (link === "loop") { while (true) {} }
              if (link.indexOf("private:") === 0) {
                try { const r = await providerContext_axios_get("http://127.0.0.1:" + link.slice(8) + "/"); return [{ server: "reached " + r.status, link: "https://cdn.example/bad.mp4", type: "mp4" }]; }
                catch (e) { return [{ server: "blocked", link: "https://cdn.example/ok.mp4", type: "mp4" }]; }
              }
              return [{ server: "Main", link: "https://cdn.example/" + type + ".m3u8", type: "m3u8", quality: "1080", headers: { Referer: "https://site.example/" } },
                      { server: "Locked", link: "https://cdn.example/l.mpd", type: "mpd", drm: { type: "clearkey", clearKeys: { a: "b" } } }];
            };
            function providerContext_axios_get(u) { return globalThis.__providerContext.axios.get(u); }
        """.trimIndent(),
    )

    private fun install(): Pair<EcosystemRegistry.Installed, File> {
        val p = EcosystemRegistry.Installed("https://fixture.example/manifest.json", SourceHub.Ecosystem.VEGA, "fixture", "Fixture", "1", null, emptyMap())
        val dir = File(ctx.cacheDir, "vega-fixture").apply { deleteRecursively(); mkdirs() }
        files.forEach { (n, code) -> File(dir, "$n.js").writeText(code) }
        return p to dir
    }

    @Test fun fixtureProviderRunsThroughTheSharedRuntime() = runBlocking {
        val (p0, dir) = install()
        val extra = VegaAdapter.prepare(ctx, p0, dir)
        val p = p0.copy(extra = extra)
        val api = VegaAdapter.build(ctx, p, dir, "Fixture")
        assertEquals(listOf("New", "Drama"), api.mainPage.map { it.name })

        val t0 = System.currentTimeMillis()
        val home = api.getMainPage(1, MainPageRequest("Drama", "drama", false))!!
        log("VEGA home ${System.currentTimeMillis() - t0} ms: ${home.items.first().list.map { it.name + "|" + it.url + "|" + it.posterUrl }}")
        val items = home.items.first().list
        assertEquals(listOf("One (2024) [drama]", "Two [drama]"), items.map { it.name })
        assertEquals(listOf("/one/", "/two/"), items.map { it.url })
        assertEquals(listOf("https://img.example/1.jpg", "https://img.example/2.jpg"), items.map { it.posterUrl })
        assertEquals("Found cats", api.search("cats", 1)?.items?.single()?.name)

        val load = api.load("/two/") as TvSeriesLoadResponse
        log("VEGA episodes ${load.episodes.map { "${it.season}x${it.episode} ${it.name}" }}")
        assertEquals(3, load.episodes.size)
        assertEquals(listOf(1, 1, 2), load.episodes.map { it.season })

        val links = ArrayList<ExtractorLink>()
        assertTrue(api.loadLinks(load.episodes.first().data, false, {}, { links += it }))
        log("VEGA links ${links.map { it.name + " " + it.url + " q=" + it.quality }}")
        assertEquals("the DRM stream is dropped", 1, links.size)
        assertEquals("https://cdn.example/series.m3u8", links.single().url)
        assertEquals(1080, links.single().quality)
        assertEquals("https://site.example/", links.single().referer)
    }

    @Test fun privateAddressesAreRefusedAndCallsDoNotShareState() = runBlocking {
        val (p, dir) = install()
        val api = VegaAdapter.build(ctx, p, dir, "Fixture")
        api.getMainPage(1, MainPageRequest("New", "", false)) // sets globalThis.__leak in that call's runtime
        val leak = ArrayList<ExtractorLink>()
        api.loadLinks(VegaAdapter.target("leak", "movie"), false, {}, { leak += it })
        log("VEGA leak=${leak.map { it.name }}")
        assertEquals("a fresh runtime per call", "Fixture", leak.single().source)
        assertTrue("state from an earlier call is gone: ${leak.single().name}", leak.single().name.startsWith("undefined"))

        // A real listener on loopback, so only the guard (not a refused connection) can block it.
        val server = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        var hits = 0
        val t = kotlin.concurrent.thread(isDaemon = true) {
            runCatching {
                while (true) server.accept().use { s ->
                    // The guard may let the TCP connection open but writes no request bytes, so
                    // only a request that actually arrives counts.
                    s.soTimeout = 3_000
                    if (runCatching { s.getInputStream().read() }.getOrDefault(-1) < 0) return@use
                    hits++
                    s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                }
            }
        }
        val priv = ArrayList<ExtractorLink>()
        try {
            api.loadLinks(VegaAdapter.target("private:${server.localPort}", "movie"), false, {}, { priv += it })
        } finally { server.close(); t.join(2_000) }
        log("VEGA private=${priv.map { it.name }} hits=$hits")
        assertTrue("127.0.0.1 must be refused (SRC-2): ${priv.map { it.name }}", priv.single().name.startsWith("blocked"))
        assertEquals("no request may reach the loopback listener", 0, hits)
    }

    @Test fun busyLoopIsStoppedWithinTheBudget() = runBlocking {
        val (p, dir) = install()
        val api = VegaAdapter.build(ctx, p, dir, "Fixture")
        val t0 = System.currentTimeMillis()
        val r = runCatching { api.loadLinks(VegaAdapter.target("loop", "movie"), false, {}, {}) }
        val ms = System.currentTimeMillis() - t0
        log("VEGA loop result=$r after $ms ms")
        assertTrue("loop ran for $ms ms", ms <= ScriptRuntime.CALL_BUDGET_MS + 5_000)
        assertTrue("a stopped call yields no links", r.getOrNull() != true)
        // The runtime still works afterwards.
        val ok = ArrayList<ExtractorLink>()
        api.loadLinks(VegaAdapter.target("/m", "movie"), false, {}, { ok += it })
        assertEquals(1, ok.size)
    }
}
