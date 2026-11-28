/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Group W (B13): a card deep inside a shelf (a genre, a service, "See all") opens its own page without every screen
 * in between passing a callback down. The app shell collects [requests] and pushes the route like any other page,
 * so the page has a real back stack entry and its own Back.
 */
object NavRequests {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val requests: SharedFlow<String> get() = flow

    /** Asks the shell to open [route]; a blank route is ignored. */
    fun open(route: String) { if (route.isNotBlank()) flow.tryEmit(route) }
}
