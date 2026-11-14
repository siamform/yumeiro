/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlinePage
import dev.asfakul.yumeiro.online.OnlineRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R15 phase 3: a live endless feed is never interrupted, reordered or replaced by stale data. */
class LiveFeedTest {
    private fun item(api: String, n: Int) = OnlineItem("$api $n", "u/$api/$n", api, "p/$api/$n")
    private fun row(api: String, title: String, range: IntRange, hasNext: Boolean = true) =
        OnlineRow(api, title, range.map { item(api, it) }, pageName = title, pageData = "d/$title", hasNext = hasNext)

    @Test fun cardsOnScreenNeverMoveWhenAShortRowGetsItsNextPage() {
        val seen = HashSet<String>()
        val shown = ArrayList<Pair<OnlineItem, Boolean>>()
        var a = HueRowFeed(row("A", "a", 0 until 3), (0 until 3).map { item("A", it) }, 1, true)
        val b = HueRowFeed(row("B", "b", 0 until 10), (0 until 10).map { item("B", it) }, 1, true)
        shown += YumixFeed.appendNew(hueFeed(listOf(a, b), null), seen, emptySet(), 42L) { it.first.key }
        val before = shown.map { it.first.key }
        // The short row A gets page 2: round-robin would put its cards in the middle of B's.
        a = a.afterPage(OnlinePage((3 until 8).map { item("A", it) }, hasNext = true))
        shown += YumixFeed.appendNew(hueFeed(listOf(a, b), null), seen, emptySet(), 42L) { it.first.key }
        assertEquals(before, shown.take(before.size).map { it.first.key })
        assertEquals(18, shown.size)
        assertEquals(shown.size, shown.map { it.first.key }.toSet().size)
    }

    @Test fun itemsLaterShownAboveDoNotRemoveCardsAlreadyInTheFeed() {
        val seen = HashSet<String>()
        val f = HueRowFeed(row("A", "a", 0 until 6), (0 until 6).map { item("A", it) }, 1, true)
        val first = YumixFeed.appendNew(hueFeed(listOf(f), null), seen, emptySet(), 1L) { it.first.key }
        val skipNow = setOf(first[0].first.key)
        val again = YumixFeed.appendNew(hueFeed(listOf(f), null), seen, skipNow, 1L) { it.first.key }
        assertTrue(again.isEmpty()) // nothing re-added, nothing removed from what the caller holds
        assertEquals(6, first.size)
    }

    @Test fun aFailedPageKeepsTheFeedOpenAndRetriesThenStops() {
        var f = HueRowFeed(row("A", "a", 0 until 3), (0 until 3).map { item("A", it) }, 1, true, loading = true)
        f = f.afterPage(null)
        assertTrue("one timeout must not end the feed", f.hasNext)
        assertFalse(f.loading)
        assertEquals(1, f.page)
        f = f.afterPage(null)
        assertTrue(f.hasNext)
        f = f.afterPage(OnlinePage((3 until 6).map { item("A", it) }, hasNext = true))
        assertEquals(0, f.fails); assertEquals(2, f.page); assertEquals(6, f.items.size)
        repeat(HueRowFeed.MAX_PAGE_FAILS) { f = f.afterPage(null) }
        assertFalse("a row that keeps failing ends", f.hasNext)
    }

    @Test fun emptyPageEndsTheRow() {
        val f = HueRowFeed(row("A", "a", 0 until 3), (0 until 3).map { item("A", it) }, 1, true).afterPage(OnlinePage(emptyList(), true))
        assertFalse(f.hasNext)
    }

    @Test fun backgroundRefreshAppendsAndNeverReplacesWhatIsShown() {
        val old = listOf(row("A", "Popular", 0 until 5), row("A", "Latest", 10 until 15))
        val fresh = listOf(row("A", "Latest", 13 until 20), row("A", "New row", 30 until 32))
        val merged = mergeRows(old, fresh)
        assertEquals(listOf("Popular", "Latest", "New row"), merged.map { it.title })
        assertEquals(old[0], merged[0])
        assertEquals((10 until 20).map { "A $it" }, merged[1].items.map { it.name })
    }

    @Test fun retryDelayGrowsAndIsCapped() {
        assertEquals(2_000L, HueRowFeed.retryDelayMs(1))
        assertEquals(8_000L, HueRowFeed.retryDelayMs(9))
    }
}
