/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.asfakul.mtrevanced.ui.screens.stream.PlaylistEditDialog
import dev.asfakul.mtrevanced.ui.theme.MTPlayerTheme
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
        rule.setContent { MTPlayerTheme { dev.asfakul.mtrevanced.ui.screens.stream.StreamScreen() } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("playlist_new", true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("playlist_new", true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_name_field").performTextInput("My list")
        rule.onNodeWithTag("playlist_save").performClick()
        rule.waitUntil(10_000) { dev.asfakul.mtrevanced.stream.PlaylistStore.playlists.value.any { it.name == "My list" && it.isLocal } }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("playlist_name_field", true).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun remoteDialogIdles() {
        rule.setContent { MTPlayerTheme { PlaylistEditDialog(null, remote = true, onDismiss = {}, onSave = { _, _ -> }) } }
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_url_field").assertExists()
    }
}
