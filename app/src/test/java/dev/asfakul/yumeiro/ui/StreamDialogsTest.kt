/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dev.asfakul.yumeiro.ui.screens.stream.PlaylistEditDialog
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StreamDialogsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun localDialogIdles() {
        rule.setContent { MTPlayerTheme { PlaylistEditDialog(null, remote = false, onDismiss = {}, onSave = { _, _ -> }) } }
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_name_field").assertExists()
    }

    @Test fun streamScreenNewPlaylistDialogIdles() {
        rule.setContent { MTPlayerTheme { dev.asfakul.yumeiro.ui.screens.stream.StreamScreen() } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("stream_screen").fetchSemanticsNodes().isNotEmpty() }
        // NBUG-15 (c): playlists are managed on their own page, Tools → Playlists.
        rule.onNodeWithTag("live_tools_button", true).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("live_tool_playlists", true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("live_tool_playlists", true).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("playlists_list").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("playlists_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("playlist_new"))
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("playlist_new", true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("playlist_new", true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_name_field").performTextInput("My list")
        rule.onNodeWithTag("playlist_save").performClick()
        rule.waitUntil(10_000) { dev.asfakul.yumeiro.stream.PlaylistStore.playlists.value.any { it.name == "My list" && it.isLocal } }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("playlist_name_field", true).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun remoteDialogIdles() {
        rule.setContent { MTPlayerTheme { PlaylistEditDialog(null, remote = true, onDismiss = {}, onSave = { _, _ -> }) } }
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_url_field").assertExists()
    }

    /** Round 8: Live TV opens on channels (like Yumix); a channel group filter and the link page work. */
    @Test fun liveHubShowsChannelsAndLinkPage() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        java.io.File(ctx.filesDir, "streams").deleteRecursively()
        dev.asfakul.yumeiro.stream.PlaylistStore.resetForTests()
        val p = kotlinx.coroutines.runBlocking {
            dev.asfakul.yumeiro.stream.PlaylistStore.init(ctx)
            dev.asfakul.yumeiro.stream.PlaylistStore.playlists.value.forEach { dev.asfakul.yumeiro.stream.PlaylistStore.delete(ctx, it.id) }
            dev.asfakul.yumeiro.stream.PlaylistStore.createLocal(ctx, "Hub list", (1..120).map {
                dev.asfakul.yumeiro.stream.StreamRequest("https://cdn.example/$it.m3u8", "Channel $it", group = if (it % 2 == 0) "News" else "Sports")
            })
        }
        rule.setContent { MTPlayerTheme { dev.asfakul.yumeiro.ui.screens.stream.StreamScreen() } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("live_channel", true).fetchSemanticsNodes().isNotEmpty() }
        // NBUG-15 (b): the channels show as a grid under category headings, with category chips.
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("live_grid_row", true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("live_categories", true).assertExists()
        rule.onNodeWithTag("live_cat_h_Sports", true).assertExists()
        // Tools -> Play link opens the link page; back returns to the hub.
        rule.onNodeWithTag("live_tools_button", true).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("live_tool_link", true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("live_tool_link", true).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("stream_url", true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("stream_link_back", true).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("stream_screen").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithTag("stream_url", true).fetchSemanticsNodes().isEmpty() }
    }
}
