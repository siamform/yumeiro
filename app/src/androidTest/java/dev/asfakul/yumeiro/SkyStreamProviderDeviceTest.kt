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
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.ecosystem.SkyStreamAdapter
import dev.asfakul.yumeiro.sources.ecosystem.SkyStreamRepoParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * F-05-S (ADR-058) on the real QuickJS: a fixture SkyStream plugin (written for this test from
 * SkyStream's public developer guide, no SkyStream code) packed as a .sky runs through the shared
 * runtime. Home answers through cb, search through a returned promise; load gives episodes;
 * DRM and torrent streams are dropped; `{success:false}` becomes the error text; a loopback
 * address is refused (SRC-2); getAndUnpack and parse_html answer from the host.
 */
@RunWith(AndroidJUnit4::class)
class SkyStreamProviderDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuSky", s)

    // A p.a.c.k.e.r. sample: eval(function(p,a,c,k,e,d){...}('0 1=2',3,3,'var|x|42'.split('|'),0,{}))
    private val packed = "eval(function(p,a,c,k,e,d){e=function(c){return c};if(!''.replace(/^/,String)){while(c--){d[c]=k[c]||c}k=[function(e){return d[e]}];e=function(){return'\\\\w+'};c=1};while(c--){if(k[c]){p=p.replace(new RegExp('\\\\b'+e(c)+'\\\\b','g'),k[c])}}return p}('0 1=2',3,3,'var|x|42'.split('|'),0,{}))"

    private val js = """
        function getHome(cb) {
          cb({ success: true, data: {
            "Latest": [new MultimediaItem({ title: "Two", url: manifest.baseUrl + "/two", posterUrl: "https://img.example/2.jpg", type: "series" })],
            "Trending": [new MultimediaItem({ title: "One", url: manifest.baseUrl + "/one", posterUrl: "https://img.example/1.jpg", type: "movie" })]
          } });
        }
        async function search(query, cb) {
          const rows = await parse_html("<ul><li><a href='/s1'>Found " + query + "</a></li></ul>", "a", "href");
          return { success: true, data: rows.map(r => ({ title: r.text, url: manifest.baseUrl + r.attr, type: "movie" })) };
        }
        function load(url, cb) {
          if (url.endsWith("/broken")) { cb({ success: false, errorCode: "SITE_OFFLINE", message: "The site is offline" }); return; }
          if (url.endsWith("/one")) { cb({ success: true, data: { title: "One", url: url, type: "movie", year: 2024,
            episodes: [{ name: "Full", url: url + "/play", season: 1, episode: 1 }] } }); return; }
          cb({ success: true, data: new MultimediaItem({ title: "Two", url: url, type: "series", description: "Plot",
            episodes: [new Episode({ name: "E1", url: url + "/e1", season: 1, episode: 1 }), new Episode({ name: "E2", url: url + "/e2", season: 1, episode: 2 }),
                       new Episode({ name: "S2E1", url: url + "/s2e1", season: 2, episode: 1 })] }) });
        }
        async function loadStreams(url, cb) {
          if (url.indexOf("private:") === 0) {
            const r = await http_get("http://127.0.0.1:" + url.slice(8) + "/");
            cb({ success: true, data: [new StreamResult({ url: "https://cdn.example/ok.mp4", source: "status " + r.status })] });
            return;
          }
          if (url === "unpack") {
            cb({ success: true, data: [new StreamResult({ url: "https://cdn.example/u.mp4", source: getAndUnpack(__PACKED__) })] });
            return;
          }
          cb({ success: true, data: [
            new StreamResult({ url: "https://cdn.example/" + url.split("/").pop() + ".m3u8", source: "Main", quality: "1080",
              headers: { Referer: manifest.baseUrl + "/" }, subtitles: [{ url: "https://cdn.example/en.vtt", label: "English" }] }),
            new StreamResult({ url: "https://cdn.example/l.mpd", source: "Locked", drmKid: "a", drmKey: "b" }),
            new StreamResult({ url: "magnet:?xt=urn:btih:abc", source: "Torrent" })
          ] });
        }
    """.trimIndent().replace("__PACKED__", org.json.JSONObject.quote(packed))

    private fun install(): Pair<EcosystemRegistry.Installed, File> {
        val p = EcosystemRegistry.Installed("https://fixture.example/repo.json", SourceHub.Ecosystem.SKYSTREAM, "com.fixture.sky", "SkyFixture", "1", "en", emptyMap())
        val dir = File(ctx.cacheDir, "sky-fixture").apply { deleteRecursively(); mkdirs() }
        ZipOutputStream(File(dir, SkyStreamRepoParser.SKY_FILE).outputStream()).use { z ->
            z.putNextEntry(ZipEntry("plugin.json")); z.write("""{"packageName":"com.fixture.sky","name":"SkyFixture","domains":[{"url":"https://site.example"}]}""".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("plugin.js")); z.write(js.toByteArray()); z.closeEntry()
        }
        return p to dir
    }

    @Test fun fixturePluginRunsThroughTheSharedRuntime() = runBlocking {
        val (p0, dir) = install()
        val p = p0.copy(extra = SkyStreamAdapter.prepare(ctx, p0, dir))
        val api = SkyStreamAdapter.build(ctx, p, dir, "SkyFixture")
        assertEquals("https://site.example", api.mainUrl)

        val t0 = System.currentTimeMillis()
        val home = api.getMainPage(1, MainPageRequest("Home", "", false))!!
        log("SKY home ${System.currentTimeMillis() - t0} ms: ${home.items.map { r -> r.name + "=" + r.list.map { it.name + "|" + it.url } }}")
        assertEquals("Trending comes first", listOf("Trending", "Latest"), home.items.map { it.name })
        assertEquals("https://site.example/one", home.items[0].list.single().url)
        assertEquals("https://img.example/2.jpg", home.items[1].list.single().posterUrl)

        val found = api.search("cats", 1)?.items.orEmpty()
        log("SKY search ${found.map { it.name + "|" + it.url }}")
        assertEquals(listOf("Found cats"), found.map { it.name })
        assertEquals("https://site.example/s1", found.single().url)

        val series = api.load("https://site.example/two") as TvSeriesLoadResponse
        log("SKY episodes ${series.episodes.map { "${it.season}x${it.episode} ${it.name}" }}")
        assertEquals(listOf(1, 1, 2), series.episodes.map { it.season })
        assertEquals("Plot", series.plot)

        val links = ArrayList<ExtractorLink>()
        val subs = ArrayList<SubtitleFile>()
        assertTrue(api.loadLinks(series.episodes.first().data, false, { subs += it }, { links += it }))
        log("SKY links ${links.map { it.name + " " + it.url + " q=" + it.quality + " ref=" + it.referer }} subs=${subs.map { it.lang }}")
        assertEquals("DRM and torrent streams are dropped", 1, links.size)
        assertEquals("https://cdn.example/e1.m3u8", links.single().url)
        assertEquals(1080, links.single().quality)
        assertEquals("https://site.example/", links.single().referer)
        assertEquals(listOf("English"), subs.map { it.lang })

        val movie = api.load("https://site.example/one") as MovieLoadResponse
        assertEquals(2024, movie.year)
        val ml = ArrayList<ExtractorLink>()
        api.loadLinks(movie.dataUrl, false, {}, { ml += it })
        assertEquals("a movie plays its first episode url", "https://cdn.example/play.m3u8", ml.single().url)
    }

    @Test fun aPluginErrorIsReportedWithItsMessage() = runBlocking {
        val (p, dir) = install()
        val api = SkyStreamAdapter.build(ctx, p, dir, "SkyFixture")
        val r = runCatching { api.load("https://site.example/broken") }
        log("SKY broken=${r.exceptionOrNull()?.message}")
        assertTrue("got ${r.exceptionOrNull()?.message}", r.exceptionOrNull()?.message.orEmpty().contains("The site is offline"))
    }

    @Test fun hostHelpersWorkAndLoopbackIsRefused() = runBlocking {
        val (p, dir) = install()
        val api = SkyStreamAdapter.build(ctx, p, dir, "SkyFixture")
        val un = ArrayList<ExtractorLink>()
        api.loadLinks(SkyStreamAdapter.target("unpack", null), false, {}, { un += it })
        log("SKY unpack=${un.map { it.name }}")
        assertTrue("getAndUnpack: ${un.map { it.name }}", un.single().name.contains("var x=42"))

        val server = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        var hits = 0
        val t = kotlin.concurrent.thread(isDaemon = true) {
            runCatching {
                while (true) server.accept().use { s ->
                    s.soTimeout = 3_000
                    if (runCatching { s.getInputStream().read() }.getOrDefault(-1) < 0) return@use
                    hits++
                    s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                }
            }
        }
        val priv = ArrayList<ExtractorLink>()
        try {
            api.loadLinks(SkyStreamAdapter.target("private:${server.localPort}", null), false, {}, { priv += it })
        } finally { server.close(); t.join(2_000) }
        log("SKY private=${priv.map { it.name }} hits=$hits")
        assertTrue("127.0.0.1 must be refused (SRC-2): ${priv.map { it.name }}", priv.single().name.contains("status 0"))
        assertEquals("no request may reach the loopback listener", 0, hits)
    }
}
