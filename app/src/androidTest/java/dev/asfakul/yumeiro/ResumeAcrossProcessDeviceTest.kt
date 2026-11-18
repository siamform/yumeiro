/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-1 regression (docs/proposals/FEATURE-BUG-1-watch-progress.md): play, seek, process dies,
 * reopen from Home → Jump back in.
 *
 * Phase a (`-e phase a`): opens the 12 s test clip from Videos → Device, plays it, pauses, seeks to
 * 8 s while paused, then kills its own process without any lifecycle callback (no ON_STOP, no
 * dispose), like the system killing a backgrounded app. Before the fix nothing was saved here: the
 * only saves were a 5 s tick while playing, ON_STOP and dispose (D3).
 * Phase b (`-e phase b`, a new process): Home → "Video · last watched" must open the clip at ≥ 7 s.
 * `scripts/device-check.sh` runs both phases.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class ResumeAcrossProcessDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val phase: String? get() = InstrumentationRegistry.getArguments().getString("phase")

    private fun log(s: String) = Log.i("YuResume", s)
    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun wait(what: String, ms: Long = 30_000, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("resume_fail_${what.replace(' ', '_')}")
        assertTrue("$what (after ${ms}ms) · playback=${PlaybackDiagnostics.state.value} · ${FirstRun.onScreen(rule)}", ok)
    }
    private fun pause(ms: Long) { val until = System.currentTimeMillis() + ms; runCatching { rule.waitUntil(ms + 2_000) { System.currentTimeMillis() > until } } }
    private fun tap(tag: String) = FirstRun.tapVisible(rule, tag)
    private fun <T> onPlayer(read: (androidx.media3.exoplayer.ExoPlayer) -> T): T? {
        var out: T? = null
        rule.runOnUiThread { PlaybackSession.playerForTest?.let { out = read(it) } }
        return out
    }
    private val title = NAME.substringBeforeLast('.')

    private fun grant() = listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.POST_NOTIFICATIONS")
        .forEach { runCatching { inst.uiAutomation.grantRuntimePermission(ctx.packageName, it) } }

    @Test fun a_playSeekThenDie() {
        assumeTrue("phase a runs from scripts/device-check.sh", phase == "a")
        grant()
        TestClip.put(ctx, NAME)
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
        wait("Videos tab") { FirstRun.hasNav(rule, "nav_tab_videos") } // TV: rail
        tap("nav_tab_videos")
        wait("video source switch", 20_000) { has("video_source_device") || has("video_source_switch") }
        if (!has("video_source_device")) tap("video_source_switch")
        wait("device source", 15_000) { has("video_source_device") }
        tap("video_source_device")
        repeat(4) {
            if (hasText(title)) return@repeat
            if (hasText("YumeiroTest")) runCatching { rule.onAllNodesWithText("YumeiroTest", substring = true)[0].performClick() }
            else if (has("video_rescan_btn")) runCatching { tap("video_rescan_btn") }
            runCatching { rule.waitUntil(10_000) { hasText(title) || hasText("YumeiroTest") } }
        }
        wait("the clip in Videos → Device") { hasText(title) }
        rule.onAllNodesWithText(title, substring = true)[0].performClick()
        wait("first frame", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
        wait("playing past 1 s", 10_000) { (onPlayer { it.currentPosition } ?: 0) >= 1_000 }
        rule.runOnUiThread { PlaybackSession.playerForTest?.playWhenReady = false }
        pause(500)
        rule.runOnUiThread { PlaybackSession.playerForTest?.seekTo(8_000) }
        wait("seek applied", 5_000) { (onPlayer { it.currentPosition } ?: 0) in 7_900..8_500 }
        // SharedPreferences.apply() writes on a background thread; give it a moment, as the system would.
        pause(2_000)
        log("RESUME_A left=${onPlayer { it.currentPosition }} pid=${android.os.Process.myPid()} — killing the process now")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun b_reopenFromHomeJumpBackIn() {
        assumeTrue("phase b runs from scripts/device-check.sh", phase == "b")
        grant()
        try {
            FirstRun.toTabs(rule)
            PlaybackDiagnostics.reset("")
            wait("Home tab") { has("nav_tab_home") }
            tap("nav_tab_home")
            wait("Jump back in shows the clip", 30_000) {
                rule.onAllNodesWithTag("home_continue_video", true).fetchSemanticsNodes().isNotEmpty() && hasText(title)
            }
            FirstRun.shot("resume_b_home")
            tap("home_continue_video")
            wait("first frame after reopening", 45_000) { PlaybackDiagnostics.state.value.firstFrame }
            rule.runOnUiThread { PlaybackSession.playerForTest?.playWhenReady = false }
            val pos = onPlayer { it.currentPosition } ?: 0
            log("RESUME_B reopened at ${pos}ms pid=${android.os.Process.myPid()}")
            assertTrue("resumes at ≥ 7 s after the process died (at ${pos}ms)", pos >= 7_000)
            log("RESUME_RESULT PASS pos=$pos")
        } finally {
            runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
            runCatching {
                ctx.contentResolver.query(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(android.provider.MediaStore.Video.Media._ID),
                    "${android.provider.MediaStore.Video.Media.DISPLAY_NAME}=?", arrayOf(NAME), null)?.use { c ->
                    if (c.moveToFirst()) TestClip.remove(ctx, android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0)), NAME)
                }
            }
        }
    }

    companion object {
        /** The androidTest asset. */
        const val NAME = "yumeiro_test_clip.mp4"
    }
}
