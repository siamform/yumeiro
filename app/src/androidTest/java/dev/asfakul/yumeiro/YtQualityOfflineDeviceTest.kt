/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.download.YtDownloads
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.QualityEngine
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.youtube.YouTube
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File

/**
 * BUG-4 (F1) without YouTube: the app plays a YouTube video from a local file
 * (`file://…/cache/yt/<id>_<itag>.mpd`). Before 9e1c6c4 the Quality dialog built its list only for
 * network URIs, so such a video showed the playing height and engine levels only. Here the video is
 * a local clip registered with a YouTube page URL, and the stream info is a built one with five
 * resolutions (CI IPs get YouTube's bot wall, see YtQualityDeviceTest). Asserts the dialog shows
 * every resolution of the download list as a real stream. Applying a pick needs real YouTube
 * links, so that part stays with YtQualityDeviceTest / OMT.
 */
@RunWith(AndroidJUnit4::class)
class YtQualityOfflineDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "YumeiroT001" // 11 characters, not a real video

    private fun log(s: String) = Log.i("YuYtQuality", s)

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching { ui.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    private fun itag(n: Int, w: Int, h: Int, codec: String) = ItagItem(ItagItem.getItag(n)).apply { width = w; height = h; setCodec(codec); bitrate = h * 2_000 }

    private fun video(n: Int, w: Int, h: Int) = VideoStream.Builder()
        .setId("$n").setContent("https://invalid.example/yumeiro-test/v$n", true)
        .setMediaFormat(MediaFormat.MPEG_4).setIsVideoOnly(true).setResolution("${h}p")
        .setItagItem(itag(n, w, h, "avc1.4d401f")).build()

    private fun builtInfo(): StreamInfo {
        val info = StreamInfo(0, YouTube.watchUrl(id), YouTube.watchUrl(id), StreamType.VIDEO_STREAM, id, "Yumeiro BUG-4 test", 0)
        info.videoOnlyStreams = listOf(video(137, 1920, 1080), video(136, 1280, 720), video(135, 854, 480), video(134, 640, 360), video(133, 426, 240))
        info.videoStreams = emptyList()
        info.audioStreams = listOf(
            AudioStream.Builder().setId("140").setContent("https://invalid.example/yumeiro-test/a140", true)
                .setMediaFormat(MediaFormat.M4A).setAverageBitrate(128).setItagItem(ItagItem.getItag(140)).build(),
        )
        return info
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()

    private fun anyRow() = rule.onAllNodes(SemanticsMatcher("quality row") { n ->
        n.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("quality_") == true
    }, false).fetchSemanticsNodes()

    private fun openQualityDialog() {
        repeat(4) {
            if (!has("video_quality_btn")) runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            if (runCatching { rule.waitUntil(3_000) { has("video_quality_btn") }; true }.getOrDefault(false)) {
                rule.onAllNodesWithTag("video_quality_btn", true)[0].performClick()
                if (runCatching { rule.waitUntil(8_000) { anyRow().size > 1 }; true }.getOrDefault(false)) return
            }
        }
        FirstRun.shot("ytqo_dialog_missing")
        throw AssertionError("the Quality dialog never opened with rows · ${FirstRun.onScreen(rule)}")
    }

    private fun rows(): List<Pair<String, String>> = anyRow().sortedBy { it.positionInRoot.y }.map { n ->
        n.config[SemanticsProperties.TestTag].removePrefix("quality_") to
            (n.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString(" · ") { it.text }.orEmpty())
    }

    @Test fun localYouTubeFileListsEveryDownloadHeight() {
        val info = builtInfo()
        YouTube.seedForTests(id, info)
        val heights = QualityEngine.youTubeLadder(YtDownloads.options(info))
        log("YT_OFFLINE download sheet heights: $heights")
        assertTrue("built info should give five heights: $heights", heights.size == 5)

        val dir = File(ctx.cacheDir, "yt").apply { mkdirs() }
        val clip = File(dir, "${id}_test.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("yumeiro_test_clip.mp4").use { i -> clip.outputStream().use { i.copyTo(it) } }
        val req = StreamRequest(url = android.net.Uri.fromFile(clip).toString(), title = "Yumeiro BUG-4 test", pageUrl = YouTube.watchUrl(id), group = "YouTube")
        rule.runOnUiThread { IncomingMedia.open(ctx, req, remember = false, id = "yt:$id", folder = "YouTube") }
        val ok = runCatching { rule.waitUntil(60_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false)
        assertTrue("no first frame · ${PlaybackDiagnostics.state.value}", ok)
        val settle = System.currentTimeMillis() + 2_000
        rule.waitUntil(5_000) { System.currentTimeMillis() > settle }

        openQualityDialog()
        val r = rows()
        r.forEach { log("YT_OFFLINE_ROW ${it.first} | ${it.second}") }
        FirstRun.shot("ytqo_dialog")
        val labels = r.map { it.first }
        val missing = heights.map { QualityEngine.label(it) }.filter { it !in labels }
        val engineOnly = heights.filter { h -> r.firstOrNull { it.first == QualityEngine.label(h) }?.second?.contains("by Yumeiro") == true }
        log("YT_OFFLINE_RESULT ${if (missing.isEmpty() && engineOnly.isEmpty()) "PASS" else "FAIL"} rows=$labels missing=$missing engineOnly=$engineOnly")
        assertTrue("player list lacks download heights $missing · rows=$labels", missing.isEmpty())
        assertTrue("download heights shown as engine levels, not streams: $engineOnly", engineOnly.isEmpty())
        FirstRun.shell("input keyevent 4")
        clip.delete()
    }
}
