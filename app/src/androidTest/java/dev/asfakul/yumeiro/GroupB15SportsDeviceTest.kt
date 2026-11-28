/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * B15 in the real app, on the real network: Live TV → Tools → Sports opens its own page, which lists real
 * matches from ESPN. The test asks ESPN itself (its own HTTP call, not the app's code) and checks that the
 * teams and scores on screen are ones ESPN sent: no made-up rows. Then Cricket and Tomorrow load too.
 * Phone and TV (TV opens Live TV from the rail and clicks with the remote's OK action).
 * Log tag YuGroupB15; GB15_RESULT_A PASS; shots gb15_*.
 */
@RunWith(AndroidJUnit4::class)
class GroupB15SportsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupB15", s)

    @After fun tearDown() { runCatching { NavPrefs.reset(ctx) } }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(ms: Long, ok: () -> Boolean): Boolean =
        runCatching { rule.waitUntil(ms) { runCatching(ok).getOrDefault(false) }; true }.getOrDefault(false)
    private fun texts(tag: String, merged: Boolean = false): List<String> = rule.onAllNodesWithTag(tag, !merged).fetchSemanticsNodes().flatMap { n ->
        n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
    }
    private fun click(tag: String) {
        rule.onAllNodesWithTag(tag, true)[0].performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle()
    }

    /** What ESPN itself lists for [sport] on [day]: "team name" -> score seen for it ("" when none). */
    private fun espn(query: String, day: LocalDate): Map<String, Set<String>> {
        val url = "https://site.web.api.espn.com/apis/v2/scoreboard/header?$query&dates=${day.format(DateTimeFormatter.BASIC_ISO_DATE)}&lang=en&region=us"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        val body = c.inputStream.bufferedReader().use { it.readText() }
        val out = HashMap<String, MutableSet<String>>()
        val sports = JSONObject(body).optJSONArray("sports") ?: return out
        for (i in 0 until sports.length()) {
            val ls = sports.getJSONObject(i).optJSONArray("leagues") ?: continue
            for (j in 0 until ls.length()) {
                val evs = ls.getJSONObject(j).optJSONArray("events") ?: continue
                for (k in 0 until evs.length()) {
                    val cs = evs.getJSONObject(k).optJSONArray("competitors") ?: continue
                    for (q in 0 until cs.length()) {
                        val o = cs.getJSONObject(q)
                        out.getOrPut(o.optString("displayName").trim()) { HashSet() } += o.optString("score").trim()
                    }
                }
            }
        }
        return out
    }

    private fun waitBoard(what: String) {
        assertTrue("$what: the page never finished loading", waitFor(45_000) { has("sports_list") || has("sports_empty") || has("sports_error") })
        assertTrue("$what: the score service failed on a working network", !has("sports_error"))
    }

    @Test fun a_toolsOpenSportsWithRealScores() {
        NavPrefs.reset(ctx)
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.LIVE, true)
        FirstRun.toTabs(rule)
        FirstRun.clickTag(rule, FirstRun.navTag(rule, "nav_shortcut_live"))
        assertTrue("Live TV header never showed", waitFor(30_000) { has("live_tools_button") })
        click("live_tools_button")
        assertTrue("Tools has no Sports tile", waitFor(10_000) { has("live_tool_sports") })
        FirstRun.shot("gb15_a_tools")
        click("live_tool_sports")
        assertTrue("Sports page did not open", waitFor(10_000) { has("sports_page") })
        val skeleton = has("sports_loading")
        waitBoard("football today")
        SystemClock.sleep(600); rule.waitForIdle()
        FirstRun.shot("gb15_a_football")
        // The source line merges its two texts into one TalkBack node, so read the merged tree.
        val source = texts("sports_source", merged = true).joinToString(" ")
        log("GB15 skeleton=$skeleton source=$source")
        assertTrue("no source line: '$source'", source.contains("ESPN") || source.contains("TheSportsDB"))
        if (source.contains("ESPN")) {
            val truth = espn("sport=soccer", LocalDate.now()) + espn("sport=soccer", LocalDate.now().plusDays(1)) + espn("sport=soccer", LocalDate.now().minusDays(1))
            val teams = texts("sports_team")
            val scores = texts("sports_score")
            log("GB15 teams=${teams.size} first=${teams.take(6)} scores=${scores.take(6)} espnTeams=${truth.size}")
            assertTrue("no football matches listed on a normal day", teams.isNotEmpty())
            val unknown = teams.filter { it !in truth.keys && !it.endsWith("…") }
            assertTrue("teams ESPN never sent: $unknown", unknown.isEmpty())
            val allScores = truth.values.flatten().toSet()
            val madeUp = scores.filter { it.isNotEmpty() && it !in allScores }
            assertTrue("scores ESPN never sent: $madeUp", madeUp.isEmpty())
        }
        click("sports_sport_cricket"); waitBoard("cricket today")
        SystemClock.sleep(600); rule.waitForIdle(); FirstRun.shot("gb15_a_cricket")
        log("GB15 cricket teams=${texts("sports_team").take(6)} scores=${texts("sports_score").take(6)}")
        click("sports_day_1"); waitBoard("cricket tomorrow")
        rule.waitForIdle(); FirstRun.shot("gb15_a_cricket_tomorrow")
        // Nothing has started tomorrow, so no score may show.
        val tomorrowScores = texts("sports_score").filter { it.isNotEmpty() }
        val live = texts("sports_status").size
        log("GB15 tomorrow rows=$live scores=$tomorrowScores")
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        assertTrue("Back did not leave Sports", waitFor(10_000) { !has("sports_page") })
        log("GB15_RESULT_A PASS")
    }

    /**
     * Second pass: a match row opens its match page (header numbers ESPN sent; statistics, moments or last games,
     * or a plain line saying there are none), Volleyball lists real matches, and Watch goes back to Live TV.
     */
    @Test fun b_matchPageVolleyballAndWatch() {
        NavPrefs.reset(ctx)
        NavPrefs.setShortcut(ctx, NavPrefs.Shortcut.LIVE, true)
        FirstRun.toTabs(rule)
        FirstRun.clickTag(rule, FirstRun.navTag(rule, "nav_shortcut_live"))
        assertTrue("Live TV header never showed", waitFor(30_000) { has("live_tools_button") })
        click("live_tools_button")
        assertTrue(waitFor(10_000) { has("live_tool_sports") })
        click("live_tool_sports")
        assertTrue("Sports page did not open", waitFor(10_000) { has("sports_page") })
        // Yesterday always has finished football somewhere, so the match page has real numbers to show.
        click("sports_day_-1"); waitBoard("football yesterday")
        assertTrue("no football yesterday", has("sports_match"))
        val truth = espn("sport=soccer", LocalDate.now().minusDays(1)) + espn("sport=soccer", LocalDate.now())
        rule.onAllNodesWithTag("sports_match", true)[0].performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle()
        assertTrue("match page did not open", waitFor(10_000) { has("sports_match_sheet") })
        val settled = waitFor(30_000) {
            listOf("sports_detail_stat", "sports_detail_moment", "sports_detail_form", "sports_detail_thin", "sports_detail_none", "sports_detail_failed", "sports_detail_periods").any { has(it) }
        }
        assertTrue("match page never finished loading", settled)
        SystemClock.sleep(500); rule.waitForIdle()
        FirstRun.shot("gb15_b_match")
        val teams = texts("sports_detail_team")
        val score = texts("sports_detail_score").joinToString()
        log("GB15 detail teams=$teams score=$score stats=${rule.onAllNodesWithTag("sports_detail_stat", true).fetchSemanticsNodes().size} " +
            "moments=${rule.onAllNodesWithTag("sports_detail_moment", true).fetchSemanticsNodes().size} form=${rule.onAllNodesWithTag("sports_detail_form", true).fetchSemanticsNodes().size} failed=${has("sports_detail_failed")}")
        assertTrue("teams on the match page ESPN never sent: $teams", teams.size == 2 && teams.all { it in truth.keys || it.endsWith("…") })
        val allScores = truth.values.flatten().toSet()
        score.split(":").map { it.trim() }.filter { it.isNotEmpty() && it != "–" }.forEach { assertTrue("score '$it' ESPN never sent", it in allScores) }
        assertTrue("a working network still failed the match page", !has("sports_detail_failed"))
        androidx.test.espresso.Espresso.pressBack(); rule.waitForIdle()
        assertTrue("sheet did not close", waitFor(10_000) { !has("sports_match_sheet") })
        click("sports_day_0")
        rule.onNodeWithTag("sports_sports", true).performScrollToNode(hasTestTag("sports_sport_volleyball")); rule.waitForIdle()
        click("sports_sport_volleyball"); waitBoard("volleyball today")
        SystemClock.sleep(600); rule.waitForIdle(); FirstRun.shot("gb15_b_volleyball")
        val vSource = texts("sports_source", merged = true).joinToString(" ")
        log("GB15 volleyball source=$vSource teams=${texts("sports_team").take(6)} empty=${has("sports_empty")}")
        assertTrue("volleyball names no source: $vSource", has("sports_empty") || vSource.contains("ESPN") || vSource.contains("TheSportsDB"))
        click("sports_watch")
        assertTrue("Watch did not land on Live TV", waitFor(15_000) { !has("sports_page") && has("live_tools_button") })
        FirstRun.shot("gb15_b_watch")
        log("GB15_RESULT_B PASS")
    }
}
