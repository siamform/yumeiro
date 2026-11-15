/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.ui.feed.FeedCardKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R16 Group 4 device checks for Yumix.
 *
 * [offlineRefreshKeepsTheFeed] (Y1, offline): pull to refresh on Yumix with the network off must keep the feed.
 * Before the fix the repository answered an empty list (every row failed), the screen took it as
 * a successful refresh, replaced each extension's section with nothing and reset "More to
 * explore", leaving an empty page. The emulator's Wi-Fi and mobile data are switched off with
 * `svc` (shell), and always switched back on in [restoreNetwork].
 *
 * [seeAllKeepsPagingWhileSearching] (Y6): a row's "See all" grid loads pages while the user scrolls
 * and a section search also asks for pages. Its fetch used to be cancelled by a second request,
 * which showed a false "Couldn't load more" and stopped paging.
 */
@RunWith(AndroidJUnit4::class)
class YumixDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(line: String) = Log.i("YuOffline", line)

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { ui.grantRuntimePermission(ctx.packageName, it) } }
        FirstRun.toTabs(rule)
        runCatching {
            runBlocking {
                kotlinx.coroutines.withTimeout(180_000) {
                    ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
                    if (ExtensionManager.provider("Internet Archive") == null) {
                        val remote = ExtensionSources.all(ctx, force = true).items.first { it.internalName == "InternetArchiveProvider" }
                        ExtensionManager.install(ctx, remote, dev.asfakul.yumeiro.online.ExtensionReview.Mode.AUTO)
                    }
                }
            }
        }.onFailure { log("extension setup failed: $it") }
    }

    @After fun restoreNetwork() {
        FirstRun.shell("svc wifi enable"); FirstRun.shell("svc data enable")
    }

    private fun idle(ms: Long) {
        val until = SystemClock.uptimeMillis() + ms
        runCatching { rule.waitUntil(ms + 5_000) { SystemClock.uptimeMillis() >= until } }
    }

    /**
     * Titles of every feed card composed inside the Yumix list right now. Only cards under
     * `online_home_list` count: run 43716649966 counted Home's cards while the tab was switching,
     * stopped waiting at once and then measured a Yumix page that was still loading (0 cards).
     */
    private fun cards(): Set<String> =
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(FeedCardKey), true).fetchSemanticsNodes()
            .filter { n -> generateSequence(n.parent) { it.parent }.any { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null } == "online_home_list" } }
            .mapNotNull { it.config.getOrElseNullable(FeedCardKey) { null } }.toSet()

    @Test fun offlineRefreshKeepsTheFeed() {
        FirstRun.tapVisible(rule, "nav_tab_home"); idle(600)
        FirstRun.tapVisible(rule, "quick_card_hues"); idle(600)
        val until = SystemClock.uptimeMillis() + 150_000
        while (SystemClock.uptimeMillis() < until && cards().size < 3) idle(3_000)
        idle(5_000)
        val before = cards()
        log("online: ${before.size} cards · ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
        org.junit.Assume.assumeTrue("Yumix showed only ${before.size} cards online (network?)", before.size >= 3)

        FirstRun.shell("svc wifi disable"); FirstRun.shell("svc data disable")
        idle(4_000)
        rule.onAllNodesWithTag("online_home_list", true).onFirst().performTouchInput { swipeDown(startY = top + 40f, endY = top + height * 0.6f) }
        // Offline the extensions fail fast (no DNS). Give every one of them time to answer, and
        // catch the "Couldn't refresh" message: it proves the refresh ran and every answer failed.
        var message: String? = null
        val end = SystemClock.uptimeMillis() + 45_000
        while (SystemClock.uptimeMillis() < end) {
            dev.asfakul.yumeiro.ui.components.AppMessages.current.value?.text?.takeIf { "refresh" in it }?.let { message = it }
            idle(500)
        }
        log("refresh message: $message")
        val after = cards()
        val kept = before.count { it in after }
        log("offline after refresh: ${after.size} cards, kept $kept of ${before.size}")
        FirstRun.shot("yumix_offline_refresh")
        assertTrue("offline refresh emptied Yumix: ${after.size} cards left of ${before.size} · ${FirstRun.onScreen(rule)}", after.size >= before.size / 2)
        assertTrue("the offline refresh never finished with its message (refresh not run?)", message != null)
        assertTrue("offline refresh replaced the cards on screen: kept $kept of ${before.size}", kept >= before.size * 3 / 4)

        FirstRun.shell("svc wifi enable"); FirstRun.shell("svc data enable")
        idle(10_000)
        log("network back: ${cards().size} cards")
    }

    @Test fun seeAllKeepsPagingWhileSearching() {
        FirstRun.tapVisible(rule, "nav_tab_home"); idle(600)
        FirstRun.tapVisible(rule, "quick_card_hues"); idle(600)
        val until = SystemClock.uptimeMillis() + 150_000
        while (SystemClock.uptimeMillis() < until && rule.onAllNodesWithTag("online_see_all", true).fetchSemanticsNodes().isEmpty()) idle(3_000)
        FirstRun.tapVisible(rule, "online_see_all", 30_000)
        runCatching { rule.waitUntil(60_000) { rule.onAllNodesWithTag("online_row_grid", true).fetchSemanticsNodes().isNotEmpty() } }
        org.junit.Assume.assumeTrue("See all showed no grid · ${FirstRun.onScreen(rule)}", rule.onAllNodesWithTag("online_row_grid", true).fetchSemanticsNodes().isNotEmpty())
        // A one-letter section search keeps loading pages (one at a time) next to the scroll trigger.
        rule.onAllNodesWithTag("online_row_search", true).onFirst().performTextInput("e")
        fun loaded(): Int? = rule.onAllNodesWithTag("online_row_search_count", true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString { it.text }
            ?.let { Regex("in (\\d+) loaded").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        fun hasText(t: String) = rule.onAllNodes(androidx.compose.ui.test.hasText(t, substring = true), true).fetchSemanticsNodes().isNotEmpty()
        idle(2_000)
        val first = loaded() ?: 0
        var errors = 0; var ended = false
        val stop = SystemClock.uptimeMillis() + 60_000
        while (SystemClock.uptimeMillis() < stop) {
            runCatching { rule.onAllNodesWithTag("online_row_grid", true).onFirst().performTouchInput { swipeUp() } }
            idle(1_000)
            if (hasText("Couldn't load more") || rule.onAllNodesWithTag("online_row_error", true).fetchSemanticsNodes().isNotEmpty()) errors++
            if (hasText("That's everything")) { ended = true; break }
        }
        val last = loaded() ?: 0
        log("see all: loaded $first → $last, error checks $errors, ended=$ended · ${android.os.Build.MODEL} API ${android.os.Build.VERSION.SDK_INT}")
        FirstRun.shot("yumix_see_all")
        org.junit.Assume.assumeTrue("this row had a single page ($first titles)", last > first || !ended)
        assertEquals("See all showed a load error while paging ($errors checks) · ${FirstRun.onScreen(rule)}", 0, errors)
        assertTrue("See all never loaded another page ($first → $last)", last > first)
    }
}
