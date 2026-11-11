/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recorded (trimmed, anonymous) YouTube Music responses, read by [YmParser]. */
class YmParserTest {
    private fun load(name: String): YmPage =
        YmParser.parse(javaClass.classLoader!!.getResource("ym/$name")!!.readText())

    @Test fun home_hasQuickPicksAsSongRows_andMoodChips() {
        val page = load("home.json")
        val quick = page.shelves.first()
        assertEquals("Quick picks", quick.title)
        assertEquals(YmShelf.Style.LIST, quick.style)
        val song = quick.items.first() as YmItem.Song
        assertTrue(song.videoId.length == 11)
        assertTrue(song.artistLine.isNotBlank())
        assertTrue(page.chips.isNotEmpty())
        assertNotNull(page.continuation)
        assertTrue(page.shelves.any { it.style == YmShelf.Style.CAROUSEL && it.items.any { i -> i is YmItem.Collection } })
    }

    @Test fun artist_hasHeaderTopSongsAndAlbums() {
        val page = load("artist.json")
        val h = page.header!!
        assertTrue(h.artist)
        assertTrue(h.title.isNotBlank())
        assertTrue(page.shelves.any { it.title == "Top songs" && it.items.first() is YmItem.Song })
        assertTrue(page.shelves.any { it.title == "Albums" && (it.items.first() as YmItem.Collection).kind == YmItem.Collection.Kind.ALBUM })
    }

    @Test fun playlist_hasHeaderAndTracksWithDurations() {
        val page = load("playlist.json")
        assertEquals("Chill R&B", page.header!!.title)
        val tracks = page.shelves.flatMap { it.items }.filterIsInstance<YmItem.Song>()
        assertTrue(tracks.size >= 5)
        assertTrue(tracks.all { it.durationSec > 0 })
    }

    @Test fun search_songsAndAll() {
        val songs = load("search_songs.json").shelves.flatMap { it.items }
        assertTrue(songs.isNotEmpty() && songs.all { it is YmItem.Song })
        val all = load("search_all.json")
        assertTrue(all.shelves.size >= 2)
    }

    @Test fun moods_areGridButtons() {
        val page = load("moods.json")
        val grid = page.shelves.first { it.style == YmShelf.Style.GRID }
        assertTrue(grid.items.all { it is YmItem.Mood })
        assertTrue((grid.items.first() as YmItem.Mood).browse.params != null)
    }

    @Test fun charts_parse() {
        assertTrue(load("charts.json").shelves.isNotEmpty())
    }

    @Test fun durationsAndThumbs() {
        assertEquals(200L, YmParser.seconds("3:20"))
        assertEquals(3723L, YmParser.seconds("1:02:03"))
        assertEquals(0L, YmParser.seconds("1.2M plays"))
    }
}
