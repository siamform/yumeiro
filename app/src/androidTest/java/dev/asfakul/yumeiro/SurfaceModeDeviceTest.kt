/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadsNav
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R15 phase 4 on a device, through the real app UI:
 *  1. a video plays full screen;
 *  2. "View downloads" (the action on the download toast) → the video keeps playing in the floating
 *     player and the Downloads page is on screen;
 *  3. a new video from another source → the player is full screen again at once.
 * Screenshots: files/shots/surface_*.png (pulled by the Device check workflow).
 */
@RunWith(AndroidJUnit4::class)
class SurfaceModeDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun grant() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { ui.grantRuntimePermission(ctx.packageName, it) } }
        PlaybackDiagnostics.reset("")
    }

    private fun shot(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "surface_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun play(url: String, title: String) {
        PlaybackDiagnostics.reset("")
        rule.runOnUiThread { IncomingMedia.open(ctx, StreamRequest(url = url, title = title), remember = false) }
        rule.waitUntil(75_000) { PlaybackDiagnostics.state.value.firstFrame || PlaybackDiagnostics.state.value.error != null }
        assertTrue("first frame for $title: ${PlaybackDiagnostics.state.value}", PlaybackDiagnostics.state.value.firstFrame)
    }

    @Test fun viewDownloadsMovesVideoToFloatingAndNewVideoExpands() {
        rule.waitUntil(30_000) { has("nav_tab_videos") || has("nav_tab_home") }
        play("https://archive.org/download/f20110625mad/f20110625mad.mp4", "Surface A")
        rule.waitUntil(10_000) { has("video_player_root") }
        assertTrue("starts full screen (no floating window)", !has("video_floating_player"))
        shot("1_full")

        rule.runOnUiThread { DownloadsNav.open() }
        rule.waitUntil(15_000) { has("video_floating_player") && has("downloads_screen") }
        shot("2_floating_downloads")
        assertTrue("floating player shown", has("video_floating_player"))
        assertTrue("downloads page shown", has("downloads_screen"))
        val playing = PlaybackDiagnostics.state.value
        Log.i("YuSurfaceTest", "after View downloads: $playing")

        play("https://storage.googleapis.com/exoplayer-test-media-1/mp4/frame-counter-one-hour.mp4", "Surface B")
        rule.waitUntil(10_000) { !has("video_floating_player") }
        shot("3_new_video_full")
        assertTrue("a new video expands the floating player", !has("video_floating_player"))
        Log.i("YuSurfaceTest", "PASS")
    }
}
