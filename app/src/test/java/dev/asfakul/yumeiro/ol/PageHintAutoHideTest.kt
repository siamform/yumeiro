/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.ui.screens.stream.PageFinderHint
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OL-8: the website helper banner hides by itself after 3 s; a tap still hides it at once. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PageHintAutoHideTest {
    @get:Rule val rule = createComposeRule()

    private fun show() = rule.setContent {
        MTPlayerTheme {
            var shown by remember { mutableStateOf(true) }
            if (shown) PageFinderHint("Browsing Example. Press play on any video.", onHide = { shown = false })
        }
    }

    @Test fun hidesByItselfAfterThreeSeconds() {
        rule.mainClock.autoAdvance = false
        show()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("page_finder_hint").assertExists()
        rule.mainClock.advanceTimeBy(2_800)
        rule.onNodeWithTag("page_finder_hint").assertExists()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("page_finder_hint").assertDoesNotExist()
    }

    @Test fun tapStillHidesAtOnce() {
        show()
        rule.onNodeWithTag("page_finder_hint").performClick()
        rule.onNodeWithTag("page_finder_hint").assertDoesNotExist()
    }
}
