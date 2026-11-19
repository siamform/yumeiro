/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/**
 * G10 ER-30 (NBUG-8): a timeout around extension code that does not depend on that code suspending.
 *
 * `withTimeout { api.load(url) }` only fires when the extension suspends; one that blocks its thread
 * (a blocking Jsoup/OkHttp call, `runBlocking`, `Thread.sleep`) kept the page loading for as long as it
 * blocked, and leaving the page waited for it too. Here the extension runs as a detached job and the
 * caller only awaits it, so the timeout (or the caller's own cancellation) returns at once and the job is
 * cancelled. A thread the extension really blocks lives on until the extension returns.
 * A timeout still throws [kotlinx.coroutines.TimeoutCancellationException], as before (ADR-029 rule 10).
 */
internal object ExtensionCall {
    private val detached = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun <T> bounded(timeoutMs: Long, block: suspend () -> T): T {
        // The caller's context minus its Job: ProviderBase and other elements still reach the extension.
        val work = detached.async(coroutineContext.minusKey(Job) + Dispatchers.IO) { block() }
        try {
            return withTimeout(timeoutMs) { work.await() }
        } finally {
            work.cancel()
        }
    }
}
