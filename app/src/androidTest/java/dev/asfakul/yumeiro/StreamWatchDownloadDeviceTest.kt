/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
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
 * BUG-7 7a (owner): a video from a website URL (or a pasted link) plays in Yumeiro's player with
 * no download button. Opens a local MP4 the way the Websites page does (IncomingMedia.open with a
 * page URL), then requires the player's watch page to offer Download (stream_watch_download), and
 * the tap to finish a "Network" download whose file is on disk.
 * OL-4 (owner list): the tap first shows the same download popup as every other download (Offline copy /
 * Permanent); old code started the download at once. Download in the popup starts it.
 * Lines: WDL_CASE button=… prompt=… queued=… done=… saved=….
 */
@RunWith(AndroidJUnit4::class)
class StreamWatchDownloadDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun log(s: String) = Log.i("YuWatchDownload", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_test_clip.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse.Builder().addHeader("Content-Type", "video/mp4").addHeader("Content-Length", clip.size.toString()).body(Buffer().write(clip)).build()
        }
        server.start()
        // OL-4: the popup is on by default; an earlier test may have ticked "Don't ask again".
        inst.runOnMainSync { dev.asfakul.yumeiro.online.download.DownloadSettings.setAskFirst(ctx, true) }
        FirstRun.toTabs(rule)
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
    }

    @After fun tearDown() {
        runCatching { OnlineDownloads.items.value.filter { it.api == "Network" && it.title.startsWith("Site clip") }.forEach { OnlineDownloads.delete(ctx, it.id) } }
        runCatching { rule.runOnUiThread { PlaybackSession.releaseNow() } }
        runCatching { server.close() }
    }

    @Test fun a_websiteVideoCanBeDownloadedFromThePlayer() {
        PlaybackDiagnostics.reset("")
        val media = server.url("/media/site-clip.mp4").toString()
        val page = server.url("/watch/page.html").toString()
        rule.runOnUiThread { IncomingMedia.open(ctx, StreamRequest(url = media, title = "Site clip", referer = page, pageUrl = page)) }
        runCatching { rule.waitUntil(25_000) { PlaybackDiagnostics.state.value.firstFrame } }
        runCatching { rule.waitUntil(10_000) { has("stream_watch_panel") } }
        runCatching { rule.onNodeWithTag("stream_watch_panel", true).performScrollToNode(hasTestTag("stream_watch_download")) }
        // G8-TV7A: on TV (and landscape phones) there is no docked watch page; Download must be in the player's
        // own top bar, which shows with the controls. DPAD_DOWN shows the controls (VideoPlayerModal key rule).
        if (!has("stream_watch_download")) { FirstRun.shell("input keyevent 20"); Thread.sleep(600); rule.waitForIdle() }
        val button = runCatching { rule.waitUntil(5_000) { has("stream_watch_download") }; true }.getOrDefault(false)
        FirstRun.shot("wdl_a_panel")
        var queued = false; var done = false; var saved = false; var prompt = false; var modes = false
        if (button) {
            rule.onNodeWithTag("stream_watch_download", true).performClick()
            // OL-4: Offline / Permanent popup first, as for every other download.
            prompt = runCatching { rule.waitUntil(5_000) { has("download_confirm") }; true }.getOrDefault(false)
            modes = has("download_mode_offline") && has("download_mode_permanent")
            FirstRun.shot("wdl_a_prompt")
            if (prompt) rule.onNodeWithTag("download_confirm_start", true).performClick()
            queued = runCatching { rule.waitUntil(5_000) { OnlineDownloads.items.value.any { it.api == "Network" && it.title == "Site clip" } }; true }.getOrDefault(false)
            done = runCatching { rule.waitUntil(60_000) { OnlineDownloads.items.value.any { it.api == "Network" && it.title == "Site clip" && it.status == OnlineDownloads.Status.DONE } }; true }.getOrDefault(false)
            saved = OnlineDownloads.items.value.firstOrNull { it.api == "Network" && it.title == "Site clip" }?.let { OnlineDownloads.isSaved(ctx, it) } == true
        }
        val item = OnlineDownloads.items.value.firstOrNull { it.api == "Network" && it.title == "Site clip" }
        log("WDL_CASE a button=$button prompt=$prompt modes=$modes queued=$queued done=$done saved=$saved status=${item?.status} titleUrl=${item?.titleUrl}")
        assertTrue("7a: the player for a website video shows no Download action", button)
        assertTrue("OL-4: a website download started without the Offline / Permanent popup", prompt)
        assertTrue("OL-4: the popup has no Offline copy / Permanent choice", modes)
        assertTrue("7a: Download did not queue a download", queued)
        assertTrue("7a: the download did not finish within 60 s: ${item?.status}", done && saved)
        // 7b: with "Save YouTube and link downloads to phone" on (default), the file is in
        // Movies/Yumeiro (MediaStore, so the gallery lists it), the app copy is gone and the item is Permanent.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val uri = item?.publicUri
            val inStore = uri != null && runCatching {
                ctx.contentResolver.query(android.net.Uri.parse(uri), arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.provider.MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
                    c.moveToFirst() && c.getString(0).orEmpty().startsWith("Movies/Yumeiro") && c.getLong(1) > 0
                } == true
            }.getOrDefault(false)
            val appCopy = item?.let { OnlineDownloads.fileOf(ctx, it).exists() } == true
            log("WDL_CASE b publicUri=$uri inStore=$inStore appCopy=$appCopy keep=${item?.keep}")
            assertTrue("7b: the link download is not in Movies/Yumeiro (gallery): $uri", inStore)
            assertTrue("7b: the app copy was kept next to the gallery copy", !appCopy)
            assertTrue("7b: a download now in the gallery must be Permanent (no auto-delete)", item?.keep == true)
        }
    }
}
