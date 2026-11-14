/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.stream.StreamMediaFactory
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.stream.StreamRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * R15 Phase 2 evidence on an emulator, with a real surface and the app's own player factory:
 *  - engine quality levels (GPU scale / Anime4K) switch without a "(timeout)" error;
 *  - every rendition of a multi-quality DASH stream plays with picture *and* decoded sound;
 *  - YouTube: each height from the quality list opens without blocking the main thread
 *    (skipped, with the reason logged, when YouTube refuses the CI machine's address).
 * Results go to logcat (`YuPlayerTest`) and files/stress/player-report.txt.
 */
@RunWith(AndroidJUnit4::class)
class PlayerSwitchDeviceTest {
    @get:Rule val scenario = ActivityScenarioRule(MainActivity::class.java)
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val report = StringBuilder()
    private var player: ExoPlayer? = null
    private val error = AtomicReference<PlaybackException?>(null)
    private val maxStall = AtomicLong()
    @Volatile private var watching = false

    private fun log(s: String) { Log.i("YuPlayerTest", s); synchronized(report) { report.append(s).append('\n') } }

    private fun <T> main(block: () -> T): T { val r = AtomicReference<T>(); inst.runOnMainSync { r.set(block()) }; return r.get() }

    @Before fun setUp() {
        watching = true
        val h = Handler(Looper.getMainLooper())
        Thread({
            while (watching) {
                val t0 = SystemClock.uptimeMillis(); val ran = AtomicLong()
                h.post { ran.set(SystemClock.uptimeMillis()) }
                while (ran.get() == 0L && watching) SystemClock.sleep(5)
                val lag = (ran.get().takeIf { it > 0 } ?: SystemClock.uptimeMillis()) - t0
                if (lag > maxStall.get()) maxStall.set(lag)
                if (lag > 500) log("main thread late by ${lag}ms")
                SystemClock.sleep(40)
            }
        }, "player-stall").apply { isDaemon = true }.start()
    }

    @After fun tearDown() {
        watching = false
        main { player?.release() }
        val dir = File(ctx.getExternalFilesDir(null), "stress").apply { mkdirs() }
        File(dir, "player-report.txt").appendText(report.toString() + "\n")
    }

    private fun newPlayer(uri: String): ExoPlayer = main {
        val p = StreamMediaFactory.createPlayer(ctx, uri)
        var sv: SurfaceView? = null
        scenario.scenario.onActivity { a ->
            sv = SurfaceView(a)
            a.setContentView(FrameLayout(a).apply { addView(sv, FrameLayout.LayoutParams(-1, -1)) })
        }
        p.setVideoSurfaceView(sv)
        p.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error.set(e); log("player error ${e.errorCodeName}: ${e.message}") }
        })
        p.also { player = it }
    }

    private fun counters(p: ExoPlayer): Pair<Int, Int> = main {
        (p.videoDecoderCounters?.renderedOutputBufferCount ?: 0) to (p.audioDecoderCounters?.renderedOutputBufferCount ?: 0)
    }

    /** Plays for [ms] and returns (video frames, audio buffers) rendered meanwhile. */
    private fun playFor(p: ExoPlayer, ms: Long): Pair<Int, Int> {
        val (v0, a0) = counters(p)
        SystemClock.sleep(ms)
        val (v1, a1) = counters(p)
        return (v1 - v0) to (a1 - a0)
    }

    private fun waitFirstFrame(p: ExoPlayer, timeoutMs: Long = 60_000): Boolean {
        val latch = CountDownLatch(1)
        main { p.addListener(object : Player.Listener { override fun onRenderedFirstFrame() { latch.countDown() } }) }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    @Test fun a_engineLevelsSwitchWithoutTimeout() {
        val uri = "https://archive.org/download/f20110625mad/f20110625mad.mp4"
        val p = newPlayer(uri)
        main { StreamMediaFactory.setMedia(p, uri, title = "engine"); p.prepare(); p.playWhenReady = true }
        assumeTrue("test video reachable", waitFirstFrame(p))
        for ((h, up) in listOf(360 to false, 240 to false, 720 to true, null to false)) {
            val t0 = SystemClock.uptimeMillis()
            main { dev.asfakul.yumeiro.ui.screens.video.applyEngineLevel(p, h, up) }
            val callMs = SystemClock.uptimeMillis() - t0
            val (v, a) = playFor(p, 6_000)
            log("engine level $h (upscale=$up): call ${callMs}ms, +$v frames, +$a audio buffers, error=${error.get()?.errorCodeName}")
            assertTrue("engine level $h failed: ${error.get()?.errorCodeName}", error.get() == null)
            assertTrue("engine level $h: picture froze", v > 20)
        }
        assertTrue("main thread blocked ${maxStall.get()}ms", maxStall.get() < 2_000)
    }

    @Test fun b_everyDashRenditionHasPictureAndSound() {
        val uri = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd"
        val p = newPlayer(uri)
        main { StreamMediaFactory.setMedia(p, uri, title = "dash"); p.prepare(); p.playWhenReady = true }
        assumeTrue("test stream reachable", waitFirstFrame(p))
        val groups = main { p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO } }
        assertTrue("video ladder present", groups.isNotEmpty())
        val g = groups.first()
        for (i in 0 until g.length) {
            val f = g.getTrackFormat(i)
            if (!g.isTrackSupported(i)) { log("rendition ${f.height}p ${f.codecs}: not supported on this device (skipped)"); continue }
            main {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
            }
            SystemClock.sleep(2_500)
            val (v, a) = playFor(p, 5_000)
            val vf = main { p.videoFormat }; val af = main { p.audioFormat }
            log("rendition ${f.height}p ${f.codecs}: playing ${vf?.height}p, +$v frames, audio ${af?.sampleMimeType} +$a buffers, error=${error.get()?.errorCodeName}")
            assertTrue("rendition ${f.height}p failed", error.get() == null)
            assertTrue("rendition ${f.height}p: no picture", v > 20)
            assertTrue("rendition ${f.height}p: no sound", af != null && a > 20)
        }
        assertTrue("main thread blocked ${maxStall.get()}ms", maxStall.get() < 2_000)
    }

    @Test fun c_youTubeQualityListOpensEveryHeight() {
        val id = InstrumentationRegistry.getArguments().getString("ytId") ?: "aqz-KE-bpKQ" // Big Buck Bunny (Blender, CC-BY), up to 2160p
        val first = runCatching { runBlocking { dev.asfakul.yumeiro.youtube.YouTubeStreams.videoRequest(ctx, id, maxHeight = 360) } }
        first.exceptionOrNull()?.let { log("YouTube refused this machine: ${it.javaClass.simpleName}: ${it.message?.take(200)}") }
        assumeTrue("YouTube reachable from this machine", first.isSuccess)
        val alts = runBlocking { dev.asfakul.yumeiro.player.QualityEngine.alternatives(ctx, first.getOrThrow(), 360) }
        log("YouTube ladder: " + alts.joinToString { it.label + (it.note?.let { n -> " [$n]" } ?: "") })
        assertTrue("YouTube offers several heights", alts.size >= 3)
        val p = newPlayer("yt:test")
        for (alt in alts.sortedBy { it.height }) {
            error.set(null)
            val stallBefore = maxStall.getAndSet(0)
            // Same call the quality dialog makes (from a main-thread coroutine).
            val req = runBlocking(kotlinx.coroutines.Dispatchers.Main) { alt.open(ctx, 0L) } ?: continue
            val openStall = maxStall.get(); maxStall.set(maxOf(stallBefore, openStall))
            main { StreamRepository.register("yt:test", req); StreamMediaFactory.setMedia(p, "yt:test", title = "yt"); p.prepare(); p.playWhenReady = true }
            val ok = waitFirstFrame(p, 45_000)
            val (v, a) = playFor(p, 5_000)
            val vf = main { p.videoFormat }; val af = main { p.audioFormat }
            log("YouTube ${alt.label}: open stalled main ${openStall}ms, first frame=$ok, playing ${vf?.height}p ${vf?.codecs}, +$v frames, audio ${af?.sampleMimeType} +$a, error=${error.get()?.errorCodeName}")
            assertTrue("YouTube ${alt.label}: main thread blocked ${openStall}ms while opening", openStall < 1_000)
            assertTrue("YouTube ${alt.label}: ${error.get()?.errorCodeName}", error.get() == null)
            assertTrue("YouTube ${alt.label}: no picture", ok && v > 10)
            assertTrue("YouTube ${alt.label}: no sound", af != null && a > 10)
        }
    }
}
