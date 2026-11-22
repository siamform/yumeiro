/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

/**
 * TV-FP-1: a far tab jump (Home -> Videos from a Home card) was interrupted on Android TV API 24 by another pager
 * scroll (MutationInterruptedException in device run 44995889578): the focused Home card asks the pager to bring
 * it into view (PagerDefaults.bringIntoViewSpec), and the pager stopped on the page in between (Music). The jump
 * now snaps with a priority that a bring-into-view request cannot interrupt, and if the pager still is not on the
 * target afterwards (and the user did not drag), it snaps again, at most [MAX_RETRIES] times. Pure, unit-tested.
 */
object TabJump {
    const val MAX_RETRIES = 2

    /** True when the pager should be snapped to [target] again after a jump ended on [current]. */
    fun needsRetry(target: Int, current: Int, userDraggedSinceJump: Boolean, retriesDone: Int): Boolean =
        target >= 0 && current != target && !userDraggedSinceJump && retriesDone < MAX_RETRIES
}
