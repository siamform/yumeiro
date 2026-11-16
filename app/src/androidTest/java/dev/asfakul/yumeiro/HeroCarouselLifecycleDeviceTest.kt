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
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                HeroCarousel(listOf(slide("A"), slide("B"), slide("C")), "hero_lc_test") {}
            }
        }
        rule.mainClock.advanceTimeBy(500)
        assertTrue("slide A must be the first page", onFirstSlide())
        rule.mainClock.advanceTimeBy(20_000)
        val stillWhileStopped = onFirstSlide()
        Log.i("YuHeroLc", "HERO_LC_CASE created 20 s: on A=$stillWhileStopped")
        assertTrue("below STARTED the carousel must not advance (20 s, period 6 s)", stillWhileStopped)

        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.mainClock.advanceTimeBy(8_000)
        rule.mainClock.advanceTimeBy(1_000)
        val stillWhenStarted = onFirstSlide()
        Log.i("YuHeroLc", "HERO_LC_CASE resumed 9 s: on A=$stillWhenStarted")
        assertFalse("once started the carousel must advance within one period", stillWhenStarted)

        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.mainClock.advanceTimeBy(2_000)
        Log.i("YuHeroLc", "HERO_LC_RESULT PASS")
    }
}
