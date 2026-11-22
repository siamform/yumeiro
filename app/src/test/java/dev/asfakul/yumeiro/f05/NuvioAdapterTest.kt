/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.f05

import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.StreamRequest
import dev.asfakul.yumeiro.sources.SourceHub
import dev.asfakul.yumeiro.sources.ecosystem.EcosystemRegistry
import dev.asfakul.yumeiro.sources.ecosystem.NuvioAdapter
import dev.asfakul.yumeiro.sources.ecosystem.RepoFormats
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-05-N (ADR-057): a Nuvio scraper's answer becomes Yumeiro links; the call gets Nuvio's
 * positional arguments; Node.js-only scrapers are reported. Shapes follow tapframe/nuvio-providers
 * (README "Provider contract") and the community repos read for the ledger; no Nuvio code is used.
 */
class NuvioAdapterTest {
    private val answer = """[
      {"name":"VidSrc 1080p","title":"Fight Club (1999)\n1.2 GB","url":"https://cdn.example/fc/master.m3u8?t=1","quality":"1080p","size":"1.2 GB","headers":{"Referer":"https://vidsrc.example/","User-Agent":"UA"},
       "subtitles":[{"url":"https://cdn.example/fc/en.vtt","lang":"English"}]},
      {"name":"VidSrc","title":"720p mirror","url":"https://cdn2.example/fc.mp4","quality":"720"},
      {"name":"Locked","url":"https://drm.example/a.mpd","drm":{"widevine":"k"}},
      {"name":"Torrent","url":"magnet:?xt=urn:btih:BF2063A5E9A69F14716288FCA7C2D6078BDA7369"},
      {"name":"Bad","url":"javascript:alert(1)"},
      {"name":"Empty","url":""},
      {"name":"Dash","url":"https://d.example/v.mpd","quality":"4K"}
    ]"""

    @Test fun streamsBecomeLinksAndDrmIsDropped() {
        val ev = NuvioAdapter.eventsOf("VidSrc", RepoFormats.json.parseToJsonElement(answer))
        val links = ev.filterIsInstance<LinkEvent.Link>().map { it.link }
        assertEquals(3, links.size)
        val a = links[0]
        assertEquals("VidSrc 1080p · Fight Club (1999)", a.name)
        assertEquals("Nuvio · VidSrc", a.source)
        assertEquals(1080, a.quality)
        assertTrue(a.isHls); assertFalse(a.isDash)
        assertEquals("https://vidsrc.example/", a.referer)
        assertEquals(mapOf("Referer" to "https://vidsrc.example/", "User-Agent" to "UA"), a.headers)
        assertEquals(720, links[1].quality); assertFalse(links[1].isHls)
        assertEquals(2160, links[2].quality); assertTrue(links[2].isDash)
        assertTrue("no DRM link is kept", links.none { it.url.contains("drm.example") })
        val skipped = ev.filterIsInstance<LinkEvent.Skipped>()
        assertEquals(listOf("DRM", "Torrent"), skipped.map { it.reason })
        assertTrue(skipped[1].url!!.startsWith("magnet:"))
        val sub = ev.filterIsInstance<LinkEvent.Subtitle>().single().subtitle
        assertEquals("English", sub.language); assertEquals("https://cdn.example/fc/en.vtt", sub.url)
    }

    @Test fun anObjectWithStreamsOrNothingIsReadToo() {
        val o = RepoFormats.json.parseToJsonElement("""{"streams":[{"name":"A","url":"https://a.example/x.mp4"}]}""")
        assertEquals(1, NuvioAdapter.eventsOf("A", o).size)
        assertTrue(NuvioAdapter.eventsOf("A", RepoFormats.json.parseToJsonElement("null")).isEmpty())
    }

    @Test fun argumentsArePositionalLikeNuvio() {
        val movie = StreamRequest(550, "tt0137523", "movie", null, null, "Fight Club", 1999)
        assertEquals("""["550","movie",null,null]""", NuvioAdapter.argsOf(movie))
        val tv = StreamRequest(1399, null, "tv", 1, 3, "Game of Thrones", 2011)
        assertEquals("""["1399","tv",1,3]""", NuvioAdapter.argsOf(tv))
    }

    @Test fun nodeOnlyModulesAreFoundWithoutARegex() {
        val code = """var a=require("crypto-js"),c=require('cheerio-without-node-native');var z=require("zlib"),f=require('node:fs/promises');
            function x(){return require( "util" )} var s=require(`stream`); var q = require(name);"""
        assertEquals(listOf("fs", "stream", "util", "zlib"), NuvioAdapter.nodeNeeds(code))
        assertTrue(NuvioAdapter.nodeNeeds("module.exports={getStreams}").isEmpty())
    }

    private fun installed(extra: JsonObject) = EcosystemRegistry.Installed("https://n.example/manifest.json", SourceHub.Ecosystem.NUVIO, "vidsrc", "VidSrc", "1", null, emptyMap(), extra)

    @Test fun typesFilterAndProblemsAreReadFromTheSavedExtra() {
        val movieOnly = installed(JsonObject(mapOf("types" to JsonArray(listOf(JsonPrimitive("movie"))))))
        assertTrue(NuvioAdapter.handles(movieOnly, "movie")); assertFalse(NuvioAdapter.handles(movieOnly, "tv"))
        val all = installed(JsonObject(emptyMap()))
        assertTrue(NuvioAdapter.handles(all, "tv"))
        assertNull(NuvioAdapter.problemOf(all))
        val node = installed(JsonObject(mapOf("needs" to JsonArray(listOf(JsonPrimitive("zlib"))))))
        assertEquals("Uses Node.js parts Yumeiro can't run (zlib); it may find nothing", NuvioAdapter.problemOf(node))
    }

    @Test fun nuvioIsAStreamOnlyAdapterBehindItsOwnFlag() {
        assertEquals(setOf("scraper.js"), NuvioAdapter.required(dev.asfakul.yumeiro.sources.ecosystem.PluginEntry("a", "A", "1", emptyMap())))
        assertEquals("scripts_nuvio", NuvioAdapter.flag.key)
        assertFalse("off until the device test passes", NuvioAdapter.flag.default)
        assertFalse(dev.asfakul.yumeiro.FeatureFlags.Flag.STREAM_ADDONS.default)
    }

    /** F-05-N-ERR (device 44982374518): QuickJS stacks have no message line; the reason shows the message. */
    @Test fun aFailedScraperShowsItsMessageNotAStackFrame() {
        assertEquals("Broken: Error: site changed",
            NuvioAdapter.reasonOf("Broken", "Error: site changed\n    at getStreams (scraper.js:2:67)\n"))
        assertEquals("only a frame is still shown", "Broken: at getStreams (scraper.js:2:67)",
            NuvioAdapter.reasonOf("Broken", "    at getStreams (scraper.js:2:67)\n"))
        assertEquals("Films: timeout", NuvioAdapter.reasonOf("Films", "timeout"))
    }

    /** F-05-N-ERR: both script invokers report errors through __errText, which puts the message first. */
    @Test fun theRuntimeReportsTheErrorMessage() {
        val js = listOf(java.io.File("src/main/assets/js/provider_runtime.js"), java.io.File("app/src/main/assets/js/provider_runtime.js")).first { it.isFile }.readText()
        assertEquals(2, js.split("__error(G.__errText(e))").size - 1)
        assertTrue(js.contains("(e.name || \"Error\") + \": \" + e.message"))
        assertTrue(dev.asfakul.yumeiro.FeatureFlags.Flag.SCRIPT_ERROR_MESSAGE.default)
    }
}
