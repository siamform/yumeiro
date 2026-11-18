/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-1 follow-through (eighth cycle 4.1): after a process death, every place that resumes a Yumix
 * title must show it again: Home → Jump back in (`home_continue_video`), Yumix → Continue watching
 * (`online_continue`) and the Yumix resume banner (`online_hero_resume`).
 *
 * Phase a (`-e phase a`): installs Internet Archive (as YumixDeviceTest), takes the first real title
 * of its home feed and records 40 % progress through [OnlineHistory.record], the call the player makes
 * every few seconds. Then the process kills itself with no lifecycle callback.
 * Phase b (`-e phase b`, a new process): checks the three surfaces show that title.
 * Resume position itself (the player seeking there) is ResumeAcrossProcessDeviceTest / ResumePointsTest.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class Bug1YumixRestoreDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val phase: String? get() = InstrumentationRegistry.getArguments().getString("phase")
    private val prefs get() = ctx.getSharedPreferences("bug1_yumix_test", Context.MODE_PRIVATE)

    private fun log(s: String) = Log.i("YuBug1Yumix", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("bug1y_fail_${what.replace(' ', '_')}")
        assertTrue("$what (after ${ms}ms) · ${FirstRun.onScreen(rule)}", ok)
    }

    private fun ensureArchive() = runBlocking {
        kotlinx.coroutines.withTimeout(180_000) {
            ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
            if (ExtensionManager.provider(ARCHIVE) == null) {
                val remote = ExtensionSources.all(ctx, force = true).items.first { it.internalName == "InternetArchiveProvider" }
                ExtensionManager.install(ctx, remote, dev.asfakul.yumeiro.online.ExtensionReview.Mode.AUTO)
            }
        }
    }

    @Test fun a_recordThenDie() {
        assumeTrue("phase a runs from scripts/device-check.sh", phase == "a")
        FirstRun.toTabs(rule)
        ensureArchive()
        val api = ExtensionManager.provider(ARCHIVE)
        assertTrue("Internet Archive not installed: ${ExtensionManager.providers().map { it.name }}", api != null)
        val item = runBlocking { OnlineRepository.home(api!!) }.flatMap { it.items }.firstOrNull()
        assertTrue("Internet Archive home feed is empty (network?)", item != null)
        item!!
        val detail = OnlineDetail(
            api = item.api, url = item.url, name = item.name, kind = OnlineKind.MOVIE, type = item.type ?: "Movie",
            posterUrl = item.posterUrl, backgroundUrl = null, posterHeaders = item.posterHeaders, plot = null, year = null,
            tags = emptyList(), durationMin = null, score = null, contentRating = null, playData = item.url,
            episodes = emptyMap(), seasonNames = emptyMap(), recommendations = emptyList(),
        )
        OnlineHistory.ensureLoaded(ctx)
        OnlineHistory.record(ctx, detail, "", null, 240_000, 600_000)
        prefs.edit().putString("name", item.name).putString("url", item.url).commit()
        // The history write runs on its own thread, as in the app; give it the time the system would.
        Thread.sleep(2_000)
        log("BUG1Y_A recorded name='${item.name}' api=${item.api} pos=240000/600000 pid=${android.os.Process.myPid()}; killing now")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun b_newProcessShowsItEverywhere() {
        assumeTrue("phase b runs from scripts/device-check.sh", phase == "b")
        val name = prefs.getString("name", null)
        assertTrue("phase a left no title", !name.isNullOrBlank())
        val short = name!!.take(18)
        try {
            FirstRun.toTabs(rule)
            OnlineHistory.ensureLoaded(ctx)
            val e = OnlineHistory.entries.value.firstOrNull { it.name == name }
            log("BUG1Y_B history entry=${e?.let { "${it.positionMs}/${it.durationMs}" }} pid=${android.os.Process.myPid()}")
            assertTrue("history lost the title across the process death", e != null && e.positionMs == 240_000L)
            ensureArchive()

            wait("Home tab") { FirstRun.hasNav(rule, "nav_tab_home") } // TV: tv_nav_tab_home on the rail
            FirstRun.tapVisible(rule, "nav_tab_home")
            // TV (tv-api34, 44343884669): the Continue row is below the fold of the lazy Home list and is
            // not composed until scrolled to; the phone shows it at once.
            wait("Home → Jump back in shows the Yumix title", 30_000) {
                runCatching { rule.onAllNodesWithTag("home_list", true)[0].performScrollToNode(hasTestTag("home_continue_video")) }
                has("home_continue_video") && hasText(short)
            }
            FirstRun.shot("bug1y_home_jump_in")
            log("BUG1Y_B home jump-in PASS")

            FirstRun.tapVisible(rule, "quick_card_hues")
            wait("Yumix resume banner", 90_000) { has("online_hero_resume") }
            FirstRun.shot("bug1y_yumix_banner")
            log("BUG1Y_B yumix banner PASS")
            // The row sits below the banner in a lazy list; bring it into view first.
            repeat(6) {
                if (has("online_continue")) return@repeat
                runCatching { rule.onAllNodesWithTag("online_home_list", true)[0].performScrollToNode(hasTestTag("online_continue")) }
                rule.waitForIdle()
            }
            wait("Yumix Continue watching row with the title", 60_000) { has("online_continue") && hasText(short) }
            FirstRun.shot("bug1y_yumix_continue")
            log("BUG1Y_B yumix continue PASS")
            log("BUG1Y_RESULT PASS name='$name'")
        } finally {
            prefs.getString("url", null)?.let { u -> runCatching { OnlineHistory.remove(ctx, ARCHIVE, u); OnlineHistory.flush() } }
            prefs.edit().clear().commit()
        }
    }

    companion object { const val ARCHIVE = "Internet Archive" }
}
