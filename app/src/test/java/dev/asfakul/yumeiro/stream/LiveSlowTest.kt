/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * LIVE-SLOW-1 (R19). Fail-before: a stale playlist with a saved copy waited for the whole new download (here
 * 6 s; on a slow link the 20 s timeout) before showing any channel; every visit retried a failing source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveSlowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        File(context.filesDir, "streams").deleteRecursively()
        PlaylistStore.resetForTests()
        server = MockWebServer(); server.start()
    }
    @After fun tearDown() { server.close() }

    private fun m3u(vararg t: String) = "#EXTM3U\n" + t.joinToString("") { "#EXTINF:-1,$it\nhttps://cdn.example/$it.m3u8\n" }

    @Test fun staleSavedCopyShowsAtOnceAndUpdatesBehindIt() = runBlocking {
        server.enqueue(MockResponse.Builder().body(m3u("A", "B")).build())
        val p = PlaylistStore.addRemote(context, "Mine", server.url("/l.m3u").toString())
        PlaylistStore.ageForTests(context, p.id, PlaylistStore.STALE_MS + 60_000)
        server.enqueue(MockResponse.Builder().body(m3u("A", "B", "C")).headersDelay(6, TimeUnit.SECONDS).build())
        val t0 = System.nanoTime()
        val first = PlaylistStore.channels(context, p.id)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(2, first.size)
        assertTrue("saved copy took $ms ms", ms < 3_000)
        // The update lands in the background and the next read has it.
        val deadline = System.currentTimeMillis() + 20_000
        while (PlaylistStore.get(p.id)!!.channelCount != 3 && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals(3, PlaylistStore.channels(context, p.id).size)
    }

    @Test fun failedAutomaticUpdateBacksOffAndKeepsTheSavedCopy() = runBlocking {
        server.enqueue(MockResponse.Builder().body(m3u("A")).build())
        val p = PlaylistStore.addRemote(context, "Mine", server.url("/l.m3u").toString())
        PlaylistStore.ageForTests(context, p.id, PlaylistStore.STALE_MS + 60_000)
        server.enqueue(MockResponse.Builder().code(503).build())
        assertEquals(1, PlaylistStore.channels(context, p.id).size)
        val deadline = System.currentTimeMillis() + 10_000
        while (!PlaylistStore.inBackoff(p.id) && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(PlaylistStore.inBackoff(p.id))
        val before = server.requestCount
        assertEquals(1, PlaylistStore.channels(context, p.id).size)
        Thread.sleep(300)
        assertEquals("no retry while backing off", before, server.requestCount)
    }

    @Test fun backoffGrowsToThirtyMinutes() {
        assertEquals(0L, PlaylistStore.backoffMs(0))
        assertEquals(60_000L, PlaylistStore.backoffMs(1))
        assertEquals(4 * 60_000L, PlaylistStore.backoffMs(3))
        assertEquals(30 * 60_000L, PlaylistStore.backoffMs(9))
    }

    @Test fun smallPlaylistsLoadFirst() {
        val big = StreamPlaylist(DefaultPlaylists.YUMEIRO_LIVE, "Yumeiro Live", url = "https://x/a")
        val bdix = StreamPlaylist("mrgify_bdix", "BDIX", url = "https://x/b")
        val known = StreamPlaylist("m", "Mine", url = "https://x/c", channelCount = 40)
        assertEquals(listOf("mrgify_bdix", "m", DefaultPlaylists.YUMEIRO_LIVE), PlaylistStore.loadOrder(listOf(big, known, bdix)).map { it.id })
        assertFalse(PlaylistStore.loadOrder(listOf(big.copy(channelCount = 900), bdix)).first().id == DefaultPlaylists.YUMEIRO_LIVE)
    }
}
