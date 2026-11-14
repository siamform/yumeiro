/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

/**
 * R15 (ADR-006 step 3b): the order in which a playback error is handled, as one pure function.
 * Before, this was ~100 lines of early returns inside the player composable. The player now asks
 * [route] what to do and only performs the step it gets back.
 *
 * The order (first match wins):
 * 1. GPU effect refused by this video → play without it.
 * 2. Local file → next item in the queue, else a plain explanation.
 * 3. Network error while offline → wait for the connection (no attempts are burned).
 * 4. Refused / network error on a refreshable source (YouTube) → fetch a fresh link (2 times max).
 * 5. Refused (401/403/…): try the next header variant of the access ladder.
 *    Online sources then move to the next source; other links fall through to the generic ladder.
 * 6. Online source: one quick retry for a network blip, then the next source.
 * 7. Anything else → the bounded generic recovery ([PlaybackRecovery.decide]).
 *
 * [A] is the access-ladder attempt type (kept generic so this stays free of network classes).
 */
object RecoveryRouter {
    sealed interface Step<out A> {
        data object DropGpuEffect : Step<Nothing>
        data object LocalFailure : Step<Nothing>
        data object WaitForNetwork : Step<Nothing>
        data object RefreshLink : Step<Nothing>
        data class HeaderVariant<A>(val attempt: A) : Step<A>
        /** Hand over to the next source; [refused] = the host refused access (error card wording). */
        data class NextSource(val refused: Boolean) : Step<Nothing>
        data object QuickRetry : Step<Nothing>
        data object Generic : Step<Nothing>
    }

    class Input(
        val facts: ErrorFacts,
        val gpuEffectFailed: Boolean,
        val networkStream: Boolean,
        val online: Boolean,
        val canRefresh: Boolean,
        val refreshAttempts: Int,
        /** True for extension sources that can fail over to another source. */
        val onlineSource: Boolean,
        val quickRetries: Int,
        val accessCodes: Set<Int>,
    )

    const val MAX_REFRESHES = 2
    const val MAX_QUICK_RETRIES = 1

    fun <A> route(i: Input, nextHeaderVariant: () -> A?): Step<A> {
        val f = i.facts
        if (i.gpuEffectFailed) return Step.DropGpuEffect
        if (!i.networkStream) return Step.LocalFailure
        val refused = f.httpStatus in i.accessCodes
        if (f.isNetwork && !i.online) return Step.WaitForNetwork
        if ((refused || f.isNetwork) && i.canRefresh && i.refreshAttempts < MAX_REFRESHES) return Step.RefreshLink
        if (refused && (i.onlineSource || f.httpStatus != 451)) {
            nextHeaderVariant()?.let { return Step.HeaderVariant(it) }
            if (i.onlineSource) return Step.NextSource(refused = true)
        }
        if (i.onlineSource) {
            if (f.isNetwork && i.quickRetries < MAX_QUICK_RETRIES) return Step.QuickRetry
            return Step.NextSource(refused = false)
        }
        return Step.Generic
    }
}
