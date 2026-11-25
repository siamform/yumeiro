/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * SEARCH-CAP-3 (A0): a title with only an IMDb id still reaches Nuvio scrapers (TMDB ids), and one with only a TMDB id
 * reaches Stremio stream add-ons (IMDb ids). Twelve Nuvio-style add-ons share one lookup; a slow one and a broken one
 * never hold back the others.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IdBridgeTest {
    private val imdbOnly = StreamRequest(null, "tt1375666", "movie", null, null, "Inception", 2010)
    private val tmdbOnly = StreamRequest(1396, null, "tv", 1, 1, "Breaking Bad", 2008)

    @Test fun theMissingIdIsLookedUpOnceForEveryAddon() = runBlocking {
        val lookups = AtomicInteger()
        val get = StreamAddons.bridgeOnce(imdbOnly, tmdbFor = { i, tv -> lookups.incrementAndGet(); delay(30); if (i == "tt1375666" && !tv) 27205 else null }, imdbFor = { _, _ -> error("not asked") })
        val seen = (1..12).map { async { get() } }.awaitAll()
        assertEquals(1, lookups.get())
        assertTrue(seen.all { it.tmdbId == 27205 && it.imdbId == "tt1375666" })
    }

    @Test fun tmdbOnlyGetsAnImdbId() = runBlocking {
        val r = StreamAddons.bridgeOnce(tmdbOnly, tmdbFor = { _, _ -> error("not asked") }, imdbFor = { t, tv -> if (t == 1396 && tv) "tt0903747" else null })()
        assertEquals("tt0903747", r.imdbId)
        assertEquals(1396, r.tmdbId)
    }

    @Test fun aFailedLookupKeepsTheRequestAsItWas() = runBlocking {
        val r = StreamAddons.bridgeOnce(imdbOnly, tmdbFor = { _, _ -> throw java.io.IOException("offline") }, imdbFor = { _, _ -> null })()
        assertEquals(imdbOnly, r)
    }

    @Test fun twelveNuvioAddonsAnswerAsTheyComeWithTheBridgedId() = runBlocking {
        val asked = ConcurrentHashMap<String, StreamRequest>()
        val get = StreamAddons.bridgeOnce(imdbOnly, tmdbFor = { _, _ -> 27205 }, imdbFor = { _, _ -> null })
        fun scraper(i: Int): Flow<LinkEvent> = flow {
            val r = get(); asked["s$i"] = r
            if (r.tmdbId == null) return@flow // a Nuvio scraper can't look anything up without TMDB
            when (i) {
                3 -> error("scraper 3 crashed")
                7 -> { delay(400); emit(link("slow$i")) }
                else -> { delay(10L * i); emit(link("n$i")) }
            }
        }
        val main = flow<LinkEvent> { emit(LinkEvent.Done(false, "no links")) }
        val started = System.nanoTime()
        val out = StreamAddons.merge(main, (1..12).map { i -> flow { emitAll(scraper(i)) } }).toList()
        val links = out.filterIsInstance<LinkEvent.Link>().map { it.link.name }
        assertEquals(12, asked.size)
        assertTrue(asked.values.all { it.tmdbId == 27205 })
        assertEquals("11 scrapers gave links (one crashed)", 11, links.size)
        assertEquals("the slow one comes last", "slow7", links.last())
        assertEquals(LinkEvent.Done(true, null), out.last())
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
    }

    private fun link(u: String) = LinkEvent.Link(OnlineLink(u, "Nuvio", "https://x.example/$u.m3u8", null, 1080, true, false, emptyMap()))
}
