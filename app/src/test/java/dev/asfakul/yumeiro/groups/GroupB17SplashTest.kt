/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groups

import androidx.compose.ui.test.junit4.createComposeRule
import dev.asfakul.yumeiro.ui.components.LocalReducedMotion
import dev.asfakul.yumeiro.ui.components.YuMarkFrame
import dev.asfakul.yumeiro.ui.screens.splash.SplashMotion
import dev.asfakul.yumeiro.ui.screens.splash.SplashProbe
import dev.asfakul.yumeiro.ui.screens.splash.SplashScreen
import dev.asfakul.yumeiro.ui.screens.splash.SplashTiming
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * B17: the splash is at least 1.5 s, dreamy (wash, stars, rising mark, staggered words), drawn from one clock
 * without recomposing per frame (60 fps), still under reduced motion, and finishes once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class GroupB17SplashTest {
    @get:Rule val rule = createComposeRule()

    @Test fun onScreenAtLeastOneAndAHalfSeconds() {
        for (sdk in listOf(24, 30, 31, 36)) for (reduced in listOf(false, true)) {
            assertTrue("sdk=$sdk reduced=$reduced", SplashTiming.hold(reduced, sdk) >= 1500L)
            assertTrue(SplashTiming.onScreen(reduced, sdk) > SplashTiming.hold(reduced, sdk))
        }
        // The motion finishes before the splash starts to leave, so nothing is cut mid-move.
        assertTrue(SplashTiming.ANIM_MS <= SplashTiming.hold(false, 36))
    }

    @Test fun android12StartsFromTheSystemMarkWithoutAJump() {
        val f0 = SplashMotion.at(0f, fromSystem = true)
        assertEquals(1f, f0.mark.moon, 0f); assertEquals(1f, f0.mark.play, 0f)
        assertEquals(0f, f0.markRiseDp, 0.001f)
        assertEquals(1f, f0.mark.playScale, 0.001f)
    }

    @Test fun everyLayerMovesAndSettles() {
        for (fromSystem in listOf(false, true)) {
            val a = SplashMotion.at(0f, fromSystem); val m = SplashMotion.at(0.5f, fromSystem); val z = SplashMotion.at(1f, fromSystem)
            assertEquals(0f, a.wash, 0.001f); assertTrue(m.wash > 0f); assertEquals(0.6f, z.wash, 0.001f)
            assertEquals(0f, a.title, 0.001f); assertEquals(1f, z.title, 0.001f)
            assertEquals(1f, z.tagline, 0.001f); assertEquals(1f, z.byline, 0.001f)
            // Staggered: the title leads the tagline, the tagline leads the by-line.
            val s = SplashMotion.at(0.45f, fromSystem)
            assertTrue("$s", s.title >= s.tagline && s.tagline >= s.byline)
            assertEquals(SplashMotion.RISE_DP, z.markRiseDp, 0.001f)
            // After the first bloom the glow breathes once (peak in the second half), then fades with the exit.
            val peak = (50..95).maxOf { SplashMotion.at(it / 100f, fromSystem).mark.glow }
            assertTrue("glow breathes after the bloom ($peak)", peak > 0.35f)
            assertEquals(1f, z.mark.moon, 0f); assertEquals(1f, z.mark.play, 0f)
        }
        val starsMid = SplashMotion.stars.count { SplashMotion.starAlpha(it, 0.4f) > 0.3f }
        assertTrue("stars twinkle ($starsMid)", starsMid >= 3)
        assertTrue(SplashMotion.stars.size == 18)
        // Stars stay off the mark in the centre.
        assertTrue(SplashMotion.stars.none { val dx = it.x - 0.5f; val dy = it.y - 0.45f; dx * dx + dy * dy < 0.045f })
    }

    /** B17 r27: the two logo parts move differently, and both start and end at rest (no jump from the system splash). */
    @Test fun crescentOrbitsAndTriangleePushesDifferently() {
        for (fromSystem in listOf(false, true)) {
            val f = { t: Float -> SplashMotion.at(t, fromSystem).mark }
            assertEquals(0f, f(1f).moonTurn, 0.001f); assertEquals(0f, f(1f).playDx, 0.001f)
            if (fromSystem) { assertEquals(0f, f(0f).moonTurn, 0.001f); assertEquals(0f, f(0f).playDx, 0.001f) }
            val turn = (1..99).maxOf { kotlin.math.abs(f(it / 100f).moonTurn) }
            val push = (1..99).maxOf { f(it / 100f).playDx }
            assertTrue("crescent turns ($turn deg)", turn >= 10f)
            assertTrue("triangle pushes ($push)", push >= 3f)
            // Different motion: the turn and the push peak at different moments.
            val turnPeak = (1..99).maxBy { kotlin.math.abs(f(it / 100f).moonTurn) }
            val pushPeak = (1..99).maxBy { f(it / 100f).playDx }
            assertTrue("peaks at $turnPeak and $pushPeak", kotlin.math.abs(turnPeak - pushPeak) >= 10)
        }
    }

    /** B17 r27 fail-before (run 46078131306, api36): the logo was still on screen after the splash left. */
    @Test fun theLeaveRemovesTheLogoBeforeTheHandOver() {
        val settled = SplashMotion.at(1f, fromSystem = true)
        assertEquals(settled, SplashMotion.leave(settled, 0f))
        val mid = SplashMotion.leave(settled, 0.5f)
        assertTrue(mid.markScale > 1f && mid.mark.alpha in 0.01f..0.99f)
        for (p in listOf(0.7f, 0.85f, 1f)) {
            val l = SplashMotion.leave(settled, p)
            assertEquals("logo gone at $p", 0f, l.mark.alpha, 0.001f)
            assertEquals(0f, l.title, 0.001f); assertEquals(0f, l.sky, 0.001f)
        }
        assertTrue(SplashTiming.ANIM_MS + SplashTiming.LEAVE_MS <= SplashTiming.hold(false, 36))
        assertTrue(SplashTiming.ANIM_MS + SplashTiming.LEAVE_MS >= 1500)
    }

    @Test fun reducedMotionIsStill() {
        assertEquals(YuMarkFrame.Full, SplashMotion.Still.mark)
        assertEquals(0f, SplashMotion.Still.markRiseDp, 0f)
        assertEquals(1f, SplashMotion.Still.title, 0f)
    }

    @Test fun noRecompositionPerFrameAndFinishesOnce() {
        var done = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { MTPlayerTheme { SplashScreen(onSplashFinished = { done++ }) } }
        rule.mainClock.advanceTimeBy(32)
        val after = SplashProbe.compositions
        rule.mainClock.advanceTimeBy(SplashTiming.ANIM_MS.toLong())
        assertTrue("recomposed while animating: ${SplashProbe.compositions - after} times", SplashProbe.compositions - after <= 1)
        rule.mainClock.advanceTimeBy(SplashTiming.hold(false) - SplashTiming.ANIM_MS - 100)
        assertEquals(0, done)
        rule.mainClock.advanceTimeBy(300)
        assertEquals(1, done)
    }

    @Test fun reducedMotionStillFinishesAfterTheHold() {
        var done = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { MTPlayerTheme { CompositionLocalProvider(LocalReducedMotion provides true) { SplashScreen(onSplashFinished = { done++ }) } } }
        rule.mainClock.advanceTimeBy(SplashTiming.HOLD_REDUCED_MS - 100)
        assertEquals(0, done)
        rule.mainClock.advanceTimeBy(300)
        assertEquals(1, done)
    }

    /** B17 fail-before: the motion started with the first frame, while Home's first composition blocked the main thread. */
    @Test fun motionWaitsForACalmMainThread() {
        val ms = 1_000_000L
        assertFalse(SplashTiming.calm(emptyList()))
        assertFalse("one slow frame in the last three", SplashTiming.calm(listOf(16 * ms, 16 * ms, 400 * ms)))
        assertFalse("too few frames", SplashTiming.calm(listOf(16 * ms, 16 * ms)))
        assertTrue(SplashTiming.calm(listOf(900 * ms, 16 * ms, 17 * ms, 16 * ms)))
        assertTrue("cap keeps the splash from waiting forever", SplashTiming.SETTLE_MAX_MS in 500L..1500L)
    }
}
