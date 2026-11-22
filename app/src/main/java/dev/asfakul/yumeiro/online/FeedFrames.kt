/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * FE-4 (1000+ extensions): Yumix rebuilt its whole mix (hero, latest, categories, sections, explore: O(all titles), on
 * the main thread) on every home update, and with FE-3 that is one update per row of every extension. Updates are
 * now taken as frames: the first at once, then at most one per [FRAME_MS] carrying the newest state, so the work per
 * second stays the same with 10 or 1000 extensions. Same idea as a feed that batches arrivals per frame instead of
 * re-laying out on each network answer.
 */
object FeedFrames {
    const val FRAME_MS = 300L

    /** First value at once, then the newest value at most every [ms]; the last value is never lost. */
    fun <T> Flow<T>.atMostEvery(ms: Long = FRAME_MS): Flow<T> = conflate().transform { emit(it); delay(ms) }
}
