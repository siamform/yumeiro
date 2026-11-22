/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.network.NetworkClient
import dev.asfakul.yumeiro.stream.torrent.TorrentEngine
import dev.asfakul.yumeiro.stream.torrent.TorrentStreams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B-31 (ADR-053): the built-in engine downloads, checks and loads libtorrent4j, then the owner's test hash
 * streams with no TorrServer. Budgets from ADR-053: first bytes within 15 s of opening, a seek to the middle
 * within 5 s. Needs the internet (peers for the test hash).
 */
@RunWith(AndroidJUnit4::class)
class BuiltinTorrentDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val testHash = "BF2063A5E9A69F14716288FCA7C2D6078BDA7369"

    @Test fun engineDownloadsChecksAndLoads(): Unit = runBlocking {
        assertTrue("no libtorrent4j pin for " + android.os.Build.SUPPORTED_ABIS.toList(), TorrentEngine.available())
        val t0 = SystemClock.elapsedRealtime()
        assertTrue("engine: " + TorrentEngine.state.value, withTimeout(240_000) { TorrentEngine.ensure(ctx, NetworkClient.get(ctx)) })
        Log.i("B31", "engine ready in ${SystemClock.elapsedRealtime() - t0} ms")
        assertTrue(TorrentEngine.isLoaded())
        assertEquals(TorrentEngine.State.Ready, TorrentEngine.state.value)
        assertTrue(TorrentEngine.ensure(ctx, NetworkClient.get(ctx)))
    }

    @Test fun testHashStreamsWithoutTorrServer(): Unit = runBlocking {
        FeatureFlags.set(ctx, FeatureFlags.Flag.BUILTIN_TORRENT, true)
        try {
            // The privacy notice gates the engine: off until accepted, on after.
            dev.asfakul.yumeiro.stream.TorrentSettings.init(ctx)
            dev.asfakul.yumeiro.stream.TorrentSettings.setEngineAccepted(ctx, false)
            assertTrue("engine played before the privacy notice was accepted", !TorrentStreams.enabled(ctx))
            dev.asfakul.yumeiro.stream.TorrentSettings.setEngineAccepted(ctx, true)
            assertTrue(TorrentStreams.enabled(ctx))
            // B31-SEEK3: every waiting read logs block progress, peers and rate once a second (tag B31).
            dev.asfakul.yumeiro.stream.torrent.TorrentTrace.sink = { Log.i("B31", it) }
            val http = NetworkClient.get(ctx)
            withTimeout(240_000) { TorrentEngine.ensure(ctx, http) }
            val t0 = SystemClock.elapsedRealtime()
            val opened = withTimeout(120_000) { TorrentStreams.open(ctx, "magnet:?xt=urn:btih:$testHash", null, http) }
            Log.i("B31", "opened ${opened.name} (${opened.size} B) in ${SystemClock.elapsedRealtime() - t0} ms")
            assertTrue(opened.url.startsWith("http://127.0.0.1"))
            assertTrue(opened.size > 1_000_000)

            val first = rangeRead(http, opened.url, 0, 65_535)
            val firstMs = SystemClock.elapsedRealtime() - t0
            Log.i("B31", "first 64 KB in $firstMs ms")
            assertEquals(65_536, first)
            assertTrue("first bytes took $firstMs ms (budget 15000)", firstMs <= 15_000)

            val mid = opened.size / 2
            val t1 = SystemClock.elapsedRealtime()
            val got = rangeRead(http, opened.url, mid, mid + 65_535)
            val seekMs = SystemClock.elapsedRealtime() - t1
            Log.i("B31", "seek to middle in $seekMs ms")
            assertEquals(65_536, got)
            assertTrue("seek took $seekMs ms (budget 5000)", seekMs <= 5_000)
        } finally {
            dev.asfakul.yumeiro.stream.torrent.TorrentTrace.sink = null
            dev.asfakul.yumeiro.stream.TorrentSettings.setEngineAccepted(ctx, false)
            FeatureFlags.set(ctx, FeatureFlags.Flag.BUILTIN_TORRENT, false)
        }
    }

    private suspend fun rangeRead(http: okhttp3.OkHttpClient, url: String, from: Long, to: Long): Int = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("Range", "bytes=$from-$to").build()).execute().use { r ->
            assertEquals(206, r.code)
            r.body.bytes().size
        }
    }
}
