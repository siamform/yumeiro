/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.tv

import android.Manifest
import android.app.Application
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.MainActivity
import dev.asfakul.yumeiro.ui.FakeMediaProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The TV layout driven like a remote: the left rail replaces the bottom bar, focus is visible
 * (the rail opens with labels), OK on a rail item switches the page and Back closes the rail.
 * Renders build/screenshots/tv_home*.png at 960×540 dp (a 1080p TV at xhdpi).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class TvLayoutTest {
    val rule = createAndroidComposeRule<MainActivity>()
    private val setupRule = object : org.junit.rules.ExternalResource() {
        override fun before() {
            Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
            TvMode.set(app, TvMode.Pref.TV)
        }
        override fun after() {
            TvMode.set(ApplicationProvider.getApplicationContext<Application>(), TvMode.Pref.AUTO)
        }
    }
    @get:Rule val chain: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(setupRule).around(rule)

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun settle(ms: Long = 700) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }
    private fun shot(name: String) {
        val dir = java.io.File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it)
        }
    }

    private fun awaitHome() {
        dev.asfakul.yumeiro.ui.screens.extras.SetupGuide.resetForTests()
        dev.asfakul.yumeiro.ui.screens.extras.ExploreTour.resetForTests()
        dev.asfakul.yumeiro.network.Connectivity.overrideForTests(true)
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitUntil(10_000) { rule.mainClock.advanceTimeBy(100); exists("tv_nav_rail") }
        settle(1_000)
    }

    @Test fun railReplacesTheBottomBarAndFollowsTheRemote() {
        awaitHome()
        assertTrue("TV rail shown", exists("tv_nav_rail"))
        assertFalse("no phone bottom bar on TV", exists("mt_bottom_navigation_bar"))
        // One Settings entry on TV: the rail's. The top bar one is hidden.
        assertTrue("rail Settings shown", exists("tv_nav_settings"))
        assertFalse("no top-bar Settings on TV", exists("topbar_settings_action"))
        assertTrue("app logo at the top of the rail", exists("tv_rail_logo"))
        assertFalse("no diamond glyph anywhere", rule.onAllNodesWithText("◆", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        shot("tv_home")

        // A TV has no touch: the window is in non-touch (keyboard) mode, so clickables are focusable.
        // (Robolectric injects keys straight into Compose, so leave touch mode the way a key press would.)
        rule.runOnUiThread {
            val decor = rule.activity.window.decorView
            val vri = decor.javaClass.getMethod("getViewRootImpl").invoke(decor)
            vri.javaClass.getDeclaredMethod("ensureTouchMode", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(vri, false)
        }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        settle(300)
        // Focus lands on a rail item → the rail opens with its labels.
        rule.onNodeWithTag("tv_nav_tab_music", useUnmergedTree = true).requestFocus()
        settle(400)
        assertTrue("rail opened with labels", rule.onAllNodesWithText("Music", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        shot("tv_home_rail_open")

        // OK on the remote switches the page.
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle(1_500)
        assertTrue("Music page open", exists("music_source_switch"))

        // D-pad Down walks the rail (focus never gets lost).
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        settle(400)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle(1_500)
        assertTrue("Videos page open", exists("video_source_switch"))
    }

    @Test fun railDropsAPinnedSettingsShortcut() {
        val settings = dev.asfakul.yumeiro.ui.navigation.NavPrefs.Shortcut.SETTINGS.item
        val music = settings.copy(route = "music", label = "Music")
        assertTrue(railItems(listOf(music, settings)) == listOf(music))
    }

    private fun leaveTouchMode() {
        // Robolectric injects keys straight into Compose; leave touch mode the way a remote press would.
        rule.runOnUiThread {
            val decor = rule.activity.window.decorView
            val vri = decor.javaClass.getMethod("getViewRootImpl").invoke(decor)
            vri.javaClass.getDeclaredMethod("ensureTouchMode", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(vri, false)
        }
    }

    private fun focused(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .any { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Focused) { null } == true }

    @Test fun playerFollowsTheRemote() {
        awaitHome()
        rule.onNodeWithTag("tv_nav_tab_videos", useUnmergedTree = true).performClick()
        settle(1_500)
        if (!exists("video_source_device")) { rule.onNodeWithTag("video_source_switch", true).performClick(); settle(800) }
        rule.onNodeWithTag("video_source_device", true).performClick()
        settle(1_200)
        if (exists("video_tab_0")) { rule.onNodeWithTag("video_tab_0", true).performClick(); settle(1_000) }
        shot("tv_videos")
        val matcher = androidx.compose.ui.test.SemanticsMatcher("video item") { n ->
            n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("video_item_") == true
        }
        rule.waitUntil(8_000) { rule.mainClock.advanceTimeBy(100); rule.onAllNodes(matcher, true).fetchSemanticsNodes().isNotEmpty() }
        // Open it the way a remote does: focus + OK (no touch from here on).
        leaveTouchMode()
        settle(300)
        rule.onAllNodes(matcher, true)[0].requestFocus()
        settle(300)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle(800)
        if (exists("dialog_play_video_btn")) { rule.onNodeWithTag("dialog_play_video_btn", true).performClick(); settle(800) }
        rule.waitUntil(8_000) { rule.mainClock.advanceTimeBy(100); exists("video_player_root") }
        leaveTouchMode()
        settle(600)
        // The test file can't be decoded, so this is the "file moved or deleted" path: the error card
        // must take focus (Retry) so OK works, and the D-pad must walk to Close.
        rule.waitUntil(8_000) { rule.mainClock.advanceTimeBy(100); exists("error_retry_btn") }
        settle(800)
        shot("tv_player_error")
        assertTrue("Retry focused when playback fails", focused("error_retry_btn"))
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        settle(400)
        assertTrue("D-pad Left reaches Close", focused("error_close_btn"))
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle(1_000)
        assertFalse("OK on Close leaves the player", exists("error_close_btn"))
    }
}
