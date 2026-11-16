/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ST-21: where the in-app floating video window is, so a bottom message ([AppMessageHost]) never
 * covers it. Run 43965355950 (both phones): the "… has a message · Show" pill sat over the floating
 * window and took the tap meant for its close button.
 *
 * The window reports its edges as distances from the bottom of the app window, in dp (whole dp, so
 * a drag does not post a new value every pixel). Null when no floating window is on screen.
 */
object FloatingPlayerArea {
    data class Edges(val bottomDp: Int, val topDp: Int)

    private val _edges = MutableStateFlow<Edges?>(null)
    val edges: StateFlow<Edges?> = _edges.asStateFlow()

    fun report(e: Edges?) { if (_edges.value != e) _edges.value = e }

    /** Height a bottom message can take (three lines + action); used to tell whether it would reach the window. */
    const val MESSAGE_ALLOWANCE_DP = 120

    /** Space kept between the message and the floating window. */
    const val GAP_DP = 8

    /**
     * Bottom padding (dp, above the navigation bar) for the bottom-centred message.
     * [baseDp] is the normal padding, [navBarDp] the navigation bar height. When the message's
     * band ([baseDp] … [baseDp] + [MESSAGE_ALLOWANCE_DP] above the bar) meets the window, the
     * message goes just above the window's top; otherwise it stays where it was.
     */
    fun messageBottom(baseDp: Int, navBarDp: Int, edges: Edges?): Int {
        if (edges == null) return baseDp
        val bandBottom = navBarDp + baseDp
        val bandTop = bandBottom + MESSAGE_ALLOWANCE_DP
        val meets = edges.bottomDp < bandTop && edges.topDp > bandBottom
        return if (!meets) baseDp else maxOf(baseDp, edges.topDp + GAP_DP - navBarDp)
    }
}
