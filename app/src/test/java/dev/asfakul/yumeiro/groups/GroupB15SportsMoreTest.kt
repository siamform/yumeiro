/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.sports.SportsScores
import dev.asfakul.yumeiro.sports.SportsScores.Sport
import dev.asfakul.yumeiro.sports.SportsScores.State
import dev.asfakul.yumeiro.stream.LiveChannelFeed
import dev.asfakul.yumeiro.ui.screens.stream.StreamLaunch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * B15 second pass: volleyball and handball, the match page (score by period, statistics, key moments, each
 * team's last games), the polite cache / rate-limit rules and Sports → Watch. Every expected value was read from
 * real ESPN / TheSportsDB answers saved on 2026-11-28 (app/src/test/resources/sports; the espn_summary_* files
 * keep only the keys the app reads).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupB15SportsMoreTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("sports/$name")!!.readText()
    private val day = LocalDate.of(2026, 11, 27)
    private val server = MockWebServer()
    private var clock = 1_000_000L

    @Before fun setUp() {
        server.start()
        SportsScores.resetForTest()
        SportsScores.clockForTest = { clock }
        SportsScores.espnForTest = server.url("/espn").toString()
        SportsScores.sportsDbForTest = server.url("/sdb").toString()
    }

    @After fun tearDown() {
        SportsScores.espnForTest = null; SportsScores.sportsDbForTest = null; SportsScores.clockForTest = null
        SportsScores.resetForTest()
        server.shutdown()
    }

    @Test fun volleyball_isAFilterWithRealMatchesFromBothFeeds() {
        assertTrue(Sport.entries.map { it.id }.containsAll(listOf("football", "volleyball", "handball")))
        val espn = SportsScores.parseEspn(res("espn_volleyball.json"), Sport.VOLLEYBALL, day, 1L)
        val ncaa = espn.leagues.single { it.name == "NCAA Women's Volleyball" }.matches
        assertEquals(11, ncaa.size)
        assertEquals(4, ncaa.count { it.state == State.LIVE })
        assertTrue(ncaa.all { it.espnPath == "volleyball/womens-college-volleyball" })
        val sdb = SportsScores.parseSportsDb(res("sportsdb_volleyball.json"), Sport.VOLLEYBALL, day.plusDays(1), 2L)
        val buli = sdb.leagues.single().matches
        assertEquals("German 1. Bundesliga", sdb.leagues.single().name)
        assertEquals(3, buli.size)
        assertTrue(buli.all { it.state == State.UPCOMING && it.espnPath == null && it.sides.all { s -> s.score == null } })
        val both = SportsScores.merge(listOf(espn, sdb))
        assertEquals(listOf(SportsScores.Source.ESPN, SportsScores.Source.SPORTSDB), both.sources)
        assertEquals(14, both.leagues.sumOf { it.matches.size })
        assertEquals("NCAA Women's Volleyball", both.leagues.first().name) // live league first
        assertEquals(1L, both.fetchedAt) // the older of the two answers, never the newer
        assertTrue(SportsScores.sportsDbUrl(Sport.HANDBALL, day).endsWith("s=Handball"))
        assertTrue(Sport.HANDBALL.espn.isEmpty())
    }

    @Test fun matchPage_football_scoreStatsMomentsAndLastGames() {
        val d = SportsScores.parseDetail(res("espn_summary_soccer.json"), "401879225", 5L)
        assertEquals(State.FINISHED, d.state); assertEquals("FT", d.status)
        assertEquals(listOf("Nottingham Forest", "Chelsea"), d.sides.map { it.name })
        assertEquals(listOf("3", "2"), d.sides.map { it.score })
        assertEquals(listOf("1", "2"), d.sides[0].periods); assertEquals(listOf("0", "2"), d.sides[1].periods)
        assertTrue(d.sides[0].winner)
        val st = d.stats.associateBy { it.label }
        assertEquals("35.2%", st.getValue("Possession").home); assertEquals("64.8%", st.getValue("Possession").away)
        assertEquals("70%", st.getValue("On Target %").home); assertEquals("30%", st.getValue("On Target %").away)
        assertEquals("15", st.getValue("Fouls").home); assertEquals("Shots", d.stats.first { it.home == "10" && it.away == "9" }.label)
        // Goals and the penalty, not substitutions or kick-off.
        assertEquals(listOf("5'", "51'", "54'", "72'", "89'"), d.moments.map { it.clock })
        assertTrue(d.moments[1].text.startsWith("Penalty - Scored"))
        assertEquals("Chelsea", d.moments[2].team)
        // Each team's last games, from that team's side.
        assertEquals("Nottingham Forest", d.form.first().team)
        val g = d.form.first().games.first { it.opponent == "Arsenal" }
        assertEquals("L", g.result); assertTrue(g.atHome); assertEquals("0", g.own); assertEquals("1", g.other)
        assertEquals(Instant.parse("2026-10-18T15:30:00Z"), g.date)
        assertEquals("The City Ground", d.venue); assertEquals(30851, d.attendance)
        assertFalse(d.thin)
        assertEquals("https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/summary?event=401879225", SportsScores.detailUrl("soccer/eng.1", "401879225"))
    }

    @Test fun matchPage_otherSports() {
        val nfl = SportsScores.parseDetail(res("espn_summary_nfl.json"), "401873081", 5L)
        assertEquals(listOf("Detroit Lions", "Chicago Bears"), nfl.sides.map { it.name })
        assertEquals(listOf("35", "39"), nfl.sides.map { it.score })
        assertEquals(listOf("3", "16", "7", "9"), nfl.sides[0].periods)
        assertEquals("P1 11:28", nfl.moments.first().clock)
        assertEquals("Jake Bates 24 Yd Field Goal", nfl.moments.first().text)
        assertEquals("Detroit Lions", nfl.moments.first().team)
        assertEquals("Ford Field", nfl.venue)
        val nba = SportsScores.parseDetail(res("espn_summary_nba.json"), "401909317", 5L)
        assertEquals(listOf("111", "104"), nba.sides.map { it.score })
        assertEquals("39-86", nba.stats.first { it.label == "FG" }.away)
        val vb = SportsScores.parseDetail(res("espn_summary_volleyball.json"), "401903426", 5L)
        assertEquals("Miami Hurricanes", vb.sides[0].name)
        assertEquals(listOf("25", "19", "25", "25"), vb.sides[0].periods)
        assertEquals(listOf("3", "1"), vb.sides.map { it.score })
        val cr = SportsScores.parseDetail(res("espn_summary_cricket.json"), "1528708", 5L)
        assertEquals("England won by 7 wkts (43b rem) (DLS)", cr.summary)
        assertEquals(listOf("108 (16.5 ov)", "93/3 (8.5/16 ov, target 93)"), cr.sides.map { it.score })
        assertTrue("innings have no plain per-period numbers", cr.sides.all { it.periods.isEmpty() })
        assertEquals(listOf("England led the 5-match series 2-1", "T20I no. 4237", "2026/27"), cr.notes)
        val rugby = SportsScores.parseDetail(res("espn_summary_rugby.json"), "604011", 5L)
        val italy = rugby.form.first { it.team == "Italy" }.games.first()
        assertEquals("L", italy.result); assertEquals("Fiji", italy.opponent)
    }

    @Test fun matchPage_upcomingShowsNoScoreOrStats() {
        val pre = """{"header":{"competitions":[{"status":{"type":{"state":"pre","shortDetail":"Sat 3:00 PM"}},
            "competitors":[{"id":"1","homeAway":"home","score":"0","team":{"displayName":"A"},"linescores":[{"displayValue":"0"}]},
                           {"id":"2","homeAway":"away","score":"0","team":{"displayName":"B"}}]}]},
            "boxscore":{"teams":[{"team":{"id":"1"},"statistics":[{"label":"Fouls","displayValue":"0"}]},{"team":{"id":"2"},"statistics":[{"label":"Fouls","displayValue":"0"}]}]}}"""
        val d = SportsScores.parseDetail(pre, "9", 1L)
        assertEquals(State.UPCOMING, d.state)
        assertTrue(d.sides.all { it.score == null && it.periods.isEmpty() })
        assertTrue(d.stats.isEmpty()); assertTrue(d.thin)
        val hostile = """{"header":{"competitions":[{"status":{"type":{"state":"post"}},"competitors":[
            {"id":"1","homeAway":"home","score":"<script>","team":{"displayName":"A"}},{"id":"2","homeAway":"away","score":"2","team":{"displayName":"B"}}]}]}}"""
        val h = SportsScores.parseDetail(hostile, "9", 1L)
        assertNull(h.sides[0].score); assertEquals("2", h.sides[1].score)
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun cache_reusesFreshAnswersAndSpacesRequests() = runBlocking {
        val today = day
        server.enqueue(ok(res("espn_soccer.json")))
        val a = SportsScores.load(ctx, Sport.FOOTBALL, day, today = today).getOrThrow()
        assertEquals(1, server.requestCount); assertFalse(a.stale)
        // Same page again, and a refresh tap 5 s later: no new request.
        clock += 5_000
        assertEquals(a, SportsScores.load(ctx, Sport.FOOTBALL, day, today = today).getOrThrow())
        assertEquals(a, SportsScores.load(ctx, Sport.FOOTBALL, day, force = true, today = today).getOrThrow())
        assertEquals(1, server.requestCount)
        // Live board: fresh for 25 s, then asked again.
        assertEquals(25_000L, SportsScores.ttlMs(a, today))
        clock += 21_000
        server.enqueue(ok(res("espn_soccer.json")))
        val b = SportsScores.load(ctx, Sport.FOOTBALL, day, today = today).getOrThrow()
        assertEquals(2, server.requestCount); assertEquals(clock, b.fetchedAt)
        // Other days keep longer.
        assertEquals(30 * 60_000L, SportsScores.ttlMs(b.copy(day = today.minusDays(1)), today))
        assertEquals(15 * 60_000L, SportsScores.ttlMs(b.copy(day = today.plusDays(1)), today))
    }

    @Test fun failure_returnsLastAnswerMarkedStale_thenAnErrorWhenTooOld() = runBlocking {
        server.enqueue(ok(res("espn_soccer.json")))
        val a = SportsScores.load(ctx, Sport.FOOTBALL, day, today = day).getOrThrow()
        clock += 60_000
        server.enqueue(MockResponse().setResponseCode(500)); server.enqueue(MockResponse().setResponseCode(500))
        val s = SportsScores.load(ctx, Sport.FOOTBALL, day, force = true, today = day).getOrThrow()
        assertTrue(s.stale); assertEquals(a.fetchedAt, s.fetchedAt) // the old time, never "now"
        clock += SportsScores.STALE_MAX_MS
        server.enqueue(MockResponse().setResponseCode(500)); server.enqueue(MockResponse().setResponseCode(500))
        val e = SportsScores.load(ctx, Sport.FOOTBALL, day, force = true, today = day).exceptionOrNull()
        assertEquals(SportsScores.Reason.SOURCE_DOWN, (e as SportsScores.Failed).reason)
    }

    @Test fun tooManyRequests_backsOffForRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        val e = SportsScores.load(ctx, Sport.HANDBALL, day, today = day).exceptionOrNull() as SportsScores.Failed
        assertEquals(SportsScores.Reason.BUSY, e.reason)
        assertEquals(1, server.requestCount)
        clock += 60_000 // still inside Retry-After: the feed is not asked at all
        assertEquals(SportsScores.Reason.BUSY, (SportsScores.load(ctx, Sport.HANDBALL, day, today = day).exceptionOrNull() as SportsScores.Failed).reason)
        assertEquals(1, server.requestCount)
        clock += 61_000
        server.enqueue(ok(res("sportsdb_volleyball.json")))
        val b = SportsScores.load(ctx, Sport.HANDBALL, day, today = day).getOrThrow()
        assertEquals(2, server.requestCount)
        assertEquals(listOf(SportsScores.Source.SPORTSDB), b.sources)
        assertFalse("handball has no ESPN feed, so TheSportsDB is not a fallback", b.fallback)
    }

    @Test fun espnDown_fallsBackToSportsDbAndSaysSo() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(ok(res("sportsdb_soccer.json")))
        val b = SportsScores.load(ctx, Sport.FOOTBALL, day.plusDays(1), today = day).getOrThrow()
        assertTrue(b.fallback); assertEquals(SportsScores.Source.SPORTSDB, b.source)
    }

    @Test fun watchSports_picksTheUsersOwnSportsChannels() {
        fun ch(name: String, group: String?, genre: String? = null) = dev.asfakul.yumeiro.stream.StreamRequest(url = "https://x/$name.m3u8", title = name, group = group, genre = genre)
        val withGroup = LiveChannelFeed.build(listOf("p"), mapOf("p" to listOf(ch("a", "News"), ch("b", "Sports"), ch("c", "Sports"))))
        assertEquals(StreamLaunch.SportsPick(group = "Sports"), StreamLaunch.sportsFilter(withGroup, genreChips = false))
        val withGenre = LiveChannelFeed.build(listOf("p"), mapOf("p" to listOf(ch("a", "US", "News"), ch("b", "UK", "Sport"))))
        assertEquals(StreamLaunch.SportsPick(genre = "Sport"), StreamLaunch.sportsFilter(withGenre, genreChips = true))
        val none = LiveChannelFeed.build(listOf("p"), mapOf("p" to listOf(ch("a", "News"))))
        assertEquals(StreamLaunch.SportsPick(query = "sport"), StreamLaunch.sportsFilter(none, genreChips = false))
        StreamLaunch.watchSports(); assertTrue(StreamLaunch.takeSports()); assertFalse(StreamLaunch.takeSports())
    }
}
