package dev.asfakul.yumeiro.ui.screens.youtube

// F-12 (ledger F-12, docs/proposals/FEATURE-F-12-vertical-video-player.md, ADR-018): the vertical Shorts player
// keeps one player and only resolves links ahead (no second decoder). Fail-before: ShortsWindow does not exist.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class F12ShortsWindowTest {
    @Test fun normalPhonesResolveTwoAheadAndOneBehind() {
        assertEquals(listOf(6, 7, 4), ShortsWindow.prefetch(5, 20, lowEnd = false))
    }

    @Test fun lowRamPhonesResolveOnlyTheNextOne() {
        assertEquals(listOf(6), ShortsWindow.prefetch(5, 20, lowEnd = true))
    }

    @Test fun theEdgesOfTheListAreRespected() {
        assertEquals(listOf(1, 2), ShortsWindow.prefetch(0, 3, lowEnd = false))
        assertEquals(listOf(1), ShortsWindow.prefetch(2, 3, lowEnd = false))
        assertEquals(emptyList<Int>(), ShortsWindow.prefetch(0, 1, lowEnd = false))
    }

    @Test fun resolvedLinksKeptAreBoundedSoLongSessionsDoNotGrowMemory() {
        assertEquals(setOf(3, 4, 5, 6, 7), ShortsWindow.keep(5, 20, lowEnd = false))
        assertEquals(setOf(4, 5, 6), ShortsWindow.keep(5, 20, lowEnd = true))
    }

    @Test fun onlyVerticalQueuesOpenTheShortsPlayer() {
        assertTrue(ShortsWindow.opens(isShort = true, queueSize = 4, tv = false, enabled = true))
        assertFalse(ShortsWindow.opens(isShort = false, queueSize = 4, tv = false, enabled = true))
        assertFalse(ShortsWindow.opens(isShort = true, queueSize = 4, tv = true, enabled = true))
        assertFalse(ShortsWindow.opens(isShort = true, queueSize = 4, tv = false, enabled = false))
        assertTrue(ShortsWindow.opens(isShort = true, queueSize = 1, tv = false, enabled = true))
    }
}
