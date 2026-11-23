/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.online.upcoming.Upcoming
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * UP-BANNER-1 / UP-FILTER-1 (owner list R21, item 16): the Yumix "Coming up" card and the Upcoming page's three
 * filter buttons, on saved calendars (fresh, so nothing is downloaded).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1400dp")
class UpcomingFiltersUiTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val today = LocalDate.now()
    private fun count(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().size
    private fun shows(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun click(tag: String) { rule.onAllNodesWithTag(tag, true)[0].performClick(); rule.waitForIdle() }
    private fun e(t: String, day: Long, rank: Int, genres: List<String> = emptyList(), country: String? = null) =
        Upcoming.Entry(t, today.plusDays(day), rank, "https://simkl.in/posters/x_ca.jpg", 1, 2, null, t.hashCode().toLong(), country = country, genres = genres)

    @Before fun saveCalendars() {
        val lists = mapOf(
            Upcoming.Kind.SERIES to listOf(e("Series Today", 0, 1, listOf("Drama"), "US"), e("Series Later", 5, 2, listOf("Comedy"), "GB")),
            Upcoming.Kind.MOVIES to listOf(e("Movie Tomorrow", 1, 3)),
            Upcoming.Kind.ANIME to listOf(e("Anime In Two", 2, 4)),
        )
        lists.forEach { (k, l) -> File(ctx.cacheDir, "upcoming_${k.file}.json").writeText(Upcoming.encodeCache(l)) }
    }

    @Test fun bannerRotatesThroughTheNextThreeDaysAndOpensSearch() {
        var searched: String? = null
        var opened = 0
        rule.setContent { MTPlayerTheme { UpcomingBanner(onOpenUpcoming = { opened++ }, onSearch = { searched = it }) } }
        rule.waitUntil(5_000) { count("upcoming_banner_slide") > 0 }
        assertTrue("Coming up card", count("upcoming_banner") == 1)
        assertTrue(shows("Series Today"))
        // 5 days out is not "coming up"; the 3-day window holds 3 titles, one per kind.
        rule.mainClock.advanceTimeBy(5_500); rule.waitForIdle()
        rule.mainClock.advanceTimeBy(5_500); rule.waitForIdle()
        assertTrue("rotated to a later slide", shows("Movie Tomorrow") || shows("Anime In Two"))
        assertTrue(!shows("Series Later"))
        rule.onAllNodesWithTag("upcoming_banner_slide", true)[0].performClick(); rule.waitForIdle()
        assertTrue("slide tap searches: $searched", searched in setOf("Series Today", "Movie Tomorrow", "Anime In Two"))
        click("upcoming_banner_all")
        assertEquals(1, opened)
    }

    @Test fun threeFilterButtonsOpenTheirSheets() {
        rule.setContent { MTPlayerTheme { UpcomingScreen(onBack = {}, onSearch = {}) } }
        rule.waitUntil(5_000) { count("upcoming_entry") > 0 }
        listOf("upcoming_category_button", "upcoming_country_button", "upcoming_date_button").forEach { assertEquals(it, 1, count(it)) }
        assertEquals("no fixed chip row on the page", 0, count("upcoming_kind_series"))

        click("upcoming_category_button")
        rule.waitUntil(5_000) { count("upcoming_category_sheet") > 0 }
        assertTrue("genres from the list", count("upcoming_genre_drama") == 1 && count("upcoming_genre_comedy") == 1)
        click("upcoming_genre_drama")
        click("upcoming_category_sheet_done")
        rule.waitUntil(5_000) { count("upcoming_category_sheet") == 0 }
        assertTrue(shows("Series Today") && !shows("Series Later"))
        assertTrue("button shows the genre", shows("Series · Drama"))

        click("upcoming_country_button")
        rule.waitUntil(5_000) { count("upcoming_country_sheet") > 0 }
        assertTrue("channel countries from the list", count("upcoming_channel_us") == 1 && count("upcoming_channel_gb") == 1)
        click("upcoming_country_sheet_done")
        rule.waitUntil(5_000) { count("upcoming_country_sheet") == 0 }

        click("upcoming_category_button")
        rule.waitUntil(5_000) { count("upcoming_category_sheet") > 0 }
        click("upcoming_kind_movies")
        click("upcoming_category_sheet_done")
        rule.waitUntil(5_000) { shows("Movie Tomorrow") }

        click("upcoming_date_button")
        rule.waitUntil(5_000) { count("upcoming_date_sheet") > 0 }
        assertEquals("days from the list", 1, count("upcoming_day_${today.plusDays(1)}"))
    }

    @Test fun countrySearchCoversEveryIsoCountry() {
        val r = UpcomingCountries.search("bangla", listOf("US", "GB"))
        assertEquals(listOf("BD"), r)
        assertTrue(UpcomingCountries.search("us", listOf("US")).first() == "US")
        assertTrue(UpcomingCountries.label("JP").endsWith("Japan"))
        assertEquals("Streaming", UpcomingCountries.label("WEB"))
    }
}
