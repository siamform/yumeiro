/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * GATE-1-RENDER-2: a feed list that opens with its sections added one per frame, top to bottom.
 *
 * Gate run 44950383602: opening Yumix composed the hero and every visible shelf in one layout pass
 * (AndroidOwner:measureAndLayout 855 ms on api33-lowram) and the next frame drew all their new draw
 * types at once (RenderThread shader compiles, main thread in postAndWait 453 ms); the main thread
 * waited 1492 ms (api36: 999 ms). With [StagedList] the first frame has the first section only and each
 * later frame adds one more below it, so nothing already shown moves and every frame stays short.
 * A list that reopens scrolled down shows everything at once (a short list would lose the position).
 */
object StagedReveal {
    /** After this many sections the rest are added together (they are below the screen by then). */
    const val MAX_STAGES = 12
    const val ALL = Int.MAX_VALUE

    /** Pure: how many sections a list shows when it is first composed. */
    fun initial(enabled: Boolean, firstVisibleIndex: Int, firstVisibleOffset: Int): Int =
        if (!enabled || firstVisibleIndex > 0 || firstVisibleOffset > 0) ALL else 1

    /** Pure: the next count after one frame. */
    fun next(current: Int): Int = if (current == ALL || current + 1 > MAX_STAGES) ALL else current + 1
}

/** The number of sections to show, growing one per frame from the first composition (see [StagedReveal]). */
@Composable
fun rememberStagedCount(enabled: Boolean, listState: LazyListState): MutableIntState {
    val count = remember { mutableIntStateOf(StagedReveal.initial(enabled, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)) }
    LaunchedEffect(count) {
        while (count.intValue != StagedReveal.ALL) {
            withFrameNanos { }
            count.intValue = StagedReveal.next(count.intValue)
        }
    }
    return count
}

/** Wraps a [LazyListScope]: each [item] / [items] call is one section; sections past [limit] are left out. */
class StagedList(private val scope: LazyListScope, private val limit: Int) {
    private var n = 0
    /** Sections declared so far (shown or not). */
    val declared: Int get() = n

    fun item(key: Any? = null, contentType: Any? = null, content: @Composable LazyItemScope.() -> Unit) {
        if (n++ < limit) scope.item(key, contentType, content)
    }

    fun <T> items(items: List<T>, key: ((T) -> Any)? = null, contentType: (T) -> Any? = { null }, itemContent: @Composable LazyItemScope.(T) -> Unit) {
        if (n++ < limit) scope.items(items.size, if (key != null) { i: Int -> key(items[i]) } else null, { i -> contentType(items[i]) }) { i -> itemContent(items[i]) }
    }
}
