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
import com.lagradost.cloudstream3.APIHolder
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlinePlayback
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.stream.StreamMediaFactory
import dev.asfakul.yumeiro.stream.StreamRepository
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * R19 owner requirement 5: HindiSubAnime end to end on a device, through the app's own engine (ExtensionManager →
 * OnlineRepository home / load / links → the app's player with the extension's headers). Real network, no fakes.
 *
 * The copy installed is AnimeDekhoProvider v71 (pinned commit), the one the market's "Try another copy" (OtherCopy)
 * offers: v72/v73 parse only the site's new `a.dgt-card` cards and hindisubanime.co still serves the old ones.
 * The current copy is installed too, only to log what it returns (an extension fault, not asserted).
 *
 * Run: Device check workflow, test = dev.asfakul.yumeiro.HindiSubAnimeDeviceTest. Output tag: HSA.
 */
@RunWith(AndroidJUnit4::class)
class HindiSubAnimeDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx = ins.targetContext.applicationContext
    private fun log(m: String) = Log.w("HSA", m)

    @Test fun homeDetailsLinksAndFirstFrame() = runBlocking<Unit> {
        ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
        val ext = ExtensionManager.installFromUrl(ctx, V71, ExtensionReview.Mode.AUTO)
        log("installed ${ext.name} v${ext.version}; providers=${APIHolder.allProviders.map { it.name }}")
        val api = APIHolder.allProviders.firstOrNull { it.name.equals("HindiSubAnime", true) }
        assertTrue("HindiSubAnime provider not registered by ${ext.name}: ${APIHolder.allProviders.map { it.name }}", api != null)
        api!!

        val t0 = SystemClock.elapsedRealtime()
        val rows = OnlineRepository.home(api, force = true)
        val items = rows.flatMap { it.items }.distinctBy { it.url }
        log("HSA_HOME rows=${rows.size} items=${items.size} ms=${SystemClock.elapsedRealtime() - t0} first=${items.take(3).map { it.name }}")
        assertTrue("HindiSubAnime home returned no titles", items.isNotEmpty())

        var played = false
        val tried = mutableListOf<String>()
        for (item in items.take(4)) {
            val detail = runCatching { OnlineRepository.load(api, item.url, force = true) }
                .onFailure { log("HSA_LOAD_FAIL ${item.name}: $it") }.getOrNull() ?: continue
            val data = detail.playData ?: detail.episodes.values.flatten().firstOrNull()?.data
            log("HSA_DETAIL ${detail.name} kind=${detail.kind} episodes=${detail.episodes.mapValues { it.value.size }} data=${data != null}")
            if (data == null) continue
            val events = withTimeoutOrNull(90_000) { OnlineRepository.links(api, data).takeWhile { it !is LinkEvent.Done }.toList() }.orEmpty()
            val links = events.filterIsInstance<LinkEvent.Link>().map { it.link }
            log("HSA_LINKS ${detail.name}: ${links.size} links ${links.map { "${it.source}/${it.quality}/hls=${it.isHls}" }} skipped=${events.filterIsInstance<LinkEvent.Skipped>().map { it.reason }}")
            for (link in links.take(4)) {
                val ms = firstFrame(detail.name, link)
                tried += "${link.source}:$ms"
                if (ms > 0) { played = true; break }
            }
            if (played) break
        }
        assertTrue("no HindiSubAnime link rendered a frame (tried $tried)", played)

        // The current copy, for the record (expected: no titles, the extension's own fault).
        runCatching {
            ExtensionManager.installFromUrl(ctx, CURRENT, ExtensionReview.Mode.AUTO)
            val cur = APIHolder.allProviders.firstOrNull { it.name.equals("HindiSubAnime", true) }
            val n = cur?.let { runCatching { OnlineRepository.home(it, force = true).sumOf { r -> r.items.size } }.getOrElse { e -> log("HSA_CURRENT error $e"); -1 } }
            log("HSA_CURRENT items=$n")
        }.onFailure { log("HSA_CURRENT install failed: $it") }
    }

    /** First rendered frame through the app's player and header path, in ms; -1 on error / no frame in 45 s. */
    private fun firstFrame(title: String, link: OnlineLink): Long {
        val req = OnlinePlayback.request(title, link, emptyList())
        StreamRepository.register(req)
        var player: ExoPlayer? = null
        var reader: ImageReader? = null
        val drain = HandlerThread("hsa-frames").apply { start() }
        val first = AtomicLong(0)
        val error = AtomicReference<PlaybackException?>(null)
        val t0 = SystemClock.elapsedRealtime()
        try {
            ins.runOnMainSync {
                val fmt = if (android.os.Build.VERSION.SDK_INT >= 29) ImageFormat.PRIVATE else ImageFormat.YUV_420_888
                reader = ImageReader.newInstance(1280, 720, fmt, 4).apply {
                    setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, Handler(drain.looper))
                }
                player = StreamMediaFactory.createPlayer(ctx, req.url).apply {
                    setVideoSurface(reader!!.surface)
                    volume = 0f
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { if (first.get() == 0L) first.set(SystemClock.elapsedRealtime()) }
                        override fun onPlayerError(e: PlaybackException) { error.set(e) }
                    })
                    setMediaItem(StreamMediaFactory.buildMediaItem(req.url, title = title))
                    playWhenReady = true
                    prepare()
                }
            }
            while (first.get() == 0L && error.get() == null && SystemClock.elapsedRealtime() - t0 < 45_000) SystemClock.sleep(100)
            val ms = if (first.get() > 0) first.get() - t0 else -1
            log("HSA_FRAME ${link.source} ms=$ms error=${error.get()?.errorCodeName} ${error.get()?.cause}")
            return ms
        } finally {
            ins.runOnMainSync { player?.release() }
            reader?.close(); drain.quitSafely()
        }
    }

    private companion object {
        const val V71 = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/3b67c552158a9dc0f898069974a3a9838743bd64/AnimeDekhoProvider.cs3"
        const val CURRENT = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/AnimeDekhoProvider.cs3"
    }
}
