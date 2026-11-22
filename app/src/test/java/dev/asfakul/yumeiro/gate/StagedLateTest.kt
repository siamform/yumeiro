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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.components.StagedLate
import dev.asfakul.yumeiro.ui.components.StagedList
import dev.asfakul.yumeiro.ui.components.StagedReveal
import dev.asfakul.yumeiro.ui.components.rememberStagedCount
import dev.asfakul.yumeiro.ui.components.rememberStagedLate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * GATE-1-FEED-LATE: gate run 44987227363 (api33-lowram) failed with a 1088 ms main-thread stall when
 * the Yumix data arrived under the skeleton: categories, hero and the first shelf were built in one
 * layout pass (measureAndLayout 1129 ms). The opening reveal (GATE-1-RENDER-2) only staged the very
 * first frames. Now sections that appear later join one per frame too.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StagedLateTest {
    @get:Rule val rule = createComposeRule()

    @Test fun afterTheOpeningOnlyOneNewSectionJoinsPerFrame() {
        val l = StagedLate()
        assertTrue(l.admit("skel", 0, tick = 0, opening = true))
        assertTrue(l.admit("a", 1, tick = 0, opening = false))
        assertFalse("a second new section waits for the next frame", l.admit("b", 2, tick = 0, opening = false))
        assertTrue("already shown sections stay", l.admit("skel", 0, tick = 0, opening = false))
        assertTrue(l.admit("b", 2, tick = 1, opening = false))
        assertTrue("sections below the screen are never held", l.admit("far", StagedReveal.MAX_STAGES, tick = 1, opening = false))
        assertTrue(l.admit("a", 1, tick = 1, opening = false))
    }

    @Test fun dataArrivingUnderTheSkeletonJoinsOneSectionPerFrame() {
        val ready = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val state = rememberLazyListState()
            val staged = rememberStagedCount(true, state)
            val late = rememberStagedLate(true)
            LazyColumn(state = state) {
                val st = StagedList(this, staged.intValue, late)
                if (ready.value) {
                    st.item("categories") { Box(Modifier.height(20.dp).testTag("cats")) }
                    st.item("hero") { Box(Modifier.height(20.dp).testTag("hero")) }
                    st.item("shelf") { Box(Modifier.height(20.dp).testTag("shelf")) }
                } else {
                    st.item("hero_skel") { Box(Modifier.height(20.dp).testTag("skel")) }
                }
            }
        }
        repeat(StagedReveal.MAX_STAGES + 2) { rule.mainClock.advanceTimeByFrame() } // the opening reveal is over
        rule.onNodeWithTag("skel").assertExists()
        ready.value = true
        // Record the frame each section first shows up in: no two may join in the same frame.
        val firstSeen = LinkedHashMap<String, Int>()
        for (frame in 1..12) {
            rule.mainClock.advanceTimeByFrame()
            for (t in listOf("cats", "hero", "shelf")) {
                if (t !in firstSeen && rule.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty()) firstSeen[t] = frame
            }
        }
        assertEquals("every section shows up: $firstSeen", setOf("cats", "hero", "shelf"), firstSeen.keys)
        assertEquals("one new section per frame, top to bottom: $firstSeen", listOf("cats", "hero", "shelf"), firstSeen.entries.sortedBy { it.value }.map { it.key })
        assertEquals("no two in the same frame: $firstSeen", 3, firstSeen.values.toSet().size)
    }

    @Test fun flagIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.FEED_STAGED_LATE.default)
    }
}
