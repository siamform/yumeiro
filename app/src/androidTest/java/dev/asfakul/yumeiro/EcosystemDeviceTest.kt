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
 * R19 owner requirement 6 (multi-provider engine): the most-used CloudStream extensions of the market index, installed
 * together and run through the app's own engine at the same time, every stage a user needs: home -> details ->
 * episodes -> links -> first rendered frame. Real network, real extensions, no fakes.
 *
 * Output: one ECO line per provider and stage, then an ECO_TABLE with the stage each provider reached and why it
 * stopped, so engine faults (ours) and extension / site faults (theirs) can be told apart.
 *
 * Asserts only what the engine owns: every provider ends with an outcome inside its budget (no hang), one slow or
 * broken provider does not hold the others (all ran at once: total time stays near the slowest single budget), and
 * at least one provider plays. Which sites are up is not asserted (it changes by the hour and by country).
 *
 * Run: Device check workflow, test = dev.asfakul.yumeiro.EcosystemDeviceTest. Output tag: ECO.
 */
@RunWith(AndroidJUnit4::class)
class EcosystemDeviceTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx = ins.targetContext.applicationContext
    private fun log(m: String) = Log.w("ECO", m)

    private class Outcome(val ext: String) {
        @Volatile var reached = "install"
        @Volatile var why = ""
        @Volatile var ms = 0L
        val stages = StringBuilder()
        @Volatile var api: MainAPI? = null
        @Volatile var title = ""
        val links = mutableListOf<OnlineLink>()
    }

    @Test fun popularExtensionsRunTogether() = runBlocking<Unit> {
        ExtensionManager.start(ctx); ExtensionManager.ensureLoaded(ctx)
        val results = EXTENSIONS.map { Outcome(it.substringAfterLast('/').removeSuffix(".cs3")) }

        // Install one by one (the engine registers plugins in order), then run every provider at once.
        for ((i, url) in EXTENSIONS.withIndex()) {
            val o = results[i]
            val before = APIHolder.allProviders.toList().toSet()
            val t = SystemClock.elapsedRealtime()
            runCatching { ExtensionManager.installFromUrl(ctx, url, ExtensionReview.Mode.AUTO) }
                .onSuccess {
                    val added = APIHolder.allProviders.toList().filter { it !in before }
                    o.api = added.firstOrNull { it.name.equals(o.ext.removeSuffix("Provider"), true) } ?: added.firstOrNull()
                    o.stages.append("install ${SystemClock.elapsedRealtime() - t} ms; ")
                    if (o.api == null) { o.why = "installed but registered no provider" }
                    log("ECO_INSTALL ${o.ext} v${it.version} providers=${added.map { a -> a.name }} ms=${SystemClock.elapsedRealtime() - t}")
                }
                .onFailure { o.why = "install: ${it.message?.take(160)}"; log("ECO_INSTALL_FAIL ${o.ext}: $it") }
        }

        val wall0 = SystemClock.elapsedRealtime()
        supervisorScope {
            results.filter { it.api != null }.map { o ->
                async(Dispatchers.IO) {
                    val t0 = SystemClock.elapsedRealtime()
                    val done = withTimeoutOrNull(PROVIDER_BUDGET_MS) { discover(o) }
                    o.ms = SystemClock.elapsedRealtime() - t0
                    if (done == null) o.why = "budget ${PROVIDER_BUDGET_MS / 1000} s ran out at ${o.reached}"
                }
            }.awaitAll()
        }
        val wall = SystemClock.elapsedRealtime() - wall0
        log("ECO_WALL discovery of ${results.count { it.api != null }} providers at once: $wall ms (budget each $PROVIDER_BUDGET_MS)")

        // Playback one at a time (frames on a small emulator are the scarce part, not the network).
        for (o in results.filter { it.links.isNotEmpty() }) {
            for (link in o.links.take(3)) {
                val ms = firstFrame(o.title, link)
                o.stages.append("frame ${link.source}=${ms}; ")
                if (ms > 0) { o.reached = "played"; o.why = "first frame $ms ms"; break }
            }
            if (o.reached != "played") o.why = "links found, none rendered a frame (see ECO_FRAME)"
        }

        log("ECO_TABLE provider | reached | why | discovery ms | stages")
        for (o in results) log("ECO_TABLE ${o.ext} | ${o.reached} | ${o.why} | ${o.ms} | ${o.stages}")
        val played = results.count { it.reached == "played" }
        log("ECO_SUMMARY played=$played/${results.size} registered=${results.count { it.api != null }}")

        assertTrue("a provider hung past its budget: ${results.filter { it.ms > PROVIDER_BUDGET_MS + 5_000 }.map { it.ext }}",
            results.none { it.ms > PROVIDER_BUDGET_MS + 5_000 })
        assertTrue("providers waited on each other: $wall ms for all at once, budget each $PROVIDER_BUDGET_MS", wall < PROVIDER_BUDGET_MS + 30_000)
        assertTrue("no popular provider played (see ECO_TABLE)", played >= 1)
    }

    /** home -> details -> episodes -> links for one provider; records the stage reached and why it stopped. */
    private suspend fun discover(o: Outcome) {
        val api = o.api!!
        o.reached = "home"
        var t = SystemClock.elapsedRealtime()
        val rows = runCatching { OnlineRepository.home(api, force = true) }
            .onFailure { o.why = "home: ${it.message?.take(160) ?: it.javaClass.simpleName}" }.getOrNull() ?: return
        val items = rows.flatMap { it.items }.distinctBy { it.url }
        o.stages.append("home ${rows.size} rows/${items.size} items ${SystemClock.elapsedRealtime() - t} ms; ")
        log("ECO_HOME ${o.ext} rows=${rows.size} items=${items.size} ms=${SystemClock.elapsedRealtime() - t}")
        if (items.isEmpty()) { o.why = "home: no titles"; return }
        o.reached = "details"
        for (item in items.take(3)) {
            t = SystemClock.elapsedRealtime()
            val detail = runCatching { OnlineRepository.load(api, item.url, force = true) }
                .onFailure { o.why = "details: ${it.message?.take(160) ?: it.javaClass.simpleName}"; log("ECO_LOAD_FAIL ${o.ext} ${item.name}: $it") }
                .getOrNull() ?: continue
            val eps = detail.episodes.values.sumOf { it.size }
            val data = detail.playData ?: detail.episodes.values.flatten().firstOrNull()?.data
            o.stages.append("details ${detail.kind} eps=$eps ${SystemClock.elapsedRealtime() - t} ms; ")
            log("ECO_DETAIL ${o.ext} ${detail.name} kind=${detail.kind} episodes=$eps data=${data != null}")
            if (data == null) { o.why = "details: nothing to play (no episodes / play data)"; continue }
            o.reached = "links"
            t = SystemClock.elapsedRealtime()
            val events = withTimeoutOrNull(60_000) { OnlineRepository.links(api, data).takeWhile { it !is LinkEvent.Done }.toList() }.orEmpty()
            val links = events.filterIsInstance<LinkEvent.Link>().map { it.link }
            o.stages.append("links ${links.size} ${SystemClock.elapsedRealtime() - t} ms; ")
            log("ECO_LINKS ${o.ext} ${detail.name}: ${links.size} ${links.take(6).map { "${it.source}/${it.quality}/hls=${it.isHls}" }} skipped=${events.filterIsInstance<LinkEvent.Skipped>().map { it.reason }.take(4)}")
            if (links.isEmpty()) { o.why = "links: none came back"; continue }
            synchronized(o.links) { o.links += links }
            o.title = detail.name; o.reached = "frame"; o.why = ""
            return
        }
    }

    /** First rendered frame through the app's player and header path, in ms; -1 on error / no frame in 30 s. */
    private fun firstFrame(title: String, link: OnlineLink): Long {
        val req = OnlinePlayback.request(title, link, emptyList())
        StreamRepository.register(req)
        var player: ExoPlayer? = null
        var reader: ImageReader? = null
        val drain = HandlerThread("eco-frames").apply { start() }
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
            while (first.get() == 0L && error.get() == null && SystemClock.elapsedRealtime() - t0 < 30_000) SystemClock.sleep(100)
            val ms = if (first.get() > 0) first.get() - t0 else -1
            log("ECO_FRAME $title ${link.source} ms=$ms error=${error.get()?.errorCodeName} ${error.get()?.cause}")
            return ms
        } finally {
            ins.runOnMainSync { player?.release() }
            reader?.close(); drain.quitSafely()
        }
    }

    private companion object {
        const val PROVIDER_BUDGET_MS = 150_000L
        private const val CNC = "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/builds/"
        private const val PH = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/"
        /** Top of the market index (origin/extensions index.json, 2026-11-23, sorted by use), no Live / adult. */
        val EXTENSIONS = listOf(
            CNC + "MovieBoxProvider.cs3", PH + "AnimePahe.cs3", CNC + "Watch32.cs3", PH + "Cinefreak.cs3",
            CNC + "HDrezkaProvider.cs3", CNC + "AniKoto.cs3", CNC + "CastleTvProvider.cs3", CNC + "DoFlixProvider.cs3",
            PH + "HDhub4u.cs3", PH + "MultiMoviesProvider.cs3",
        )
    }
}
