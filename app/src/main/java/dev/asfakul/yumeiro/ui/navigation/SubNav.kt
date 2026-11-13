/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

/** Rules for opening sub-pages. */
object SubNav {
    /**
     * True for pages without arguments (Extensions, Market, Settings, Downloads…): if one is
     * already on the back stack, going "there" again returns to it rather than stacking a copy.
     * Pages with arguments (a title, a playlist, a row) are always pushed: each one is different.
     */
    fun reusable(route: String): Boolean =
        route.isNotBlank() && '?' !in route && '{' !in route && !route.startsWith(PLAYLIST_PREFIX)

    private const val PLAYLIST_PREFIX = "stream/playlist/"
}
