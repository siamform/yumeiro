package dev.asfakul.yumeiro.ui.watch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import dev.asfakul.yumeiro.ui.screens.video.TrackGroups
import dev.asfakul.yumeiro.ui.screens.watch.SERVER_PAGE
import dev.asfakul.yumeiro.ui.screens.watch.WatchServer
import dev.asfakul.yumeiro.ui.screens.watch.WatchServerRow
import dev.asfakul.yumeiro.ui.screens.watch.openGroupIndex
import dev.asfakul.yumeiro.ui.screens.watch.visibleServerCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceListLogicTest {
    private fun row(n: Int, playing: Int = -1) = WatchServerRow("g", Icons.Default.Mic, (0 until n).map { WatchServer("s$it", it == playing, "t") {} })

    @Test fun onlyThePlayingGroupOpens() {
        assertEquals(1, openGroupIndex(listOf(row(3), row(4, playing = 2), row(2))))
        assertEquals(0, openGroupIndex(listOf(row(3), row(4))))
    }

    @Test fun pagingGrowsAndAlwaysShowsThePlayingSource() {
        assertEquals(SERVER_PAGE, visibleServerCount(50, -1, 1))
        assertEquals(20, visibleServerCount(50, -1, 2))
        assertEquals(31, visibleServerCount(50, 30, 1))
        assertEquals(5, visibleServerCount(5, -1, 1))
    }

    @Test fun tracksGroupByLanguageWithOtherLast() {
        val g = TrackGroups.group(listOf("en", "hi", null, "en", "und", "ja"))
        assertEquals(listOf("English", "Hindi", "Japanese", "Other"), g.map { it.label })
        assertEquals(listOf(0, 3), g[0].indices)
        assertEquals(listOf(2, 4), g[3].indices)
        assertEquals("Hindi", TrackGroups.openKey(g, 1))
        assertEquals("English", TrackGroups.openKey(g, -1))
        assertTrue(TrackGroups.shouldGroup(g, 6))
        assertFalse(TrackGroups.shouldGroup(g, 3))
        assertFalse(TrackGroups.shouldGroup(TrackGroups.group(listOf("en", "en", "en", "en", "en", "en")), 6))
    }
}
