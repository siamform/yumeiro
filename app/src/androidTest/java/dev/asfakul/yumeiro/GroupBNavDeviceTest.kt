/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.ui.components.LocalReducedMotion
import dev.asfakul.yumeiro.ui.components.MTBottomBar
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Group B / B1 on the device (phone and TV): the floating pill bar's accent capsule must slide from
 * the old tab to the new one (positions in between on the way), land centred under the new tab,
 * follow D-pad / keyboard selection, and jump with reduced motion. Frame times of the slide are
 * logged (tag YuGroupB) as a profile; software GL frame counts are not asserted.
 *
 * Before B1 there was no single capsule: each tab faded its own pill in place. The test then
 * measures that pill ("nav_active_pill"), which never passes through the tabs in between.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class GroupBNavDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var route by mutableStateOf(bottomNavItems[0].route)

    private fun show(reduced: Boolean) {
        rule.setContent {
            MTPlayerTheme(Palettes.Dark) {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    MTBottomBar(currentRoute = route, onNavigateToRoute = { route = it })
                }
            }
        }
        rule.waitForIdle()
    }

    /** The capsule; before B1 the selected tab's own pill. */
    private fun indicatorCentre(): Float {
        val capsule = rule.onAllNodesWithTag("nav_capsule", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        val node = rule.onNodeWithTag(if (capsule) "nav_capsule" else "nav_active_pill", useUnmergedTree = true)
        val b = node.getUnclippedBoundsInRoot()
        return ((b.left + b.right) / 2).value
    }

    private fun tabCentre(i: Int): Float {
        val b = rule.onNodeWithTag(bottomNavItems[i].testTag).getUnclippedBoundsInRoot()
        return ((b.left + b.right) / 2).value
    }

    private fun slideTo(target: Int, act: () -> Unit): List<Float> {
        rule.mainClock.autoAdvance = false
        act()
        val seen = mutableListOf<Float>()
        val frameNs = mutableListOf<Long>()
        repeat(36) {
            val t0 = System.nanoTime()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            frameNs += System.nanoTime() - t0
            seen += indicatorCentre()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val ms = frameNs.map { it / 1e6 }
        Log.i(TAG, "slide to $target: positions=${seen.map { it.toInt() }} frameMs avg=${"%.1f".format(ms.average())} max=${"%.1f".format(ms.max())}")
        return seen
    }

    @Test fun a_capsuleSlidesAndLandsUnderTheTappedTab() {
        show(reduced = false)
        val start = indicatorCentre()
        assertEquals("starts under the first tab", tabCentre(0), start, 2f)
        val target = bottomNavItems.lastIndex
        val seen = slideTo(target) { rule.onNodeWithTag(bottomNavItems[target].testTag).performClick() }
        val end = tabCentre(target)
        val between = seen.filter { it > start + 6f && it < end - 6f }.map { it.toInt() }.distinct()
        Log.i(TAG, "a: start=$start end=$end between=$between")
        assertTrue("indicator passes through the tabs on the way (slide, not a jump): $seen", between.size >= 3)
        assertEquals("lands centred under the tapped tab", end, indicatorCentre(), 2f)
        assertEquals(bottomNavItems[target].route, route)
        Log.i(TAG, "GROUPB_NAV_A PASS")
    }

    @Test fun b_dpadSelectionSlidesTheCapsuleToo() {
        // A D-pad / keyboard press takes a phone out of touch mode; do the same before focusing.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        show(reduced = false)
        rule.onNodeWithTag(bottomNavItems[1].testTag).requestFocus()
        rule.waitForIdle()
        val seen = slideTo(1) { rule.onNodeWithTag(bottomNavItems[1].testTag).performKeyInput { pressKey(Key.DirectionCenter) } }
        val start = tabCentre(0); val end = tabCentre(1)
        val between = seen.filter { it > start + 4f && it < end - 4f }.map { it.toInt() }.distinct()
        Log.i(TAG, "b: start=$start end=$end between=$between route=$route")
        assertEquals("D-pad centre opens the tab", bottomNavItems[1].route, route)
        assertTrue("indicator slides on D-pad selection: $seen", between.isNotEmpty())
        assertEquals(end, indicatorCentre(), 2f)
        Log.i(TAG, "GROUPB_NAV_B PASS")
    }

    @Test fun c_reducedMotionJumps() {
        show(reduced = true)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(bottomNavItems[2].testTag).performClick()
        val trail = mutableListOf<Float>()
        repeat(4) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle(); trail += indicatorCentre() }
        val at = trail.last()
        rule.mainClock.autoAdvance = true
        Log.i(TAG, "c: trail=$trail want=${tabCentre(2)}")
        assertEquals("reduced motion: already under the new tab four frames later (the spring needs ~18)", tabCentre(2), at, 2f)
        Log.i(TAG, "GROUPB_NAV_C PASS")
    }

    companion object { const val TAG = "YuGroupB" }
}

