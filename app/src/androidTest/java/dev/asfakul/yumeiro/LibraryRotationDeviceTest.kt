/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.data.model.Playlist
import dev.asfakul.yumeiro.data.model.StorageStats
import dev.asfakul.yumeiro.ui.screens.library.LibraryScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G5 LB-7 (i): an open playlist closed on rotation (`remember` instead of `rememberSaveable` in
 * LibraryScreen). The real LibraryScreen opens a playlist; StateRestorationTester then saves the
 * screen's state into a real Bundle, throws the composition away and restores it, which is what a
 * rotation does to saveable state. The playlist must still be open. Log tag YuLibRotate.
 */
@RunWith(AndroidJUnit4::class)
class LibraryRotationDeviceTest {
    @get:Rule val rule = createComposeRule()

    private fun has(tag: String) = rule.onAllNodes(hasTestTag(tag), true).fetchSemanticsNodes().isNotEmpty()

    @Test fun openPlaylistSurvivesRecreation() {
        val lists = listOf(
            Playlist(id = "favorites_default", name = "Favorite Tracks", trackIds = emptyList()),
            Playlist(id = "pl_rotate", name = "Rotate me", trackIds = emptyList()),
        )
        val restore = StateRestorationTester(rule)
        restore.setContent {
            LibraryScreen(
                stats = StorageStats(), playlists = lists, allTracks = emptyList(), recentlyPlayed = emptyList(),
                mostPlayed = emptyList(), currentPlayingTrack = null, onCreatePlaylist = {}, onRenamePlaylist = { _, _ -> },
                onDeletePlaylist = {}, onRemoveTrackFromPlaylist = { _, _ -> }, onReorderPlaylist = { _, _, _ -> },
                onTrackClick = { _, _ -> }, onToggleFavorite = {}, onNavigateToMusic = {}, onNavigateToSettings = {},
                onTrackOptionsClick = {},
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("library_screen_root").performScrollToNode(hasTestTag("playlist_item_pl_rotate"))
        rule.onNodeWithTag("playlist_item_pl_rotate", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        val openBefore = has("playlist_detail_view")
        restore.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        val openAfter = has("playlist_detail_view")
        val overview = has("library_screen_root")
        Log.i("YuLibRotate", "LIB_ROT open before=$openBefore after=$openAfter overview=$overview")
        assertTrue("the playlist did not open", openBefore)
        assertTrue("LB-7: the open playlist closed when the screen was recreated (overview=$overview)", openAfter && !overview)
        Log.i("YuLibRotate", "LIB_ROT_RESULT PASS")
    }
}
