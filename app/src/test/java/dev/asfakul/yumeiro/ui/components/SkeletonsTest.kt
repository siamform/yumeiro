/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import dev.asfakul.yumeiro.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SKELETON-2 (owner list 32): skeletons don't flash for cached content and stay still with reduced motion. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SkeletonsTest {
    @get:Rule val rule = createComposeRule()

    private fun centre(): Color {
        val px = rule.onNodeWithTag("host").captureToImage().toPixelMap()
        return px[px.width / 2, px.height / 2]
    }

    private fun show(reduced: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MTPlayerTheme(palette = Palettes.Dark) {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    Box(Modifier.size(120.dp).background(Color.Black).testTag("host")) { SkeletonBlock(120.dp, 120.dp) }
                }
            }
        }
    }

    @Test fun invisibleForTheFirstMomentSoCachedContentNeverFlashesIt() {
        show(reduced = false)
        rule.mainClock.advanceTimeByFrame()
        assertEquals("nothing drawn before the appear delay", Color.Black, centre())
        rule.mainClock.advanceTimeBy(Skeletons.APPEAR_DELAY_MS + Skeletons.FADE_MS + 100L)
        assertNotEquals("the placeholder is visible once loading takes a while", Color.Black, centre())
    }

    @Test fun reducedMotionIsAStillBlock() {
        show(reduced = true)
        rule.mainClock.advanceTimeBy(Skeletons.APPEAR_DELAY_MS + 50L)
        val a = centre()
        assertNotEquals(Color.Black, a)
        rule.mainClock.advanceTimeBy(Skeletons.SWEEP_MS / 2L)
        assertEquals("no sweep with reduced motion", a, centre())
    }

    @Test fun gridsShowAPageFirstAndOneShortRowWhenAddingMore() {
        assertEquals(9, skeletonCells(hasItems = false))
        assertEquals(3, skeletonCells(hasItems = true))
    }
}
