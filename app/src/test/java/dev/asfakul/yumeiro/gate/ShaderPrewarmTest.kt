/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.gate

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.components.ShaderPrewarm
import dev.asfakul.yumeiro.ui.components.ShaderPrewarmHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GATE-1-RENDER: the first-use GPU programs (gate trace 44949137965: Skia shader_compile 100–230 ms per
 * draw type inside the Source-menu and Yumix taps) compile in idle frames a short while after start:
 * nothing is drawn at start, every step is drawn in its own frames, and the canvas leaves afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ShaderPrewarmTest {
    @get:Rule val rule = createComposeRule()

    @Test fun drawsEveryStepAfterTheDelayThenLeaves() {
        ShaderPrewarm.stepsDrawn = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { ShaderPrewarmHost(true, startDelayMs = 1_000) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(ShaderPrewarm.TAG).assertDoesNotExist() // start frames stay free
        rule.mainClock.advanceTimeBy(600)
        rule.waitUntil(5_000) { rule.mainClock.advanceTimeByFrame(); ShaderPrewarm.stepsDrawn >= 1 }
        rule.onNodeWithTag(ShaderPrewarm.TAG).assertExists()
        rule.waitUntil(5_000) { rule.mainClock.advanceTimeBy(200); ShaderPrewarm.stepsDrawn == ShaderPrewarm.STEPS }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(ShaderPrewarm.TAG).assertDoesNotExist()
        assertEquals(ShaderPrewarm.STEPS, ShaderPrewarm.stepsDrawn)
    }

    @Test fun offDrawsNothing() {
        ShaderPrewarm.stepsDrawn = 0
        rule.mainClock.autoAdvance = false
        rule.setContent { ShaderPrewarmHost(false, startDelayMs = 0) }
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithTag(ShaderPrewarm.TAG).assertDoesNotExist()
        assertEquals(0, ShaderPrewarm.stepsDrawn)
    }

    @Test fun flagIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.SHADER_PREWARM.default)
    }
}
