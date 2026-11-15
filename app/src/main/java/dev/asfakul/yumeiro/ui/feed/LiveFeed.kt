/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.feed

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * R15 Phase 3 (ADR-008): one model for every endless feed (YouTube, YouTube Music, live TV,
 * Home, Yumix). The bug the user saw — "scrolling suddenly stops and the screen shows saved data"
 * — came from the same four mistakes repeated per screen:
 *
 *  1. Feed state lived in `remember {}` of a pager page / tab. Leaving the page threw it away;
 *     coming back rebuilt it from the disk / memory cache while [ScrollMemory] put the list back
 *     at the old index, so the user landed in old data.
 *  2. Coroutines that loaded a page ran in the screen's scope. When the page left composition
 *     they were cancelled with `loading = true` already set, and the feed never asked again.
 *  3. A late answer wrote over newer state (`state = cur.copy(...)` with a stale `cur`), so an old
 *     query's or an old chip's page replaced what was on screen.
 *  4. One failed or timed-out page set `continuation = null`, ending the feed for good.
 *
 * [LivePaged] fixes all four: it is owned by a process-scoped store (not by the composable),
 * loads in [FeedScope] (survives navigation), drops any answer from an older generation, only
 * ever appends (cards on screen never move or vanish), and retries a failed page with backoff
 * instead of ending. [AppendOnly] is the same rule for feeds merged from several sources.
 */
object FeedScope {
    /** Loads outlive the screen that asked for them; results land in the store. */
    val main: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

/** A merged feed that only ever grows at the end; [append] adds what isn't on screen yet. */
@Stable
class AppendOnly<T>(private val key: (T) -> String) {
    val items: SnapshotStateList<T> = mutableStateListOf()
    private val seen = HashSet<String>()

    /** Adds the entries of [candidates] not shown before, in order. Returns how many were added. */
    fun append(candidates: List<T>): Int {
        val fresh = candidates.filter { seen.add(key(it)) }
        if (fresh.isNotEmpty()) items.addAll(fresh)
        return fresh.size
    }

    fun contains(k: String) = k in seen
    fun clear() { items.clear(); seen.clear() }
}

/**
 * A paged, append-only feed. [fetch] gets the cursor of the next page (null = first page) and
 * returns the page's items and the next cursor (null = no more pages).
 */
@Stable
class LivePaged<T, P : Any>(
    private val key: (T) -> String,
    private var fetch: suspend (P?) -> Pair<List<T>, P?>,
    private val scope: CoroutineScope = FeedScope.main,
    private val maxFails: Int = 3,
    private val backoffMs: (fails: Int) -> Long = { f -> (1_500L shl (f - 1).coerceIn(0, 3)).coerceAtMost(12_000L) },
    private val describe: (Throwable) -> String = { it.message ?: "Couldn't load more" },
) {
    val items: SnapshotStateList<T> = mutableStateListOf()
    var loading by mutableStateOf(false); private set
    var ended by mutableStateOf(false); private set
    /** Set after [maxFails] failures in a row: the list shows a retry button instead of loading forever. */
    var error by mutableStateOf<String?>(null); private set
    var started by mutableStateOf(false); private set
    private var next: P? = null
    private var fails = 0
    private var emptyPages = 0
    private var gen = 0
    private val seen = HashSet<String>()
    /** The page load in flight; [reset] cancels it (G12 X15-1). */
    private var job: Job? = null

    val canLoadMore: Boolean get() = !loading && !ended && error == null

    /** Starts over (new query, new chip plan); answers still in flight are dropped. */
    fun reset(newFetch: (suspend (P?) -> Pair<List<T>, P?>)? = null) {
        gen++
        // G12 X15-1: the old answer was already dropped, but its request kept running (and kept
        // its connection slot); switching chips quickly stacked requests. Cancel it.
        job?.cancel(); job = null
        if (newFetch != null) fetch = newFetch
        items.clear(); seen.clear(); next = null
        loading = false; ended = false; error = null; started = false; fails = 0; emptyPages = 0
    }

    /** Asks for the next page unless one is loading, the feed ended or it is waiting for [retry]. */
    fun more() {
        if (!canLoadMore) return
        val g = gen
        val cursor = next
        loading = true
        job = scope.launch {
            val r = try { Result.success(fetch(cursor)) } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }
            if (g != gen) return@launch
            r.onSuccess { (page, nxt) ->
                fails = 0
                started = true
                val fresh = page.filter { seen.add(key(it)) }
                if (fresh.isNotEmpty()) items.addAll(fresh)
                emptyPages = if (fresh.isEmpty()) emptyPages + 1 else 0
                next = nxt
                // No cursor = the source has no more. A source that keeps sending only cards we
                // already show is treated as ended after 3 pages (never an endless spinner).
                ended = nxt == null || emptyPages >= 3
                loading = false
            }.onFailure { e ->
                fails++
                // Stay "loading" through the backoff so the scroll trigger can't hammer a failing source.
                delay(backoffMs(fails))
                if (g != gen) return@launch
                if (fails >= maxFails) error = describe(e)
                loading = false
            }
        }
    }

    /** The user tapped retry after [error]. */
    fun retry() {
        if (loading) return
        fails = 0; error = null
        more()
    }
}
