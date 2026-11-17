/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.asfakul.yumeiro.ui.components.AfterFirstFrame
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ST-18: overlay hosts wrapped in [AfterFirstFrame] are not part of the first composition (the
 * first Home frame) and appear on the next frame, with any request made before still pending.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AfterFirstFrameTest {
    @get:Rule val rule = createComposeRule()

    @Test fun contentWaitsForTheSecondFrame() {
        var outer = 0
        var inner = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SideEffect { outer++ }
            AfterFirstFrame { SideEffect { inner++ } }
        }
        assertTrue("the first composition ran", outer > 0)
        assertEquals("deferred content was composed in the first frame", 0, inner)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertTrue("deferred content never appeared", inner > 0)
    }

    @Test fun aRequestMadeBeforeIsShownWhenTheHostAppears() {
        val pending = MutableStateFlow<String?>(null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AfterFirstFrame {
                val p by pending.collectAsState()
                p?.let { Text(it) }
            }
        }
        pending.value = "Checking your browser"
        assertEquals(0, rule.onAllNodesWithText("Checking your browser").fetchSemanticsNodes().size)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText("Checking your browser").fetchSemanticsNodes().size)
    }
}
