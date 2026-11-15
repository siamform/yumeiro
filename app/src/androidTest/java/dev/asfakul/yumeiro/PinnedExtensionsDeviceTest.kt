/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionSources
import dev.asfakul.yumeiro.online.PinnedExtensions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R16 Part 1: pinned (favourite) extensions on a real (emulated) device, through the real UI.
 * Two real sources are on (Internet Archive, installed from the market, and the built-in drama
 * source). The test pins the one listed last in Yumix → filter, and checks: it moves to the top
 * of the sheet, a one-tap chip appears beside the feed, the chip narrows the feed to that source
 * and a second tap widens it again, the pin survives the activity being destroyed and recreated
 * and is written to storage, and unpinning removes the chip.
 */
@RunWith(AndroidJUnit4::class)
class PinnedExtensionsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun log(s: String) = Log.i("YuPinTest", s)

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching { ui.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
        runBlocking {
            kotlinx.coroutines.withTimeout(180_000) {
                ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
                if (ExtensionManager.provider("Internet Archive") == null) {
                    val remote = ExtensionSources.all(ctx, force = true).items.first { it.internalName == "InternetArchiveProvider" }
                    ExtensionManager.install(ctx, remote, dev.asfakul.yumeiro.online.ExtensionReview.Mode.AUTO)
                }
            }
        }
        // Start clean: no pins left from an earlier run.
        rule.runOnUiThread { PinnedExtensions.pins.value.forEach { PinnedExtensions.toggle(ctx, it) } }
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 30_000) {
        val ok = runCatching { rule.waitUntil(ms) { has(tag) }; true }.getOrDefault(false)
        assertTrue("no '$tag' after ${ms}ms · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun waitGone(tag: String, ms: Long = 10_000) {
        val ok = runCatching { rule.waitUntil(ms) { !has(tag) }; true }.getOrDefault(false)
        assertTrue("'$tag' still shown after ${ms}ms", ok)
    }
    private fun tap(tag: String) { waitTag(tag); rule.onAllNodesWithTag(tag, true)[0].performClick(); rule.waitForIdle() }
    private fun back() { InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close() }

    /** Extension rows in the filter sheet, top to bottom (without "All"). */
    private fun sheetOrder(): List<String> = rule.onAllNodes(SemanticsMatcher("filter row") { n ->
        val t = n.config.getOrElseNullable(SemanticsProperties.TestTag) { null } ?: return@SemanticsMatcher false
        t.startsWith("online_filter_") && !t.startsWith("online_filter_pin_") &&
            t !in setOf("online_filter_all", "online_filter_sheet", "online_filter_clear", "online_filter_empty", "online_filter_search_clear", "online_filter_active", "online_filter_active_clear")
    }, true).fetchSemanticsNodes().sortedBy { it.positionInRoot.y }.map { it.config[SemanticsProperties.TestTag].removePrefix("online_filter_") }

    private fun openYumix() {
        tap("nav_tab_videos")
        tap("video_source_switch"); tap("video_source_online")
        waitTag("online_filter", 120_000) // shown once two or more sources are on
    }

    @Test fun pinMovesFirstShowsAChipFiltersAndPersists() {
        openYumix()
        tap("online_filter"); waitTag("online_filter_sheet")
        val before = sheetOrder()
        log("sheet before: $before")
        assertTrue("need two sources in the sheet: $before", before.size >= 2)
        val target = before.last()

        tap("online_filter_pin_$target")
        rule.waitUntil(5_000) { PinnedExtensions.pins.value == listOf(target) }
        val after = sheetOrder()
        log("sheet after pin: $after")
        assertEquals("the pinned source must move to the top", target, after.first())
        assertEquals("the other sources keep their order", before - target, after.drop(1))

        back(); waitGone("online_filter_sheet")
        waitTag("online_pinned_bar"); waitTag("online_pinned_$target")

        // The chip narrows the feed to that one source; a second tap goes back to all.
        tap("online_pinned_$target"); waitTag("online_filter_active", 20_000)
        tap("online_pinned_$target"); waitGone("online_filter_active", 20_000)

        // Written to storage (not only kept in memory).
        rule.waitUntil(5_000) {
            ctx.getSharedPreferences("pinned_extensions", Context.MODE_PRIVATE).getString("pinned", null) == target
        }

        // Survives the activity being destroyed and recreated.
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        if (!has("online_pinned_$target")) openYumix()
        waitTag("online_pinned_$target", 60_000)
        log("pin survived recreate")

        // Unpin: the chip bar goes away.
        tap("online_filter"); waitTag("online_filter_sheet")
        tap("online_filter_pin_$target")
        rule.waitUntil(5_000) { PinnedExtensions.pins.value.isEmpty() }
        back(); waitGone("online_filter_sheet")
        waitGone("online_pinned_bar")
        log("PIN_RESULT PASS target=$target sources=$before")
    }
}
