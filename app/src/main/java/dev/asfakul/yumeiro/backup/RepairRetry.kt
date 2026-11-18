/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * G9-2 (ADR-021): re-downloading what a restore left out used to be tried once per process; a
 * failed first try (offline, repo down) left the sources missing until the next app start.
 * This runs [attempt] at start and again after a back-off (30 s, 2 min, then every 10 min) or as
 * soon as the connection comes back, at most [MAX_TRIES] times per process. It stops when
 * [attempt] returns true (nothing missing any more). Cancellation is never swallowed.
 */
object RepairRetry {
    const val MAX_TRIES = 12

    internal fun delayFor(failedTries: Int): Long = when (failedTries) {
        1 -> 30_000L
        2 -> 120_000L
        else -> 600_000L
    }

    fun launch(
        scope: CoroutineScope,
        name: String,
        online: StateFlow<Boolean> = dev.asfakul.yumeiro.network.Connectivity.online,
        delayFor: (Int) -> Long = ::delayFor,
        attempt: suspend () -> Boolean,
    ): Job = scope.launch {
        var tries = 0
        while (tries < MAX_TRIES) {
            tries++
            val done = try {
                attempt()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                runCatching { android.util.Log.w("RepairRetry", "$name try $tries", t) }
                false
            }
            if (done) return@launch
            if (tries >= MAX_TRIES) break
            withTimeoutOrNull(delayFor(tries)) {
                // Offline: wake as soon as it is back. Online: wake on the next reconnect.
                if (!online.value) online.first { it } else { online.first { !it }; online.first { it } }
            }
        }
        runCatching { android.util.Log.w("RepairRetry", "$name: gave up after $tries tries") }
    }
}
