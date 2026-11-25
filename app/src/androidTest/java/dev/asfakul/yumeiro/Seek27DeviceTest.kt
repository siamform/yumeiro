/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.player.PlaybackDiagnostics
import dev.asfakul.yumeiro.service.VideoSessionBridge
import dev.asfakul.yumeiro.stream.IncomingMedia
import dev.asfakul.yumeiro.stream.StreamRequest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * SEEK-1 (owner item 27): a seek whose range request fails (HTTP 416 here, as CDNs answer when an edge has the file
 * only partly) keeps playing at the seek target instead of showing an error; a second seek that fails twice works
 * too (each seek has its own count), and the source is not reloaded from the start (a reopen reads only the header). The clip is 49 min long and
 * 582 KB, so a far seek always makes a new range request. Old code: 416 → "HTTP 416 … can't be loaded" card.
 * Log tag YuSeekTest.
 */
@RunWith(AndroidJUnit4::class)
class Seek27DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var server: MockWebServer
    private val ranges = Collections.synchronizedList(mutableListOf<String>())
    private val armed = AtomicBoolean(false)
    private val faultsLeft = AtomicInteger(0)
    private val faults = AtomicInteger(0)

    @Before fun setUp() {
        val clip = inst.context.assets.open("yumeiro_skip_2950.mp4").use { it.readBytes() }
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.headers["Range"]
                ranges += range ?: "(none)"
                val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
                val endAsked = range?.substringAfter('-', "")?.toLongOrNull()
                if (armed.get() && start > 100_000L && faultsLeft.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) {
                    faults.incrementAndGet()
                    return MockResponse.Builder().code(416).body("range not ready").build()
                }
                if (start >= clip.size) return MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${clip.size}").build()
                val end = (endAsked ?: (clip.size - 1L)).coerceAtMost(clip.size - 1L)
                val body = Buffer().write(clip, start.toInt(), (end - start + 1).toInt())
                return MockResponse.Builder().code(if (range != null) 206 else 200)
                    .addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "bytes")
                    .apply { if (range != null) addHeader("Content-Range", "bytes $start-$end/${clip.size}") }
                    // SEEK-1e: about 128 KB/s, so the 582 KB clip is never all loaded before the test seeks (run
                    // 45475835785: seek 1 was served from what was already loaded, no new request, nothing to fail).
                    .throttleBody(32_768L, 250L, TimeUnit.MILLISECONDS)
                    .body(body).build()
            }
        }
        server.start()
        PlaybackDiagnostics.reset("")
    }

    @After fun tearDown() { runCatching { server.close() } }

    private fun player(): Player = VideoSessionBridge.player ?: error("no player")

    private fun onMain(block: () -> Unit) = inst.runOnMainSync(block)

    private fun waitPlayingNear(targetMs: Long, timeoutMs: Long): Pair<Int, Long> {
        var state = 0; var pos = 0L
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            onMain { val p = player(); state = p.playbackState; pos = p.currentPosition }
            if (state == Player.STATE_READY && pos >= targetMs - 1_000L && pos < targetMs + 30_000L) return state to pos
            Thread.sleep(250)
        }
        return state to pos
    }

    @Test fun failedRangeAfterSeekKeepsPlayingAtTheTarget() {
        onMain { IncomingMedia.open(inst.targetContext, StreamRequest(url = server.url("/v.mp4").toString(), title = "Seek 27")) }
        rule.waitUntil(60_000) { PlaybackDiagnostics.state.value.firstFrame }

        // Seek 1: forward, the first range request answers 416.
        val firstSeekAt = ranges.size
        armed.set(true); faultsLeft.set(1)
        onMain { player().seekTo(1_500_000L) }
        val (s1, p1) = waitPlayingNear(1_500_000L, 25_000L)
        Log.w("YuSeekTest", "SEEK_RESULT seek1 state=$s1 pos=$p1 faults=${faults.get()} ranges=${ranges.takeLast(6)}")
        assertEquals("seek 1 met the failed request (ranges ${ranges.takeLast(8)})", 1, faults.get())
        assertEquals("seek 1 not playing (state $s1, pos $p1)", Player.STATE_READY, s1)
        assertTrue("seek 1 position $p1", p1 >= 1_499_000L)

        // Seek 2: backward, the next two range requests far into the file fail (one seek's full allowance).
        // The player may need only one of them (the reopen can start at a byte the fault rule doesn't match,
        // or be served from what is already loaded), so the seek must meet at least one failure and recover.
        val faultsAfter1 = faults.get()
        faultsLeft.set(2)
        onMain { player().seekTo(700_000L) }
        val (s2, p2) = waitPlayingNear(700_000L, 30_000L)
        Log.w("YuSeekTest", "SEEK_RESULT seek2 state=$s2 pos=$p2 faults=${faults.get()} ranges=${ranges.takeLast(6)}")
        assertEquals("seek 2 not playing (state $s2, pos $p2)", Player.STATE_READY, s2)
        assertTrue("seek 2 position $p2", p2 >= 699_000L && p2 < 730_000L)
        assertTrue("seek 2 met no failed request (faults ${faults.get()}, ranges ${ranges.takeLast(8)})", faults.get() - faultsAfter1 >= 1)
        // No reload from the start. A reopen is a fresh media source, which reads the MP4 header (moov, at byte 0,
        // so a request with no Range) and then jumps to the seek target. So: at most one header read per reopen
        // (one per failed request), each one followed by a range request, and the player is at the target (above).
        val after = synchronized(ranges) { ranges.drop(firstSeekAt) }
        val headerReads = after.indices.filter { after[it] == "(none)" || after[it].startsWith("bytes=0-") }
        assertTrue("more header reads (${headerReads.size}) than reopens (${faults.get()}): $after", headerReads.size <= faults.get())
        assertTrue("a header read not followed by a jump to the target: $after",
            headerReads.all { i -> after.drop(i + 1).firstOrNull()?.let { it.startsWith("bytes=") && !it.startsWith("bytes=0-") } == true })
        armed.set(false)
    }

    @Test fun rapidSeeksLandOnTheLastOne() {
        onMain { IncomingMedia.open(inst.targetContext, StreamRequest(url = server.url("/v.mp4").toString(), title = "Seek 27 rapid")) }
        rule.waitUntil(60_000) { PlaybackDiagnostics.state.value.firstFrame }
        armed.set(true); faultsLeft.set(1)
        onMain { player().seekTo(400_000L); player().seekTo(1_200_000L); player().seekTo(2_000_000L) }
        val (s, p) = waitPlayingNear(2_000_000L, 25_000L)
        Log.w("YuSeekTest", "SEEK_RESULT rapid state=$s pos=$p faults=${faults.get()}")
        assertEquals(Player.STATE_READY, s)
        assertTrue("rapid position $p", p >= 1_999_000L && p < 2_030_000L)
    }
}
