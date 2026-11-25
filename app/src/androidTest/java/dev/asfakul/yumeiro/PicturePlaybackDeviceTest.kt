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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PictureChain
import dev.asfakul.yumeiro.player.PictureLook
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.stream.StreamMediaFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * A4K-3 profiled on the emulator with the app's own player factory and the real effect path ([applyEngineLevel]):
 * the same clip plays (1) with no effects, (2) with a preset + sharpness + grain while the values change every
 * 16 ms (a finger dragging a slider), (3) with Anime4K × 2 + the grade. Rendered and dropped frames, main-thread
 * stalls and graph rebuilds are logged (tag YuPicturePlay) as the before/after profile. PICTURE_PLAY_RESULT PASS.
 */
@RunWith(AndroidJUnit4::class)
class PicturePlaybackDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val scenario = ActivityScenarioRule(MainActivity::class.java)
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private var player: ExoPlayer? = null
    private val error = AtomicReference<PlaybackException?>(null)
    private val maxStall = AtomicLong()
    @Volatile private var watching = false
    private var a4kWas = false

    private fun log(s: String) = Log.w("YuPicturePlay", s)
    private fun <T> main(block: () -> T): T { val r = AtomicReference<T>(); inst.runOnMainSync { r.set(block()) }; return r.get() }

    @Before fun setUp() {
        a4kWas = VideoPrefs.anime4k.value
        PictureLook.init(ctx); PictureLook.resetAll(ctx)
        watching = true
        val h = Handler(Looper.getMainLooper())
        Thread({
            while (watching) {
                val t0 = SystemClock.uptimeMillis(); val ran = AtomicLong()
                h.post { ran.set(SystemClock.uptimeMillis()) }
                while (ran.get() == 0L && watching) SystemClock.sleep(2)
                val lag = (ran.get().takeIf { it > 0 } ?: SystemClock.uptimeMillis()) - t0
                if (lag > maxStall.get()) maxStall.set(lag)
                SystemClock.sleep(20)
            }
        }, "picture-stall").apply { isDaemon = true }.start()
    }

    @After fun tearDown() {
        watching = false
        main { player?.release() }
        PictureLook.resetAll(ctx); PictureLook.resetForTests(); VideoPrefs.setAnime4k(ctx, a4kWas)
    }

    private fun newPlayer(uri: String): ExoPlayer = main {
        val p = StreamMediaFactory.createPlayer(ctx, uri)
        var sv: SurfaceView? = null
        scenario.scenario.onActivity { a ->
            sv = SurfaceView(a)
            a.setContentView(FrameLayout(a).apply { addView(sv, FrameLayout.LayoutParams(-1, -1)) })
        }
        p.setVideoSurfaceView(sv)
        p.addListener(object : Player.Listener { override fun onPlayerError(e: PlaybackException) { error.set(e); log("player error ${e.errorCodeName}: ${e.message}") } })
        p.also { player = it }
    }

    private data class Seg(val rendered: Int, val dropped: Int)
    private fun counts(p: ExoPlayer) = main { p.videoDecoderCounters?.let { Seg(it.renderedOutputBufferCount, it.droppedBufferCount) } ?: Seg(0, 0) }
    private fun segment(p: ExoPlayer, ms: Long, during: (() -> Unit)? = null): Seg {
        maxStall.set(0)
        val a = counts(p)
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) { during?.invoke(); SystemClock.sleep(16) }
        val b = counts(p)
        return Seg(b.rendered - a.rendered, b.dropped - a.dropped)
    }

    @Test fun livePreviewKeepsPlaybackSmooth() {
        val f = File(ctx.cacheDir, "picture_clip.mp4")
        inst.context.assets.open("yumeiro_test_clip.mp4").use { i -> f.outputStream().use { i.copyTo(it) } }
        val uri = android.net.Uri.fromFile(f).toString()
        val p = newPlayer(uri)
        val first = CountDownLatch(1)
        main {
            p.addListener(object : Player.Listener { override fun onRenderedFirstFrame() { first.countDown() } })
            StreamMediaFactory.setMedia(p, uri, title = "Picture test · S1E1")
            p.repeatMode = Player.REPEAT_MODE_ONE; p.prepare(); p.playWhenReady = true
        }
        assertTrue("first frame", first.await(60, TimeUnit.SECONDS))
        SystemClock.sleep(1500)

        val base = segment(p, 6_000)
        val baseStall = maxStall.get()
        log("BEFORE no effects: rendered=${base.rendered} dropped=${base.dropped} stall=${baseStall}ms")

        // Media3's effect pipeline alone (identity Presentation at the clip's own 240p, no grade): on the CI emulators
        // the pipeline's final full-surface pass runs in software GL, so this is the fair baseline for the grade.
        PictureLook.setTitle(PictureLook.titleKey("Picture test · S1E1"))
        main { dev.asfakul.yumeiro.ui.screens.video.applyEngineLevel(p, 240, upscale = false) }
        assertTrue("no grade pass while neutral", !PictureChain.lastHadGrade)
        SystemClock.sleep(1500)
        val pipe = segment(p, 6_000)
        log("PIPELINE only: rendered=${pipe.rendered} dropped=${pipe.dropped} stall=${maxStall.get()}ms")

        // Preset + controls, then a 6 s "drag": the live grade changes every 16 ms with no rebuild.
        PictureLook.save(ctx, PictureLook.Profile(PictureLook.Look.CINEMATIC, PictureLook.Controls(sharpness = 0.5f), PictureLook.Tuning(grain = 0.3f)), false, null)
        main { dev.asfakul.yumeiro.ui.screens.video.applyEngineLevel(p, 240, upscale = false, previewing = true) }
        assertTrue("grade pass in the chain", PictureChain.lastHadGrade)
        SystemClock.sleep(1500)
        var i = 0
        val setsBefore = StreamMediaFactory.effectSets.get()
        val drag = segment(p, 6_000) {
            i++
            val g = PictureLook.live.grade
            PictureLook.live.grade = g.copy(brightness = ((i % 50) - 25) / 100f, saturation = 0.5f + (i % 40) / 40f)
        }
        val dragStall = maxStall.get()
        val rebuilds = StreamMediaFactory.effectSets.get() - setsBefore
        log("AFTER grade + live drag ($i updates): rendered=${drag.rendered} dropped=${drag.dropped} stall=${dragStall}ms")

        VideoPrefs.setAnime4k(ctx, true)
        PictureLook.save(ctx, PictureLook.global.value.copy(tuning = PictureLook.Tuning(iterations = 2, grain = 0.3f)), false, null)
        main { dev.asfakul.yumeiro.ui.screens.video.applyEngineLevel(p, 240, upscale = false) }
        SystemClock.sleep(1500)
        val a4k = segment(p, 6_000)
        log("AFTER Anime4K x2 + grade: rendered=${a4k.rendered} dropped=${a4k.dropped} stall=${maxStall.get()}ms")

        assertEquals("no player error", null, error.get()?.errorCodeName)
        val extra = drag.dropped - pipe.dropped
        log("PROFILE live grade: rebuilds=$rebuilds, rendered ${drag.rendered} vs pipeline ${pipe.rendered}, extra dropped $extra (software GL; real GPU in MANUAL_TESTS A4K-FPS)")
        // 24 fps clip: ~144 frames in 6 s.
        assertTrue("baseline played (${base.rendered})", base.rendered > 100)
        assertTrue("pipeline played (${pipe.rendered})", pipe.rendered > 20)
        // Live preview: 60 value changes a second reach the shader with no effect-graph rebuild.
        assertEquals("effect graph rebuilt during the drag", 0, rebuilds)
        assertTrue("live drag kept the frame rate (${drag.rendered} vs pipeline ${pipe.rendered})", drag.rendered >= pipe.rendered * 0.8)
        assertTrue("main thread stalled ${dragStall}ms during drag", dragStall < 250)
        assertTrue("Anime4K x2 + grade not frozen (${a4k.rendered})", a4k.rendered > 20)
        log("PICTURE_PLAY_RESULT PASS")
    }
}
