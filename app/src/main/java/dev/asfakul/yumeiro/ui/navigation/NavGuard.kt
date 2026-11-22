/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

/**
 * OWN-1 (owner 2026-11-22): pages "close by themselves" or a push is cut off. Two causes in the app's own
 * navigation: a double tap on a card pushed the same page twice (the second push cut the first slide off), and
 * a double tap on an on-screen Back button ran two pops (the page under it closed too, as if by itself).
 * Like YouTube / Netflix, one tap = one page: repeats inside a short window are dropped. System Back (gesture or
 * remote) is not touched. Pure, unit-tested; flag FeatureFlags.Flag.NAV_GUARD.
 */
class NavGuard(private val pushWindowMs: Long = PUSH_WINDOW_MS, private val popWindowMs: Long = POP_WINDOW_MS) {
    private var lastRoute: String? = null
    private var lastPushAt = Long.MIN_VALUE / 2
    private var lastPopAt = Long.MIN_VALUE / 2

    private var pushedTop: String? = null

    /**
     * True when this push should run. The same route again within [pushWindowMs] is a double tap, but only
     * while the page that push opened is still on top ([top] = the current back stack entry id): after a
     * system Back (gesture, key) the same page can open again at once.
     */
    fun allowPush(route: String, now: Long, top: String? = null): Boolean {
        if (route == lastRoute && now - lastPushAt < pushWindowMs && top == pushedTop) return false
        lastRoute = route; lastPushAt = now; pushedTop = null
        return true
    }

    /** The back stack entry id the last allowed push opened. */
    fun pushed(top: String?) { pushedTop = top }

    /** True when this button pop should run. A second pop within [popWindowMs] of the last one is a double tap. */
    fun allowPop(now: Long): Boolean {
        if (now - lastPopAt < popWindowMs) return false
        lastPopAt = now; lastRoute = null
        return true
    }

    companion object {
        const val PUSH_WINDOW_MS = 700L
        /** A page's pop slide is 320 ms; a real second Back comes after the first page is gone. */
        const val POP_WINDOW_MS = 350L
    }
}
