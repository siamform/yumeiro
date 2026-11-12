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
import org.junit.Assert.assertTrue
import org.junit.Test

class YumixFeedTest {
    private fun item(api: String, n: Int, type: String? = null) = OnlineItem("$api $n", "u/$api/$n", api, "p/$api/$n", type = type)
    private fun row(api: String, title: String, count: Int, type: String? = null, from: Int = 0) =
        OnlineRow(api, title, (from until from + count).map { item(api, it, type) })

    @Test fun latestRowsAreRecognised() {
        listOf("Latest Episodes", "Recently Updated", "New Episodes", "New Releases", "Ongoing", "Airing Today", "最新").forEach {
            assertTrue(it, YumixFeed.isLatestRow(it))
        }
        listOf("Popular", "Top Rated", "Movies", "Romance", "Trending Anime").forEach { assertFalse(it, YumixFeed.isLatestRow(it)) }
    }

    @Test fun latestIsRoundRobinAcrossExtensions() {
        val feed = listOf(
            "A" to listOf(row("A", "Popular", 5), row("A", "Latest Episodes", 10, from = 100)),
            "B" to listOf(row("B", "Recently Updated", 10)),
        )
        val out = YumixFeed.latest(feed, max = 6)
        assertEquals(listOf("A", "B", "A", "B", "A", "B"), out.map { it.api })
        assertTrue(out.none { it.name == "A 0" }) // "Popular" isn't a latest row
    }

    @Test fun categoriesComeFromTypesAndRowTitlesAndSkipThinOnes() {
        val feed = listOf(
            "Anix" to listOf(row("Anix", "Trending", 6, type = "Anime")),
            "Flix" to listOf(row("Flix", "Romance Movies", 5, type = "Movie"), row("Flix", "Top Football", 4, from = 50)),
            "Tiny" to listOf(row("Tiny", "Comedy", 2)),
        )
        val cats = YumixFeed.categories(feed).associate { it.first to it.second.size }
        assertEquals(6, cats[YumixFeed.Category.ANIME])
        assertEquals(5, cats[YumixFeed.Category.MOVIES])
        assertEquals(5, cats[YumixFeed.Category.ROMANCE])
        assertEquals(4, cats[YumixFeed.Category.SPORTS])
        assertFalse(YumixFeed.Category.COMEDY in cats) // only 2 titles: no dead-end chip
        assertFalse(YumixFeed.Category.LIVE in cats)
    }

    @Test fun bannerMixesNewEpisodesEverySecondSlide() {
        val spot = (0 until 5).map { item("S", it) }
        val fresh = (0 until 5).map { item("L", it) }
        val (slides, newKeys) = YumixFeed.bannerSlides(spot, fresh, max = 8, latestCount = 3)
        assertEquals(listOf("S", "L", "S", "L", "S", "L", "S", "S"), slides.map { it.api })
        assertEquals(3, newKeys.size)
        assertTrue(slides.filter { it.key in newKeys }.all { it.api == "L" })
    }

    @Test fun bannerWorksWithOnlyNewEpisodes() {
        val (slides, newKeys) = YumixFeed.bannerSlides(emptyList(), (0 until 4).map { item("L", it) })
        assertEquals(3, slides.size)
        assertEquals(3, newKeys.size)
    }

    @Test fun blockShuffleIsStableAndKeepsEarlierBlocks() {
        val keys = (0 until 70).map { "k$it" }
        val a = YumixFeed.blockShuffle(keys, 42L) { it }
        val b = YumixFeed.blockShuffle(keys, 42L) { it }
        assertEquals(a, b)
        assertEquals(keys.toSet(), a.toSet())
        assertTrue(a != keys) // actually shuffled
        // Loading a next page never reorders what's already on screen.
        val more = YumixFeed.blockShuffle(keys + (70 until 100).map { "k$it" }, 42L) { it }
        assertEquals(a.take(60), more.take(60))
        assertEquals(keys.take(30).toSet(), a.take(30).toSet())
    }

    @Test fun shortEpisodeLabel() {
        assertEquals("E5", shortEpisode("S1 · E5"))
        assertEquals("E12", shortEpisode("Episode 12"))
        assertEquals("E3", shortEpisode("Ep. 3 — The Gate"))
        assertEquals("Special", shortEpisode("Special"))
    }
}
