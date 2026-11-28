/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B11 in the real app (MainActivity, phone): for EVERY tab on the bar, Videos included, and with pinned shortcuts
 * (6 items, the narrowest tabs), the one accent capsule must sit under the selected tab and hold its filled icon and
 * its whole label; exactly one filled icon is shown. The old check (GroupLNavDeviceTest) only measured Music on a
 * bar drawn alone. Log tag YuGroupB11; GB11_RESULT_A/B PASS; shots gb11_<tab>.
 */
@RunWith(AndroidJUnit4::class)
class GroupB11NavDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun log(s: String) = Log.i("YuGroupB11", s)

    @After fun tearDown() { runCatching { NavPrefs.reset(ctx) } }

    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag, true).getUnclippedBoundsInRoot()
    private fun inside(o: DpRect, c: DpRect) =
        o.top >= c.top - 0.5.dp && o.bottom <= c.bottom + 0.5.dp && o.left >= c.left - 0.5.dp && o.right <= c.right + 0.5.dp

    private fun checkEveryTab(label: String) {
        val items = NavPrefs.barItems(NavPrefs.config.value)
        log("GB11_$label items=${items.map { it.testTag }}")
        val bad = mutableListOf<String>()
        items.forEach { item ->
            // A shortcut opens its own page; come back to a main tab first so the bar is on screen.
            if (!FirstRun.hasNav(rule, item.testTag)) { FirstRun.clickTag(rule, items[0].testTag); rule.waitForIdle() }
            rule.onNodeWithTag(item.testTag, true).performClick()
            rule.waitForIdle(); SystemClock.sleep(900); rule.waitForIdle()
            if (rule.onAllNodesWithTag("mt_bottom_navigation_bar", true).fetchSemanticsNodes().isEmpty()) {
                log("GB11_$label ${item.testTag}: page without the bar (immersive), skipped"); return@forEach
            }
            val cap = bounds("nav_capsule")
            val tab = bounds(item.testTag)
            val filled = rule.onAllNodesWithTag("nav_icon_filled", true).fetchSemanticsNodes()
            val icon = if (filled.size == 1) bounds("nav_icon_filled") else null
            val text = bounds("nav_label_${item.testTag}")
            val centred = kotlin.math.abs(((cap.left + cap.right) / 2 - (tab.left + tab.right) / 2).value) <= 2f
            log("GB11_$label ${item.testTag} cap=$cap tab=$tab icon=$icon label=$text filled=${filled.size} centred=$centred")
            FirstRun.shot("gb11_${label.lowercase()}_${item.testTag}")
            if (filled.size != 1) bad += "${item.testTag}: ${filled.size} filled icons"
            if (icon != null && !inside(icon, cap)) bad += "${item.testTag}: icon outside capsule"
            if (!inside(text, cap)) bad += "${item.testTag}: label outside capsule ($text vs $cap)"
            if (!centred) bad += "${item.testTag}: capsule not under the tab"
            if (item.testTag == "nav_tab_videos" && filled.size == 1) {
                // The lit Videos glyph is a filled screen with the play triangle cut out: the cut-out must
                // show (it closed to a dot when the stroke ran round its inside).
                val px = rule.onNodeWithTag("nav_icon_filled", true).captureToImage().toPixelMap()
                fun lum(x: Float, y: Float): Float = px[(x * px.width).toInt().coerceIn(0, px.width - 1), (y * px.height).toInt().coerceIn(0, px.height - 1)]
                    .let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue }
                val solid = lum(5f / 24f, 12f / 24f)
                // The whole triangle must be open, not only its centre: the old stroke left a pinhole there
                // (fail-before 46068811587 passed on hole=0.91 at the centre). Points near its three corners.
                val holes = listOf(10.4f to 10f, 10.4f to 14f, 14.4f to 12f, 11.8f to 12f).map { (x, y) -> lum(x / 24f, y / 24f) }
                log("GB11_$label videos glyph solid=$solid holes=$holes size=${px.width}x${px.height}")
                if (holes.any { kotlin.math.abs(it - solid) < 0.25f }) bad += "videos: play cut-out not visible (solid=$solid holes=$holes)"
            }
        }
        assertTrue("B11 $label: ${bad.joinToString("; ")}", bad.isEmpty())
    }

    @Test fun a_everyMainTabIncludingVideos() {
        assumeFalse("phone bar only (TV uses the side rail)", Edition.isTv)
        NavPrefs.reset(ctx)
        FirstRun.toTabs(rule)
        checkEveryTab("A")
        log("GB11_RESULT_A PASS")
    }

    @Test fun b_sixItemsWithShortcuts() {
        assumeFalse("phone bar only (TV uses the side rail)", Edition.isTv)
        NavPrefs.reset(ctx)
        NavPrefs.Shortcut.offered().take(2).forEach { NavPrefs.setShortcut(ctx, it, true) }
        FirstRun.toTabs(rule)
        checkEveryTab("B")
        log("GB11_RESULT_B PASS")
    }
}
