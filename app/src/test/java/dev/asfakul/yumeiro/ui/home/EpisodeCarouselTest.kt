/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import dev.asfakul.yumeiro.notify.Inbox
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.ui.screens.home.EpisodeCarousel
import dev.asfakul.yumeiro.ui.screens.home.EpisodesEmpty
import dev.asfakul.yumeiro.ui.screens.home.episodeCards
import dev.asfakul.yumeiro.ui.theme.LocalMtPalette
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Home "New episodes" carousel. Bookmarks here are TEST FIXTURES (no posters, invented names),
 * used only to check ordering, dates, paging and tap-through; the app itself only ever shows the
 * user's own My List entries and the dates of real new-episode alerts.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-night-xxhdpi")
class EpisodeCarouselTest {
    @get:Rule val rule = createComposeRule()

    private fun bm(name: String, newEp: Int, eps: Int, status: OnlineBookmarks.Status = OnlineBookmarks.Status.WATCHING, added: Long = 0) =
        OnlineBookmarks.Bookmark(api = "Fixture", url = "https://example.invalid/$name", name = name, posterUrl = null, posterHeaders = emptyMap(),
            type = "Anime", status = status, addedAt = added, episodes = eps, newEpisodes = newEp)

    @Test fun ordersNewFirstAndDatesFromRealAlertsOnly() {
        val a = bm("Fixture A", 0, 12, added = 5); val b = bm("Fixture B", 2, 24); val c = bm("Fixture C", 1, 8)
        val done = bm("Fixture Done", 0, 10, status = OnlineBookmarks.Status.COMPLETED)
        val alert = Inbox.Item("ep-${b.key}-123", Inbox.Kind.EPISODE, "New episode", "", at = 1_700_000_000_000)
        val cards = episodeCards(listOf(a, b, c, done), listOf(alert))
        assertEquals(listOf("Fixture B", "Fixture C", "Fixture A"), cards.map { it.bookmark.name })
        assertEquals(1_700_000_000_000, cards[0].foundAt)
        assertNull("no alert, no date — never invented", cards[1].foundAt)
        assertNull(cards[2].foundAt)
    }

    @Test fun pagesAndOpensTheTappedSeries() {
        val cards = episodeCards(listOf(bm("Fixture B", 2, 24), bm("Fixture C", 1, 8), bm("Fixture A", 0, 12)), emptyList())
        var opened: String? = null
        rule.setContent {
            MTPlayerTheme { Box(Modifier.fillMaxSize().background(LocalMtPalette.current.background).padding(16.dp)) { EpisodeCarousel(cards) { opened = it.bookmark.name } } }
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("home_episode_Fixture B", true).fetchSemanticsNodes().isNotEmpty())
        snap("carousel_page1")
        rule.onNodeWithTag("home_episode_Fixture B", true).performClick()
        rule.waitForIdle()
        assertEquals("Fixture B", opened)
        // Swiping moves to another series (a fling may skip ahead, like a real swipe).
        rule.onNodeWithTag("home_next_episodes", true).performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        snap("carousel_page2")
        assertTrue(rule.onAllNodesWithTag("home_episode_Fixture A", true).fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithTag("home_episode_Fixture C", true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun emptyStateOffersTheRealNextStep() {
        var add = 0; var find = 0
        rule.setContent { MTPlayerTheme { Box(Modifier.background(LocalMtPalette.current.background).padding(16.dp)) { EpisodesEmpty(hasSources = false, onFind = { find++ }, onAddSources = { add++ }) } } }
        rule.onNodeWithTag("home_episodes_action", true).performClick()
        assertEquals(1, add); assertEquals(0, find)
    }

    private fun snap(name: String) {
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        val img = rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(dir, "fixture_$name.png").outputStream().use { img.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
    }
}
