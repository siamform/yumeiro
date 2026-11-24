/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.player.VideoRotation
import dev.asfakul.yumeiro.sources.SourceHub
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * ROT-1 (owner list 29): the user turns the video 90° at a time from the real player and resets it.
 *
 * A source (a CloudStream MainAPI, as an installed extension is) gives a movie whose file a local
 * server streams with Range support. Everything after the source is the app's own path:
 * OnlineSession.play -> the full-screen player. The test taps the player to show its controls,
 * opens More options and taps Rotate video (or the top-bar button when Settings put it there), then
 * reads the real video surface View: its rotation and its fit scale. It checks the same ExoPlayer
 * keeps playing (no reload: same instance, position moves on, no new first frame). Then the
 * "Reset rotation · 270°" pill turns it upright. Screens go to shots/. Log tag YuRotTest.
 */
@RunWith(AndroidJUnit4::class)
class RotationPlayerDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private fun log(s: String) = Log.i(TAG, s)

    private class Movie(base: String) : MainAPI() {
        override var name = "YuRot Movie"
        override var mainUrl = base
        override val supportedTypes = setOf(TvType.Movie)
        override suspend fun load(url: String): LoadResponse =
            newMovieLoadResponse("Rotation test clip", url, TvType.Movie, "$mainUrl/v/clip.mp4")
        override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            callback(newExtractorLink(name, name, data) { quality = Qualities.P720.value }); return true
        }
    }

    @Before fun setUp() {
        // The long Skip clip, so the video is still playing through every step.
        val clip = inst.context.assets.open("yumeiro_skip_1510.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath != "/v/clip.mp4") return MockResponse.Builder().code(404).build()
                val range = request.headers["Range"]?.removePrefix("bytes=")?.split("-")
                val from = range?.getOrNull(0)?.toLongOrNull() ?: 0L
                val to = range?.getOrNull(1)?.toLongOrNull()?.coerceAtMost(clip.size - 1L) ?: (clip.size - 1L)
                val b = MockResponse.Builder().addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "bytes")
                return if (range == null) b.code(200).body(Buffer().write(clip)).build()
                else b.code(206).addHeader("Content-Range", "bytes $from-$to/${clip.size}")
                    .body(Buffer().write(clip, from.toInt(), (to - from + 1).toInt())).build()
            }
        }
        server.start()
        base = "http://${server.hostName}:${server.port}"
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        rule.runOnUiThread { runCatching { OnlineSession.end() } }
        SourceHub.remove(BRIDGE)
        runCatching { server.close() }
    }

    private fun shot(name: String) {
        inst.waitForIdleSync()
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "rot_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun has(tag: String) = rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun position(): Long { var p = -1L; inst.runOnMainSync { p = PlaybackSession.playerForTest?.currentPosition ?: -1L }; return p }

    private fun findPlayerView(v: View): PlayerView? {
        if (v is PlayerView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findPlayerView(v.getChildAt(i))?.let { return it }
        return null
    }

    /** Rotation, scale and sizes of the real video surface and its box, read on the main thread. */
    private data class Surface(val rotation: Float, val scale: Float, val w: Int, val h: Int, val boxW: Int, val boxH: Int, val texture: Boolean)
    private fun surface(): Surface {
        var s: Surface? = null
        inst.runOnMainSync {
            val pv = findPlayerView(rule.activity.window.decorView)
            val sv = pv?.videoSurfaceView
            if (pv != null && sv != null) s = Surface(sv.rotation, sv.scaleX, sv.width, sv.height, pv.width, pv.height, sv is android.view.TextureView)
        }
        return requireNotNull(s) { "no PlayerView with a video surface on screen" }
    }

    private fun controlsUp() {
        if (has("video_more_btn") || has("video_rotate_btn")) return
        rule.onNodeWithTag("video_player_root", true).performClick()
        rule.waitUntil(10_000) { has("video_more_btn") || has("video_rotate_btn") }
    }

    /** Taps Rotate video wherever Settings > Player controls placed it (top bar or More options). */
    private fun tapRotate() {
        controlsUp()
        if (has("video_rotate_btn")) { rule.onNodeWithTag("video_rotate_btn", true).performClick(); return }
        rule.onNodeWithTag("video_more_btn", true).performClick()
        rule.waitUntil(5_000) { has("video_rotate_btn_menu") }
        shot("more_menu")
        rule.onNodeWithTag("video_rotate_btn_menu", true).performClick()
    }

    private fun expectTurn(deg: Int, label: String) {
        rule.waitUntil(5_000) { abs(surface().rotation - deg) < 0.5f }
        inst.waitForIdleSync()
        val s = surface()
        val fit = VideoRotation.fitScale(deg, s.w.toFloat(), s.h.toFloat(), s.boxW.toFloat(), s.boxH.toFloat(), cover = false)
        log("CASE $label rotation=${s.rotation} scale=${s.scale} expectedFit=$fit surface=${s.w}x${s.h} box=${s.boxW}x${s.boxH} texture=${s.texture} pos=${position()}")
        assertEquals("$label: surface rotation", deg.toFloat(), s.rotation, 0.5f)
        assertEquals("$label: turned picture is scaled to fit the player", fit, s.scale, 0.02f)
        // The turned picture stays inside the player box.
        val visualW = (if (deg % 180 != 0) s.h else s.w) * s.scale
        val visualH = (if (deg % 180 != 0) s.w else s.h) * s.scale
        assertTrue("$label: turned picture ${visualW}x$visualH overflows ${s.boxW}x${s.boxH}", visualW <= s.boxW + 2 && visualH <= s.boxH + 2)
        shot(label)
    }

    @Test fun rotateInQuarterTurnsAndResetWithoutReload() {
        val api = Movie(base)
        SourceHub.publishVideo(BRIDGE, SourceHub.Ecosystem.CLOUDSTREAM, listOf(api))
        OnlineRepository.clearCaches()
        FirstRun.toTabs(rule)
        val detail = kotlinx.coroutines.runBlocking { OnlineRepository.load(api, "$base/title/rot") }
        rule.runOnUiThread { OnlineSession.play(ctx, detail, episodeIndex = 0) }
        rule.waitUntil(120_000) { PlaybackDiagnostics.state.value.firstFrame || OnlineSession.state.value?.phase == OnlineSession.Phase.FAILED }
        assertTrue("clip did not play: ${OnlineSession.state.value?.message} ${PlaybackDiagnostics.state.value}", PlaybackDiagnostics.state.value.firstFrame)
        rule.waitUntil(10_000) { has("video_player_root") }
        assertEquals("starts upright", 0f, surface().rotation, 0.5f)
        shot("upright")
        val player = PlaybackSession.playerForTest
        val diagBefore = PlaybackDiagnostics.state.value
        val startPos = position()

        tapRotate(); expectTurn(90, "r90")
        tapRotate(); expectTurn(180, "r180")
        tapRotate(); expectTurn(270, "r270")

        // Reset pill, shown with the controls while the picture is turned.
        controlsUp()
        rule.waitUntil(5_000) { has("video_rotate_reset") }
        shot("reset_pill")
        rule.onNodeWithTag("video_rotate_reset", true).performClick()
        rule.waitUntil(5_000) { abs(surface().rotation) < 0.5f }
        val s = surface()
        log("CASE reset rotation=${s.rotation} scale=${s.scale}")
        assertEquals("reset turns it upright", 0f, s.rotation, 0.5f)
        assertEquals("reset restores 100 %", 1f, s.scale, 0.02f)
        rule.waitUntil(5_000) { !has("video_rotate_reset") }
        shot("reset")

        // No reload: same player, still playing, position moved on.
        Thread.sleep(1_500)
        val endPos = position()
        var playing = false
        inst.runOnMainSync { playing = PlaybackSession.playerForTest?.isPlaying == true }
        log("CASE no-reload samePlayer=${player === PlaybackSession.playerForTest} start=$startPos end=$endPos playing=$playing diag=${PlaybackDiagnostics.state.value.state}")
        assertSame("the player was replaced while rotating", player, PlaybackSession.playerForTest)
        assertTrue("playback did not continue through the rotations ($startPos -> $endPos)", endPos > startPos + 1_000)
        assertTrue("player stopped", playing)
        assertTrue("first frame flag lost (reload)", PlaybackDiagnostics.state.value.firstFrame && diagBefore.firstFrame)
        log("ROT_RESULT PASS")
    }

    companion object {
        const val TAG = "YuRotTest"
        const val BRIDGE = "yu-rot-test"
    }
}
