/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.f05

import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.StreamAddons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F-05-N (ADR-057): a title's own links and the stream-only add-ons' links arrive in one flow with
 * one Done at the end. Before the fix there was no merge: only the opened source's links were
 * collected, so a Nuvio scraper's streams could never reach the source list.
 */
@RunWith(RobolectricTestRunner::class)
class StreamAddonsMergeTest {
    private fun link(u: String) = LinkEvent.Link(OnlineLink(u, "s", "https://x.example/$u.m3u8", null, 1080, true, false, emptyMap()))
    private fun f(vararg e: LinkEvent, wait: Long = 0): Flow<LinkEvent> = flow { if (wait > 0) delay(wait); e.forEach { emit(it) } }

    @Test fun linksFromEveryAddonJoinTheMainLinksAndOneDoneComesLast() = runBlocking {
        val out = StreamAddons.merge(
            f(link("a"), LinkEvent.Done(true)),
            listOf(f(link("n1"), link("n2"), wait = 50), f(LinkEvent.Done(false, "nothing"))),
        ).toList()
        val urls = out.filterIsInstance<LinkEvent.Link>().map { it.link.name }.toSet()
        assertEquals(setOf("a", "n1", "n2"), urls)
        assertEquals("exactly one Done", 1, out.count { it is LinkEvent.Done })
        assertTrue("Done is last", out.last() is LinkEvent.Done)
        assertEquals(LinkEvent.Done(true, null), out.last())
    }

    @Test fun anAddonThatThrowsEndsOnlyItself() = runBlocking {
        val bad = flow<LinkEvent> { emit(link("n1")); error("scraper crashed") }
        val out = StreamAddons.merge(f(link("a"), LinkEvent.Done(true), wait = 30), listOf(bad)).toList()
        assertEquals(listOf("n1", "a"), out.filterIsInstance<LinkEvent.Link>().map { it.link.name })
        assertEquals(LinkEvent.Done(true, null), out.last())
    }

    @Test fun withNoLinksTheMainSourcesErrorIsKept() = runBlocking {
        val out = StreamAddons.merge(f(LinkEvent.Done(false, "Extension failed")), listOf(f(LinkEvent.Done(false, "No scrapers returned a stream")))).toList()
        assertEquals(listOf<LinkEvent>(LinkEvent.Done(false, "Extension failed")), out)
    }

    @Test fun anAddonErrorShowsWhenTheMainSourceHadNone() = runBlocking {
        val out = StreamAddons.merge(f(LinkEvent.Done(false, null)), listOf(f(LinkEvent.Done(false, "No scrapers returned a stream")))).toList()
        assertEquals(LinkEvent.Done(false, "No scrapers returned a stream"), out.single())
    }

    @Test fun takingFewerEventsCancelsTheSlowAddons() = runBlocking {
        val slow = flow<LinkEvent> { delay(60_000); emit(link("late")) }
        val t = System.currentTimeMillis()
        val out = StreamAddons.merge(f(link("a"), link("b")), listOf(slow)).take(2).toList()
        assertEquals(2, out.size)
        assertTrue("cancelled at once, not after the slow add-on", System.currentTimeMillis() - t < 5_000)
    }

    @Test fun noAddonsMeansTheMainFlowUnchanged() {
        val main = f(link("a"))
        assertSame(main, StreamAddons.merge(main, emptyList()))
    }

    private fun detail(kind: OnlineKind = OnlineKind.MOVIE, tmdb: Int? = 550, imdb: String? = "tt0137523") = OnlineDetail(
        "api", "u", "Fight Club", kind, "Movie", null, null, emptyMap(), null, 1999, emptyList(), null, null, null,
        "data", emptyMap(), emptyMap(), emptyList(), imdbId = imdb, tmdbId = tmdb,
    )

    @Test fun theRequestCarriesIdsTypeSeasonAndEpisode() {
        val m = StreamAddons.requestFor(detail(), null)!!
        assertEquals("movie", m.mediaType); assertEquals(550, m.tmdbId); assertEquals("tt0137523", m.imdbId)
        assertNull(m.season); assertNull(m.episode); assertEquals(1999, m.year)
        val ep = OnlineEpisode("d", "Pilot", 2, 5, null, null, null)
        val t = StreamAddons.requestFor(detail(OnlineKind.SERIES), ep)!!
        assertEquals("tv", t.mediaType); assertEquals(2, t.season); assertEquals(5, t.episode)
        val noSeason = StreamAddons.requestFor(detail(OnlineKind.ANIME), OnlineEpisode("d", null, null, 7, null, null, null))!!
        assertEquals("an episode without a season is season 1", 1, noSeason.season)
        assertNull("no ids, nothing to ask", StreamAddons.requestFor(detail(tmdb = null, imdb = null), null))
        assertNull("live channels are never asked", StreamAddons.requestFor(detail(OnlineKind.LIVE), null))
    }
}
