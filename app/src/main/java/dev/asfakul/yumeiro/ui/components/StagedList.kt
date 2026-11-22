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

/**
 * GATE-1-FEED-LATE: sections that appear after the opening reveal (a feed's data arriving while
 * its skeleton shows) join at most one per frame too. Gate run 44987227363 (api33-lowram): the
 * Yumix data arrived while the skeleton showed and categories, hero and the first shelf with its
 * posters were built in one layout pass (AndroidOwner:measureAndLayout 1129 ms, main thread stalled
 * 1088 ms). Pure state, no snapshot writes: [admit] runs inside the lazy list's content, and a new
 * section held back asks [StagedLate.Holder.signal] for one more frame. Sections from index
 * [StagedReveal.MAX_STAGES] on are below the screen and are never held back.
 */
class StagedLate {
    private val shown = HashSet<Any>()
    private var admittedAt = -1

    /** True when the section [key] at [index] may be shown in the pass of frame [tick]. */
    fun admit(key: Any, index: Int, tick: Int, opening: Boolean): Boolean {
        if (opening || index >= StagedReveal.MAX_STAGES || key in shown) { shown += key; return true }
        if (admittedAt == tick) return false
        admittedAt = tick
        shown += key
        return true
    }

    /** One per list: the [StagedLate] state, the frame [tick] and the "need another frame" signal. */
    class Holder {
        val late = StagedLate()
        val tick = mutableIntStateOf(0)
        val signal = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    }
}

/** The [StagedLate.Holder] of one list, or null when GATE-1-FEED-LATE is off. Bumps the tick one frame after a hold. */
@Composable
fun rememberStagedLate(enabled: Boolean): StagedLate.Holder? {
    if (!enabled) return null
    val h = remember { StagedLate.Holder() }
    LaunchedEffect(h) {
        for (s in h.signal) {
            withFrameNanos { }
            h.tick.intValue++
        }
    }
    return h
}

/** Wraps a [LazyListScope]: each [item] / [items] call is one section; sections past [limit] are left out. */
class StagedList(private val scope: LazyListScope, private val limit: Int, private val late: StagedLate.Holder? = null) {
    private var n = 0
    // Read in the lazy content, so a tick re-runs it; opening = the GATE-1-RENDER-2 reveal still runs.
    private val tick = late?.tick?.intValue ?: 0
    private val opening = limit != StagedReveal.ALL
    /** Sections declared so far (shown or not). */
    val declared: Int get() = n

    private fun allowed(key: Any): Boolean {
        val i = n++
        if (i >= limit) return false
        val l = late ?: return true
        if (l.late.admit(key, i, tick, opening)) return true
        l.signal.trySend(Unit)
        return false
    }

    fun item(key: Any? = null, contentType: Any? = null, content: @Composable LazyItemScope.() -> Unit) {
        val name = "feed:" + (key ?: n).toString().take(40)
        if (allowed(key ?: "#$n")) scope.item(key, contentType) { traced(name) { content() } }
    }

    fun <T> items(items: List<T>, key: ((T) -> Any)? = null, contentType: (T) -> Any? = { null }, itemContent: @Composable LazyItemScope.(T) -> Unit) {
        val name = "feed:items#$n"
        if (allowed("items#$n")) scope.items(items.size, if (key != null) { i: Int -> key(items[i]) } else null, { i -> contentType(items[i]) }) { i -> traced(name) { itemContent(items[i]) } }
    }
}

/** GATE-1-TRACE-3: names each feed section in system traces (atrace), so a slow frame says which section it built. */
@Composable
private inline fun traced(name: String, content: @Composable () -> Unit) {
    val on = android.os.Build.VERSION.SDK_INT >= 29 && android.os.Trace.isEnabled()
    if (on) android.os.Trace.beginSection(name)
    content() // no try/finally: Compose does not allow it around composable calls (a throw ends the app anyway)
    if (on) android.os.Trace.endSection()
}
