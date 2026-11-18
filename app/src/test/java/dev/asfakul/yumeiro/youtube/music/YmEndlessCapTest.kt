/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube.music

import dev.asfakul.yumeiro.youtube.YtItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * G6-PG2: the endless Music Home grows by up to [YmEndless.MAX_SHELVES] (80) "Similar to" shelves.
 * `use()` stores the seed and its artist, and `nextSeed` counted both, so the feed ended at 40.
 */
class YmEndlessCapTest {
    private fun id(i: Int) = "v" + i.toString().padStart(10, '0')
    private val history = (0 until 120).map { YtItem(id = id(it), url = "", title = "Song $it", channel = "Artist $it") }

    @Test fun eightyShelvesFromDifferentArtists() {
        var used = emptySet<String>()
        var seeds = 0
        while (true) {
            val s = YmEndless.nextSeed(history, emptyList(), used) ?: break
            used = YmEndless.use(used, s); seeds++
            if (seeds > 200) break
        }
        assertEquals("seeds before the cap", YmEndless.MAX_SHELVES, seeds)
        assertNull(YmEndless.nextSeed(history, emptyList(), used))
    }
}
