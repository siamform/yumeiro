/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * SPD-1…SPD-4 (ADR-038): the start-up path for extensions and the Yumix feed, kept pure so it can be tested.
 *
 * SPD-1: the feed waited for every installed extension to load, one by one, before it showed anything.
 * Extensions are now published one at a time, pinned ones first, and the slow part of each load (opening the
 * file's code) is started ahead for the next ones while the current one runs.
 * SPD-3: on a fresh install the starter extensions waited 4 s after the welcome screen and then downloaded one
 * after another. They now start as soon as the welcome screen is closed and download side by side.
 */
object StartupSpeed {
    /** Pause before the first background checks. A new user already waited for the welcome screen. */
    const val RETURNING_USER_DELAY_MS = 4_000L
    fun firstChecksDelayMs(firstRun: Boolean): Long = if (firstRun) 0L else RETURNING_USER_DELAY_MS

    /** Pinned extensions load first (they lead the feed), then the saved order. Stable. */
    fun <T> loadOrder(items: List<T>, name: (T) -> String, pinned: List<String>): List<T> {
        if (pinned.isEmpty()) return items
        val rank = pinned.withIndex().associate { (i, n) -> n to i }
        return items.withIndex().sortedWith(compareBy({ rank[name(it.value)] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
    }

    /**
     * Loads [items] in order and calls [publish] after each one, so a screen can use the first extension
     * while the rest load. [open] (the costly, thread-safe step: opening the code file) runs ahead for up to
     * [ahead] items at once; [load] (registers providers, must be one at a time) runs strictly in order.
     * An [open] that fails hands null to [load], which then does the work itself.
     */
    suspend fun <T, O, R> loadProgressively(
        items: List<T>,
        ahead: Int,
        open: suspend (T) -> O?,
        load: (T, O?) -> R,
        publish: (T, R) -> Unit,
    ): List<R> = coroutineScope {
        val permits = Semaphore(ahead.coerceAtLeast(1))
        val opened: List<Deferred<O?>> = items.map { item ->
            async {
                permits.withPermit {
                    try { open(item) } catch (e: CancellationException) { throw e } catch (_: Throwable) { null }
                }
            }
        }
        items.mapIndexed { i, item ->
            val o = opened[i].await()
            load(item, o).also { publish(item, it) }
        }
    }

    /** Installs every pick side by side; returns the names that installed, in pick order. */
    suspend fun <T> installAll(picks: List<T>, install: suspend (T) -> String?): List<String> = coroutineScope {
        picks.map { p ->
            async { try { install(p) } catch (e: CancellationException) { throw e } catch (_: Throwable) { null } }
        }.awaitAll().filterNotNull()
    }
}
