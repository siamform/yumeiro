/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import dev.asfakul.yumeiro.ui.components.FloatingPlayerArea
import dev.asfakul.yumeiro.ui.components.FloatingPlayerArea.Edges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ST-21: the bottom message moves above the floating video window instead of covering it. */
class FloatingPlayerAreaTest {
    private val nav = 48

    @Test fun noFloatingWindowKeepsThePadding() {
        assertEquals(150, FloatingPlayerArea.messageBottom(150, nav, null))
    }

    /** Pixel 6 numbers from run 43965355950: 393 dp wide → 220 x 124 dp window, 100 dp above the bar. */
    @Test fun defaultFloatingWindowLiftsTheMessageAboveIt() {
        val win = Edges(bottomDp = nav + 100, topDp = nav + 100 + 124)
        val bottom = FloatingPlayerArea.messageBottom(150, nav, win)
        // The message's bottom edge (above the bar) is over the window's top edge, with the gap.
        assertEquals(win.topDp + FloatingPlayerArea.GAP_DP - nav, bottom)
        assertTrue("message bottom above the window top", nav + bottom > win.topDp)
    }

    @Test fun windowDraggedUpOutOfTheBandLeavesTheMessage() {
        val win = Edges(bottomDp = 500, topDp = 624)
        assertEquals(150, FloatingPlayerArea.messageBottom(150, nav, win))
    }

    @Test fun subpageWindowLowAndMessageLowStillLifts() {
        // Sub-page: message 24 dp above the bar, window 16 dp above it.
        val win = Edges(bottomDp = nav + 16, topDp = nav + 16 + 124)
        val bottom = FloatingPlayerArea.messageBottom(24, nav, win)
        assertTrue(nav + bottom > win.topDp)
    }

    @Test fun windowEntirelyBelowTheBandLeavesTheMessage() {
        // Window top under the message's bottom edge: no overlap, nothing moves.
        val win = Edges(bottomDp = 10, topDp = nav + 150)
        assertEquals(150, FloatingPlayerArea.messageBottom(150, nav, win))
    }
}
