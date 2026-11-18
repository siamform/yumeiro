/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NBUG-15 (b) (NB15-B1..B3, ADR-028): the Live TV feed merged from every playlist and grouped by category.
 * Pure model; the device proof (fail-before on the old page) is Nbug15RedesignDeviceTest.
 */
class LiveChannelFeedTest {
    private fun ch(url: String, group: String?) = StreamRequest(url = "https://cdn.example/$url.m3u8", title = url, group = group)

    @Test fun categoriesMergeAcrossPlaylistsCaseBlindAndSemicolonLists() {
        val feed = LiveChannelFeed.build(
            listOf("a", "b"),
            mapOf(
                "a" to listOf(ch("a1", "News"), ch("a2", "Sports;HD"), ch("a3", " News ")),
                "b" to listOf(ch("b1", "news"), ch("b2", "sports"), ch("b3", null), ch("b4", "  ")),
            ),
        )
        assertEquals(listOf("News" to 3, "Sports" to 2, "Other" to 2), feed.categories.map { it.name to it.count })
        assertEquals(listOf("a1", "a3", "b1", "a2", "b2", "b3", "b4"), feed.entries.map { it.channel.title })
        assertEquals("b", feed.entries.first { it.channel.title == "b1" }.playlistId)
    }

    @Test fun sameStreamInTwoPlaylistsShowsOnceFirstCopyWins() {
        val dup = ch("same", "News")
        val feed = LiveChannelFeed.build(listOf("pinned", "other"), mapOf("other" to listOf(dup, ch("o", "News")), "pinned" to listOf(dup.copy(title = "pinned copy"))))
        assertEquals(2, feed.size)
        assertEquals("pinned copy", feed.entries.first().channel.title)
        assertEquals("pinned", feed.entries.first().playlistId)
    }

    @Test fun otherIsAlwaysLastAndTiesKeepFirstSeenOrder() {
        val feed = LiveChannelFeed.build(listOf("a"), mapOf("a" to listOf(ch("1", null), ch("2", null), ch("3", null), ch("4", "Kids"), ch("5", "Music"))))
        assertEquals(listOf("Kids", "Music", "Other"), feed.categories.map { it.name })
    }

    @Test fun unloadedPlaylistsAreSkipped() {
        val feed = LiveChannelFeed.build(listOf("a", "missing"), mapOf("a" to listOf(ch("1", "News"))))
        assertEquals(1, feed.size)
    }

    @Test fun pagingShowsFiftyThenFiftyMoreNeverPastTheEnd() {
        assertEquals(50, LiveChannelFeed.PAGE)
        assertEquals(100, LiveChannelFeed.nextLimit(50, 120))
        assertEquals(120, LiveChannelFeed.nextLimit(100, 120))
        assertEquals(120, LiveChannelFeed.nextLimit(120, 120))
        assertEquals(50, LiveChannelFeed.nextLimit(50, 30))
    }

    @Test fun rowsCutTheFirstLimitIntoHeadingsAndTileRows() {
        val chans = (1..30).map { ch("n$it", "News") } + (1..30).map { ch("s$it", "Sports") }
        val feed = LiveChannelFeed.build(listOf("a"), mapOf("a" to chans))
        val counts = feed.categories.associate { it.name to it.count }
        val rows = LiveChannelFeed.rows(feed.entries, 50, 4, counts)
        val tiles = rows.filterIsInstance<LiveChannelFeed.Row.Tiles>()
        assertEquals(50, tiles.sumOf { it.entries.size })
        assertEquals(listOf("News" to 30, "Sports" to 30), rows.filterIsInstance<LiveChannelFeed.Row.Header>().map { it.name to it.count })
        assertTrue("a row never mixes two categories", tiles.all { r -> r.entries.all { it.category == r.category } })
        assertTrue("rows hold at most 4 tiles", tiles.all { it.entries.size <= 4 })
        assertEquals("keys are unique", rows.size, rows.map { it.key }.toSet().size)
        // The key's last part is the index of its first tile (StreamScreen pages from it).
        assertEquals(46, tiles.last().key.substringAfterLast('_').toInt())
        val filtered = LiveChannelFeed.rows(LiveChannelFeed.filter(feed, "sports"), 50, 4, counts, headers = false)
        assertTrue(filtered.none { it is LiveChannelFeed.Row.Header })
        assertEquals(30, filtered.sumOf { (it as LiveChannelFeed.Row.Tiles).entries.size })
    }

    @Test fun pinnedPlaylistsComeFirst() {
        assertEquals(listOf("c", "a", "b"), PinnedPlaylists.pinnedFirst(listOf("a", "b", "c"), listOf("c", "gone")))
    }
}
