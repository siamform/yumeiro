/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import dev.asfakul.yumeiro.player.VideoPrefs.Control
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R17 PL-16: the top bar never clips; what doesn't fit goes to More, in the user's order. */
class TopBarFitTest {
    private val all = Control.values().toList()

    @Test fun wideScreenKeepsEverythingInline() {
        val s = TopBarFit.split(all, 2000f, "1080p")
        assertEquals(all, s.inline); assertTrue(s.overflow.isEmpty())
    }

    @Test fun narrowScreenMovesTheTailToTheMenuInOrder() {
        val s = TopBarFit.split(all, 200f, "1080p")
        assertEquals(all, s.inline + s.overflow)
        assertTrue(s.overflow.isNotEmpty())
        val used = s.inline.sumOf { (TopBarFit.widthOf(it, "1080p") + TopBarFit.GAP_DP).toDouble() } + TopBarFit.BUTTON_DP
        assertTrue("inline row $used dp overflows 200 dp", used <= 200.0)
    }

    @Test fun moreButtonSpaceIsAlwaysReserved() {
        val s = TopBarFit.split(listOf(Control.values().first { it != Control.SUBTITLES && it != Control.QUALITY }), 60f, null)
        assertTrue("48 dp More + 50 dp button cannot fit in 60 dp", s.inline.isEmpty())
        assertEquals(1, s.overflow.size)
    }

    @Test fun nothingAfterAButtonThatDoesNotFitStaysInline() {
        // Quality with a long label is wide; a small button after it must not jump ahead of it.
        val order = listOf(Control.QUALITY) + all.filter { it != Control.QUALITY && it != Control.SUBTITLES }.take(1)
        val s = TopBarFit.split(order, 48f + 50f + 52f, "2160p HDR10+ Dolby")
        assertTrue(s.inline.isEmpty()); assertEquals(order, s.overflow)
    }

    @Test fun qualityPillNeverUnderTouchTarget() {
        assertEquals(48f, TopBarFit.widthOf(Control.QUALITY, "4K"), 0.01f)
        assertEquals(2 * 48f + 2f, TopBarFit.widthOf(Control.SUBTITLES, null), 0.01f)
    }
}
