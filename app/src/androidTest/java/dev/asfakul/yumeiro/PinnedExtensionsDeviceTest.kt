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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private fun back() { FirstRun.shell("input keyevent 4") }

    /** The soft keyboard is up (InputMethodManagerService's own flag, present on API 24 and 34). */
    private fun imeShown(): Boolean = FirstRun.shell("dumpsys input_method").lineSequence().any { "mInputShown=true" in it }

    /**
     * FINAL-39 (run 44666813073, tv-api34): on TV the sheet's first focus landed on its search field and
     * focus alone opened the full-screen keyboard (ImeTracker onShown with no key pressed); the first
     * Back then closed only the keyboard and the sheet stayed. On the TV layout the keyboard must stay
     * down until OK is pressed on the field.
     */
    private fun assertNoKeyboardOnTv(where: String) {
        if (!has("tv_nav_rail")) return
        Thread.sleep(2_000)
        val shown = imeShown()
        log("TV keyboard after $where: shown=$shown")
        assertTrue("TV: the keyboard opened by itself over the filter sheet ($where)", !shown)
    }

    /** Extension rows in the filter sheet, top to bottom (without "All"). */
    private fun sheetOrder(): List<String> = rule.onAllNodes(SemanticsMatcher("filter row") { n ->
        val t = n.config.getOrElseNullable(SemanticsProperties.TestTag) { null } ?: return@SemanticsMatcher false
        t.startsWith("online_filter_") && !t.startsWith("online_filter_pin_") &&
            t !in setOf("online_filter_all", "online_filter_sheet", "online_filter_clear", "online_filter_empty", "online_filter_search", "online_filter_search_clear", "online_filter_active", "online_filter_active_clear")
    }, true).fetchSemanticsNodes().sortedBy { it.positionInRoot.y }.map { it.config[SemanticsProperties.TestTag].removePrefix("online_filter_") }

    /** True if a node with [tag] lies fully inside the window. */
    private fun onWindow(tag: String): Boolean = runCatching {
        val root = rule.onAllNodes(androidx.compose.ui.test.isRoot(), true).fetchSemanticsNodes().maxByOrNull { it.size.height }!!
        rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().any { n ->
            val b = n.boundsInRoot
            b.height > 1f && b.top >= -1f && b.bottom <= root.size.height + 1f
        }
    }.getOrDefault(false)

    /**
     * Phones open the sheet half-height; with many sources the last row sits below the screen
     * (44376358282, 7 sources: tapVisible's fallback then scrolled the feed under the sheet, which
     * hides the pinned bar). A user drags the sheet up first; so does the test. TV opens it fully (G9-8).
     */
    private fun showInSheet(tag: String): Boolean {
        if (onWindow(tag)) return false
        log("'$tag' below the screen: dragging the sheet up")
        rule.onAllNodesWithTag("online_filter_sheet", true)[0].performTouchInput { swipeUp(startY = top + 80f, endY = top - 800f, durationMillis = 400) } // from the sheet top: its middle can be off-screen
        rule.waitForIdle()
        val ok = runCatching { rule.waitUntil(5_000) { onWindow(tag) }; true }.getOrDefault(false)
        assertTrue("'$tag' still below the screen after dragging the sheet up · ${FirstRun.onScreen(rule)}", ok)
        return true
    }

    private fun sheetTop(): Float? = rule.onAllNodesWithTag("online_filter_sheet", true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top

    /**
     * Back closes the sheet. After a drag to full height, Material 3 first settles an expanded sheet
     * that has a half-height state back to half height (ModalBottomSheet `settleToDismiss`:
     * `currentValue == Expanded && hasPartiallyExpandedState` → `partialExpand()`), and the next Back
     * closes it: diagnostic run 44386683931 logged top 444 → 1176 px after the first Back on api36,
     * gone after the second, the same on api33. The test checks both steps.
     */
    private fun closeSheet(dragged: Boolean) {
        if (dragged) {
            val top = sheetTop()
            back()
            val settled = runCatching { rule.waitUntil(5_000) { (sheetTop() ?: Float.MAX_VALUE) > (top ?: 0f) + 100f } ; true }.getOrDefault(false)
            log("back on the expanded sheet: top $top -> ${sheetTop()}")
            assertTrue("Back on the expanded sheet neither settled it to half height nor closed it (top $top -> ${sheetTop()})", settled)
        }
        if (has("online_filter_sheet")) back()
        waitGone("online_filter_sheet")
    }

    private fun openYumix() {
        tap("nav_tab_home"); tap("quick_card_hues") // Home's Yumix shortcut
        waitTag("online_filter", 120_000) // shown once two or more sources are on
    }

    @Test fun pinMovesFirstShowsAChipFiltersAndPersists() {
        openYumix()
        tap("online_filter"); waitTag("online_filter_sheet")
        assertNoKeyboardOnTv("opening the sheet")
        val before = sheetOrder()
        log("sheet before: $before")
        assertTrue("need two sources in the sheet: $before", before.size >= 2)
        val target = before.last()

        val dragged = showInSheet("online_filter_pin_$target")
        tap("online_filter_pin_$target")
        rule.waitUntil(5_000) { PinnedExtensions.pins.value == listOf(target) }
        val after = sheetOrder()
        log("sheet after pin: $after")
        assertEquals("the pinned source must move to the top", target, after.first())
        assertEquals("the other sources keep their order", before - target, after.drop(1))

        closeSheet(dragged)
        waitTag("online_pinned_bar"); waitTag("online_pinned_$target")

        // The chip narrows the feed to that one source; a second tap goes back to all.
        FirstRun.clickTag(rule, "online_pinned_$target"); waitTag("online_filter_active", 20_000) // TV: OK, not touch
        FirstRun.clickTag(rule, "online_pinned_$target"); waitGone("online_filter_active", 20_000)

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
