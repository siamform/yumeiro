/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * FE-2 (docs/FEED-ENGINE-2.1.md): a semaphore whose waiters are served by priority (lower first), then in arrival
 * order, and whose waiting jobs can be moved up ([bump]). Yumix used a fair FIFO `Semaphore(3)`: the order was fixed
 * when loading began, so an extension the user picked in "Show videos from" after that still waited behind every
 * extension queued before it (minutes with many installed).
 *
 * Lanes: [PLAYING] 0, [PICKED] 1, [VISIBLE] 2, [BACKGROUND] 3. Pure and lock-based (no main-thread work).
 */
class PriorityGate(
    private val permits: Int,
    /**
     * FE-P (docs/FEED-ENGINE-2.1.md): how many [BACKGROUND] jobs may run at once right now. Yumix passes 1 while
     * something plays ([PlayingNow]), so the feed's background fetches leave the network and CPU to playback;
     * picked and on-screen work keeps every permit. Call [poke] when the answer may have grown.
     */
    private val backgroundLimit: () -> Int = { permits },
) {
    private class Waiter(val key: String, @Volatile var priority: Int, val seq: Long, val go: CompletableDeferred<Unit> = CompletableDeferred()) {
        @Volatile var background = false
    }

    private val lock = Any()
    private var free = permits
    private var backgroundRunning = 0
    private var seq = 0L
    private val waiting = ArrayList<Waiter>()

    suspend fun <T> run(key: String, priority: Int, block: suspend () -> T): T {
        val (w, grants) = synchronized(lock) {
            val w = Waiter(key, priority, seq++)
            waiting += w
            w to dispatchLocked()
        }
        grants.forEach { it.go.complete(Unit) }
        try {
            w.go.await()
        } catch (c: CancellationException) {
            // Cancelled while waiting: leave the queue; a permit already handed over goes to the next one.
            val handed = synchronized(lock) { !waiting.remove(w) }
            if (handed) release(w.background)
            throw c
        }
        try { return block() } finally { release(w.background) }
    }

    /** Moves every waiting job of [key] up to [priority] (never down). True when one was waiting. */
    fun bump(key: String, priority: Int): Boolean {
        val (hit, grants) = synchronized(lock) {
            var hit = false
            waiting.forEach { if (it.key == key && priority < it.priority) { it.priority = priority; hit = true } }
            (hit || waiting.any { it.key == key }) to dispatchLocked()
        }
        grants.forEach { it.go.complete(Unit) }
        return hit
    }

    /** Re-checks the queue, e.g. after playback stopped and [backgroundLimit] grew. */
    fun poke() {
        val grants = synchronized(lock) { dispatchLocked() }
        grants.forEach { it.go.complete(Unit) }
    }

    /** Jobs waiting for a permit (tests, diagnostics). */
    fun waitingCount(): Int = synchronized(lock) { waiting.size }

    private fun release(background: Boolean) {
        val grants = synchronized(lock) {
            free++
            if (background) backgroundRunning--
            dispatchLocked()
        }
        grants.forEach { it.go.complete(Unit) }
    }

    /** Hands free permits to the best eligible waiters (priority, then arrival). Caller holds [lock]. */
    private fun dispatchLocked(): List<Waiter> {
        val out = ArrayList<Waiter>(0)
        while (free > 0) {
            val bgOk = backgroundRunning < runCatching(backgroundLimit).getOrDefault(permits)
            val n = waiting.filter { it.priority < BACKGROUND || bgOk }.minWithOrNull(compareBy<Waiter>({ it.priority }, { it.seq })) ?: break
            waiting.remove(n)
            free--
            n.background = n.priority >= BACKGROUND
            if (n.background) backgroundRunning++
            out.add(n)
        }
        return out
    }

    /**
     * FE-2c: the lane of the extension calls made inside this coroutine (search = PICKED, scroll pages = VISIBLE,
     * feed rows = the Yumix lane of their extension). Read by HeatGuard's shared extension-call gate.
     */
    class Lane(val priority: Int) : kotlin.coroutines.AbstractCoroutineContextElement(Lane) {
        companion object Key : kotlin.coroutines.CoroutineContext.Key<Lane>
    }

    companion object {
        const val PLAYING = 0
        const val PICKED = 1
        const val VISIBLE = 2
        const val BACKGROUND = 3
    }
}
