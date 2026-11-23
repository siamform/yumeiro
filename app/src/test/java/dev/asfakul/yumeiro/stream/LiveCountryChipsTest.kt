/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * UX-LIVE-1 (probe 45143962374): Yumeiro Live groups channels by country, so the Live TV page showed the same filter
 * twice ("United States · 1386" as a category chip and again as a country chip). Country chips only when they add a
 * filter the categories don't already give.
 */
class LiveCountryChipsTest {
    private fun ch(url: String, country: String?, group: String) = StreamRequest(url = url, title = url, country = country, group = group)

    @Test fun countryNamedGroupsHideTheCountryChips() {
        val list = listOf(
            ch("a", "US", "United States"), ch("b", "US", "United States"), ch("c", "IN", "India"),
            ch("d", "BD", "Bangladesh"), ch("e", "IN", "Sports"), // one channel in a non-country group (80 % rule)
        )
        val feed = LiveChannelFeed.build(listOf("p"), mapOf("p" to list))
        assertTrue(feed.countriesAreCategories)
        assertFalse(feed.showCountryChips)
    }

    @Test fun phoneLanguageNamesCountToo() {
        val list = listOf(ch("a", "DE", "Allemagne"), ch("b", "FR", "France"))
        val feed = LiveChannelFeed.build(listOf("p"), mapOf("p" to list))
        assertTrue(LiveChannelFeed.countriesRepeatCategories(feed, Locale.FRENCH))
    }

    @Test fun genreGroupsKeepTheCountryChips() {
        val list = listOf(
            ch("a", "US", "News"), ch("b", "IN", "News"), ch("c", "BD", "Sports"), ch("d", "IN", "Movies"),
        )
        val feed = LiveChannelFeed.build(listOf("p"), mapOf("p" to list))
        assertFalse(feed.countriesAreCategories)
        assertTrue(feed.showCountryChips)
    }

    @Test fun oneCountryNeverShowsChips() {
        val feed = LiveChannelFeed.build(listOf("p"), mapOf("p" to listOf(ch("a", "BD", "News"), ch("b", "BD", "Sports"))))
        assertFalse(feed.showCountryChips)
    }
}
