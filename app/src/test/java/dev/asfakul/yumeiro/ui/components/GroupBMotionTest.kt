/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import dev.asfakul.yumeiro.ui.navigation.bottomNavItems
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Group B (B1 floating pill nav, B2 entrances, B3 loader).
 * B1: one capsule slides between tabs (intermediate positions on the way), lands centred under the
 * new tab, jumps with reduced motion, and a tab change gives exactly one haptic tick.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
class GroupBMotionTest {
    @get:Rule val rule = createComposeRule()

    private class Haptics : HapticFeedback {
        val ticks = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { ticks += hapticFeedbackType }
    }

    private fun capsuleCentre(): Dp {
        val b = rule.onNodeWithTag("nav_capsule", useUnmergedTree = true).getUnclippedBoundsInRoot()
        return (b.left + b.right) / 2
    }

    private fun tabCentre(i: Int): Dp {
        val b = rule.onNodeWithTag(bottomNavItems[i].testTag).getUnclippedBoundsInRoot()
        return (b.left + b.right) / 2
    }

    private fun setBar(reduced: Boolean, haptics: Haptics, route: () -> String, onRoute: (String) -> Unit) {
        rule.setContent {
            MTPlayerTheme {
                CompositionLocalProvider(LocalReducedMotion provides reduced, LocalHapticFeedback provides haptics) {
                    MTBottomBar(currentRoute = route(), onNavigateToRoute = onRoute)
                }
            }
        }
    }

    @Test fun capsuleSlidesToTheNewTabAndTicksOnce() {
        val haptics = Haptics()
        var route by mutableStateOf(bottomNavItems[0].route)
        setBar(false, haptics, { route }) { route = it }
        rule.waitForIdle()
        val start = capsuleCentre()
        assertEquals("capsule starts under the first tab", tabCentre(0).value, start.value, 1f)

        val target = bottomNavItems.lastIndex
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(bottomNavItems[target].testTag).performClick()
        val seen = mutableListOf<Float>()
        repeat(30) { rule.mainClock.advanceTimeByFrame(); seen += capsuleCentre().value }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        val end = tabCentre(target).value
        val between = seen.filter { it > start.value + 4f && it < end - 4f }.distinct()
        assertTrue("capsule passes through the tabs on the way (slide, not a fade): $seen", between.size >= 3)
        assertEquals("capsule lands centred under the new tab", end, capsuleCentre().value, 1f)
        assertEquals("one haptic tick per tab change", 1, haptics.ticks.size)

        rule.onNodeWithTag(bottomNavItems[target].testTag).performClick()
        rule.waitForIdle()
        assertEquals("tapping the open tab gives no tick", 1, haptics.ticks.size)
    }

    @Test fun reducedMotionJumpsTheCapsule() {
        val haptics = Haptics()
        var route by mutableStateOf(bottomNavItems[0].route)
        setBar(true, haptics, { route }) { route = it }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(bottomNavItems[2].testTag).performClick()
        val trail = mutableListOf<Float>()
        repeat(4) { rule.mainClock.advanceTimeByFrame(); trail += capsuleCentre().value }
        assertEquals("reduced motion: the capsule is there within 4 frames (the spring needs ~18): $trail", tabCentre(2).value, trail.last(), 1f)
        assertTrue("no positions in between: $trail", trail.none { it > trail.first() + 4f && it < tabCentre(2).value - 4f })
        rule.mainClock.autoAdvance = true
    }

    @Test fun entranceStaggersABurstOncePerCard() {
        Entrance.resetForTest()
        assertTrue(Entrance.firstTime("a"))
        assertTrue("a card animates only the first time", !Entrance.firstTime("a"))
        val d = (0 until 12).map { Entrance.nextDelay(1_000L + it) }
        assertEquals("first card of a burst has no delay", 0, d[0])
        assertEquals("each card waits one step more", Entrance.STEP_MS, d[1] - d[0])
        assertEquals("stagger is capped", Entrance.MAX_STEPS * Entrance.STEP_MS, d.last())
        assertEquals("a card after a pause starts a new burst", 0, Entrance.nextDelay(1_000L + 12 + Entrance.BURST_MS + 1))
        assertTrue("off with reduced motion", !Entrance.enabled(reduced = true, lowRam = false))
        assertTrue("off on low-RAM phones", !Entrance.enabled(reduced = false, lowRam = true))
    }

    @Test fun loaderIsTheBrandMarkAndAnnouncesBusy() {
        rule.setContent { MTPlayerTheme { YuLoader(label = "Loading") } }
        rule.onNodeWithTag("yu_loader").assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        assertTrue("loader stands still with reduced motion", !YuLoaderSpec.animates(reduced = true))
    }
}
