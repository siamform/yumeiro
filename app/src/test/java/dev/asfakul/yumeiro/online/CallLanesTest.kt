/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.player.HeatGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections

/**
 * FE-2c (docs/FEED-ENGINE-2.1.md): every extension call shares HeatGuard's limit of 6. It was a FIFO semaphore, so a
 * search typed while the feed loaded (or the next page under the finger) waited behind every background row queued
 * before it. The limit now serves PICKED, then VISIBLE, then BACKGROUND.
 */
class CallLanesTest {
    private suspend fun settle() = repeat(40) { yield() }

    @Test fun searchAndScrollPagesGoBeforeQueuedBackgroundRows() = runBlocking {
        HeatGuard.set(HeatGuard.Level.COOL)
        val hold = CompletableDeferred<Unit>()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val busy = (1..6).map { launch { HeatGuard.limited { hold.await() } } }
        settle()
        val queued = listOf(
            "row1" to PriorityGate.BACKGROUND, "row2" to PriorityGate.BACKGROUND,
            "page" to PriorityGate.VISIBLE, "search" to PriorityGate.PICKED,
        ).map { (k, p) -> launch { withContext(PriorityGate.Lane(p)) { HeatGuard.limited { order += k } } }.also { settle() } }
        hold.complete(Unit)
        (busy + queued).forEach { it.join() }
        assertEquals(listOf("search", "page", "row1", "row2"), order)
    }
}
