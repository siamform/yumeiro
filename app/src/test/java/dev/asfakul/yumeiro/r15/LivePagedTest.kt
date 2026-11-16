/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import dev.asfakul.yumeiro.ui.feed.AppendOnly
import dev.asfakul.yumeiro.ui.feed.LivePaged
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R15 Phase 3 (ADR-008): the shared live-feed model never ends, reorders or replaces a live scroll. */
@OptIn(ExperimentalCoroutinesApi::class)
class LivePagedTest {
    @Test fun pagesOnlyAppendAndDuplicatesAreDropped() = runTest(StandardTestDispatcher()) {
        val f = LivePaged<String, Int>({ it }, { p -> (if (p == null) listOf("a", "b") else listOf("b", "c")) to (if (p == null) 2 else 3) }, this)
        f.more(); advanceUntilIdle()
        val first = f.items.toList()
        f.more(); advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), f.items.toList())
        assertEquals(first, f.items.take(first.size))
        assertFalse(f.ended)
    }

    @Test fun anAnswerFromBeforeAResetIsDropped() = runTest(StandardTestDispatcher()) {
        val gate = CompletableDeferred<Unit>()
        var call = 0
        val f = LivePaged<String, Int>({ it }, { _ -> call++; if (call == 1) { gate.await(); listOf("old") to 2 } else listOf("new") to 2 }, this)
        f.more(); advanceUntilIdle()
        assertTrue(f.loading)
        f.reset()
        f.more(); advanceUntilIdle()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("new"), f.items.toList())
        assertFalse(f.loading)
    }

    /** G12 X15-1: a reset cancels the request in flight instead of letting it run on. */
    @Test fun resetCancelsTheLoadInFlight() = runTest(StandardTestDispatcher()) {
        var cancelled = false
        var call = 0
        val f = LivePaged<String, Int>({ it }, { _ ->
            call++
            if (call == 1) try { kotlinx.coroutines.awaitCancellation() } finally { cancelled = true }
            listOf("new") to 2
        }, this)
        f.more(); advanceUntilIdle()
        assertTrue(f.loading)
        f.reset(); advanceUntilIdle()
        assertTrue("the old request is still running after reset", cancelled)
        f.more(); advanceUntilIdle()
        assertEquals(listOf("new"), f.items.toList())
        assertFalse(f.loading)
    }

    @Test fun aFailedPageKeepsTheCursorAndRetries() = runTest(StandardTestDispatcher()) {
        val asked = ArrayList<Int?>()
        var failNext = false
        val f = LivePaged<String, Int>({ it }, { p -> asked += p; if (failNext) { failNext = false; error("HTTP 502") }; listOf("p${p ?: 1}") to ((p ?: 1) + 1) }, this, backoffMs = { 500L })
        f.more(); advanceUntilIdle()
        failNext = true
        f.more(); advanceUntilIdle()
        assertNull("one failure is not an error", f.error)
        assertFalse("the feed must not end on a failure", f.ended)
        f.more(); advanceUntilIdle()
        assertEquals(listOf(null, 2, 2), asked)
        assertEquals(listOf("p1", "p2"), f.items.toList())
    }

    @Test fun threeFailuresShowRetryAndRetryResumes() = runTest(StandardTestDispatcher()) {
        var down = true
        val f = LivePaged<String, Int>({ it }, { p -> if (down) error("timeout"); listOf("x${p ?: 0}") to 1 }, this, backoffMs = { 500L })
        repeat(3) { f.more(); advanceUntilIdle() }
        assertNotNull(f.error)
        assertFalse(f.canLoadMore)
        f.more(); advanceUntilIdle()
        assertTrue(f.items.isEmpty())
        down = false
        f.retry(); advanceUntilIdle()
        assertNull(f.error)
        assertEquals(listOf("x0"), f.items.toList())
    }

    @Test fun noCursorOrThreeRepeatPagesEndTheFeed() = runTest(StandardTestDispatcher()) {
        val f = LivePaged<String, Int>({ it }, { _ -> listOf("same") to 1 }, this)
        repeat(5) { f.more(); advanceUntilIdle() }
        assertTrue(f.ended)
        assertEquals(listOf("same"), f.items.toList())
        val g = LivePaged<String, Int>({ it }, { _ -> listOf("only") to null }, this)
        g.more(); advanceUntilIdle()
        assertTrue(g.ended)
    }

    @Test fun appendOnlyNeverMovesShownCards() {
        val a = AppendOnly<String> { it }
        assertEquals(2, a.append(listOf("1", "2")))
        assertEquals(1, a.append(listOf("2", "1", "3")))
        assertEquals(listOf("1", "2", "3"), a.items.toList())
    }

    /** G12 X15-2: a timeout inside the fetch is a failed page, not a feed stuck on "loading". */
    @Test fun aTimeoutInsideTheFetchIsAFailureNotAStuckSpinner() = runTest(StandardTestDispatcher()) {
        var call = 0
        val f = LivePaged<String, Int>({ it }, { _ ->
            call++
            if (call == 1) kotlinx.coroutines.withTimeout(100) { kotlinx.coroutines.delay(10_000); listOf("never") to 2 }
            else listOf("a") to 2
        }, this, backoffMs = { 50 })
        f.more(); advanceUntilIdle()
        assertFalse("the feed is stuck loading after a timeout", f.loading)
        assertNull(f.error)
        f.more(); advanceUntilIdle()
        assertEquals(listOf("a"), f.items.toList())
    }
}
