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
class PriorityGate(private val permits: Int) {
    private class Waiter(val key: String, @Volatile var priority: Int, val seq: Long, val go: CompletableDeferred<Unit> = CompletableDeferred())

    private val lock = Any()
    private var free = permits
    private var seq = 0L
    private val waiting = ArrayList<Waiter>()

    suspend fun <T> run(key: String, priority: Int, block: suspend () -> T): T {
        val w = synchronized(lock) {
            if (free > 0 && waiting.isEmpty()) { free--; null } else Waiter(key, priority, seq++).also { waiting += it }
        }
        if (w != null) {
            try {
                w.go.await()
            } catch (c: CancellationException) {
                // Cancelled while waiting: leave the queue; a permit already handed over goes to the next one.
                val handed = synchronized(lock) { !waiting.remove(w) }
                if (handed) release()
                throw c
            }
        }
        try { return block() } finally { release() }
    }

    /** Moves every waiting job of [key] up to [priority] (never down). True when one was waiting. */
    fun bump(key: String, priority: Int): Boolean = synchronized(lock) {
        var hit = false
        waiting.forEach { if (it.key == key && priority < it.priority) { it.priority = priority; hit = true } }
        hit || waiting.any { it.key == key }
    }

    /** Jobs waiting for a permit (tests, diagnostics). */
    fun waitingCount(): Int = synchronized(lock) { waiting.size }

    private fun release() {
        val next = synchronized(lock) {
            val n = waiting.minWithOrNull(compareBy<Waiter>({ it.priority }, { it.seq }))
            if (n == null) { free++; null } else { waiting.remove(n); n }
        }
        next?.go?.complete(Unit)
    }

    companion object {
        const val PLAYING = 0
        const val PICKED = 1
        const val VISIBLE = 2
        const val BACKGROUND = 3
    }
}
