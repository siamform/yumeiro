/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.EpisodeStatus
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.OnboardingGate
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.online.YumeiroDrama
import dev.asfakul.yumeiro.ui.screens.online.OnlineDetailScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * NF-1 device check: the real detail page of a real series (built-in drama source, live network) labels
 * episode rows Watched, "N min left" and Fresh from the app's own stores. The test writes progress through
 * [OnlineHistory.record] (the player's path) and the last-visit count through the `episode_seen` file, shows
 * [OnlineDetailScreen], switches to the list layout and reads the labels the user sees.
 */
@RunWith(AndroidJUnit4::class)
class Nf1EpisodeStatusDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("NF1", s)

    private fun siteUp(): Boolean = runCatching {
        okhttp3.OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(20)).build()
            .newCall(okhttp3.Request.Builder().url("https://onshort.net/").header("User-Agent", com.lagradost.cloudstream3.USER_AGENT).build())
            .execute().use { it.code < 500 }
    }.getOrDefault(false)

    @Test fun rowsSayWatchedMinutesLeftAndFresh() {
        assumeTrue("onshort.net is unreachable from this machine", siteUp())
        OnboardingGate.release()
        ExtensionManager.start(ctx)
        val deadline = System.currentTimeMillis() + 60_000
        var api: com.lagradost.cloudstream3.MainAPI? = null
        while (api == null && System.currentTimeMillis() < deadline) { api = ExtensionManager.provider(YumeiroDrama.NAME); if (api == null) Thread.sleep(200) }
        assertTrue("built-in drama source not published", api != null)

        // A series whose first season has 3+ numbered episodes.
        val rows = runBlocking { OnlineRepository.home(api!!, force = true) }
        var detail: OnlineDetail? = null
        val tried = ArrayList<String>()
        for (item in rows.flatMap { it.items }.distinctBy { it.url }.take(8)) {
            val d = runCatching { runBlocking { OnlineRepository.load(api!!, item.url) } }.getOrNull()
            val n = d?.let { dd -> OnlineSession.labelledEpisodes(dd, OnlineSession.defaultVersion(dd.versions)).count { it.first.episode != null } } ?: -1
            tried += "${item.name}=$n"
            if (d != null && n >= 3) { detail = d; break }
        }
        log("candidates: $tried")
        assertTrue("no series with 3 numbered episodes: $tried", detail != null)
        val d = detail!!
        val version = OnlineSession.defaultVersion(d.versions)
        val season = d.seasons(version).firstOrNull()
        val eps = OnlineSession.labelledEpisodes(d, version).map { it.first }
            .filter { it.episode != null && (season == null || it.season == season.number) }
        assertTrue("first season has fewer than 3 numbered episodes", eps.size >= 3)
        val (e1, e2, e3) = Triple(eps[0], eps[1], eps[2])

        // Progress through the player's own path: e1 finished, e2 at 4.5 of 10 min (6 min left, rounded up).
        dev.asfakul.yumeiro.security.Privacy.incognito.value.let { assumeTrue("incognito on", !it) }
        OnlineHistory.record(ctx, d, version, e1, 600_000L, 600_000L)
        OnlineHistory.record(ctx, d, version, e2, 270_000L, 600_000L)
        // Last visit saw up to just before e3: e3 is Fresh (index = ordinal - 1 >= seenBefore).
        val seen = EpisodeStatus.ordinal(e3)!! - 1
        ctx.getSharedPreferences("episode_seen", Context.MODE_PRIVATE).edit()
            .putString("${d.api}|${d.url}|$version", "$seen,${System.currentTimeMillis()}").commit()
        log("title ${d.name} version '$version' e1=${e1.episode} e2=${e2.episode} e3=${e3.episode} seen=$seen")

        rule.setContent { MTPlayerTheme { OnlineDetailScreen(d.api, d.url, onBack = {}, onOpenTitle = {}, onOpenExtensions = {}) } }
        rule.waitUntil(60_000) { rule.onAllNodesWithTag("online_episode_layout", true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("online_detail_list", true)[0].performScrollToNode(hasTestTag("online_episode_layout"))
        rule.onAllNodesWithTag("online_episode_layout", true)[0].performClick()
        // Bring the first episode row to the top of the list (header, poster and plot come first).
        val list = rule.onAllNodesWithTag("online_detail_list", true)[0]
        list.performScrollToNode(hasTestTag("online_episode"))
        rule.waitForIdle()
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val epTop = rule.onAllNodesWithTag("online_episode", true).fetchSemanticsNodes().minOf { it.boundsInRoot.top }
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, epTop - listTop) }
        rule.waitForIdle()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("online_episode_status", true).fetchSemanticsNodes().size >= 3 }
        val labels = rule.onAllNodesWithTag("online_episode_status", true).fetchSemanticsNodes()
            .sortedBy { it.positionInRoot.y }
            .map { n -> n.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString { it.text } }
        log("NF1_RESULT labels=$labels")
        assertTrue("first row is not Watched: $labels", labels.getOrNull(0) == "Watched")
        assertTrue("second row is not '6 min left': $labels", labels.getOrNull(1) == "6 min left")
        assertTrue("third row is not Fresh: $labels", labels.getOrNull(2) == "Fresh")
        // The visit raised the stored count to the newest episode, so the next visit has no Fresh rows for these.
        rule.waitUntil(5_000) { (EpisodeStatus.Seen.get(ctx, d.api, d.url, version) ?: 0) >= EpisodeStatus.highest(eps)!! }
        log("NF1_RESULT PASS")
    }
}
