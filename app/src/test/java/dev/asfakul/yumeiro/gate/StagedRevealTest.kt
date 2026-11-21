/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.gate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.components.StagedList
import dev.asfakul.yumeiro.ui.components.StagedReveal
import dev.asfakul.yumeiro.ui.components.rememberStagedCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GATE-1-RENDER-2: opening Yumix composed the hero and every visible shelf in one layout pass (855 ms on
 * api33-lowram, gate run 44950383602). The feed now opens with one section and adds one per frame.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StagedRevealTest {
    @get:Rule val rule = createComposeRule()

    private fun list(enabled: Boolean, startIndex: Int = 0) = rule.setContent {
        val state = rememberLazyListState(startIndex)
        val staged = rememberStagedCount(enabled, state)
        LazyColumn(state = state) {
            val st = StagedList(this, staged.intValue)
            st.item("hero") { Box(Modifier.height(20.dp).testTag("s0")) }
            st.items(listOf(1, 2), key = { it }) { i -> Box(Modifier.height(20.dp).testTag("s$i")) }
            st.item("s3") { Box(Modifier.height(20.dp).testTag("s3")) }
            st.item("s4") { Box(Modifier.height(20.dp).testTag("s4")) }
        }
    }

    @Test fun firstFrameHasOneSectionThenOneMorePerFrame() {
        rule.mainClock.autoAdvance = false
        list(true)
        rule.onNodeWithTag("s0").assertExists()
        rule.onNodeWithTag("s1").assertDoesNotExist()
        rule.onNodeWithTag("s3").assertDoesNotExist()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("s1").assertExists() // the items() call is one section
        rule.onNodeWithTag("s2").assertExists()
        rule.onNodeWithTag("s4").assertDoesNotExist()
        repeat(6) { rule.mainClock.advanceTimeByFrame() }
        rule.onNodeWithTag("s3").assertExists()
        rule.onNodeWithTag("s4").assertExists()
    }

    @Test fun offShowsEverySectionAtOnce() {
        rule.mainClock.autoAdvance = false
        list(false)
        rule.onNodeWithTag("s4").assertExists()
    }

    @Test fun aListReopenedScrolledDownShowsEverythingAtOnce() {
        assertEquals(StagedReveal.ALL, StagedReveal.initial(true, firstVisibleIndex = 3, firstVisibleOffset = 0))
        assertEquals(StagedReveal.ALL, StagedReveal.initial(true, firstVisibleIndex = 0, firstVisibleOffset = 40))
        assertEquals(1, StagedReveal.initial(true, 0, 0))
        assertEquals(StagedReveal.ALL, StagedReveal.initial(false, 0, 0))
    }

    @Test fun growthStopsAtAll() {
        var n = 1; var frames = 0
        while (n != StagedReveal.ALL) { n = StagedReveal.next(n); frames++ }
        assertEquals(StagedReveal.MAX_STAGES, frames)
        assertEquals(StagedReveal.ALL, StagedReveal.next(StagedReveal.ALL))
    }

    @Test fun flagIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.FEED_STAGED_REVEAL.default)
    }
}
