/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import dev.asfakul.yumeiro.online.OnlineItem
import dev.asfakul.yumeiro.online.OnlineRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R16 Group 4: what a Yumix section shows after a fetch, and when "More to explore" may start. */
class YumixRefreshTest {
    private fun item(api: String, n: Int) = OnlineItem("$api $n", "u/$api/$n", api, "p/$api/$n")
    private fun row(api: String, title: String, count: Int, from: Int = 0) =
        OnlineRow(api, title, (from until from + count).map { item(api, it) }, pageData = title)

    private val live = listOf(row("A", "Popular", 5), row("A", "Latest", 5, 10), row("A", "Movies", 5, 20))

    @Test fun offlineRefreshKeepsTheRowsOnScreen() {
        // Offline every row fails; the repository answers an empty list rather than an error.
        assertEquals(live, YumixFeed.homeAfterFetch(live, emptyList()))
        assertEquals(live, YumixFeed.homeAfterFetch(live, null))
    }

    @Test fun refreshWithFewerRowsKeepsEveryRowInItsPlace() {
        val fresh = listOf(row("A", "Latest", 4, 200), row("A", "New row", 3, 300))
        val out = YumixFeed.homeAfterFetch(live, fresh)!!
        assertEquals(listOf("Popular", "Latest", "Movies", "New row"), out.map { it.title })
        assertEquals(live[0], out[0]) // not brought back: kept as it was
        assertEquals(fresh[0], out[1]) // the fresh copy wins for rows it brought back
        assertEquals(live[2], out[2])
    }

    @Test fun aRefreshThatBringsOnlyWhatIsShownIsNotNew() {
        // Offline the drama catalog answers from its shelf cache: the same titles come back.
        assertFalse(YumixFeed.broughtNew(live, live))
        assertFalse(YumixFeed.broughtNew(live, listOf(row("A", "Popular", 3))))
        assertFalse(YumixFeed.broughtNew(live, null))
        assertTrue(YumixFeed.broughtNew(live, listOf(row("A", "Popular", 6)))) // item 5 is new
        assertTrue(YumixFeed.broughtNew(null, live))
    }

    @Test fun firstLoadWithNothingIsReportedNotHidden() {
        assertNull(YumixFeed.homeAfterFetch(null, emptyList()))
        assertNull(YumixFeed.homeAfterFetch(null, null))
        assertNull(YumixFeed.homeAfterFetch(emptyList(), listOf(OnlineRow("A", "Empty", emptyList()))))
        assertEquals(live, YumixFeed.homeAfterFetch(null, live))
    }

    @Test fun anExtensionWithoutRowsNoLongerBlocksMoreToExplore() {
        val ready = listOf("A" to live, "B" to emptyList(), "C" to listOf(row("C", "Top", 4)))
        // mixGroups leaves B out: two sections for three ready extensions.
        val groups = mixGroups(ready, maxGroups = 10)
        assertEquals(2, groups.size)
        assertTrue(YumixFeed.sectionsDone(groups.size, ready))
        assertFalse(YumixFeed.sectionsDone(1, ready))
        assertFalse(YumixFeed.sectionsDone(0, listOf("B" to emptyList())))
    }
}

class YumixGoneTest {
    @Test fun titlesOfAnExtensionThatIsOffAreDropped() {
        val a = OnlineItem("A 1", "u/A/1", "A", null); val b = OnlineItem("B 1", "u/B/1", "Adult", null)
        assertEquals(setOf(b.key), YumixFeed.goneFrom(listOf(a, b), setOf("A")))
        assertTrue(YumixFeed.goneFrom(listOf(a), setOf("A")).isEmpty())
    }
}
