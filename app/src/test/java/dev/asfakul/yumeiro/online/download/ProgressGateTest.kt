/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** G8/BUG-3: download progress reaches the list at most every interval, and the end always. */
class ProgressGateTest {
    @Test fun firstReportPassesThenOnePerInterval() {
        val g = ProgressGate(250)
        assertTrue(g.pass(1_000, 1, 100))
        val passed = (1_001L..1_999L).count { g.pass(it, 2, 100) }
        // 1250, 1500, 1750 pass; everything else in between is dropped.
        assertEquals(3, passed)
    }

    @Test fun theLastReportAlwaysPasses() {
        val g = ProgressGate(250)
        assertTrue(g.pass(1_000, 10, 100))
        assertTrue("done == total must pass inside the interval", g.pass(1_001, 100, 100))
    }

    @Test fun unknownTotalIsThrottledToo() {
        val g = ProgressGate(250)
        assertTrue(g.pass(0, 1, -1))
        assertEquals(false, g.pass(100, 2, -1))
        assertTrue(g.pass(250, 3, -1))
    }

    @Test fun manyConnectionsAtOnceLetOneThroughPerTick() {
        val g = ProgressGate(250)
        val pool = Executors.newFixedThreadPool(4)
        val passed = AtomicInteger()
        repeat(4_000) { i -> pool.execute { if (g.pass(5_000, i.toLong(), 1_000_000)) passed.incrementAndGet() } }
        pool.shutdown(); pool.awaitTermination(10, TimeUnit.SECONDS)
        assertEquals("one report per instant, not one per connection", 1, passed.get())
    }
}
