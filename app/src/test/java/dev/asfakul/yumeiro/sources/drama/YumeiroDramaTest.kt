/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.drama

import dev.asfakul.yumeiro.online.ExtensionFormats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yumeiro Drama: parsing of saved ONShort pages and the three playback transports (no network). */
class YumeiroDramaTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("drama/$name")!!.readText()

    @Test fun platformShelfParsesCards() {
        val cards = OnShortParser.cards(res("platform.html"))
        assertTrue("cards: ${cards.size}", cards.size >= 10)
        val first = cards.first()
        assertEquals("Daddy, Save Me", first.title)
        assertEquals("https://onshort.net/daddy-save-me/", first.url)
        assertEquals(40, first.episodes)
        assertEquals("DramaBox", first.platform)
        assertTrue(first.cover!!.startsWith("https://"))
    }

    @Test fun searchJsonParses() {
        val r = OnShortParser.searchResults(res("search.json"))
        assertEquals(6, r.size)
        assertEquals("Fake Wife, True Love", r.first().title)
        assertEquals("NetShort", r.first().platform)
        assertEquals(40, r.first().episodes)
        assertTrue(OnShortParser.searchResults("<html>nope</html>").isEmpty())
    }

    @Test fun restSeriesKeepsTicketAndEndpoint() {
        val s = OnShortParser.series(res("series_rest.html"), "https://onshort.net/daddy-save-me/")!!
        assertEquals("Daddy, Save Me", s.title)
        assertEquals(40, s.episodes)
        assertEquals("125740", s.hints["post"])
        assertEquals("rest-get", s.hints["transport"])
        assertEquals("https://onshort.net/wp-json/onshort-player/v1/episode", s.hints["endpoint"])
        assertEquals("1794495084.testticket", s.hints["ticket"])
        assertTrue(s.plot!!.startsWith("Melissa"))
        assertTrue(s.hints["tags"]!!.contains("Billionaire"))
    }

    @Test fun runtimeSeriesFindsPlatform() {
        val s = OnShortParser.series(res("series_runtime.html"), "https://onshort.net/fake-wife-true-love/")!!
        assertEquals("netshort", s.hints["platformSlug"])
        assertTrue(s.hints["runtime"]!!.endsWith("/series/217985/runtime"))
    }

    @Test fun runtimeTransportServesEpisode() = runBlocking {
        val seen = mutableListOf<String>()
        val cat = OnShortCatalog(fetch = { url, _ ->
            seen += url
            when {
                url.endsWith("/runtime") -> 200 to """{"mode":"api","episodes":[{"episode":1,"url":"https://cdn.example/1.mp4","subtitles":[{"url":"https://cdn.example/1.vtt","lang":"es","label":"Español"}]},{"episode":2,"url":"https://cdn.example/2.mp4","subtitles":[]}]}"""
                else -> 200 to res("series_runtime.html")
            }
        })
        val s = cat.series("https://onshort.net/fake-wife-true-love/")
        val e2 = cat.streams(s, 2)
        assertEquals("https://cdn.example/2.mp4", e2.sources.values.single())
        val e1 = cat.streams(s, 1)
        assertEquals("es", e1.subtitles.single().language)
        // Runtime JSON is fetched once for all episodes; the page once.
        assertEquals(1, seen.count { it.endsWith("/runtime") })
        assertEquals(1, seen.count { it.endsWith("/fake-wife-true-love/") })
    }

    @Test fun restTransportRefreshesTicketOn403() = runBlocking {
        val tickets = mutableListOf<String>()
        val cat = OnShortCatalog(fetch = { url, headers ->
            when {
                url.contains("/onshort-player/v1/episode") -> {
                    val t = headers["X-ONShort-Ticket"].orEmpty(); tickets += t
                    if (t == "fresh") 200 to """{"ok":true,"episode":3,"url":"https://cdn.example/3.720p.mp4","quality":"720","sources":{"720":"https://cdn.example/3.720p.mp4","480":"https://cdn.example/3.480p.mp4"},"subtitles":[]}"""
                    else 403 to """{"ok":false,"message":"expired","ticket":"fresh"}"""
                }
                else -> 200 to res("series_rest.html")
            }
        })
        val s = cat.series("https://onshort.net/daddy-save-me/")
        val e = cat.streams(s, 3)
        assertEquals(listOf("1794495084.testticket", "fresh"), tickets)
        assertEquals(setOf("720", "480"), e.sources.keys)
    }

    @Test fun failedEpisodeSaysWhy() = runBlocking {
        val cat = OnShortCatalog(fetch = { url, _ -> if (url.contains("/episode")) 500 to "oops" else 200 to res("series_rest.html") })
        val s = cat.series("https://onshort.net/daddy-save-me/")
        val err = runCatching { cat.streams(s, 1) }.exceptionOrNull()
        assertNotNull(err)
        assertTrue(err!!.message!!.contains("Episode 1"))
    }

    @Test fun shortDramaDetection() {
        assertTrue(YumeiroDrama.isShortDrama("DramaBox"))
        assertTrue(YumeiroDrama.isShortDrama(null, "Watch ReelShort and ShortMax"))
        assertTrue(YumeiroDrama.isShortDrama("Short Dramas Hub"))
        assertFalse(YumeiroDrama.isShortDrama("Dramacool"))
        assertFalse(YumeiroDrama.isShortDrama("KissKH", "Asian dramas"))
        assertEquals(listOf("Short drama"), ExtensionFormats.withShortDrama(listOf("Other"), "FreeReels"))
        assertEquals(listOf("Asian drama"), ExtensionFormats.withShortDrama(listOf("Asian drama"), "Dramacool"))
    }

    @Test fun providerIdsRoundTrip() {
        val (c, u) = YumeiroDramaProvider.parseId("drama:onshort|https://onshort.net/x-y/")
        assertEquals("onshort", c); assertEquals("https://onshort.net/x-y/", u)
        assertEquals("Spanish", YumeiroDramaProvider.languageLabel("es"))
    }
}
