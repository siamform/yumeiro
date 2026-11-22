/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.f05

import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.StreamAddons
import dev.asfakul.yumeiro.online.StreamRequest
import dev.asfakul.yumeiro.sources.stremio.StremioAddons
import dev.asfakul.yumeiro.sources.stremio.StremioStreams
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F-05-STREMIO: a Stremio add-on with no catalog (stream-only, like Torrentio or a direct-link add-on)
 * must give links to any title with an IMDb id, whatever source opened it. Before the fix it was asked
 * only when a Stremio catalog title was played, because the only caller was StremioProvider.loadLinks.
 */
@RunWith(RobolectricTestRunner::class)
class StremioStreamsTest {
    private fun addon(name: String, resources: List<StremioAddons.Resource>, catalogs: List<StremioAddons.Catalog> = emptyList(), types: List<String> = listOf("movie", "series"), prefixes: List<String>? = listOf("tt")) =
        StremioAddons.Addon("https://$name.example/manifest.json", name, name, "1.0", "", null, types, prefixes, resources, catalogs)

    private val streamOnly = addon("direct", listOf(StremioAddons.Resource("stream", null, null)))
    private val moviesOnly = addon("films", listOf(StremioAddons.Resource("stream", listOf("movie"), null)), types = listOf("movie"))
    private val catalog = addon("cat", listOf(StremioAddons.Resource("catalog", null, null), StremioAddons.Resource("stream", null, null)),
        listOf(StremioAddons.Catalog("movie", "top", "Top", false, false)))

    private val movie = StreamRequest(tmdbId = 1, imdbId = "tt0111161", mediaType = "movie", season = null, episode = null, title = "M", year = 1994)
    private val episode = movie.copy(imdbId = "tt0898266", mediaType = "tv", season = 9, episode = 17)

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test fun theAddonIsRegisteredOnTheSharedSeamBehindItsOwnFlagWhichIsOn() {
        assertTrue("StremioStreams joins Nuvio on the add-on seam", StremioStreams in StreamAddons.registered)
        assertEquals("f05_stremio_streams", FeatureFlags.Flag.F05_STREMIO_STREAMS.key)
        assertTrue(FeatureFlags.Flag.F05_STREMIO_STREAMS.default)
    }

    @Test fun onlyAddonsWithoutACatalogAreAskedFromOtherSources() {
        assertEquals(listOf(streamOnly, moviesOnly), StremioStreams.streamOnly(listOf(streamOnly, moviesOnly, catalog)))
    }

    @Test fun idsFollowTheStremioProtocol() {
        assertEquals("movie" to "tt0111161", StremioStreams.idFor(movie))
        assertEquals("series" to "tt0898266:9:17", StremioStreams.idFor(episode))
        assertNull("no IMDb id, nothing to ask", StremioStreams.idFor(movie.copy(imdbId = null)))
        assertEquals("https://direct.example/stream/series/tt0898266%3A9%3A17.json", StremioStreams.urlFor(streamOnly, "series", "tt0898266:9:17"))
    }

    @Test fun webLinksBecomeLinksTorrentsAreSkippedWithTheirMagnet() {
        val ev = StremioStreams.eventsOf("Direct", obj("""{"streams":[
            {"name":"Direct\n1080p","description":"Main mirror","url":"https://cdn.example/v/master.m3u8",
             "behaviorHints":{"notWebReady":true,"proxyHeaders":{"request":{"Referer":"https://site.example/"}}},
             "subtitles":[{"lang":"eng","url":"https://cdn.example/en.vtt"}]},
            {"name":"4k","url":"https://cdn.example/v/film.mp4"},
            {"title":"Torrent 720p","infoHash":"0123456789abcdef0123456789abcdef01234567","fileIdx":2,"sources":["tracker:udp://t.example:80"]},
            {"ytId":"dQw4w9WgXcQ"},
            {"externalUrl":"https://www.example.com/watch"},
            {"url":"ftp://old.example/x.mp4"}]}"""))
        val links = ev.filterIsInstance<LinkEvent.Link>().map { it.link }
        assertEquals(2, links.size)
        val hls = links[0]
        assertEquals("Stremio · Direct", hls.source)
        assertEquals("Direct 1080p · Main mirror", hls.name)
        assertEquals(1080, hls.quality); assertTrue(hls.isHls)
        assertEquals("https://site.example/", hls.referer)
        assertEquals(mapOf("Referer" to "https://site.example/"), hls.headers)
        assertEquals(2160, links[1].quality)
        assertEquals(listOf("https://cdn.example/en.vtt"), ev.filterIsInstance<LinkEvent.Subtitle>().map { it.subtitle.url })
        val torrent = ev.filterIsInstance<LinkEvent.Skipped>().single()
        assertEquals("Torrent", torrent.reason)
        assertTrue(torrent.url!!.startsWith("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
    }

    @Test fun everyMatchingAddonIsAskedAndASlowOrBrokenOneEndsOnlyItself() = runBlocking {
        val broken = addon("broken", listOf(StremioAddons.Resource("stream", null, null)))
        val asked = java.util.Collections.synchronizedList(ArrayList<String>())
        val out = StremioStreams.streamsOf(listOf(streamOnly, moviesOnly, broken), episode) { url ->
            asked += url
            when {
                url.startsWith("https://broken.") -> error("HTTP 500")
                else -> { delay(10); obj("""{"streams":[{"name":"720p","url":"https://cdn.example/ep.mp4"}]}""") }
            }
        }.toList()
        assertEquals("the movie-only add-on is not asked for an episode",
            setOf("https://direct.example/stream/series/tt0898266%3A9%3A17.json", "https://broken.example/stream/series/tt0898266%3A9%3A17.json"), asked.toSet())
        assertEquals(1, out.count { it is LinkEvent.Link })
        assertTrue(out.any { it is LinkEvent.Skipped && it.reason.startsWith("broken:") })
        assertTrue("no Done when a link came in", out.none { it is LinkEvent.Done })
    }

    @Test fun withNothingFoundOneDoneSaysSo() = runBlocking {
        val out = StremioStreams.streamsOf(listOf(streamOnly), movie) { obj("""{"streams":[]}""") }.toList()
        assertEquals(listOf<LinkEvent>(LinkEvent.Done(false, StremioStreams.NO_STREAMS)), out)
        assertTrue("no IMDb id: nothing asked", StremioStreams.streamsOf(listOf(streamOnly), movie.copy(imdbId = null)) { error("asked") }.toList().isEmpty())
    }
}
