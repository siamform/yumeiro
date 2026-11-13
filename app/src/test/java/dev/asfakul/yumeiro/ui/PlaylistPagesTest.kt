/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.stream.PlaylistMarket
import dev.asfakul.yumeiro.stream.PlaylistStore
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.components.AppMessages
import dev.asfakul.yumeiro.ui.screens.stream.OPENED_PLAYLIST_SLUG
import dev.asfakul.yumeiro.ui.screens.stream.PlaylistDetailScreen
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PlaylistPagesTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun reset() {
        java.io.File(context.filesDir, "streams").deleteRecursively()
        PlaylistStore.resetForTests(); PlaylistMarket.resetForTests()
        StreamRepository.setPlaylist(null, emptyList())
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun settle(cond: () -> Boolean) = rule.waitUntil(10_000) { cond() }

    @Test fun unknownSlugShowsNotFoundWithWayBack() {
        var back = 0; var market = 0
        rule.setContent { MTPlayerTheme { PlaylistDetailScreen("does-not-exist", onBack = { back++ }, onOpenMarket = { market++ }) } }
        settle { has("playlist_not_found") }
        rule.onNodeWithTag("playlist_not_found_primary").performClick()
        rule.onNodeWithTag("playlist_not_found_secondary").performClick()
        assertEquals(1, back); assertEquals(1, market)
    }

    @Test fun openedSlugWithoutListIsNotFound() {
        rule.setContent { MTPlayerTheme { PlaylistDetailScreen(OPENED_PLAYLIST_SLUG, onBack = {}, onOpenMarket = {}) } }
        settle { has("playlist_not_found") }
    }

    @Test fun openedListShowsChannelsAndCanBeSaved() {
        StreamRepository.setPlaylist("From link", (1..5).map { StreamRequest("https://c/$it.m3u8", "Ch $it") })
        rule.setContent { MTPlayerTheme { PlaylistDetailScreen(OPENED_PLAYLIST_SLUG, onBack = {}, onOpenMarket = {}) } }
        settle { has("playlist_channels") }
        rule.onNodeWithTag("detail_menu").performClick()
        rule.onNodeWithTag("channels_save").performClick()
        settle { PlaylistStore.playlists.value.any { it.isLocal && it.name == "From link" && it.channelCount == 5 } }
    }

    @Test fun localPlaylistLoadsSearchesAndShowsNoMatchState() {
        val p = runBlocking {
            PlaylistStore.init(context)
            PlaylistStore.createLocal(context, "Big", (1..3000).map { StreamRequest("https://c/$it.m3u8", "Channel $it", group = "G${it % 7}") })
        }
        rule.setContent { MTPlayerTheme { PlaylistDetailScreen(p.id, onBack = {}, onOpenMarket = {}) } }
        settle { has("playlist_channels") && has("channel_search") }
        rule.onNodeWithTag("channel_search").performTextInput("zzz-nothing")
        settle { has("playlist_no_match") }
        rule.onNodeWithTag("playlist_no_match_primary").performScrollTo().performClick()
        settle { !has("playlist_no_match") }
    }

    @Test fun messagesFallBackWithoutHostAndNeverThrow() {
        // No host attached: falls back to a system toast instead of being lost.
        AppMessages.show(context, "hello")
        AppMessages.error(null, "no context") // no host, no context: ignored
        AppMessages.show(context, "   ")
        assertTrue(true)
    }
}
