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
import com.lagradost.cloudstream3.MainAPI
import dev.asfakul.yumeiro.online.ExtensionManager
import dev.asfakul.yumeiro.online.ExtensionReview
import dev.asfakul.yumeiro.online.LinkEvent
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.OnlinePlayback
import dev.asfakul.yumeiro.online.OnlineRepository
import dev.asfakul.yumeiro.stream.StreamMediaFactory
import dev.asfakul.yumeiro.stream.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Mockup probe: which real anime extensions find and play "Solo Leveling" on this emulator's network.
 * Logs PROBE lines only (search hits, details, episodes, links, first frame). Tag YuProbe.
 */
@RunWith(AndroidJUnit4::class)
class MockupProbeDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx = ins.targetContext.applicationContext
    private fun log(m: String) = Log.w("YuProbe", m)

    @Test fun soloLeveling() = runBlocking<Unit> {
        ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
        val apis = ArrayList<MainAPI>()
        for (url in EXT) {
            val before = APIHolder.allProviders.toList().toSet()
            runCatching { ExtensionManager.installFromUrl(ctx, url, ExtensionReview.Mode.AUTO) }
                .onSuccess { apis += APIHolder.allProviders.toList().filter { it !in before }; log("PROBE install ok ${url.substringAfterLast('/')}") }
                .onFailure { log("PROBE install fail ${url.substringAfterLast('/')}: ${it.message?.take(120)}") }
        }
        var played = 0
        for (api in apis) {
            val hits = withTimeoutOrNull(40_000) { OnlineRepository.search(QUERY, listOf(api)).toList() }.orEmpty().flatMap { it.items }
            log("PROBE ${api.name} search=${hits.size} ${hits.take(4).map { it.name }}")
            val item = hits.firstOrNull { it.name.contains("Solo Leveling", true) } ?: continue
            val d = withTimeoutOrNull(40_000) { runCatching { OnlineRepository.load(api, item.url, force = true) }.getOrNull() } ?: run { log("PROBE ${api.name} details fail"); null } ?: continue
            val eps = d.episodes.values.flatten()
            log("PROBE ${api.name} details=${d.name} poster=${d.posterUrl != null} eps=${eps.size} keys=${d.episodes.keys}")
            val data = eps.firstOrNull()?.data ?: d.playData ?: continue
            val events = withTimeoutOrNull(60_000) { OnlineRepository.links(api, data).takeWhile { it !is LinkEvent.Done }.toList() }.orEmpty()
            val links = events.filterIsInstance<LinkEvent.Link>().map { it.link }
            log("PROBE ${api.name} links=${links.size} ${links.take(6).map { "${it.source}/${it.quality}/hls=${it.isHls}" }}")
            for (l in links.take(4)) {
                val ms = firstFrame(d.name, l)
                log("PROBE ${api.name} frame ${l.source} ms=$ms")
                if (ms > 0) { played++; log("PROBE_PLAYS ${api.name} item=${item.url} source=${l.source}"); break }
            }
        }
        log("PROBE_SUMMARY played=$played of ${apis.size}")
    }

    private fun firstFrame(title: String, link: OnlineLink): Long {
        val req = OnlinePlayback.request(title, link, emptyList())
        StreamRepository.register(req)
        var player: ExoPlayer? = null
        var reader: ImageReader? = null
        val drain = HandlerThread("probe-frames").apply { start() }
        val first = AtomicLong(0); val error = AtomicReference<PlaybackException?>(null)
        val t0 = SystemClock.elapsedRealtime()
        try {
            ins.runOnMainSync {
                val fmt = if (android.os.Build.VERSION.SDK_INT >= 29) ImageFormat.PRIVATE else ImageFormat.YUV_420_888
                reader = ImageReader.newInstance(1280, 720, fmt, 4).apply { setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, Handler(drain.looper)) }
                player = StreamMediaFactory.createPlayer(ctx, req.url).apply {
                    setVideoSurface(reader!!.surface); volume = 0f
                    addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { if (first.get() == 0L) first.set(SystemClock.elapsedRealtime()) }
                        override fun onPlayerError(e: PlaybackException) { error.set(e) }
                    })
                    setMediaItem(StreamMediaFactory.buildMediaItem(req.url, title = title)); playWhenReady = true; prepare()
                }
            }
            while (first.get() == 0L && error.get() == null && SystemClock.elapsedRealtime() - t0 < 30_000) SystemClock.sleep(100)
            return if (first.get() > 0) first.get() - t0 else -1
        } finally { ins.runOnMainSync { player?.release() }; reader?.close(); drain.quitSafely() }
    }

    companion object {
        const val QUERY = "Solo Leveling"
        private const val PH = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/"
        val EXT = listOf(PH + "HiAnime.cs3", PH + "AnimePahe.cs3", PH + "Anichi.cs3", PH + "Kickassanime.cs3",
            "https://raw.githubusercontent.com/JoeTinnySpace/Cloudstream_Kerala/builds/AnimeKai.cs3",
            "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/AniKoto.cs3",
            "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/AnimeSuge.cs3")
    }
}
