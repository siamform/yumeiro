/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.gate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.components.NextFramePage
import dev.asfakul.yumeiro.ui.components.NextFramePages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GATE-1-VIDEOS-LOWRAM: a tab page opened after start composes its content one frame after the page,
 * so the pager's jump frame does not also compose the whole tab (794 ms on api33-lowram, run 44937485728).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class NextFramePageTest {
    @get:Rule val rule = createComposeRule()

    @Test fun contentWaitsOneFrame() {
        rule.mainClock.autoAdvance = false
        rule.setContent { NextFramePage(true) { Box(Modifier.size(10.dp).testTag("tab_content")) } }
        rule.onNodeWithTag("tab_content").assertDoesNotExist()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("tab_content").assertExists()
    }

    @Test fun offComposesAtOnce() {
        rule.mainClock.autoAdvance = false
        rule.setContent { NextFramePage(false) { Box(Modifier.size(10.dp).testTag("tab_content")) } }
        rule.onNodeWithTag("tab_content").assertExists()
    }

    @Test fun firstPageAfterStartIsNotDelayedLaterOnesAre() {
        val pages = NextFramePages(true)
        assertEquals(listOf(false, true, true, true), List(4) { pages.deferNext() })
        val off = NextFramePages(false)
        assertEquals(listOf(false, false, false), List(3) { off.deferNext() })
    }

    @Test fun flagIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.TAB_NEXT_FRAME.default)
    }

    /** NAV-1: the page shown again under a closing page draws its content in the first frame. */
    @Test fun pageShownAgainAfterAPopDoesNotWait() {
        val pages = NextFramePages(true)
        assertEquals(listOf(false, true), List(2) { pages.deferNext() })
        pages.restoring() // Home entry composed again (a sub page was closed)
        assertEquals(listOf(false, true), List(2) { pages.deferNext() })
    }

    /**
     * NAV-1 regression (CI 45138713013, AppNavigationCrashTest.playlistOpensOnItsOwnPageAndBackReturns): a page first
     * composed deferred and composed again without waiting must get its saved state back.
     */
    @Test fun savedStateSurvivesDeferredThenImmediate() {
        var shown by androidx.compose.runtime.mutableStateOf(true)
        var defer by androidx.compose.runtime.mutableStateOf(true)
        var seen = ""
        var open = true // the user opens the Playlists pane once, on the first showing only
        rule.setContent {
            val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            if (shown) holder.SaveableStateProvider("page") {
                NextFramePage(defer) {
                    var pane by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("hub") }
                    val now = pane // read in composition, so a change recomposes and the SideEffect sees it
                    androidx.compose.runtime.SideEffect { seen = now }
                    androidx.compose.runtime.LaunchedEffect(Unit) { if (open) { open = false; pane = "playlists" } }
                }
            }
        }
        rule.waitForIdle()
        assertEquals("playlists", seen)
        shown = false; rule.waitForIdle()
        defer = false; shown = true; rule.waitForIdle()
        assertEquals("playlists", seen)
    }
}
