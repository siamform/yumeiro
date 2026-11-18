/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube.music

import dev.asfakul.yumeiro.youtube.YtItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * G6-PG2: a "Similar to" call that brings nothing back while offline gives its seed back and is not
 * a miss (six misses close the endless feed for the session). Online, nothing back stays a miss.
 */
class YmEndlessOfflineTest {
    private val history = listOf(YtItem(id = "aaaaaaaaaa1", url = "", title = "One", channel = "A"), YtItem(id = "bbbbbbbbbb1", url = "", title = "Two", channel = "B"))
    private val shelf = YmShelf("Similar to One", null, listOf(YmItem.Song("ccccccccccc", "x", listOf(YmRef("C")))), YmShelf.Style.LIST)

    @Test fun offlineGivesTheSeedBackAndCountsNoMiss() {
        val before = emptySet<String>()
        val seed = YmEndless.nextSeed(history, emptyList(), before)!!
        val after = YmEndless.use(before, seed)
        var used = before; var misses = 0
        repeat(10) { val r = YmEndless.afterCall(used, YmEndless.use(used, seed), misses, null, offline = true); used = r.first; misses = r.second }
        assertEquals(before, used)
        assertEquals(0, misses)
        assertEquals("same seed tried again once back online", seed, YmEndless.nextSeed(history, emptyList(), used))
        assertEquals(after to 0, YmEndless.afterCall(before, after, 3, shelf, offline = false))
        assertEquals(after to 4, YmEndless.afterCall(before, after, 3, null, offline = false))
        assertEquals(1, YmEndless.seedsUsed(after))
    }
}
