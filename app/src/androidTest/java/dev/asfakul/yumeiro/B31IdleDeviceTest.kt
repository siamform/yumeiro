/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.stream.StreamMediaFactory
import dev.asfakul.yumeiro.stream.torrent.TorrentEngine
import dev.asfakul.yumeiro.stream.torrent.TorrentStreams
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * TOR-IDLE-1 on a device: Sintel (CC BY 3.0, the open film B31FrameDeviceTest uses) plays, the player is paused for
 * longer than TorrentStreams.IDLE_MS (the engine pauses), then a seek to a part not downloaded yet must play again.
 * Before the fix the idle timer closed the source and that read failed. Budget: 15 s (the engine reconnects to
 * peers, like opening the film). Log tag B31I.
 */
@RunWith(AndroidJUnit4::class)
class B31IdleDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx = ins.targetContext.applicationContext
    private fun log(s: String) = Log.i("B31I", s)

    @Test fun aLongPauseThenASeekPlaysOn(): Unit = runBlocking {
        FeatureFlags.set(ctx, FeatureFlags.Flag.BUILTIN_TORRENT, true)
        dev.asfakul.yumeiro.stream.TorrentSettings.init(ctx)
        dev.asfakul.yumeiro.stream.TorrentSettings.setEngineAccepted(ctx, true)
        val trace = CopyOnWriteArrayList<String>()
        var player: ExoPlayer? = null
        var reader: ImageReader? = null
        val drain = HandlerThread("b31i-frames").apply { start() }
        try {
            dev.asfakul.yumeiro.stream.torrent.TorrentTrace.sink = { trace += it; Log.i("B31", it) }
            val http = NetworkClient.get(ctx)
            withTimeout(240_000) { TorrentEngine.ensure(ctx, http) }
            val opened = withTimeout(120_000) { TorrentStreams.open(ctx, SINTEL, null, http) }
            val firstFrameAt = AtomicLong(0)
            val error = AtomicReference<PlaybackException?>(null)
            ins.runOnMainSync {
                val fmt = if (android.os.Build.VERSION.SDK_INT >= 29) ImageFormat.PRIVATE else ImageFormat.YUV_420_888
                reader = ImageReader.newInstance(1280, 720, fmt, 4).apply {
                    setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, Handler(drain.looper))
                }
                player = StreamMediaFactory.createPlayer(ctx, opened.url).apply {
                    setVideoSurface(reader!!.surface)
                    volume = 0f
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { if (firstFrameAt.get() == 0L) firstFrameAt.set(SystemClock.elapsedRealtime()) }
                        override fun onPlayerError(e: PlaybackException) { error.set(e) }
                    })
                    setMediaItem(StreamMediaFactory.buildMediaItem(opened.url))
                    playWhenReady = true
                    prepare()
                }
            }
            val until = SystemClock.elapsedRealtime() + 60_000
            while (firstFrameAt.get() == 0L && error.get() == null && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
            assertTrue("no first frame: ${error.get()?.errorCodeName}", firstFrameAt.get() > 0 && error.get() == null)

            ins.runOnMainSync { player!!.pause() }
            val pauseMs = TorrentStreams.IDLE_MS + 20_000
            log("B31I_PAUSE ms=$pauseMs")
            SystemClock.sleep(pauseMs)
            val idled = trace.any { it == "idle pause" }
            log("B31I_IDLED $idled error=${error.get()?.errorCodeName}")

            val target = AtomicLong(0)
            ins.runOnMainSync { val d = player!!.duration; target.set(d * 3 / 4); player!!.seekTo(d * 3 / 4); player!!.play() }
            val t1 = SystemClock.elapsedRealtime()
            var resumedMs = -1L
            while (SystemClock.elapsedRealtime() - t1 < 45_000 && error.get() == null) {
                val pos = AtomicLong(0); val ready = AtomicReference(false)
                ins.runOnMainSync { pos.set(player!!.currentPosition); ready.set(player!!.playbackState == Player.STATE_READY && player!!.isPlaying) }
                if (ready.get() && pos.get() > target.get() + 200) { resumedMs = SystemClock.elapsedRealtime() - t1; break }
                SystemClock.sleep(100)
            }
            val resumed = trace.any { it == "idle resume" }
            log("B31I_PLAY_ON ms=$resumedMs idled=$idled resumed=$resumed error=${error.get()?.errorCodeName} ${error.get()?.cause}")
            assertTrue("the engine was not paused by the idle timer", idled)
            assertTrue("player error after the long pause: ${error.get()?.errorCodeName} ${error.get()?.cause}", error.get() == null)
            assertTrue("no playback after the long pause in 45 s", resumedMs > 0)
            assertTrue("the read did not resume the engine", resumed)
            assertTrue("playing again took $resumedMs ms (budget 15000)", resumedMs <= 15_000)
        } finally {
            ins.runOnMainSync { player?.release() }
            reader?.close(); drain.quitSafely()
            dev.asfakul.yumeiro.stream.torrent.TorrentTrace.sink = null
            dev.asfakul.yumeiro.stream.TorrentSettings.setEngineAccepted(ctx, false)
            FeatureFlags.set(ctx, FeatureFlags.Flag.BUILTIN_TORRENT, false)
        }
    }

    private companion object {
        const val SINTEL = "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Sintel" +
            "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337&tr=udp%3A%2F%2Fexplodie.org%3A6969" +
            "&ws=https%3A%2F%2Fwebtorrent.io%2Ftorrents%2F&xs=https%3A%2F%2Fwebtorrent.io%2Ftorrents%2Fsintel.torrent"
    }
}
