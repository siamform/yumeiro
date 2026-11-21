/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.diagnostics.DrawCounter
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.stream.PlaylistStore
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FREEZE-4: the 2.0.0 freeze dump had Home recomposing over and over while Live TV played (Home stays
 * in the tab pager under the player). This plays a Live TV channel (local playlist and clip) from the
 * real app for 60 s and counts Home's recompositions with DrawCounter (the same counter that goes into
 * freeze reports). Fails above [MAX_HOME_DRAWS]. Lines: F4_CASE ….
 */
@RunWith(AndroidJUnit4::class)
class Freeze4HomeDrawsDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun log(s: String) = Log.i("YuFreeze4", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private fun shows(t: String) = runCatching { rule.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private lateinit var server: MockWebServer
    private var mine: String? = null
    private var wasOn = emptyList<String>()
    private val channel = "YuF4 Live ${System.currentTimeMillis() % 100_000}"

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith(".m3u") -> MockResponse.Builder().addHeader("Content-Type", "audio/x-mpegurl")
                    .body("#EXTM3U\n#EXTINF:-1 group-title=\"News\",$channel\n${server.url("/f4.mp4")}\n").build()
                else -> MockResponse.Builder().addHeader("Content-Type", "video/mp4").addHeader("Content-Length", clip.size.toString()).body(Buffer().write(clip)).build()
            }
        }
        server.start()
        // No third-party starter extension (B-26) and no other playlist: only Yumeiro's own work is counted.
        ctx.getSharedPreferences("starter_extensions", android.content.Context.MODE_PRIVATE).edit().putBoolean("done_v1", true).commit()
        runBlocking {
            PlaylistStore.init(ctx)
            wasOn = PlaylistStore.playlists.value.filter { it.enabled }.map { it.id }
            wasOn.forEach { PlaylistStore.setEnabled(ctx, it, false) }
            mine = PlaylistStore.addRemote(ctx, "YuF4 test", server.url("/f4.m3u").toString()).id
        }
    }

    @After fun tearDown() {
        runBlocking {
            mine?.let { runCatching { PlaylistStore.delete(ctx, it) } }
            wasOn.forEach { runCatching { PlaylistStore.setEnabled(ctx, it, true) } }
        }
        runCatching { server.close() }
    }

    @Test fun homeDoesNotRedrawWhileLiveTvPlays() {
        FirstRun.toTabs(rule)
        runCatching { rule.waitUntil(30_000) { has("home_screen_root") } }
        Thread.sleep(3_000)
        FirstRun.tapVisible(rule, "nav_tab_videos")
        rule.waitUntil(20_000) { has("video_source_switch") || has("video_source_live") }
        if (!has("video_source_live")) FirstRun.tapVisible(rule, "video_source_switch")
        FirstRun.tapVisible(rule, "video_source_live")
        val listed = runCatching { rule.waitUntil(30_000) { shows(channel) }; true }.getOrDefault(false)
        if (!listed) { FirstRun.shot("f4_no_channel"); throw AssertionError("the test channel never showed on Live TV · ${FirstRun.onScreen(rule)}") }
        rule.onAllNodesWithText(channel, substring = true, useUnmergedTree = true)[0].performClick()
        val playing = runCatching { rule.waitUntil(30_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false)
        FirstRun.shot("f4_playing")
        DrawCounter.drain() // count from here
        val start = System.currentTimeMillis()
        val samples = ArrayList<String>()
        while (System.currentTimeMillis() - start < 60_000) {
            Thread.sleep(10_000)
            samples += DrawCounter.drain().ifEmpty { "none" }
        }
        val home = samples.sumOf { s -> Regex("home=(\\d+)").find(s)?.groupValues?.get(1)?.toInt() ?: 0 }
        log("F4_CASE playing=$playing home=$home per10s=$samples")
        assertTrue("Live TV never played the test channel", playing)
        assertTrue("Home recomposed $home times in 60 s of Live TV (limit $MAX_HOME_DRAWS): $samples", home <= MAX_HOME_DRAWS)
    }

    companion object {
        /** Home has no input that changes while a channel plays: a few draws for the player opening only. */
        const val MAX_HOME_DRAWS = 5
    }
}
