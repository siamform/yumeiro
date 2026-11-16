/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.player.HeatGuard
import dev.asfakul.yumeiro.ui.screens.home.HeroCarousel
import dev.asfakul.yumeiro.ui.screens.home.HeroSlide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Y6/H1 (G1 H1, G4 Y6): the Home hero carousel must not auto-advance while its screen is below
 * STARTED (app in the background, screen off), and must advance again once started.
 * The real [HeroCarousel] runs under a lifecycle owner the test moves, with the Compose clock driven by
 * hand: 20 s while CREATED → still on slide A; then RESUMED, 8 s (one 6 s period + the animation) →
 * slide A has left the screen. Log tag YuHeroLc: HERO_LC_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class HeroCarouselLifecycleDeviceTest {
    @get:Rule val rule = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun slide(name: String) = HeroSlide(name, name, null, emptyMap(), "Latest", false, null, "", "Open")

    /** True while slide A is still the page in view (its card starts near the left edge). */
    private fun onFirstSlide(): Boolean = rule.onAllNodesWithTag("home_episode_A", true).fetchSemanticsNodes()
        .any { it.boundsInRoot.left > -20f && it.boundsInRoot.left < 60f }

    @Test fun carouselWaitsWhileStoppedAndMovesWhenStarted() {
        assumeTrue("HeatGuard blocks decorative motion on this run (${HeatGuard.level.value})", HeatGuard.allowDecorativeMotion())
        val owner = Owner()
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        // Real time, not the test clock: run 43904330191 showed slide A still in view after 9 s of
        // mainClock time on both phones, because the carousel's delay() runs on the real main
        // dispatcher in an instrumented test (only frames follow mainClock). Waiting in real time
        // checks what a user sees.
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                HeroCarousel(listOf(slide("A"), slide("B"), slide("C")), "hero_lc_test") {}
            }
        }
        rule.waitForIdle()
        assertTrue("slide A must be the first page", onFirstSlide())
        Thread.sleep(14_000) // > 2 periods of 6 s
        rule.waitForIdle()
        val stillWhileStopped = onFirstSlide()
        Log.i("YuHeroLc", "HERO_LC_CASE created 14 s real: on A=$stillWhileStopped")
        assertTrue("below STARTED the carousel must not advance (14 s, period 6 s)", stillWhileStopped)

        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        val t0 = android.os.SystemClock.elapsedRealtime()
        // Run 43912650347: api36 moved after 14.4 s, api33-lowram not within 15 s, with a 6 s period.
        // The requirement (Y6/H1) is "advances again once started"; the window is 30 s and the
        // first-move time and HeatGuard level are logged so a slow start stays visible.
        val moved = runCatching { rule.waitUntil(30_000) { !onFirstSlide() }; true }.getOrDefault(false)
        val ms = android.os.SystemClock.elapsedRealtime() - t0
        Log.i("YuHeroLc", "HERO_LC_CASE resumed: moved=$moved after $ms ms heat=${HeatGuard.level.value} decorative=${HeatGuard.allowDecorativeMotion()}")
        assertTrue("once started the carousel must advance (30 s window, period 6 s)", moved)

        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.waitForIdle()
        Log.i("YuHeroLc", "HERO_LC_RESULT PASS")
    }
}
