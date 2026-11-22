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
import android.view.Surface
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * B31-FRAME: the goal bar is a decoded FIRST FRAME within 15 s of opening the owner's test hash and a seek to the
 * middle that plays again within 5 s, in the app's own player (StreamMediaFactory.createPlayer: same renderers,
 * extractors and data sources as the video screen). BuiltinTorrentDeviceTest only reads bytes from the loopback
 * server. Budgets unchanged (ADR-053). The engine flag is turned on for this test only. Log tag B31F.
 */
@RunWith(AndroidJUnit4::class)
class B31FrameDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx = ins.targetContext.applicationContext
    private val testHash = "BF2063A5E9A69F14716288FCA7C2D6078BDA7369"
    private fun log(s: String) = Log.i("B31F", s)

    /** The owner's test hash (10-bit HEVC: the emulator decoder c2.goldfish.hevc.decoder cannot decode it). */
    @Test fun firstFrameAndSeekInThePlayer() = check("magnet:?xt=urn:btih:$testHash", "owner")

    /**
     * B9-6 (2026-11-23): the same bar on Sintel, the Blender Foundation's open film (CC BY 3.0, H.264, which the
     * emulator decodes), from WebTorrent's public demo torrent with its web seed. Budgets unchanged.
     */
    @Test fun firstFrameAndSeekOnAnOpenFilm() = check(SINTEL, "sintel")

    private fun check(magnet: String, tag: String): Unit = runBlocking {
        log("B31F_CASE $tag")
        FeatureFlags.set(ctx, FeatureFlags.Flag.BUILTIN_TORRENT, true)
        dev.asfakul.yumeiro.stream.TorrentSettings.init(ctx)
        dev.asfakul.yumeiro.stream.TorrentSettings.setEngineAccepted(ctx, true)
        var player: ExoPlayer? = null
        var reader: ImageReader? = null
        val drain = HandlerThread("b31f-frames").apply { start() }
        try {
            dev.asfakul.yumeiro.stream.torrent.TorrentTrace.sink = { Log.i("B31", it) }
            val http = NetworkClient.get(ctx)
            withTimeout(240_000) { TorrentEngine.ensure(ctx, http) }
            val t0 = SystemClock.elapsedRealtime()
            val opened = withTimeout(120_000) { TorrentStreams.open(ctx, magnet, null, http) }
            log("opened ${opened.name} (${opened.size} B) in ${SystemClock.elapsedRealtime() - t0} ms")

            val firstFrameAt = AtomicLong(0)
            val error = AtomicReference<PlaybackException?>(null)
            ins.runOnMainSync {
                // A real video surface whose frames are taken and dropped at once, so the decoder never stalls.
                val fmt = if (android.os.Build.VERSION.SDK_INT >= 29) ImageFormat.PRIVATE else ImageFormat.YUV_420_888
                reader = ImageReader.newInstance(1280, 720, fmt, 4).apply {
                    setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, Handler(drain.looper))
                }
                val surface: Surface = reader!!.surface
                player = StreamMediaFactory.createPlayer(ctx, opened.url).apply {
                    setVideoSurface(surface)
                    volume = 0f
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            if (firstFrameAt.get() == 0L) firstFrameAt.set(SystemClock.elapsedRealtime())
                        }
                        override fun onPlayerError(e: PlaybackException) { error.set(e) }
                    })
                    setMediaItem(StreamMediaFactory.buildMediaItem(opened.url))
                    playWhenReady = true
                    prepare()
                }
            }
            val until = t0 + 60_000
            while (firstFrameAt.get() == 0L && error.get() == null && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
            val formats = AtomicReference("")
            ins.runOnMainSync { formats.set("video=${player?.videoFormat?.sampleMimeType} ${player?.videoFormat?.codecs} audio=${player?.audioFormat?.sampleMimeType}") }
            val firstMs = if (firstFrameAt.get() > 0) firstFrameAt.get() - t0 else -1
            log("B31F_FIRST_FRAME ms=$firstMs error=${error.get()?.errorCodeName} ${error.get()?.cause} ${formats.get()}")
            assertTrue("player error before the first frame: ${error.get()?.errorCodeName} ${error.get()?.cause} ${formats.get()}", error.get() == null)
            assertTrue("no first frame in 60 s (${formats.get()})", firstMs > 0)
            assertTrue("first frame took $firstMs ms (budget 15000)", firstMs <= 15_000)

            // Seek to the middle: playing again (READY + position moving past the target) within 5 s.
            val target = AtomicLong(0)
            ins.runOnMainSync { val d = player!!.duration; target.set(d / 2); player!!.seekTo(d / 2) }
            val t1 = SystemClock.elapsedRealtime()
            var resumedMs = -1L
            while (SystemClock.elapsedRealtime() - t1 < 30_000 && error.get() == null) {
                val pos = AtomicLong(0); val ready = AtomicReference(false)
                ins.runOnMainSync { pos.set(player!!.currentPosition); ready.set(player!!.playbackState == Player.STATE_READY && player!!.isPlaying) }
                if (ready.get() && pos.get() > target.get() + 200) { resumedMs = SystemClock.elapsedRealtime() - t1; break }
                SystemClock.sleep(100)
            }
            log("B31F_SEEK ms=$resumedMs target=${target.get()} error=${error.get()?.errorCodeName}")
            assertTrue("player error after the seek: ${error.get()?.errorCodeName}", error.get() == null)
            assertTrue("no playback after the seek in 30 s", resumedMs > 0)
            assertTrue("seek to playing took $resumedMs ms (budget 5000)", resumedMs <= 5_000)
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
