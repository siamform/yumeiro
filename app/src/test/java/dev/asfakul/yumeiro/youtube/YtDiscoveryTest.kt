/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDiscoveryTest {
    private fun song(id: String, channel: String, title: String = "Song $id") = YtItem(id, "https://music.youtube.com/watch?v=$id", title, channel, music = true)
    private fun video(id: String, channel: String) = YtItem(id, "https://www.youtube.com/watch?v=$id", "Video $id", channel)

    @Test fun newUserStillGetsAVariedPage() {
        val f = YtDiscovery.plan(YtDiscovery.Input(true, emptyList(), YtDiscovery.DEFAULT_MUSIC_TOPICS, emptyList(), rotation = 1))
        assertTrue(f.size >= 5)
        assertTrue(f.any { it.kind == YtFeed.Kind.KIOSK })
        assertEquals(3, f.count { it.overline == "Explore" })
        assertTrue(f.all { it.music })
    }
    @Test fun historyDrivesTheFirstShelves() {
        val h = listOf(song("a", "Yoasobi - Topic"), song("b", "Yoasobi"), song("c", "Aimer"), video("v", "Some channel"))
        val f = YtDiscovery.plan(YtDiscovery.Input(true, h, emptyList(), emptyList(), rotation = 1))
        assertEquals(YtFeed.Kind.RELATED, f[0].kind); assertEquals("a", f[0].arg)
        assertEquals(YtFeed.Kind.RELATED, f[1].kind); assertEquals("b", f[1].arg)
        // Yoasobi played twice (Topic suffix folded) → artist shelf; Aimer once → none.
        assertTrue(f.any { it.title == "Yoasobi" && it.arg == "Yoasobi songs" })
        assertFalse(f.any { it.title == "Aimer" })
        // Video history never leaks into the music page.
        assertFalse(f.any { it.arg == "v" })
    }
    @Test fun topicsAndSearchesAreIncludedWithoutDuplicates() {
        val f = YtDiscovery.plan(YtDiscovery.Input(false, emptyList(), listOf("Frieren", "AMV"), listOf("frieren", "speedrun"), rotation = 3))
        assertEquals(1, f.count { YtDiscovery.fold(it.arg) == "frieren" })
        assertTrue(f.any { it.arg == "speedrun" && it.overline == "From your searches" })
        assertEquals(f.size, f.map { it.id }.toSet().size)
    }
    @Test fun exploreMixRotatesButIsStableForTheSameDay() {
        fun explore(r: Long) = YtDiscovery.plan(YtDiscovery.Input(false, emptyList(), emptyList(), emptyList(), r)).filter { it.overline == "Explore" }.map { it.arg }
        assertEquals(explore(20_000), explore(20_000))
        val days = (20_000L..20_006L).map { explore(it) }.toSet()
        assertTrue("explore mix should change across a week", days.size > 1)
    }
    @Test fun capRespected() {
        val f = YtDiscovery.plan(YtDiscovery.Input(false, emptyList(), (1..20).map { "topic $it" }, emptyList(), 1, maxFeeds = 8))
        assertEquals(8, f.size)
    }
    @Test fun topicEditing() {
        var t = YtDiscovery.withTopic(listOf("J-Pop", "Lo-fi"), "  city   pop ")
        assertEquals(listOf("city pop", "J-Pop", "Lo-fi"), t)
        t = YtDiscovery.withTopic(t, "jpop") // same as J-Pop → moved to front, not duplicated
        assertEquals(listOf("jpop", "city pop", "Lo-fi"), t)
        assertEquals(listOf("city pop", "Lo-fi"), YtDiscovery.withoutTopic(t, "J POP"))
        assertNull(YtDiscovery.cleanTopic(" "))
        assertNull(YtDiscovery.cleanTopic("x".repeat(80)))
    }
    @Test fun foldIgnoresCaseAccentsAndPunctuation() {
        assertEquals(YtDiscovery.fold("J-Pop"), YtDiscovery.fold("jpop"))
        assertEquals(YtDiscovery.fold("Pokémon"), YtDiscovery.fold("pokemon"))
        assertNotEquals(YtDiscovery.fold("lofi"), YtDiscovery.fold("lo fi hip hop"))
    }
    @Test fun relatedChipsSkipQueryAndExisting() {
        val r = YtDiscovery.relatedChips("frieren", listOf("frieren", "Frieren opening", "frieren ost", "AMV"), existing = listOf("amv"))
        assertEquals(listOf("Frieren opening", "frieren ost"), r)
    }
    @Test fun feedIdsAreStable() {
        val a = YtFeed(YtFeed.Kind.QUERY, "City Pop", "City Pop", "Your topic", true)
        val b = YtFeed(YtFeed.Kind.QUERY, "city-pop", "city-pop", "Explore", true)
        assertEquals(a.id, b.id)
        assertTrue(a.pages)
        assertFalse(YtFeed(YtFeed.Kind.KIOSK, "live", "Live", "Trending", false).pages)
    }
}
