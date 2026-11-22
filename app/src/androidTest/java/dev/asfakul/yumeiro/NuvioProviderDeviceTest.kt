/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.StreamAddons
import dev.asfakul.yumeiro.online.StreamRequest
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.ecosystem.NuvioAdapter
import dev.asfakul.yumeiro.sources.ecosystem.NuvioStreams
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * F-05-N (ADR-057) on the real QuickJS: fixture Nuvio scrapers (written for this test in the shape
 * esbuild gives community scrapers: CommonJS, `require("crypto-js")` with `.default` interop,
 * `require("cheerio-without-node-native")`) run through NuvioStreams. Their streams map to links,
 * DRM is dropped, crypto-js AES (key + iv and passphrase), MD5, HMAC and TripleDES give the same
 * answers as crypto-js 4.2.0 itself (ciphertexts made with it), a crashing scraper ends only itself,
 * and the merged flow carries the opened source's links plus the scrapers'.
 */
@RunWith(AndroidJUnit4::class)
class NuvioProviderDeviceTest {
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuNuvio", s)

    private val good = """
        "use strict";
        var import_crypto_js = __toESM(require("crypto-js"));
        var cheerio = require("cheerio-without-node-native");
        function __toESM(m) { return m && m.__esModule ? m : { default: m }; }
        var PAGE = '<ul><li data-q="1080p">HLS</li><li data-q="720p">MP4</li></ul>';
        async function getStreams(tmdbId, mediaType, season, episode) {
          var C = import_crypto_js.default.default || import_crypto_js.default;
          var key = C.enc.Utf8.parse("0123456789abcdef0123456789abcdef"), iv = C.enc.Utf8.parse("abcdef9876543210");
          var hls = C.AES.decrypt("dAgSP1vHRwS5+1r0iooTHCYgD2UMjvkDcILb6WJg3NLnaUDu2SrK2M5dcJvsKiuK", key, { iv: iv, mode: C.mode.CBC, padding: C.pad.Pkcs7 }).toString(C.enc.Utf8);
          var mp4 = C.AES.decrypt("U2FsdGVkX184xvVqeornQ/CEeXY0liF1V2AcNqD3Wlb2W1pVeN8Z+w8UzsvNK7Id", "yumeiro").toString(C.enc.Utf8);
          var token = C.MD5(tmdbId + mediaType).toString();
          var sig = C.HmacSHA256(String(tmdbId), "k").toString(C.enc.Base64);
          var tri = C.TripleDES.decrypt("BQ0Tg4j6tY4=", C.enc.Utf8.parse("123456789012345678901234"), { mode: C.mode.ECB }).toString(C.enc.Utf8);
          var $ = cheerio.load(PAGE);
          var q = $("li").map(function (i, el) { return $(el).attr("data-q"); }).get();
          var tag = mediaType + (season ? " S" + season + "E" + episode : "") + " id=" + SCRAPER_ID;
          return [
            { name: "Fixture " + q[0], title: tag + "\nline two", url: hls + "?t=" + token, quality: q[0], headers: { Referer: "https://fx.example/", "X-Sig": sig, "X-Tri": tri } },
            { name: "Fixture", title: "mirror", url: mp4, quality: q[1] },
            { name: "Locked", url: "https://drm.example/a.mpd", drm: { widevine: "x" } }
          ];
        }
        module.exports = { getStreams };
    """.trimIndent()

    private val crashing = """module.exports = { getStreams: async function () { throw new Error("site changed"); } };"""
    private val movieOnly = """module.exports = { getStreams: async (id, t) => [{ name: "M", url: "https://m.example/" + t + ".mp4" }] };"""

    private fun plugin(id: String, code: String, types: List<String> = emptyList()): Pair<EcosystemRegistry.Installed, File> {
        val extra = if (types.isEmpty()) JsonObject(emptyMap()) else JsonObject(mapOf("types" to JsonArray(types.map(::JsonPrimitive))))
        val p = EcosystemRegistry.Installed("https://fixture.example/manifest.json", SourceHub.Ecosystem.NUVIO, id, id.replaceFirstChar { it.uppercase() }, "1", null, emptyMap(), extra)
        val dir = File(ctx.cacheDir, "nuvio-fixture/$id").apply { deleteRecursively(); mkdirs() }
        File(dir, NuvioAdapter.FILE).writeText(code)
        return p to dir
    }

    private val movie = StreamRequest(550, "tt0137523", "movie", null, null, "Fight Club", 1999)
    private val tv = StreamRequest(1399, null, "tv", 1, 3, "Game of Thrones", 2011)

    @Test fun aFixtureScraperRunsWithCryptoJsAndCheerio() = runBlocking {
        val t0 = System.currentTimeMillis()
        val ev = NuvioStreams.streamsOf(ctx, listOf(plugin("fixture", good)), movie).toList()
        log("NUVIO movie ${System.currentTimeMillis() - t0} ms: $ev")
        val links = ev.filterIsInstance<LinkEvent.Link>().map { it.link }
        assertEquals("two streams, the DRM one dropped: $ev", 2, links.size)
        val a = links.first { it.isHls }
        assertEquals("AES-CBC key+iv decrypts like crypto-js; MD5 matches", "https://cdn.example/fc/master.m3u8?t=8f15bdca95f9204845684290a5549103", a.url)
        assertEquals("Fixture 1080p · movie id=fixture", a.name)
        assertEquals("Nuvio · Fixture", a.source)
        assertEquals(1080, a.quality)
        assertEquals("https://fx.example/", a.referer)
        assertEquals("HmacSHA256 Base64 matches crypto-js", "D1BcQu8SsK9MC7PJfKRxQwAwNBUbGQmfg6xQhY/7qnU=", a.headers["X-Sig"])
        assertEquals("TripleDES ECB decrypts like crypto-js", "tri", a.headers["X-Tri"])
        val b = links.first { !it.isHls }
        assertEquals("passphrase AES (OpenSSL Salted__) decrypts like crypto-js", "https://cdn2.example/fc.mp4", b.url)
        assertEquals(720, b.quality)
        assertEquals(listOf("DRM"), ev.filterIsInstance<LinkEvent.Skipped>().map { it.reason })
    }

    @Test fun seriesGetSeasonAndEpisodeAndTypesAreRespected() = runBlocking {
        val ev = NuvioStreams.streamsOf(ctx, listOf(plugin("fixture", good), plugin("films", movieOnly, listOf("movie"))), tv).toList()
        log("NUVIO tv: $ev")
        val links = ev.filterIsInstance<LinkEvent.Link>().map { it.link }
        assertTrue("season and episode reach getStreams: ${links.map { it.name }}", links.any { it.name == "Fixture 1080p · tv S1E3 id=fixture" })
        assertTrue("a movie-only scraper is not asked for a series", links.none { it.source == "Nuvio · Films" })
        val m = NuvioStreams.streamsOf(ctx, listOf(plugin("films", movieOnly, listOf("movie"))), movie).toList()
        assertEquals("https://m.example/movie.mp4", m.filterIsInstance<LinkEvent.Link>().single().link.url)
    }

    @Test fun aCrashingScraperEndsOnlyItselfAndTheListMerges() = runBlocking {
        val main = LinkEvent.Link(OnlineLink("Ext server", "Extension", "https://ext.example/v.m3u8", null, 1080, true, false, emptyMap()))
        val ev = StreamAddons.merge(
            flowOf(main, LinkEvent.Done(true)),
            listOf(NuvioStreams.streamsOf(ctx, listOf(plugin("broken", crashing), plugin("fixture", good)), movie)),
        ).toList()
        log("NUVIO merged: $ev")
        val links = ev.filterIsInstance<LinkEvent.Link>().map { it.link }
        assertEquals("1 from the opened source + 2 from the scraper: ${links.map { it.name }}", 3, links.size)
        assertTrue(links.any { it.source == "Extension" })
        assertEquals(2, links.count { it.source == "Nuvio · Fixture" })
        assertTrue("the broken scraper is reported, not fatal", ev.filterIsInstance<LinkEvent.Skipped>().any { it.reason.startsWith("Broken: ") && it.reason.contains("site changed") })
        assertEquals(LinkEvent.Done(true, null), ev.last())
        assertEquals(1, ev.count { it is LinkEvent.Done })
    }

    @Test fun nothingFoundSaysSo() = runBlocking {
        val ev = NuvioStreams.streamsOf(ctx, listOf(plugin("broken", crashing)), movie).toList()
        assertEquals(LinkEvent.Done(false, NuvioStreams.NO_STREAMS), ev.last())
        assertTrue("no TMDB id, nothing asked", NuvioStreams.streamsOf(ctx, listOf(plugin("fixture", good)), movie.copy(tmdbId = null)).toList().isEmpty())
    }
}
