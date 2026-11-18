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
import dev.asfakul.yumeiro.youtube.YouTubeStreams
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * BUG-4 (F1) on an emulator: a YouTube video opened the way the app opens it (local DASH manifest)
 * lists every resolution the download sheet lists, as real streams (not "Scaled by Yumeiro"), and
 * picking a height really changes the decoded height.
 */
@RunWith(AndroidJUnit4::class)
class YtQualityDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "aqz-KE-bpKQ" // Big Buck Bunny (Blender Foundation, CC-BY)

    private fun log(s: String) = Log.i("YuYtQuality", s)

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching { ui.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
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
                if (runCatching { rule.waitUntil(5_000) { anyRow().isNotEmpty() }; true }.getOrDefault(false)) return
            }
        }
        FirstRun.shot("ytq_dialog_missing")
        throw AssertionError("the Quality dialog never opened · ${FirstRun.onScreen(rule)}")
    }

    /** (label, row text) top to bottom. */
    private fun rows(): List<Pair<String, String>> = anyRow().sortedBy { it.positionInRoot.y }.map { n ->
        n.config[SemanticsProperties.TestTag].removePrefix("quality_") to
            (n.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString(" · ") { it.text }.orEmpty())
    }

    private fun shortSide() = PlaybackDiagnostics.state.value.let { minOf(it.width, it.height) }

    private fun back() { FirstRun.shell("input keyevent 4") }

    @Test fun youTubeListsEveryDownloadHeightAndAppliesThem() {
        val (download, info) = runCatching {
            runBlocking { YtDownloads.optionsFor(ctx, id).let { (i, c) -> c to i } }
        }.onFailure { log("YT_QUALITY_RESULT SKIP youtube unreachable: $it") }.getOrNull() ?: (null to null)
        assumeTrue("YouTube unreachable from this emulator", download != null && info != null)
        val heights = QualityEngine.youTubeLadder(download!!)
        log("download sheet heights: $heights")
        assertTrue("download sheet should list several heights: $heights", heights.size >= 3)

        val req = runBlocking { YouTubeStreams.videoRequest(ctx, id, maxHeight = 360) }
        log("request url=${req.url.substringAfterLast('/')} page=${req.pageUrl} group=${req.group}")
        rule.runOnUiThread { IncomingMedia.open(ctx, req, id = "yt:$id", folder = "YouTube") }
        val ok = runCatching { rule.waitUntil(90_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false)
        assertTrue("no first frame · ${PlaybackDiagnostics.state.value}", ok)
        val settle = System.currentTimeMillis() + 3_000
        rule.waitUntil(6_000) { System.currentTimeMillis() > settle }
        FirstRun.shot("ytq_playing")

        openQualityDialog()
        val r = rows()
        r.forEach { log("YT_QUALITY_ROW ${it.first} | ${it.second}") }
        FirstRun.shot("ytq_dialog")
        val labels = r.map { it.first }
        val missing = heights.map { QualityEngine.label(it) }.filter { it !in labels }
        assertTrue("player list lacks download heights $missing · rows=$labels", missing.isEmpty())
        val engineRows = r.filter { "by Yumeiro" in it.second }
        assertTrue("YouTube rows are engine levels, not real streams: $engineRows", engineRows.isEmpty())
        back()
        rule.waitUntil(5_000) { anyRow().isEmpty() }

        // Apply: every height up to 720p that plays as itself (no "Plays as" note), lowest first.
        val noted = r.filter { "Plays as" in it.second }.map { it.first }.toSet()
        val toPick = heights.filter { it <= 720 && QualityEngine.label(it) !in noted }.sorted()
        val applied = mutableListOf<String>()
        for (h in toPick) {
            val before = PlaybackDiagnostics.state.value.positionMs
            openQualityDialog()
            rule.onAllNodesWithTag("quality_${QualityEngine.label(h)}", true)[0].performClick()
            val got = runCatching { rule.waitUntil(30_000) { abs(shortSide() - h) <= 8 && PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false)
            val s = PlaybackDiagnostics.state.value
            log("YT_QUALITY_PICK ${h}p -> decoder ${s.width}x${s.height} pos ${before}->${s.positionMs} error=${s.error} ok=$got")
            FirstRun.shot("ytq_${h}p")
            assertTrue("picking ${h}p did not apply: decoder ${s.width}x${s.height}", got)
            assertTrue("picking ${h}p raised an error: ${s.error}", s.error == null)
            applied += "${h}p"
        }
        log("YT_QUALITY_RESULT PASS listed=$labels applied=$applied")
    }
}
