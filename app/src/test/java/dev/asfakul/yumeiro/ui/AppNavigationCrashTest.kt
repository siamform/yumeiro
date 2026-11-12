/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import androidx.compose.ui.test.performSemanticsAction
import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import dev.asfakul.yumeiro.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Fake MediaStore with realistic edge cases (same-named folders, unknown tags, duplicates). */
class FakeMediaProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    override fun query(uri: Uri, projection: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor {
        val cols = projection ?: arrayOf(MediaStore.MediaColumns._ID)
        val c = MatrixCursor(cols)
        val isVideo = uri.pathSegments.contains("video")
        val n = if (isVideo) 30 else 60
        for (i in 1..n) {
            val folder = if (i % 3 == 0) "Download" else if (i % 3 == 1) "Music" else "<unknown>"
            val root = if (i % 2 == 0) "/storage/emulated/0" else "/storage/1234-ABCD"
            val row: List<Any?> = cols.map { col ->
                when (col) {
                    MediaStore.MediaColumns._ID -> i.toLong()
                    MediaStore.MediaColumns.TITLE -> if (i % 7 == 0) null else "Item $i"
                    MediaStore.Audio.AudioColumns.ARTIST -> if (i % 5 == 0) "<unknown>" else "Artist ${i % 4}"
                    MediaStore.Audio.AudioColumns.ALBUM -> if (i % 2 == 0) "Album A" else "album a"
                    MediaStore.Audio.AudioColumns.ALBUM_ID -> (i % 3).toLong()
                    MediaStore.MediaColumns.DURATION -> if (i % 9 == 0) 0L else 180_000L + i
                    MediaStore.MediaColumns.SIZE -> 1_000_000L * i
                    MediaStore.MediaColumns.DATE_ADDED -> 1_700_000_000L + i
                    MediaStore.MediaColumns.DATA -> "$root/$folder/file$i.${if (isVideo) "mp4" else "mp3"}"
                    MediaStore.MediaColumns.RELATIVE_PATH -> "$folder/"
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME -> folder
                    MediaStore.MediaColumns.WIDTH -> 1920
                    MediaStore.MediaColumns.HEIGHT -> 1080
                    else -> null
                }
            }
            c.addRow(row.toTypedArray<Any?>())
        }
        return c
    }
}

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AppNavigationCrashTest {
    val rule = createAndroidComposeRule<MainActivity>()

    /** Grants media permissions before MainActivity launches so the first scan sees the fake media. */
    private val grantRule = object : org.junit.rules.ExternalResource() {
        override fun before() {
            // Robolectric resets providers between tests: register the fake MediaStore every time.
            Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(
                Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(grantRule).around(rule)

    private fun tap(tag: String) {
        // Music and Videos pick their source from the title menu: open it first.
        val menu = Regex("^(music|video)_source_(?!switch|menu)").find(tag)?.groupValues?.get(1)
        if (menu != null && rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) tap("${menu}_source_switch")
        if (tag.startsWith("quick_card_")) {
            // The bento grid lives in a LazyColumn; bring the tile into view on small test screens.
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("home_list", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            val list = rule.onNodeWithTag("home_list", useUnmergedTree = true)
            list.performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
            rule.waitForIdle()
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    private fun tabsIfPresent(prefix: String, count: Int) {
        for (i in 0 until count) {
            val tag = "${prefix}_$i"
            if (rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) tap(tag)
        }
    }

    private fun snap(name: String, topmost: Boolean = false) {
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.File(dir, "app_$name.png").outputStream().use {
            val roots = rule.onAllNodes(androidx.compose.ui.test.isRoot())
            val index = if (topmost) roots.fetchSemanticsNodes().size - 1 else 0
            roots[index].captureToImage()
                .asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it)
        }
    }

    @Test fun navigateEverywhereAndBack() {
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nav_tab_music", true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_000)
        snap("home"); tap("nav_tab_videos"); snap("videos")
        repeat(3) {
            tap("nav_tab_music"); tabsIfPresent("music_tab", 5)
            tap("nav_tab_videos"); tabsIfPresent("video_tab", 5)
            tap("nav_tab_library")
            rule.onAllNodesWithTag("playlist_item_workout_mix", true).fetchSemanticsNodes().firstOrNull()?.let {
                tap("playlist_item_workout_mix"); back()
            }
            tap("nav_tab_home")
            tap("nav_tab_videos"); tap("video_source_live"); if (it == 0) snap("stream")
            tap("nav_tab_videos"); tap("nav_tab_music"); tap("nav_tab_library"); if (it == 0) snap("library")
            back(); back()
            tap("nav_tab_music")
            tap("topbar_settings_action"); if (it == 0) snap("settings"); back()
        }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkThemeRenders() {
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nav_tab_music", true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_000)
        snap("dark_home"); tap("nav_tab_videos"); snap("dark_videos")
        tap("nav_tab_videos"); tap("video_source_live"); snap("dark_stream")
        tap("topbar_settings_action"); snap("dark_settings")
        tap("open_user_guide"); waitFor("user_guide_screen"); snap("dark_user_guide"); back()
        waitFor("open_user_guide"); back()
        // Design QA captures for the remaining screens.
        tap("nav_tab_music"); snap("dark_music")
        tap("music_tab_1"); snap("dark_albums")
        tap("nav_tab_library"); snap("dark_library")
        tap("library_tab_1"); snap("dark_library_videos"); tap("library_tab_2"); snap("dark_library_reading"); tap("library_tab_0")
        tap("nav_tab_home"); tap("quick_card_downloads"); snap("dark_downloads"); back()
        tap("nav_tab_music"); tap("music_tab_0")
        tap(firstTagWithPrefix("track_item_")); waitFor("now_playing_screen_root")
        // The fake test files can't be decoded; capture the error tip, then dismiss it for clean shots.
        if (exists("now_playing_error_dismiss")) { snap("dark_now_playing_error"); tap("now_playing_error_dismiss") }
        snap("dark_now_playing")
        tap("now_playing_sleep_button"); waitFor("sleep_timer_sheet"); tap("sleep_30")
        waitFor("now_playing_queue_toggle_button")
        tap("now_playing_queue_toggle_button"); snap("dark_queue")
        tap("now_playing_lyrics_button"); rule.waitUntil(5_000) { exists("lyrics_import_button") }; snap("dark_lyrics")
        tap("now_playing_info_button"); waitFor("now_playing_info_panel"); snap("dark_info")
        tap("now_playing_info_button"); waitFor("now_playing_artwork_box")
        tap("now_playing_collapse_button"); waitFor("mini_player_close_button"); snap("dark_mini_player")
    }

    private fun awaitHome() {
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("nav_tab_music", true).fetchSemanticsNodes().isNotEmpty() }
        // Wait for the background MediaStore scan to publish the fake library.
        rule.waitUntil(10_000) {
            rule.mainClock.advanceTimeBy(200)
            rule.onAllNodes(androidx.compose.ui.test.hasText("0 songs"), true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun exists(tag: String) =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

    private fun firstTagWithPrefix(prefix: String): String {
        val matcher = androidx.compose.ui.test.SemanticsMatcher("tag prefix $prefix") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }
        rule.waitUntil(5_000) { rule.onAllNodes(matcher, true).fetchSemanticsNodes().isNotEmpty() }
        return rule.onAllNodes(matcher, true).fetchSemanticsNodes().first()
            .config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
    }

    /** Stream page lists the 8 default playlists; they can be disabled, deleted and restored. */
    @Test fun streamDefaultPlaylistsCanBeDisabledDeletedAndRestored() {
        java.io.File(rule.activity.filesDir, "streams").deleteRecursively()
        dev.asfakul.yumeiro.stream.PlaylistStore.resetForTests()
        awaitHome()
        tap("nav_tab_videos"); tap("video_source_live")
        fun settle(cond: () -> Boolean) = rule.waitUntil(10_000) { rule.mainClock.advanceTimeBy(100); cond() }
        val list = rule.onNodeWithTag("stream_screen")
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("playlist_mrgify_bdix"))
        settle { exists("playlist_mrgify_bdix") }
        // Only one default ships; the former defaults live in the market.
        org.junit.Assert.assertEquals(listOf("mrgify_bdix"), dev.asfakul.yumeiro.stream.PlaylistStore.playlists.value.map { it.id })
        org.junit.Assert.assertFalse(exists("playlist_zilla_combined"))
        snap("stream_playlists")
        // Disable.
        tap("playlist_toggle_mrgify_bdix")
        settle { dev.asfakul.yumeiro.stream.PlaylistStore.get("mrgify_bdix")?.enabled == false }
        // Delete with confirmation.
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("playlist_mrgify_bdix"))
        tap("playlist_menu_mrgify_bdix"); tap("playlist_delete"); tap("confirm_delete")
        settle { dev.asfakul.yumeiro.stream.PlaylistStore.get("mrgify_bdix") == null && !exists("playlist_mrgify_bdix") }
        // Restore brings it back.
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("playlists_more"))
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        tap("playlists_more"); tap("playlists_restore")
        settle { dev.asfakul.yumeiro.stream.PlaylistStore.playlists.value.let { p -> p.size == 1 && p.all { it.enabled } } }
        list.performScrollToIndex(0); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        snap("stream_playlists_after")
    }

    /** Playlists open on their own page (stream/playlist/{id}) and back returns to the Stream tab. */
    @Test fun playlistOpensOnItsOwnPageAndBackReturns() {
        java.io.File(rule.activity.filesDir, "streams").deleteRecursively()
        dev.asfakul.yumeiro.stream.PlaylistStore.resetForTests()
        val p = kotlinx.coroutines.runBlocking {
            dev.asfakul.yumeiro.stream.PlaylistStore.init(rule.activity)
            dev.asfakul.yumeiro.stream.PlaylistStore.createLocal(rule.activity, "Test list", (1..40).map {
                dev.asfakul.yumeiro.stream.StreamRequest("https://cdn.example/$it.m3u8", "Channel $it", group = if (it % 2 == 0) "Even" else "Odd")
            })
        }
        awaitHome()
        tap("nav_tab_videos"); tap("video_source_live")
        fun settle(cond: () -> Boolean) = rule.waitUntil(10_000) { rule.mainClock.advanceTimeBy(100); cond() }
        val list = rule.onNodeWithTag("stream_screen")
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("playlist_${p.id}"))
        tap("playlist_${p.id}")
        settle { exists("playlist_detail_screen") && exists("playlist_channels") }
        org.junit.Assert.assertFalse("channels are not shown on the Stream tab", exists("stream_screen"))
        org.junit.Assert.assertFalse("bottom bar hidden on the sub page", exists("nav_tab_videos"))
        snap("playlist_detail")
        back()
        settle { exists("stream_screen") && !exists("playlist_detail_screen") }
        // Market entry opens the market page.
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("market_entry"))
        tap("market_entry")
        settle { exists("market_screen") }
        snap("market")
        back()
        settle { exists("stream_screen") && !exists("market_screen") }
    }

    /** Regression: quick-access card -> Home tab used to restore the previous tab (Home "dead"). */
    @Test fun quickNavigationThenHomeDoesNotCrash() {
        awaitHome()
        repeat(2) {
            for (card in listOf("quick_card_hues", "quick_card_youtube", "quick_card_ytmusic", "quick_card_downloads")) {
                tap(card)
                if (card == "quick_card_downloads") back() else tap("nav_tab_home")
                waitFor("home_screen_root")
                tap(card)
                back()
                waitFor("home_screen_root")
            }
        }
        // Mixed: quick card, then bottom tabs, then Home again.
        tap("quick_card_ytmusic"); tap("nav_tab_videos"); tap("nav_tab_home"); waitFor("home_screen_root")
        tap("quick_card_hues"); tap("nav_tab_library"); tap("nav_tab_home"); waitFor("home_screen_root")
        snap("home_after_quick_nav")
    }

    @Test fun homeShowsVideosAsGrid() {
        awaitHome()
        // The vertical home list (the tabs themselves are a horizontal pager).
        val list = androidx.compose.ui.test.hasScrollAction() and
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        rule.onAllNodes(list, true).fetchSemanticsNodes().first()
        rule.onNode(androidx.compose.ui.test.hasTestTag("home_screen_root"), true)
        // Scroll the home list to the videos section.
        rule.onAllNodes(list, true)[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("home_video_grid_row"))
        rule.mainClock.advanceTimeBy(600)
        val row = rule.onAllNodesWithTag("home_video_grid_row", true).fetchSemanticsNodes().first()
        val cardsInRow = row.children.count {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("video_item_") == true
        }
        org.junit.Assert.assertEquals("two videos per row on a phone", 2, cardsInRow)
        snap("home_video_grid")
    }

    @Test fun swipeAnywhereMovesBetweenTabsAndBackReturnsHome() {
        awaitHome()
        // A swipe on the page content (not on empty space) follows the finger to the next tab.
        rule.onNodeWithTag("tab_pager", useUnmergedTree = true).performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        waitFor("music_screen_root")
        fun pagerOffset() = rule.onNodeWithTag("tab_pager", useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value()
        org.junit.Assert.assertTrue("pager moved off Home", pagerOffset() > 0f)
        // Back on another tab goes to Home first instead of exiting.
        back()
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        org.junit.Assert.assertFalse(rule.activity.isFinishing)
        org.junit.Assert.assertEquals(0f, pagerOffset(), 0.5f)
        waitFor("home_screen_root")
    }

    @Test fun doubleBackPressExitsFromHome() {
        awaitHome()
        rule.mainClock.advanceTimeBy(1_000)
        back()
        org.junit.Assert.assertFalse("single back must not exit", rule.activity.isFinishing)
        back()
        org.junit.Assert.assertTrue("second back within 2s exits", rule.activity.isFinishing)
    }

    @Test fun playingSongOpensFullPlayerFavoriteAndClose() {
        awaitHome()
        tap("nav_tab_music")
        val trackTag = firstTagWithPrefix("track_item_")
        tap(trackTag)
        waitFor("now_playing_screen_root")
        snap("now_playing")
        tap("now_playing_lyrics_button"); rule.waitUntil(5_000) { exists("lyrics_import_button") }; snap("lyrics")
        tap("now_playing_info_button"); waitFor("now_playing_info_panel"); snap("info")
        tap("now_playing_info_button")
        // Favourite toggles on the full player and is reflected immediately.
        fun desc() = rule.onAllNodes(
            androidx.compose.ui.test.hasContentDescription("Remove from favorites"), true
        ).fetchSemanticsNodes().size.toString() + "/" + rule.onAllNodes(
            androidx.compose.ui.test.hasContentDescription("Add to favorites"), true
        ).fetchSemanticsNodes().size
        val before = desc()
        tap("now_playing_favorite_button")
        val after = desc()
        org.junit.Assert.assertNotEquals("favourite state should change", before, after)
        // Collapse to the mini player, then close it.
        tap("now_playing_collapse_button")
        waitFor("mini_player_close_button")
        snap("mini_player")
        tap("mini_player_close_button")
        rule.waitUntil(5_000) { !exists("mini_player_close_button") }
    }

    @Test fun videoOpensPortraitAndFullscreenGoesLandscape() {
        awaitHome()
        tap("nav_tab_videos")
        tabsIfPresent("video_tab", 1)
        val videoTag = firstTagWithPrefix("video_item_")
        tap(videoTag)
        if (exists("dialog_play_video_btn")) tap("dialog_play_video_btn")
        waitFor("video_player_root")
        rule.mainClock.advanceTimeBy(500)
        org.junit.Assert.assertNotEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            rule.activity.requestedOrientation
        )
        snap("video_portrait")
        // Reveal controls and toggle fullscreen.
        if (!exists("video_fullscreen_btn")) {
            rule.onNodeWithTag("video_player_root", true).performClick()
            rule.mainClock.advanceTimeBy(600)
        }
        tap("video_fullscreen_btn")
        org.junit.Assert.assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            rule.activity.requestedOrientation
        )
        back() // leaves fullscreen first
        org.junit.Assert.assertNotEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            rule.activity.requestedOrientation
        )
        back() // closes the player
    }

    @Test fun nextAndPreviousVideoButtonsMoveThroughTheList() {
        awaitHome()
        tap("nav_tab_videos")
        tabsIfPresent("video_tab", 1)
        tap(firstTagWithPrefix("video_item_"))
        if (exists("dialog_play_video_btn")) tap("dialog_play_video_btn")
        waitFor("video_player_root")
        fun reveal() {
            if (!exists("video_next_btn")) {
                rule.onNodeWithTag("video_player_root", true).performClick()
                rule.mainClock.advanceTimeBy(600)
            }
        }
        fun title(): String = rule.onNodeWithTag("video_player_title", true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        // Test media cannot be decoded, so the error card covers the controls: invoke the
        // buttons' click actions directly, and check the error card offers "skip to next".
        fun press(tag: String) {
            rule.onNodeWithTag(tag, useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        }
        reveal()
        val first = title()
        if (exists("error_close_btn")) waitFor("error_next_video_btn")
        press("video_next_btn")
        rule.mainClock.advanceTimeBy(600)
        waitFor("video_player_root")
        reveal()
        val second = title()
        org.junit.Assert.assertNotEquals(first, second)
        snap("video_next")
        press("video_previous_btn")
        reveal()
        org.junit.Assert.assertEquals(first, title())
        back()
    }
}
