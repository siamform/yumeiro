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
import dev.asfakul.yumeiro.online.OnboardingGate
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * R16 Part 1: the player's Quality list on a real (emulated) device, through the real UI.
 *
 * A public multi-rendition HLS stream is opened in Yumeiro's own player. The test reads the
 * stream's renditions straight from its master playlist (independently of the app), opens the
 * Quality dialog from the player controls, and checks: "Auto" first, then one row per real
 * height, highest first, no duplicates, nothing invented. It then picks two heights and checks
 * that the decoder really switches to each, without starting over, and goes back to Auto.
 */
@RunWith(AndroidJUnit4::class)
class QualityListDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    private fun log(s: String) = Log.i("YuQualityTest", s)

    @Before fun setUp() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        runCatching { ui.grantRuntimePermission(ctx.packageName, "android.permission.POST_NOTIFICATIONS") }
        rule.waitForIdle()
        OnboardingGate.release()
        PlaybackDiagnostics.reset("")
    }

    /** Heights (the short side) listed in the master playlist, read without the app's code. */
    private fun manifestHeights(): List<Int> {
        val body = okhttp3.OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(30)).build()
            .newCall(okhttp3.Request.Builder().url(url).build()).execute().use { it.body!!.string() }
        return Regex("RESOLUTION=(\\d+)x(\\d+)").findAll(body).map { minOf(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            .distinct().sortedDescending().toList()
    }

    private fun wait(ms: Long, what: String, cond: () -> Boolean) {
        val ok = runCatching { rule.waitUntil(ms) { cond() }; true }.getOrDefault(false)
        assertTrue("timed out after ${ms}ms waiting for $what · playback=${PlaybackDiagnostics.state.value}", ok)
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()

    private fun openQualityDialog() {
        // The controls hide themselves; a tap on the video shows them.
        repeat(4) {
            if (!has("video_quality_btn")) runCatching { rule.onAllNodesWithTag("video_player_root", true)[0].performClick() }
            if (runCatching { rule.waitUntil(3_000) { has("video_quality_btn") }; true }.getOrDefault(false)) {
                rule.onAllNodesWithTag("video_quality_btn", true)[0].performClick()
                if (runCatching { rule.waitUntil(5_000) { has("quality_Auto") }; true }.getOrDefault(false)) return
            }
        }
        throw AssertionError("the Quality dialog never opened")
    }

    /** The dialog's rows, top to bottom: (label, selected). */
    private fun rows(): List<Pair<String, Boolean>> {
        val nodes = rule.onAllNodes(SemanticsMatcher("quality row") { n ->
            n.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("quality_") == true
        }, true).fetchSemanticsNodes()
        return nodes.sortedBy { it.positionInRoot.y }.map { n ->
            val tag = n.config[SemanticsProperties.TestTag].removePrefix("quality_")
            val selected = rule.onAllNodes(SemanticsMatcher("check in $tag") { c ->
                c.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.contains("Selected") == true &&
                    generateSequence(c.parent) { it.parent }.any { p -> p.id == n.id }
            }, true).fetchSemanticsNodes().isNotEmpty()
            tag to selected
        }
    }

    private fun back() { InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close() }

    private fun shortSide() = PlaybackDiagnostics.state.value.let { minOf(it.width, it.height) }

    private fun pick(label: String, height: Int) {
        val before = PlaybackDiagnostics.state.value.positionMs
        openQualityDialog()
        rule.onAllNodesWithTag("quality_$label", true)[0].performClick()
        wait(25_000, "decoder at ${height}p after picking $label") { abs(shortSide() - height) <= 4 }
        val s = PlaybackDiagnostics.state.value
        log("picked $label: decoder ${s.width}x${s.height} at ${s.positionMs}ms (was ${before}ms) error=${s.error}")
        assertTrue("picking $label restarted playback: ${before}ms → ${s.positionMs}ms", s.positionMs >= before - 1_500)
        assertTrue("picking $label raised a player error: ${s.error}", s.error == null)
        openQualityDialog()
        val r = rows()
        assertEquals("after picking $label the dialog marks the wrong row: $r", listOf(label), r.filter { it.second }.map { it.first })
        back()
        rule.waitUntil(5_000) { !has("quality_Auto") }
    }

    @Test fun qualityListMatchesTheStreamAndEachHeightPlays() {
        val heights = manifestHeights()
        log("manifest heights: $heights")
        assertTrue("test stream has no ladder: $heights", heights.size >= 3)
        rule.runOnUiThread { IncomingMedia.open(ctx, StreamRequest(url = url, title = "Quality ladder"), remember = false) }
        wait(90_000, "first frame") { PlaybackDiagnostics.state.value.firstFrame }
        // Let the adaptive player settle so the track list is complete.
        val settle = System.currentTimeMillis() + 3_000
        rule.waitUntil(6_000) { System.currentTimeMillis() > settle }

        openQualityDialog()
        val r = rows()
        log("dialog rows: $r")
        assertEquals("first row must be Auto: $r", "Auto", r.first().first)
        val labels = r.drop(1).map { it.first }
        assertEquals("duplicate rows: $labels", labels.distinct(), labels)
        val listed = labels.map { it.substringBefore('p').toInt() }
        assertEquals("rows must be highest first: $labels", listed.sortedDescending(), listed)
        assertEquals("rows must be exactly the stream's heights", heights, listed)
        assertTrue("Auto should be selected by default: $r", r.first().second)
        back()
        rule.waitUntil(5_000) { !has("quality_Auto") }

        // Lowest real height, then the highest, then back to Auto.
        val low = heights.last(); val high = heights.first()
        pick("${low}p", low)
        pick("${high}p", high)
        openQualityDialog()
        rule.onAllNodesWithTag("quality_Auto", true)[0].performClick()
        val after = System.currentTimeMillis() + 4_000
        rule.waitUntil(8_000) { System.currentTimeMillis() > after }
        assertTrue("player error after Auto: ${PlaybackDiagnostics.state.value.error}", PlaybackDiagnostics.state.value.error == null)
        log("QUALITY_RESULT PASS heights=$heights")
    }
}
