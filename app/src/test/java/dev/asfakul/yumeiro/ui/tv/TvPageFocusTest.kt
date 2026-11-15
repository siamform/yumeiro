/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPageFocusTest {
    @Test fun focusLeftOnTheOldPageAfterTheTabChangedIsStranded() =
        assertTrue(TvPageFocus.isStranded(settledPage = 1, focusedPage = 0, scrolling = false))

    @Test fun focusOnTheShownPageIsFine() =
        assertFalse(TvPageFocus.isStranded(settledPage = 1, focusedPage = 1, scrolling = false))

    @Test fun focusOnTheRailOrNowhereIsFine() =
        assertFalse(TvPageFocus.isStranded(settledPage = 1, focusedPage = -1, scrolling = false))

    @Test fun nothingMovesWhileThePagerIsStillMoving() =
        assertFalse(TvPageFocus.isStranded(settledPage = 1, focusedPage = 0, scrolling = true))
}
