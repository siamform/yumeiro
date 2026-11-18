/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.PlayCounts
import dev.asfakul.yumeiro.online.OnlineSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NBUG-11 (owner, 2026-11-18): 18+ off, yet adult titles stay in Home → "Most played".
 * Home decided "18+" only from the extension that is loaded right now, once, when Home first
 * composed — before the extensions finish loading after a cold start, and never for an extension
 * that is turned off or removed. Seeded play counts (prefs "play_counts", the app's own format):
 *  - A: an online title saved with the 18+ mark ("ad": true) from an extension that is not loaded;
 *  - B: an online title saved before the mark existed, from an extension that is not loaded
 *    (18+ unknown: with 18+ hidden it must stay out, as Continue watching does);
 *  - C: an online title saved as not 18+; D: a YouTube video — both must show (controls).
 * Case a: 18+ off → A and B gone, C and D shown. Case b: 18+ on and not private → all four shown.
 * Log tag YuN11Test, N11_CASE / N11_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class Nbug11MostPlayedAdultDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val adultBefore = OnlineSettings.adult.value
    private val privateBefore = OnlineSettings.adultPrivate.value
    private fun log(s: String) = Log.i("YuN11Test", s)

    private val a = "ONLINE:YuN11 Adult Hub|https://n11.invalid/a"
    private val b = "ONLINE:YuN11 Gone Hub|https://n11.invalid/b"
    private val c = "ONLINE:YuN11 Safe Hub|https://n11.invalid/c"
    private val d = "YOUTUBE:yuN11tube"

    private fun online(api: String, path: String, title: String, count: Int, adult: Boolean?) = JSONObject().apply {
        put("k", "ONLINE"); put("id", "$api|https://n11.invalid/$path"); put("t", title); put("s", api)
        put("c", count); put("at", System.currentTimeMillis()); put("api", api); put("url", "https://n11.invalid/$path")
        if (adult != null) put("ad", adult)
    }

    private fun seed() {
        val arr = JSONArray()
            .put(online("YuN11 Adult Hub", "a", "YuN11 Adult A", 9_000, true))
            .put(online("YuN11 Gone Hub", "b", "YuN11 Legacy B", 8_000, null))
            .put(online("YuN11 Safe Hub", "c", "YuN11 Safe C", 7_000, false))
            .put(JSONObject().apply { put("k", "YOUTUBE"); put("id", "yuN11tube"); put("t", "YuN11 Tube D"); put("s", "ch"); put("c", 6_000); put("at", System.currentTimeMillis()) })
        ctx.getSharedPreferences("play_counts", Context.MODE_PRIVATE).edit().putString("entries", arr.toString()).commit()
        PlayCounts.resetForTests(); PlayCounts.ensureLoaded(ctx)
    }

    private fun shown(key: String) = runCatching {
        rule.onAllNodesWithTag("home_most_$key", true).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    /** Scrolls Home to the Most played row and returns which seeded cards it shows. */
    private fun visible(): Map<String, Boolean> {
        val ok = runCatching { rule.waitUntil(30_000) {
            runCatching { rule.onNodeWithTag("home_list", true).performScrollToKey("most_row") }.isSuccess &&
                rule.onAllNodesWithTag("home_most_played", true).fetchSemanticsNodes().isNotEmpty()
        }; true }.getOrDefault(false)
        assertTrue("no Most played row on Home · ${FirstRun.onScreen(rule)}", ok)
        rule.waitForIdle(); Thread.sleep(800); rule.waitForIdle()
        return listOf(a, b, c, d).associateWith { shown(it) }
    }

    @Test fun a_adultOffHidesAdultAndUnknownTitles() {
        FirstRun.toTabs(rule)
        OnlineSettings.setAdult(ctx, false); OnlineSettings.setAdultPrivate(ctx, true)
        seed()
        // The controls must appear first, so a missing A/B is not just a slow row.
        runCatching { rule.waitUntil(30_000) { runCatching { rule.onNodeWithTag("home_list", true).performScrollToKey("most_row") }.isSuccess && shown(c) && shown(d) } }
        val v = visible(); FirstRun.shot("n11_a")
        log("N11_CASE a 18+ off: A(adult mark)=${v[a]} B(legacy, extension not loaded)=${v[b]} C(safe)=${v[c]} D(YouTube)=${v[d]}")
        val problems = buildList {
            if (v[a] == true) add("A: a title saved as 18+ is shown with 18+ off")
            if (v[b] == true) add("B: an online title of unknown 18+ status (extension not loaded) is shown with 18+ off")
            if (v[c] != true) add("C: a normal online title is missing")
            if (v[d] != true) add("D: a YouTube video is missing")
        }
        log("N11_RESULT a problems=${problems.size} $problems")
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    @Test fun b_adultOnAndPublicShowsEverything() {
        FirstRun.toTabs(rule)
        OnlineSettings.setAdult(ctx, true); OnlineSettings.setAdultPrivate(ctx, false)
        seed()
        runCatching { rule.waitUntil(30_000) { runCatching { rule.onNodeWithTag("home_list", true).performScrollToKey("most_row") }.isSuccess && shown(a) && shown(d) } }
        val v = visible(); FirstRun.shot("n11_b")
        log("N11_CASE b 18+ on, not private: A=${v[a]} B=${v[b]} C=${v[c]} D=${v[d]}")
        val missing = v.filterValues { !it }.keys
        log("N11_RESULT b missing=$missing")
        assertTrue("with 18+ on and not private every seeded title must show; missing $missing", missing.isEmpty())
    }

    @After fun tearDown() {
        OnlineSettings.setAdult(ctx, adultBefore); OnlineSettings.setAdultPrivate(ctx, privateBefore)
        PlayCounts.clear(ctx); PlayCounts.resetForTests()
    }
}
