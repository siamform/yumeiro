/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.tv

/**
 * X9-4 (TV run 43768054941): the tab pager composes only the page on screen, but a lazy layout
 * keeps the focused item composed ("pinned") after its page has gone. When the tab changes
 * without the remote moving focus (a touch tap on the rail, Home's "Continue video" card opening
 * Videos, a deep link), focus stayed on that off-screen card. It has no neighbour to its left, so
 * D-pad Left did nothing and the remote looked dead until the user pressed something else.
 *
 * Rule: once the pager is at rest, focus that is still inside a page other than the settled one
 * goes to the rail's selected tab.
 */
object TvPageFocus {
    /** [focusedPage] is the page that holds focus, or -1 when focus is outside every page. */
    fun isStranded(settledPage: Int, focusedPage: Int, scrolling: Boolean): Boolean =
        !scrolling && focusedPage >= 0 && focusedPage != settledPage
}
