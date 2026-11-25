/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * LIVE-1 (owner list R21, item 1): Live TV channel search and the Country sheet, on a trimmed copy of the real
 * Yumeiro Live playlist (streams branch, playlists/yumeiro_live.m3u: group-title = country, tvg-category = genre).
 */
class LiveFindTest {
    private fun res(name: String) = requireNotNull(javaClass.classLoader!!.getResource("live/$name")).readText()
    private val channels = M3uParser.parse(res("yumeiro_live_sample.m3u"))
    private val feed = LiveChannelFeed.build(listOf("yumeiro"), mapOf("yumeiro" to channels))

    @Test fun parserKeepsTheGenreFromTvgCategory() {
        assertEquals(31, channels.size)
        val atn = channels.first { it.title == "ATN Bangla" }
        assertEquals("Bangladesh", atn.group)
        assertTrue(atn.genre != null && atn.genre!!.isNotBlank())
        assertTrue(feed.genres.any { it.name == "News" })
        assertEquals(10, feed.genres.first { it.name == "News" }.count)
    }

    @Test fun genreFilterNarrowsInsideACountry() {
        val news = LiveChannelFeed.filter(feed, category = "United States", genre = "News")
        assertTrue(news.isNotEmpty())
        assertTrue(news.all { it.category == "United States" && it.channel.genre == "News" })
    }

    @Test fun searchFindsByNameAccentAndQualityBlind() {
        val hit = LiveFind.search(feed.entries, "atn bangla hd")
        assertEquals("ATN Bangla", hit.first().channel.title)
        assertEquals(1, LiveFind.search(feed.entries, "ÁTN").size)
        assertEquals(feed.entries.size, LiveFind.search(feed.entries, "  ").size)
        assertTrue(LiveFind.search(feed.entries, "zzzz-no-such-channel").isEmpty())
    }

    @Test fun searchPutsNameStartsFirstAndMatchesCountry() {
        val bd = LiveFind.search(feed.entries, "bangladesh")
        assertEquals(6, bd.size) // every channel of the country, though none is named "Bangladesh"
        val tv = LiveFind.search(feed.entries, "channel")
        assertTrue(tv.first().channel.displayTitle.lowercase().startsWith("channel"))
    }

    @Test fun countriesComeFromCategoriesWithFlags() {
        assertTrue(LiveFind.categoriesAreCountries(feed))
        val rows = LiveFind.countries(feed, Locale.ENGLISH)
        assertEquals("United States", rows.first().key) // most channels first
        assertTrue(rows.all { it.byCategory })
        assertEquals("BD", rows.first { it.key == "Bangladesh" }.code)
        assertEquals("TR", rows.first { it.key == "Turkiye" }.code)
        assertEquals("CI", rows.first { it.key == "Ivory Coast" }.code)
        assertEquals("\uD83C\uDDE7\uD83C\uDDE9", LiveFind.flag("BD"))
        assertNull(LiveFind.flag("Bangladesh"))
    }

    @Test fun everyRealYumeiroLiveGroupIsACountry() {
        val groups = res("yumeiro_live_groups.txt").lines().filter { it.isNotBlank() }
        assertEquals(171, groups.size)
        val missing = groups.filter { LiveFind.isoForName(it) == null }
        assertTrue("no flag/ISO for: $missing", missing.isEmpty())
    }

    @Test fun countrySearchMatchesNameOrCode() {
        val rows = LiveFind.countries(feed, Locale.ENGLISH)
        assertEquals(listOf("Bangladesh"), LiveFind.filterCountries(rows, "bangla").map { it.key })
        assertEquals(listOf("India"), LiveFind.filterCountries(rows, "IN").map { it.key }.filter { it == "India" })
        assertTrue(LiveFind.filterCountries(rows, "curaçao").isNotEmpty())
        assertTrue(LiveFind.filterCountries(rows, "atlantis").isEmpty())
    }

    @Test fun genreGroupsUseTvgCountryCodes() {
        val list = listOf(
            StreamRequest(url = "a", title = "A", country = "US", group = "News"),
            StreamRequest(url = "b", title = "B", country = "IN", group = "News"),
            StreamRequest(url = "c", title = "C", country = "BD", group = "Sports"),
        )
        val f = LiveChannelFeed.build(listOf("p"), mapOf("p" to list))
        val rows = LiveFind.countries(f, Locale.ENGLISH)
        assertEquals(setOf("US", "IN", "BD"), rows.map { it.code }.toSet())
        assertTrue(rows.none { it.byCategory })
    }
}
