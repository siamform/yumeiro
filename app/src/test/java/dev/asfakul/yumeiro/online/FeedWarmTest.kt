/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FE-5 (docs/FEED-ENGINE-2.1.md, owner: reserve cache engine): the background warm-up refreshes the working set only:
 * pinned first, then fastest; quarantined extensions and pages saved < 3 h ago are skipped; at most 6 on a 2 GB
 * phone / TV and 12 otherwise. Before, nothing refreshed saved homes while the app was closed.
 */
class FeedWarmTest {
    private val now = 1_000_000_000_000L
    private val h = 3_600_000L

    @Test fun picksPinnedThenFastestAndSkipsQuarantinedAndFresh() {
        val stats = mapOf(
            "fast" to ExtensionSpeed.Stat(300, 0, 0), "slow" to ExtensionSpeed.Stat(9_000, 0, 0),
            "dead" to ExtensionSpeed.Stat(25_000, 3, now - h), "fresh" to ExtensionSpeed.Stat(100, 0, 0),
        )
        val age = mapOf("fresh" to 1 * h, "fast" to 5 * h, "slow" to 30 * h)
        val items = listOf("slow", "dead", "fresh", "fast", "unknown", "pinned")
        val picked = FeedWarm.pick(items, { it }, listOf("pinned"), { stats[it] }, { age[it] }, now, max = 3)
        assertEquals(listOf("pinned", "fast", "unknown"), picked)
    }

    @Test fun budgetIsSmallerOnLowEndDevices() {
        assertEquals(6, FeedWarm.maxFor(lowEnd = true))
        assertEquals(12, FeedWarm.maxFor(lowEnd = false))
    }
}
