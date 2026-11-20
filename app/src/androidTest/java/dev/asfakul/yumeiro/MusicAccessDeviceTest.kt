/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.repository.OfflineMediaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * G6-SP1 on Android 13+, through the real permission system (scripts/device-check.sh drives it;
 * revoking kills the app, so it cannot happen inside a test). The script pushes a song to
 * Music/YuAccess as the shell user (not owned by the app, so only music access shows it).
 *  - `a_prepare` (all allowed): gets past first run.
 *  - script: force-stop, READ_MEDIA_AUDIO revoked, videos still allowed.
 *  - `b_videosOnly`: Music (On device) shows the "Music access is off" card, not "No audio files…
 *    Rescan"; its button opens Android's permission dialog.
 *  - script: READ_MEDIA_AUDIO granted.
 *  - `c_audioAllowed`: no card; the scan lists the pushed song.
 * Log tag YuMusicAccess: MA_… lines; screenshots ma_*.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MusicAccessDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private val phase = InstrumentationRegistry.getArguments().getString("phase").orEmpty()
    private fun log(s: String) = Log.i("YuMusicAccess", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun waitFor(ms: Long, ok: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { if (ok()) return true; SystemClock.sleep(300) }
        return ok()
    }
    private fun audioAllowed() = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_MEDIA_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    private fun dialogOnTop() = FirstRun.shell("dumpsys activity activities").lineSequence().any { FirstRun.isPermissionDialogLine(it) }
    // TV has no touch screen and the open rail floats over the page: use the click action there (FirstRun.clickTag).
    private fun tap(tag: String) { if (has("tv_nav_rail")) FirstRun.clickTag(rule, tag) else rule.onAllNodesWithTag(tag, true)[0].performClick() }

    /** Music tab, On device mode (the tab may open Online when there is internet). */
    private fun openDeviceMusic() {
        // FINAL-44 (run 44671880872, tv-api34): Yumeiro TV has a side rail (tv_nav_tab_music), not the phone bar.
        assertTrue("no Music tab", waitFor(30_000) { FirstRun.hasNav(rule, "nav_tab_music") })
        FirstRun.clickTag(rule, FirstRun.navTag(rule, "nav_tab_music")); rule.waitForIdle(); SystemClock.sleep(1_000)
        if (!has("music_screen_root")) {
            assertTrue("no music source switch", waitFor(10_000) { has("music_source_switch") })
            tap("music_source_switch"); rule.waitForIdle()
            assertTrue("no On device choice", waitFor(5_000) { has("music_source_device") })
            tap("music_source_device"); rule.waitForIdle()
        }
        assertTrue("device music library not shown", waitFor(10_000) { has("music_screen_root") })
    }

    @Test fun a_prepare() {
        assumeTrue("phase a", phase == "a")
        assumeTrue("Android 13+ only (separate music permission)", Build.VERSION.SDK_INT >= 33)
        FirstRun.toTabs(rule)
        log("MA_PREPARE audio=${audioAllowed()} sdk=${Build.VERSION.SDK_INT}")
    }

    @Test fun b_videosOnly() {
        assumeTrue("phase b", phase == "b")
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        assumeTrue("script must have revoked music access", !audioAllowed())
        // Start-up asks for the missing music permission: the user says no (Back).
        if (waitFor(8_000) { dialogOnTop() }) { log("MA_STARTUP_DIALOG shown → Back"); FirstRun.shell("input keyevent 4"); SystemClock.sleep(1_000) }
        openDeviceMusic()
        val card = waitFor(8_000) { has("permission_prompt_card") }
        FirstRun.shot("ma_b_card")
        val misleading = has("music_tracks_list")
        log("MA_CARD shown=$card tracksList=$misleading")
        assertTrue("music access off but no card offering to allow it", card)
        tap("grant_permission_button"); rule.waitForIdle()
        val asked = waitFor(8_000) { dialogOnTop() }
        FirstRun.shot("ma_b_dialog")
        log("MA_ASK dialog=$asked")
        if (asked) { FirstRun.shell("input keyevent 4"); SystemClock.sleep(1_000) }
        assertTrue("Allow access did not open Android's permission dialog", asked)
        assertFalse(audioAllowed())
        log("MA_RESULT_B PASS")
    }

    @Test fun c_audioAllowed() {
        assumeTrue("phase c", phase == "c")
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        assumeTrue("script must have granted music access", audioAllowed())
        if (waitFor(5_000) { dialogOnTop() }) { FirstRun.shell("input keyevent 4"); SystemClock.sleep(1_000) }
        val repo = OfflineMediaRepository(ctx)
        var seen = runBlocking { repo.scanDeviceMedia(); repo.audioTracks.first() }.filter { it.folderName == FOLDER || FOLDER in it.folderPath }
        if (seen.isEmpty()) {
            // The shell's media-scan broadcast may not reach MediaProvider on this image: scan the file now.
            val path = "/sdcard/Music/$FOLDER/yu_access_song.m4a"
            val done = java.util.concurrent.CountDownLatch(1)
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(path), null) { _, _ -> done.countDown() }
            done.await(15, java.util.concurrent.TimeUnit.SECONDS)
            seen = runBlocking { OfflineMediaRepository(ctx).let { r -> r.scanDeviceMedia(); r.audioTracks.first() } }.filter { it.folderName == FOLDER || FOLDER in it.folderPath }
            log("MA_SCANFILE fallback seen=${seen.size}")
            // The app's own start-up scan ran before the file was indexed: rescan, as Rescan does.
            rule.runOnUiThread { androidx.lifecycle.ViewModelProvider(rule.activity)[dev.asfakul.yumeiro.ui.viewmodel.MainViewModel::class.java].scanMedia() }
        }
        openDeviceMusic()
        val card = waitFor(3_000) { has("permission_prompt_card") }
        FirstRun.shot("ma_c_allowed")
        log("MA_ALLOWED card=$card song=${seen.size} title=${seen.firstOrNull()?.title}")
        assertFalse("card still shown with music access allowed", card)
        assertTrue("pushed song not listed with music access allowed", seen.isNotEmpty())
        assertTrue("track list not shown", waitFor(10_000) { has("music_tracks_list") })
        log("MA_RESULT_C PASS")
    }

    private companion object { const val FOLDER = "YuAccess" }
}
