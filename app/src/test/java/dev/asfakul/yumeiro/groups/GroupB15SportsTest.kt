/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import dev.asfakul.yumeiro.sports.SportsScores
import dev.asfakul.yumeiro.sports.SportsScores.Sport
import dev.asfakul.yumeiro.sports.SportsScores.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/**
 * B15: the Sports page reads real answers saved from ESPN and TheSportsDB on 2026-11-27/28
 * (app/src/test/resources/sports). Nothing here is made up: every expected value was read from those files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupB15SportsTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("sports/$name")!!.readText()
    private val day = LocalDate.of(2026, 11, 27)

    @Test fun espnFootball_liveFinishedAndUpcoming() {
        val b = SportsScores.parseEspn(res("espn_soccer.json"), Sport.FOOTBALL, day, 1L)
        assertEquals(SportsScores.Source.ESPN, b.source)
        val names = b.leagues.map { it.name }
        assertTrue(names.containsAll(listOf("English Premier League", "Coupe de France", "Women's International Friendly", "MLS")))
        // Leagues with something live come first.
        assertTrue(b.leagues.first().matches.any { it.state == State.LIVE })
        val epl = b.leagues.first { it.name == "English Premier League" }.matches.single()
        assertEquals(State.LIVE, epl.state)
        assertEquals("49'", epl.detail)
        assertEquals(listOf("Nottingham Forest", "Chelsea"), epl.sides.map { it.name }) // home first
        assertEquals(listOf("1", "0"), epl.sides.map { it.score })
        assertEquals(Instant.parse("2026-11-27T20:00:00Z"), epl.start)
        val cannes = b.leagues.first { it.name == "Coupe de France" }.matches.first { m -> m.sides.any { it.name == "AS Cannes" } }
        assertEquals(State.FINISHED, cannes.state); assertEquals("FT-Pens", cannes.detail)
        assertTrue(cannes.sides.first { it.name == "AS Cannes" }.winner)
        assertEquals(2, b.liveCount)
    }

    @Test fun espn_upcomingMatchesNeverShowAScore() {
        val b = SportsScores.parseEspn(res("espn_soccer.json"), Sport.FOOTBALL, day, 1L)
        val mls = b.leagues.first { it.name == "MLS" }.matches
        assertEquals(2, mls.size)
        assertTrue(mls.all { it.state == State.UPCOMING && it.sides.all { s -> s.score == null && !s.winner } })
        // Inside one league: live, then upcoming by time, then finished.
        val friendly = b.leagues.first { it.name == "Women's International Friendly" }.matches.map { it.state }
        assertEquals(listOf(State.LIVE, State.UPCOMING, State.UPCOMING, State.FINISHED, State.FINISHED, State.FINISHED), friendly)
        val pre = SportsScores.parseEspn(res("espn_soccer_pre.json"), Sport.FOOTBALL, day.plusDays(1), 1L)
        assertTrue(pre.leagues.isNotEmpty() && pre.liveCount == 0)
        assertTrue(pre.leagues.flatMap { it.matches }.all { m -> m.sides.all { it.score == null } })
    }

    @Test fun espnCricket_keepsRealInningsScores() {
        val b = SportsScores.parseEspn(res("espn_cricket.json"), Sport.CRICKET, day, 1L)
        val scores = b.leagues.flatMap { it.matches }.flatMap { it.sides }.mapNotNull { it.score }
        assertTrue("cricket scores read: $scores", scores.containsAll(listOf("73/5 (31 ov)", "603/9d", "198 & 105/5 (35 ov)", "495/7 (96.3 ov)")))
        assertTrue(b.liveCount >= 10)
        assertTrue(b.leagues.flatMap { it.matches }.any { it.note.isNotEmpty() })
    }

    @Test fun espnOtherSports_parseAndLinksStayOnEspn() {
        for ((file, sport) in listOf("espn_nba.json" to Sport.BASKETBALL, "espn_tennis.json" to Sport.TENNIS)) {
            val b = SportsScores.parseEspn(res(file), sport, day, 1L)
            val ms = b.leagues.flatMap { it.matches }
            assertTrue("$file has matches", ms.isNotEmpty())
            assertTrue(ms.all { it.sides.size == 2 && it.sides.all { s -> s.name.isNotBlank() } })
            ms.mapNotNull { it.link }.forEach { assertTrue(it, it.startsWith("https://") && it.substringAfter("https://").substringBefore('/').contains("espn")) }
            ms.flatMap { it.sides }.mapNotNull { it.logo }.forEach { assertTrue(it, it.startsWith("https://") && it.contains("espncdn.com")) }
        }
    }

    @Test fun hostileAnswer_isCleanedNotTrusted() {
        val evil = """{"sports":[{"leagues":[{"name":"L","events":[
            {"id":"1","status":"in","date":"2026-11-27T20:00Z","fullStatus":{"type":{"name":"STATUS_IN","shortDetail":"10'"}},
             "links":[{"href":"javascript:alert(1)"},{"href":"https://evil.example/espn.com"}],
             "competitors":[{"displayName":"A","score":"<b>9</b>","homeAway":"home","logo":"http://a.espncdn.com/x.png"},
                            {"displayName":"B","score":"2","homeAway":"away","logo":"https://evil.example/x.png"}]},
            {"id":"2","status":"weird","competitors":[{"displayName":"C"},{"displayName":"D"}]},
            {"id":"3","status":"post","competitors":[{"displayName":"OnlyOne","score":"1"}]}]}]}]}"""
        val b = SportsScores.parseEspn(evil, Sport.FOOTBALL, day, 1L)
        val m = b.leagues.single().matches.single()
        assertNull("markup is not a score", m.sides[0].score)
        assertEquals("2", m.sides[1].score)
        assertNull(m.link); assertTrue(m.sides.all { it.logo == null })
        assertEquals(Instant.parse("2026-11-27T20:00:00Z"), m.start)
    }

    @Test fun postponedIsOffNotFinished() {
        val j = """{"sports":[{"leagues":[{"name":"L","events":[{"id":"1","status":"post",
            "fullStatus":{"type":{"name":"STATUS_POSTPONED","description":"Postponed","shortDetail":"Postponed"}},
            "competitors":[{"displayName":"A","score":"0","homeAway":"home","winner":true},{"displayName":"B","score":"0","homeAway":"away"}]}]}]}]}"""
        val m = SportsScores.parseEspn(j, Sport.FOOTBALL, day, 1L).leagues.single().matches.single()
        assertEquals(State.OFF, m.state); assertEquals("Postponed", m.note)
        assertFalse(m.sides.any { it.winner })
    }

    @Test fun sportsDbFallback_readsTheFreeTier() {
        val b = SportsScores.parseSportsDb(res("sportsdb_soccer.json"), Sport.FOOTBALL, day.plusDays(1), 1L)
        assertEquals(SportsScores.Source.SPORTSDB, b.source)
        val ms = b.leagues.flatMap { it.matches }
        assertTrue(ms.isNotEmpty())
        assertTrue(ms.all { it.state == State.UPCOMING && it.sides.all { s -> s.score == null } })
        assertEquals(Instant.parse("2026-11-28T15:00:00Z"), ms.minOf { it.start!! })
        val done = """{"events":[{"idEvent":"9","strHomeTeam":"H","strAwayTeam":"A","strStatus":"FT","intHomeScore":"3","intAwayScore":"1","strLeague":"X","strTimestamp":"2026-11-27T15:00:00"}]}"""
        val m = SportsScores.parseSportsDb(done, Sport.FOOTBALL, day, 1L).leagues.single().matches.single()
        assertEquals(State.FINISHED, m.state); assertTrue(m.sides[0].winner); assertFalse(m.sides[1].winner)
        assertEquals("{\"events\":null}".let { SportsScores.parseSportsDb(it, Sport.FOOTBALL, day, 1L).empty }, true)
    }

    @Test fun refreshOnlyTodayWhileLive() {
        val live = SportsScores.parseEspn(res("espn_soccer.json"), Sport.FOOTBALL, day, 1L)
        assertEquals(30_000L, SportsScores.refreshEveryMs(live, day))
        assertNull(SportsScores.refreshEveryMs(live, day.plusDays(1)))
        assertNull(SportsScores.refreshEveryMs(SportsScores.parseEspn(res("espn_soccer_pre.json"), Sport.FOOTBALL, day, 1L), day))
        assertNull(SportsScores.refreshEveryMs(null, day))
        assertTrue(SportsScores.espnUrl(Sport.CRICKET, day).contains("sport=cricket&dates=20261127"))
        assertTrue(SportsScores.sportsDbUrl(Sport.HOCKEY, day).endsWith("d=2026-11-27&s=Ice%20Hockey"))
    }

    @Test fun sportsHasItsOwnRouteAndLightsItsOwnShortcut() {
        val nav = dev.asfakul.yumeiro.ui.navigation.NavActive
        val route = dev.asfakul.yumeiro.ui.navigation.Screen.Sports.route
        val st = dev.asfakul.yumeiro.ui.navigation.NavActive.State(route, "live", "device")
        assertTrue(nav.matches("shortcut:sports", st))
        assertFalse("Live TV is not lit on Sports", nav.matches("shortcut:live", st))
        assertEquals("shortcut:sports", nav.active(st, null, "videos", listOf("home", "videos", "shortcut:live", "shortcut:sports")))
        assertEquals(dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.SPORTS, dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.of("sports"))
        assertFalse(nav.isImmersive(route))
    }
}
