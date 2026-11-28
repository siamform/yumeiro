/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.intel.ai.AiPreset
import dev.asfakul.yumeiro.intel.ai.AiSettings
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.online.OnlineSubtitle
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.player.PlaybackSession
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamSubtitle
import dev.asfakul.yumeiro.subtitles.translate.SubtitleTranslate
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * R28 item 20: playback keeps going after a translated subtitle track is turned on.
 * A real online-style episode (MockWebServer stands in for the video host and the AI server) plays in the real
 * player; Yumeiro AI translates its English track into Bengali; the track goes in at the same position.
 * Checked after each step: the player is READY and playing, the position moves on, the translated track is the
 * selected text track, and no error card. Then a second translation (Hindi) while playing, then a far seek.
 * Log tag YuR28Sub; GR28_SUB_RESULT PASS is the evidence.
 */
@RunWith(AndroidJUnit4::class)
class GroupR28SubTlDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private fun log(s: String) = Log.i("YuR28Sub", s)
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val tag = System.nanoTime().toString().takeLast(6)
    private val id = "online:yu-r28-sub-$tag"

    private val vtt = buildString {
        append("WEBVTT\n\n")
        // A cue every 3 s for the first 20 minutes, so a translated line is on screen wherever the test looks.
        for (i in 0 until 400) {
            val a = i * 3_000L; val b = a + 2_500L
            append(t(a)).append(" --> ").append(t(b)).append('\n').append("Line number ${i + 1}.").append("\n\n")
        }
    }
    private fun t(ms: Long) = "%02d:%02d:%02d.%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_skip_2950.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.url.encodedPath
                return when {
                    p == "/v.mp4" -> {
                        val range = request.headers["Range"]
                        val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
                        if (start >= clip.size) return MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${clip.size}").build()
                        val end = (range?.substringAfter('-', "")?.toLongOrNull() ?: (clip.size - 1L)).coerceAtMost(clip.size - 1L)
                        MockResponse.Builder().code(if (range != null) 206 else 200)
                            .addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "bytes")
                            .apply { if (range != null) addHeader("Content-Range", "bytes $start-$end/${clip.size}") }
                            .throttleBody(65_536L, 250L, TimeUnit.MILLISECONDS)
                            .body(Buffer().write(clip, start.toInt(), (end - start + 1).toInt())).build()
                    }
                    p == "/sub/en.vtt" -> MockResponse.Builder().code(200).addHeader("Content-Type", "text/vtt").body(vtt).build()
                    p == "/v1/models" -> json(JSONObject().put("data", JSONArray().put(JSONObject().put("id", "yu-local").put("context_length", 8192))))
                    p == "/v1/chat/completions" -> json(chatReply(JSONObject(request.body?.utf8().orEmpty())))
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        base = "http://${server.hostName}:${server.port}"
        Profiles.init(ctx)
        AiSettings.clear(ctx)
        AiSettings.setProvider(ctx, AiPreset.CUSTOM)
        AiSettings.setExtra(ctx, "$base/v1")
        AiSettings.setModel(ctx, "yu-local")
        rule.runOnUiThread { PlaybackSession.releaseNow() }
        FirstRun.toTabs(rule)
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() {
        runCatching { SubtitleTranslate.cancel() }
        runCatching { rule.runOnUiThread { OnlineSession.setStateForTest(null); PlaybackSession.releaseNow() } }
        runCatching { AiSettings.clear(ctx) }
        runCatching { server.close() }
    }

    private fun json(o: JSONObject) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(o.toString()).build()

    private fun chatReply(req: JSONObject): JSONObject {
        val msgs = req.optJSONArray("messages") ?: JSONArray()
        val system = (0 until msgs.length()).map { msgs.getJSONObject(it) }.firstOrNull { it.optString("role") == "system" }?.optString("content").orEmpty()
        val user = (0 until msgs.length()).map { msgs.getJSONObject(it) }.lastOrNull { it.optString("role") == "user" }?.optString("content").orEmpty()
        val lang = if (system.contains("Hindi", true) || user.contains("\"hi\"")) "hi" else "bn"
        val content = if (system.contains("subtitles", true) && user.startsWith("{")) {
            val lines = JSONObject(user).optJSONArray("lines") ?: JSONArray()
            JSONObject().put("t", JSONArray((0 until lines.length()).map { "[$lang] " + lines.optString(it) })).toString()
        } else "ready"
        return JSONObject().put("model", "yu-local").put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content))))
    }

    private fun player(): Player? = PlaybackSession.playerForTest
    private data class Snap(val state: Int, val playing: Boolean, val pos: Long, val text: String?, val error: String?)
    private fun snap(): Snap {
        var s = Snap(0, false, -1, null, null)
        rule.runOnUiThread {
            val p = player()
            val sel = p?.currentTracks?.groups?.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            val label = sel?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it).label } }
            s = Snap(p?.playbackState ?: 0, p?.isPlaying == true, p?.currentPosition ?: -1, label, p?.playerError?.errorCodeName)
        }
        return s
    }

    /** Waits until the player is playing and has moved at least [moveMs] beyond where it was when it started playing. */
    private fun keepsPlaying(what: String, moveMs: Long = 2_000L, ms: Long = 30_000L): Snap {
        val until = System.currentTimeMillis() + ms
        var startPos = -1L
        var last = snap()
        while (System.currentTimeMillis() < until) {
            last = snap()
            if (last.state == Player.STATE_READY && last.playing) {
                if (startPos < 0) startPos = last.pos
                else if (last.pos - startPos >= moveMs) { log("GR28_SUB $what ok: $last"); return last }
            }
            Thread.sleep(250)
        }
        FirstRun.shot("gr28_sub_fail_" + what.filter { it.isLetterOrDigit() }.take(30))
        throw AssertionError("$what: playback did not keep going · $last · ${PlaybackDiagnostics.state.value}")
    }

    private fun translate(target: String): SubtitleTranslate.Status {
        rule.runOnUiThread { SubtitleTranslate.start(ctx, player()?.currentPosition ?: 0L, SubtitleTranslate.Settings(target, SubtitleTranslate.Mode.NATIVE, SubtitleTranslate.Engine.AI)) }
        runCatching { rule.waitUntil(90_000) { SubtitleTranslate.status.value.let { it is SubtitleTranslate.Status.Ready || it is SubtitleTranslate.Status.Failed } } }
        return SubtitleTranslate.status.value
    }

    private fun waitText(what: String, cond: (String?) -> Boolean) {
        val ok = runCatching { rule.waitUntil(20_000) { cond(snap().text) }; true }.getOrDefault(false)
        if (!ok) FirstRun.shot("gr28_sub_fail_track")
        assertTrue("$what · selected text track = ${snap().text}", ok)
    }

    @Test fun playbackKeepsGoingWithTranslatedSubtitles() {
        val ep = OnlineEpisode("$base/ep1-$tag", "Ep 1", 1, 1, null, null, null)
        val detail = OnlineDetail(
            api = "YuR28", url = "$base/show-$tag", name = "R28 Show", kind = OnlineKind.SERIES, type = "TvSeries", posterUrl = null, backgroundUrl = null,
            posterHeaders = emptyMap(), plot = null, year = 2026, tags = emptyList(), durationMin = null, score = null, contentRating = null,
            playData = null, episodes = mapOf("" to listOf(ep)), seasonNames = emptyMap(), recommendations = emptyList(),
        )
        val english = OnlineSubtitle("English", "$base/sub/en.vtt", emptyMap())
        rule.runOnUiThread {
            OnlineSession.setStateForTest(OnlineSession.State("YuR28", detail, "", listOf(ep), 0, phase = OnlineSession.Phase.PLAYING,
                subtitles = listOf(english), videoId = id))
            IncomingMedia.open(ctx, StreamRequest(url = "$base/v.mp4", title = "R28 Show · Ep 1",
                subtitles = listOf(StreamSubtitle(url = english.url, label = "English", language = "en", mimeType = "text/vtt"))), id = id)
        }
        val first = runCatching { rule.waitUntil(60_000) { PlaybackDiagnostics.state.value.firstFrame }; true }.getOrDefault(false)
        assertTrue("first frame · ${PlaybackDiagnostics.state.value}", first)
        rule.runOnUiThread { player()?.repeatMode = Player.REPEAT_MODE_ONE; player()?.playWhenReady = true }
        keepsPlaying("before translation")

        val bn = translate("bn")
        log("GR28_SUB bn: $bn")
        assertTrue("Bengali translation finished: $bn", bn is SubtitleTranslate.Status.Ready)
        waitText("the Bengali track is on") { it?.contains("translated") == true }
        keepsPlaying("after Bengali track", moveMs = 3_000L)
        FirstRun.shot("gr28_sub_bn_playing")

        val hi = translate("hi")
        log("GR28_SUB hi: $hi")
        assertTrue("Hindi translation finished: $hi", hi is SubtitleTranslate.Status.Ready)
        waitText("the Hindi track is on") { it?.contains("Hindi") == true }
        keepsPlaying("after Hindi track", moveMs = 3_000L)

        rule.runOnUiThread { player()?.seekTo(600_000L) }
        keepsPlaying("after a far seek with the translated track", moveMs = 2_000L, ms = 45_000L)
        val end = snap()
        assertTrue("still the translated track after the seek: $end", end.text?.contains("Hindi") == true)
        assertTrue("no player error: $end", end.error == null)
        log("GR28_SUB_RESULT PASS")
    }
}
