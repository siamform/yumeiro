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

    @Test fun refreshWithFewerRowsKeepsTheMissingOnesAfterTheFreshOnes() {
        val fresh = listOf(row("A", "Popular", 6, 100), row("A", "Latest", 4, 200))
        val out = YumixFeed.homeAfterFetch(live, fresh)!!
        assertEquals(listOf("Popular", "Latest", "Movies"), out.map { it.title })
        assertEquals(fresh[0], out[0]) // the fresh copy wins for rows it brought back
        assertEquals(live[2], out[2])
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
