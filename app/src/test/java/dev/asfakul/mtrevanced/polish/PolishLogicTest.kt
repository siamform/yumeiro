/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.polish

import dev.asfakul.mtrevanced.online.AccessLadder
import dev.asfakul.mtrevanced.player.SleepTimer
import dev.asfakul.mtrevanced.stream.StreamRequest
import dev.asfakul.mtrevanced.ui.components.ErrorHelp
import dev.asfakul.mtrevanced.ui.screens.watch.StreamWatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PolishLogicTest {

    // ---- Sleep timer -------------------------------------------------------------------------

    private class FakePlayer {
        var pos = 0L; var dur = 180_000L; var id: String? = "a"; var volume = 1f; var paused = false
    }

    private fun timer(scope: TestScope, p: FakePlayer) = SleepTimer(
        scope = scope, now = { scope.currentTime },
        positionMs = { p.pos }, durationMs = { p.dur }, trackId = { p.id },
        setVolume = { p.volume = it }, pause = { p.paused = true },
    )

    @Test fun countdownFadesThenPauses() {
        val scope = TestScope(); val p = FakePlayer(); val t = timer(scope, p)
        t.start(1)
        assertTrue(t.state.value is SleepTimer.State.Countdown)
        scope.advanceTimeBy(52_000); scope.runCurrent()
        assertFalse(p.paused)
        assertTrue("volume fades in the last 20 s", p.volume < 0.5f)
        scope.advanceTimeBy(10_000); scope.runCurrent()
        assertTrue(p.paused)
        assertEquals(1f, p.volume)
        assertNull(t.state.value)
    }

    @Test fun cancelStopsTimerAndRestoresVolume() {
        val scope = TestScope(); val p = FakePlayer(); val t = timer(scope, p)
        t.start(1)
        scope.advanceTimeBy(55_000); scope.runCurrent()
        t.cancel()
        scope.advanceTimeBy(60_000); scope.runCurrent()
        assertFalse(p.paused); assertEquals(1f, p.volume); assertNull(t.state.value)
    }

    @Test fun extendAddsTime() {
        val scope = TestScope(); val p = FakePlayer(); val t = timer(scope, p)
        t.start(1); t.extend(5)
        val s = t.state.value as SleepTimer.State.Countdown
        assertEquals(6 * 60_000L, s.totalMs)
        scope.advanceTimeBy(61_000); scope.runCurrent()
        assertFalse(p.paused)
    }

    @Test fun endOfTrackPausesWhenSongEndsOrChanges() {
        val scope = TestScope(); val p = FakePlayer(); val t = timer(scope, p)
        t.endOfTrack()
        scope.advanceTimeBy(1_000); scope.runCurrent()
        assertFalse(p.paused)
        p.pos = p.dur - 100
        scope.advanceTimeBy(500); scope.runCurrent()
        assertTrue(p.paused)

        val p2 = FakePlayer(); val t2 = timer(scope, p2)
        t2.endOfTrack(); p2.id = "b"
        scope.advanceTimeBy(500); scope.runCurrent()
        assertTrue(p2.paused)
    }

    // ---- Error suggestions ---------------------------------------------------------------------

    @Test fun forbiddenGetsHeaderAndRefreshTips() {
        val tips = ErrorHelp.suggestionsFor("Access denied (HTTP 403)")
        assertTrue(tips.any { "Referer" in it })
        assertTrue(tips.any { "fresh link" in it })
    }

    @Test fun eachProblemKindHasTips() {
        listOf("HTTP 404", "Connection timed out", "No internet", "Decoder failed: HEVC", "Widevine license", "HTTP 451", "something odd").forEach {
            assertTrue(it, ErrorHelp.suggestionsFor(it).isNotEmpty())
        }
        assertTrue(ErrorHelp.suggestionsFor("HTTP 451").any { "region" in it.lowercase() || "countries" in it })
    }

    @Test fun parseKeepsOwnBulletsAndSummary() {
        val p = ErrorHelp.parse("The server refused this link.\n• Refresh the playlist\n• Add the Referer")
        assertEquals("The server refused this link.", p.summary)
        assertEquals(listOf("Refresh the playlist", "Add the Referer"), p.tips)
        val q = ErrorHelp.parse("Connection timed out")
        assertTrue(q.tips.isNotEmpty())
    }

    // ---- Stream access ladder --------------------------------------------------------------------

    @Test fun ladderFindsVlcForIptvPanels() {
        val r = StreamRequest(url = "http://panel.example:8080/live/u/p/1.ts")
        val hit = AccessLadder.firstWorking(r, null) { it.userAgent == AccessLadder.VLC_UA }
        assertNotNull(hit)
        assertEquals(AccessLadder.Step.VLC_UA, hit!!.step)
    }

    @Test fun ladderFindsSelfOriginReferer() {
        val r = StreamRequest(url = "https://cdn.tv.example/hls/master.m3u8")
        val hit = AccessLadder.firstWorking(r, null) { it.referer == "https://cdn.tv.example/" }
        assertNotNull(hit)
    }

    @Test fun ladderGivesUpWhenNothingWorks() {
        var probes = 0
        val r = StreamRequest(url = "https://x.example/a.m3u8")
        assertNull(AccessLadder.firstWorking(r, "https://site.example/watch") { probes++; false })
        assertTrue(probes in 1..7)
    }

    // ---- Stream watch page -------------------------------------------------------------------------

    @Test fun channelsOrderedByGroupAndNeighboursWrap() {
        val a = StreamRequest(url = "u1", title = "A", group = "News")
        val b = StreamRequest(url = "u2", title = "B", group = "Sports")
        val c = StreamRequest(url = "u3", title = "C", group = "Sports")
        val list = listOf(a, b, c)
        assertEquals(listOf("u2", "u3", "u1"), StreamWatch.ordered(list, "u3").map { it.url })
        assertEquals("u1", StreamWatch.neighbour(list, "u3", 1)?.url)
        assertEquals("u3", StreamWatch.neighbour(list, "u1", -1)?.url)
        assertNull(StreamWatch.neighbour(listOf(a), "u1", 1))
    }
}
