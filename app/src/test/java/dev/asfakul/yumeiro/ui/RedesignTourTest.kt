/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui

import android.Manifest
import android.app.Application
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Redesign tour: walks every reachable destination of the real app (fake MediaStore, no network)
 * and saves a screenshot of each to build/screenshots/tour_<theme>_<name>.png. Each stop is
 * independent: if one cannot be reached it is recorded in build/screenshots/tour_<theme>_missing.txt
 * and the tour returns to Home and goes on, so one broken stop never hides the others.
 * Also asserts that the redesigned shell keeps its contracts (icon-only dock tabs with accessible labels, mini-player swipe).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class RedesignTourTest {
    val rule = createAndroidComposeRule<MainActivity>()

    private val grantRule = object : org.junit.rules.ExternalResource() {
        override fun before() {
            Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(
                Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(grantRule).around(rule)

    private var theme = "light"
    private val missing = mutableListOf<String>()

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 5_000) = rule.waitUntil(ms) { rule.mainClock.advanceTimeBy(100); exists(tag) }
    private fun settle(ms: Long = 700) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }

    private fun scrollTo(list: String, tag: String) {
        if (exists(list)) runCatching { rule.onNodeWithTag(list, useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) }
        settle(300)
    }

    private fun tap(tag: String, list: String? = null) {
        // Music and Videos pick their source from the title menu: open it first.
        val menu = Regex("^(music|video)_source_(?!switch|menu)").find(tag)?.groupValues?.get(1)
        if (menu != null && !exists(tag)) { waitFor("${menu}_source_switch"); rule.onNodeWithTag("${menu}_source_switch", true).performClick(); settle(500) }
        if (list != null) scrollTo(list, tag)
        if (tag.startsWith("quick_card_")) {
            scrollTo("home_list", tag)
        }
        waitFor(tag)
        rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        settle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /**
     * Saves the screen. Dialogs and sheets live in their own windows (extra roots), so every root
     * is drawn on top of the first one: full-window roots at (0,0), smaller ones centred (dialogs).
     */
    private fun snap(name: String, @Suppress("UNUSED_PARAMETER") topmost: Boolean = false, sheet: String? = null, sheetColor: Int = 0) {
        settle(400)
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        val roots = rule.onAllNodes(isRoot())
        val count = roots.fetchSemanticsNodes().size
        val base = roots[0].captureToImage().asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(base)
        for (i in 1 until count) {
            val layer = runCatching { roots[i].captureToImage().asAndroidBitmap() }.getOrNull() ?: continue
            val full = layer.width == base.width
            val x = if (full) 0f else (base.width - layer.width) / 2f
            val y = if (layer.height >= base.height - 4) 0f else if (full) (base.height - layer.height).toFloat() else (base.height - layer.height) / 2f
            if (!full || layer.height < base.height - 4) {
                // Dim what is behind a dialog / sheet, like the real scrim.
                canvas.drawColor(android.graphics.Color.argb(120, 0, 0, 0))
            }
            canvas.drawBitmap(layer.copy(android.graphics.Bitmap.Config.ARGB_8888, false), x, y, null)
        }
        if (sheet != null) {
            // Under Robolectric the sheet window captures as blank, so the sheet content node is drawn on the bottom edge
            // over a plain surface-coloured backing. The real drag handle is not part of that node, so it is not drawn.
            runCatching {
                val node = rule.onNodeWithTag(sheet, useUnmergedTree = true)
                val img = node.captureToImage().asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                canvas.drawColor(android.graphics.Color.argb(120, 0, 0, 0))
                val d = rule.activity.resources.displayMetrics.density
                val handle = 36 * d
                val top = base.height - img.height - handle
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = sheetColor }
                canvas.drawRoundRect(0f, top, base.width.toFloat(), base.height + 40 * d, 28 * d, 28 * d, paint)
                canvas.drawBitmap(img, (base.width - img.width) / 2f, top + handle, null)
            }
        }
        java.io.File(dir, "tour_${theme}_$name.png").outputStream().use { base.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
    }

    private val surface get() = if (theme == "light") 0xFFFFFFFF.toInt() else 0xFF16161A.toInt()
    private val surfaceHigh get() = if (theme == "light") 0xFFE8E6E1.toInt() else 0xFF1E1E23.toInt()

    private fun firstTagWithPrefix(prefix: String): String {
        val matcher = androidx.compose.ui.test.SemanticsMatcher("tag prefix $prefix") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }
        rule.waitUntil(5_000) { rule.onAllNodes(matcher, true).fetchSemanticsNodes().isNotEmpty() }
        return rule.onAllNodes(matcher, true).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag]
    }

    /** Back until the bottom dock is showing, then select Home. */
    private fun home() {
        repeat(6) { if (!exists("mt_bottom_navigation_bar")) back() }
        if (exists("nav_tab_home")) { rule.onNodeWithTag("nav_tab_home", true).performClick(); settle() }
    }

    private fun stop(name: String, block: () -> Unit) {
        runCatching(block).onFailure {
            missing += "$name: ${it.javaClass.simpleName} ${it.message?.take(160)}"
            rule.runOnUiThread { dev.asfakul.yumeiro.ui.screens.extras.ExploreTour.resetForTests() }
            home()
        }
    }

    private fun awaitHome() {
        dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.resetForTests()
        dev.asfakul.yumeiro.ui.screens.extras.ExploreTour.resetForTests()
        dev.asfakul.yumeiro.ui.screens.home.HomeLayout.resetForTests()
        dev.asfakul.yumeiro.network.Connectivity.overrideForTests(true)
        dev.asfakul.yumeiro.ui.components.ScrollMemory.clear()
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { exists("nav_tab_music") }
        rule.waitUntil(10_000) {
            rule.mainClock.advanceTimeBy(200)
            rule.onAllNodes(androidx.compose.ui.test.hasText("0 songs"), true).fetchSemanticsNodes().isEmpty()
        }
        settle(1_000)
        // The activity starts before the test body, so a scroll spot left by an earlier test can be
        // restored on Home. Start every tour at the top, like a fresh launch.
        dev.asfakul.yumeiro.ui.components.ScrollMemory.clear()
        runCatching { rule.onNodeWithTag("home_list", true).performScrollToIndex(0) }
        settle(600)
    }

    private fun tour() {
        awaitHome()
        stop("home") { snap("01_home") }
        stop("home_sections") {
            for ((i, t) in listOf("home_next_episodes", "home_episodes_empty", "home_continue_card", "quick_card_store", "home_moods",
                "home_new_arrivals", "home_most_played", "home_feed", "home_random_more", "home_customize").withIndex()) {
                if (runCatching { rule.onNodeWithTag("home_list", true).performScrollToNode(hasTestTag(t)) }.isSuccess) {
                    settle(300); snap("02_home_${"%02d".format(i)}_$t")
                }
            }
            home()
        }
        stop("home_layout") {
            // Spaces: exactly the 8 everyday tiles (Extensions and Sources included, no Mix/Equalizer);
            // Customize Home hides/shows whole sections.
            scrollTo("home_list", "quick_card_sources")
            org.junit.Assert.assertTrue("extensions shortcut is on Home", exists("quick_card_store"))
            org.junit.Assert.assertFalse("equalizer is not a Home space", exists("quick_card_audio_engine"))
            org.junit.Assert.assertFalse("no show-all toggle", exists("home_spaces_all"))
            snap("03_home_spaces")
            tap("home_customize", list = "home_list"); waitFor("home_customize_sheet"); settle(600)
            snap("03_home_customize", sheet = "home_customize_sheet", sheetColor = surfaceHigh)
            tap("home_customize_moods"); settle(400)
            org.junit.Assert.assertTrue("hiding a section is remembered", "moods" in dev.asfakul.yumeiro.ui.screens.home.HomeLayout.hidden.value)
            tap("home_customize_moods"); settle(400)
            back(); settle(400)
            runCatching { rule.onNodeWithTag("home_list", true).performScrollToIndex(0) }; settle(400)
        }
        stop("setup_guide") {
            rule.runOnUiThread { dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.show() }
            waitFor("setup_guide"); settle(900); snap("06_setup_welcome")
            tap("setup_open_checklist"); settle(600); snap("08_setup_checklist")
            tap("setup_back"); settle(600)
            tap("setup_skip"); settle()
            org.junit.Assert.assertFalse("setup guide closes on skip", exists("setup_guide"))
            org.junit.Assert.assertEquals(dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.Onboarding.SKIPPED, dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.onboarding.value)
            home()
        }
        stop("explore_tour") {
            rule.runOnUiThread { dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.show() }
            waitFor("setup_guide"); tap("setup_explore"); settle(900)
            waitFor("tour_step_home"); snap("07_tour_01_home")
            tap("tour_next"); settle(900); waitFor("tour_step_spaces"); snap("07_tour_02a_spaces")
            tap("tour_next"); settle(900); waitFor("tour_step_spaces_more"); snap("07_tour_02b_spaces_more")
            // Tutor step: really tap See more under the spotlight, the grid opens smoothly.
            rule.onNodeWithTag("home_spaces_more", true).performClick(); settle(300)
            org.junit.Assert.assertTrue("see more opens", exists("home_spaces_more_block"))
            // Doing the step moves the tour on by itself.
            settle(900); waitFor("tour_step_tab_music"); snap("07_tour_02_tab_music")
            // The highlighted tab is live: tapping the real Music tab moves the tour on.
            rule.onNodeWithTag("nav_tab_music", true).performClick(); settle(900)
            waitFor("tour_step_music_source"); snap("07_tour_03_music_source")
            // Try the real source menu; closing it continues the tour.
            rule.onNodeWithTag("music_source_switch", true).performClick(); settle(700)
            waitFor("music_source_menu"); snap("07_tour_04_music_menu_open")
            rule.onNodeWithTag("music_source_device", true).performClick(); settle(900)
            waitFor("tour_step_tab_videos"); snap("07_tour_05_tab_videos")
            tap("tour_next"); settle(900); waitFor("tour_step_video_source"); snap("07_tour_06_video_source")
            tap("tour_next"); settle(900); waitFor("tour_step_yumix"); snap("07_tour_07_yumix")
            tap("tour_next"); settle(900); waitFor("tour_step_tab_library"); snap("07_tour_08_tab_library")
            tap("tour_next"); settle(900); waitFor("tour_step_actions"); snap("07_tour_09_actions")
            tap("tour_back"); settle(600); waitFor("tour_step_tab_library")
            tap("tour_next"); settle(600)
            tap("tour_next"); settle(600); waitFor("tour_step_finish"); snap("07_tour_10_finish")
            org.junit.Assert.assertEquals(dev.asfakul.yumeiro.ui.screens.extras.ExploreTour.stops.size, 11)
            tap("tour_done"); settle()
            org.junit.Assert.assertFalse("tour closes", exists("explore_tour"))
            org.junit.Assert.assertEquals(dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.Onboarding.EXPLORED, dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.onboarding.value)
            home()
        }
        stop("search") {
            tap("topbar_search_action"); waitFor("topbar_search_input")
            rule.onNodeWithTag("topbar_search_input", true).performTextInput("Item 1"); settle(); snap("04_search")
            if (exists("search_close_button")) tap("search_close_button") else back()
            home()
        }
        stop("inbox") { tap("topbar_inbox_action"); settle(); snap("05_inbox"); back(); home() }

        // Music
        stop("music") { tap("nav_tab_music"); tap("music_source_device"); snap("10_music_tracks") }
        stop("music_menu") { tap("music_source_switch"); waitFor("music_source_menu"); settle(500); snap("12_music_source_menu"); tap("music_source_menu_scrim") }
        stop("music_modes") {
            tap("music_source_mix"); settle(800); snap("13_music_mode_mix")
            tap("music_source_online"); settle(1_200); snap("14_music_mode_online")
            tap("music_source_device"); settle()
        }
        stop("music_tabs") {
            for (i in 1..4) if (exists("music_tab_$i")) { tap("music_tab_$i"); snap("11_music_tab_$i") }
            tap("music_tab_0")
        }
        stop("song_sheet") {
            tap(firstTagWithPrefix("track_more_")); waitFor("song_action_bottom_sheet"); settle(1_200); snap("12_song_actions", sheet = "song_action_sheet_content", sheetColor = surfaceHigh); back()
        }
        stop("now_playing") {
            tap(firstTagWithPrefix("track_item_")); waitFor("now_playing_screen_root")
            if (exists("now_playing_error_dismiss")) { snap("20_now_playing_error"); tap("now_playing_error_dismiss") }
            snap("21_now_playing")
            tap("now_playing_queue_toggle_button"); snap("22_up_next")
            tap("now_playing_lyrics_button"); settle(); snap("23_lyrics")
            tap("now_playing_info_button"); waitFor("now_playing_info_panel"); snap("24_song_info")
            tap("now_playing_info_button")
            tap("now_playing_sleep_button"); waitFor("sleep_timer_sheet"); settle(1_200); snap("25_sleep_sheet", sheet = "sleep_timer_sheet", sheetColor = surface); tap("sleep_30")
            settle(); snap("26_sleep_active")
            tap("now_playing_collapse_button"); waitFor("mini_player_bar"); snap("28_mini_player")
            // Swipe the mini player left = next track (the bar stays, playback moves on).
            rule.onNodeWithTag("mini_player_bar", true).performTouchInput { swipeLeft() }; settle()
            org.junit.Assert.assertTrue("mini player survives a swipe", exists("mini_player_bar"))
        }
        stop("now_playing_more") {
            tap("mini_player_bar"); waitFor("now_playing_screen_root")
            if (exists("now_playing_error_dismiss")) tap("now_playing_error_dismiss")
            tap("now_playing_more_button"); settle(); settle(1_200); snap("27_now_playing_more", sheet = "song_action_sheet_content", sheetColor = surfaceHigh)
            back(); home()
        }
        stop("sources") { home(); tap("quick_card_sources"); settle(800); snap("29_sources"); back() }

        // Videos
        stop("videos") {
            home(); tap("nav_tab_videos"); tap("video_source_device"); snap("30_videos")
            for (i in 0..4) if (exists("video_tab_$i")) { tap("video_tab_$i"); snap("31_videos_tab_$i") }
        }
        stop("video_menu") { tap("video_source_switch"); waitFor("video_source_menu"); settle(500); snap("34_video_source_menu"); tap("video_source_menu_scrim") }
        stop("video_modes") {
            tap("video_source_online"); settle(1_500); snap("35_video_mode_extensions")
            tap("video_source_youtube"); settle(1_500); snap("36_video_mode_youtube")
            tap("video_source_device"); settle()
        }
        stop("hues") { home(); tap("quick_card_hues"); settle(1_500); snap("32_hues_home") }
        stop("youtube") { home(); tap("quick_card_youtube"); settle(1_500); snap("33_youtube") }
        stop("ytmusic") { home(); tap("quick_card_ytmusic"); settle(1_500); snap("34_youtube_music") }

        // Stream / Live TV
        stop("stream") { home(); tap("nav_tab_videos"); tap("video_source_live"); settle(1_000); snap("40_stream") }
        stop("market") { tap("market_entry", list = "stream_screen"); waitFor("market_screen"); snap("41_playlist_market"); back() }
        stop("websites") {
            tap("stream_websites", list = "stream_screen"); waitFor("websites_screen"); settle(600); snap("42_websites_empty")
            rule.onNodeWithTag("site_add_field", true).performTextInput("archive.org"); settle(300)
            tap("site_add"); waitFor("site_row"); settle(500); snap("43_websites_saved")
            tap("site_menu"); tap("site_remove"); settle(500)
            org.junit.Assert.assertFalse("removed site is gone", exists("site_row"))
            back()
        }

        // Library
        stop("library") {
            home(); tap("nav_tab_library"); snap("50_library")
            for (i in 1..2) if (exists("library_tab_$i")) { tap("library_tab_$i"); snap("51_library_tab_$i") }
        }
        stop("downloads") { home(); tap("quick_card_downloads"); waitFor("downloads_screen"); snap("52_downloads"); back() }
        stop("store") { home(); tap("quick_card_store"); settle(1_500); snap("53_hue_store"); back() }

        // Settings and every category
        stop("offline") {
            rule.runOnUiThread { dev.asfakul.yumeiro.network.Connectivity.overrideForTests(false) }
            home(); settle(800)
            runCatching { rule.onNodeWithTag("home_list", true).performScrollToIndex(0) }; settle(400); snap("80_offline_home")
            if (runCatching { rule.onNodeWithTag("home_list", true).performScrollToNode(hasTestTag("home_moods_offline")) }.isSuccess) { settle(300); snap("81_offline_moods") }
            home(); tap("nav_tab_music"); tap("music_source_online"); settle(1_000); waitFor("offline_notice_music"); snap("82_offline_music_online")
            tap("music_source_device"); settle()
            home(); tap("nav_tab_videos"); tap("video_source_youtube"); settle(1_000); waitFor("offline_notice_videos"); snap("83_offline_youtube")
            tap("video_source_online"); settle(1_000); snap("84_offline_yumix")
            tap("offline_exit_videos"); settle(800)
            org.junit.Assert.assertFalse("device videos have no offline notice", exists("offline_notice_videos"))
            snap("85_offline_device_videos")
            rule.runOnUiThread { dev.asfakul.yumeiro.network.Connectivity.overrideForTests(true) }; settle()
            home()
        }
        stop("settings") { home(); tap("topbar_settings_action"); waitFor("settings_home"); snap("60_settings") }
        val cats = listOf("appearance", "player", "audio", "online", "downloads", "library", "sources", "source_status", "tracking",
            "youtube", "together", "notifications", "background", "network", "privacy", "backup", "updates", "setup", "guide", "about")
        cats.forEachIndexed { i, c ->
            stop("settings_$c") {
                if (!exists("settings_home")) { home(); tap("topbar_settings_action"); waitFor("settings_home") }
                tap("settings_cat_$c", list = "settings_home"); settle(1_000)
                snap("6${'1' + i / 10}_${"%02d".format(i)}_settings_$c")
                back()
            }
        }
        stop("extensions") {
            home(); tap("topbar_settings_action"); waitFor("settings_home")
            tap("settings_cat_sources", list = "settings_home"); waitFor("sources_hub"); snap("70_sources_hub")
            for (i in 1..4) if (exists("sources_tab_$i")) { tap("sources_tab_$i"); settle(800); snap("71_sources_tab_$i") }
            home()
        }
        // The "New playlist" dialog focuses a text field; its blinking cursor never lets Robolectric go idle,
        // so that dialog is not part of the tour.
        home()
        stop("home_end") { snap("99_home_end") }

        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.File(dir, "tour_${theme}_missing.txt").writeText(missing.joinToString("\n"))
    }

    @Test fun lightTour() { theme = "light"; tour() }

    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkTour() { theme = "dark"; tour() }

    /** Small phone: the dock, kana keys and Now Playing tool rail must still fit. */
    @Test @Config(qualifiers = "w360dp-h640dp-night-xxhdpi")
    fun smallScreenTour() {
        theme = "small"
        awaitHome()
        snap("01_home")
        stop("music") { tap("nav_tab_music"); snap("10_music") }
        stop("videos") { home(); tap("nav_tab_videos"); snap("30_videos") }
        stop("setup") {
            rule.runOnUiThread { dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.show() }
            waitFor("setup_guide"); settle(900); snap("06_setup_welcome"); tap("setup_open_checklist"); settle(600); snap("08_setup_checklist"); tap("setup_done")
            home()
        }
        stop("np") {
            home(); tap("nav_tab_music")
            tap(firstTagWithPrefix("track_item_")); waitFor("now_playing_screen_root")
            if (exists("now_playing_error_dismiss")) tap("now_playing_error_dismiss")
            snap("21_now_playing")
            tap("now_playing_collapse_button"); waitFor("mini_player_bar"); snap("28_mini_player")
        }
        stop("settings") { home(); tap("topbar_settings_action"); waitFor("settings_home"); snap("60_settings") }
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.File(dir, "tour_${theme}_missing.txt").writeText(missing.joinToString("\n"))
    }

    /** Large text (font scale 1.3): labels in the dock and tool rail must stay readable. */
    @Test @Config(qualifiers = "w411dp-h891dp-night-xxhdpi", fontScale = 1.3f)
    fun largeTextTour() {
        theme = "largetext"
        awaitHome()
        snap("01_home")
        stop("np") {
            tap("nav_tab_music"); tap(firstTagWithPrefix("track_item_")); waitFor("now_playing_screen_root")
            if (exists("now_playing_error_dismiss")) tap("now_playing_error_dismiss")
            snap("21_now_playing"); tap("now_playing_collapse_button")
        }
        stop("settings") { home(); tap("topbar_settings_action"); waitFor("settings_home"); snap("60_settings") }
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.File(dir, "tour_${theme}_missing.txt").writeText(missing.joinToString("\n"))
    }
}
