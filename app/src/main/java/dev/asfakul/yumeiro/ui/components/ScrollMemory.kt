/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Remembers where a page was scrolled to, so coming back from a title, a stream or the player
 * lands on the same card instead of the top.
 *
 * `rememberLazyListState()` alone is not enough here: most pages load their rows again when they
 * come back, so the saved position is applied to an empty list and clamped to 0. This keeps the
 * position in memory for [TTL_MS] and applies it once the list has enough items.
 */
object ScrollMemory {
    const val TTL_MS = 30L * 60 * 1000
    private const val RESTORE_WAIT_MS = 8_000L
    private const val MAX = 64

    data class Spot(val index: Int, val offset: Int, val at: Long)

    private val spots = object : LinkedHashMap<String, Spot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Spot>?) = size > MAX
    }

    @Synchronized fun get(key: String, now: Long = System.currentTimeMillis()): Spot? =
        spots[key]?.takeIf { now - it.at <= TTL_MS }

    @Synchronized fun put(key: String, index: Int, offset: Int, now: Long = System.currentTimeMillis()) {
        if (index <= 0 && offset <= 0) spots.remove(key) else spots[key] = Spot(index, offset, now)
    }

    @Synchronized fun clear() = spots.clear()

    /** A [LazyListState] that comes back to where [key] was left. */
    @Composable
    fun list(key: String): LazyListState {
        val saved = remember(key) { get(key) }
        val state = remember(key) { LazyListState(saved?.index ?: 0, saved?.offset ?: 0) }
        Track(key, saved, { state.layoutInfo.totalItemsCount }, { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset },
            { state.isScrollInProgress }, { i, o -> state.scrollToItem(i, o) })
        return state
    }

    /** A [LazyGridState] that comes back to where [key] was left. */
    @Composable
    fun grid(key: String): LazyGridState {
        val saved = remember(key) { get(key) }
        val state = remember(key) { LazyGridState(saved?.index ?: 0, saved?.offset ?: 0) }
        Track(key, saved, { state.layoutInfo.totalItemsCount }, { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset },
            { state.isScrollInProgress }, { i, o -> state.scrollToItem(i, o) })
        return state
    }

    /** A [androidx.compose.foundation.ScrollState] (plain scrolling pages) that comes back to where [key] was left. */
    @Composable
    fun scroll(key: String): androidx.compose.foundation.ScrollState {
        val saved = remember(key) { get(key) }
        val state = remember(key) { androidx.compose.foundation.ScrollState(0) }
        Track(key, saved, { if (state.maxValue == Int.MAX_VALUE) 0 else state.maxValue + 1 }, { state.value to 0 },
            { state.isScrollInProgress }, { i, _ -> state.scrollTo(i) })
        return state
    }

    @Composable
    private fun Track(
        key: String,
        saved: Spot?,
        count: () -> Int,
        position: () -> Pair<Int, Int>,
        scrolling: () -> Boolean,
        scrollTo: suspend (Int, Int) -> Unit,
    ) {
        val restored = remember(key) { booleanArrayOf(saved == null) }
        LaunchedEffect(key) {
            if (saved != null && !restored[0]) {
                // Wait until the rows are back (they load asynchronously), unless the user scrolls first.
                withTimeoutOrNull(RESTORE_WAIT_MS) {
                    snapshotFlow { count() > saved.index || scrolling() }.first { it }
                }
                // Jump on the next frame, never inside the measure pass that delivered the rows:
                // a list inside a pager / nav host is subcomposed during measure, and an immediate
                // scrollToItem there threw "performMeasureAndLayout called during measure layout".
                repeat(2) { attempt ->
                    androidx.compose.runtime.withFrameNanos { }
                    if (scrolling() || count() <= saved.index || position().first == saved.index) return@repeat
                    val ok = runCatching { scrollTo(saved.index, saved.offset) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        .isSuccess
                    if (ok || attempt == 1) return@repeat
                }
                restored[0] = true
            }
            snapshotFlow { position() }.collect { (i, o) -> if (restored[0]) put(key, i, o) }
        }
        DisposableEffect(key) {
            onDispose { if (restored[0]) position().let { (i, o) -> put(key, i, o) } }
        }
    }
}
