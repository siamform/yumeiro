/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import dev.asfakul.yumeiro.stream.TorrentSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * G8 D3 (torrent / magnet downloads) on a device. A magnet link is saved through the user's
 * TorrServer: the worker asks `<server>/stream/<title>?link=<magnet>&index=1&play` and saves the
 * answer like any file. A local server plays TorrServer here (same paths and Range answers).
 * a) With a server set, the magnet is saved byte for byte and the request carries the magnet.
 * b) With no server set, the download fails with the message that says where to add one
 *    (instead of a crash or a silent failure).
 */
@RunWith(AndroidJUnit4::class)
class TorrentDownloadDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var server: MockWebServer
    private val blob = ByteArray(400_000) { (it % 239 + 5).toByte() }
    private val asked = Collections.synchronizedList(ArrayList<String>())
    private var savedServer = ""
    private val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Yu+Test"

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.url.encodedPath.startsWith("/stream/")) return MockResponse.Builder().code(404).build()
                asked += request.url.toString()
                val range = request.headers["Range"]?.removePrefix("bytes=")
                val from = range?.substringBefore('-')?.toIntOrNull()
                val to = range?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(blob.size - 1) ?: (blob.size - 1)
                return if (from != null && from < blob.size) MockResponse.Builder().code(206)
                    .addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "bytes")
                    .addHeader("Content-Range", "bytes $from-$to/${blob.size}")
                    .body(Buffer().write(blob.copyOfRange(from, to + 1))).build()
                else MockResponse.Builder().addHeader("Content-Type", "video/mp4").addHeader("Accept-Ranges", "bytes")
                    .body(Buffer().write(blob)).build()
            }
        }
        server.start()
        TorrentSettings.init(ctx)
        savedServer = TorrentSettings.server.value
        OnlineDownloads.ensureLoaded(ctx)
    }

    @After fun tearDown() {
        TorrentSettings.setServer(ctx, savedServer)
        runCatching { server.close() }
    }

    private fun link(url: String) = OnlineLink("Torrent", "Torrent", url, null, -1, false, false, emptyMap())

    private suspend fun waitFor(id: String, ms: Long, what: String, ok: (OnlineDownloads.Item) -> Boolean): OnlineDownloads.Item {
        val until = System.currentTimeMillis() + ms
        while (true) {
            val it = OnlineDownloads.get(id)
            if (it != null && ok(it)) return it
            if (System.currentTimeMillis() > until) throw AssertionError("timed out waiting for $what: status=${it?.status} error=${it?.error}")
            delay(200)
        }
    }

    @Test fun a_magnetIsSavedThroughTorrServer() = runBlocking<Unit> {
        assertTrue(TorrentSettings.setServer(ctx, "http://${server.hostName}:${server.port}"))
        val title = "YuTorrent ${System.currentTimeMillis()}"
        assertEquals(null, OnlineDownloads.enqueue(ctx, title, "yu-test", "t-torrent-$title", "E1", null, emptyMap(), link(magnet)))
        val id = OnlineDownloads.items.value.first { it.title == title }.id
        val done = waitFor(id, 90_000, "a done") { it.status == Status.DONE || it.status == Status.FAILED }
        val file = OnlineDownloads.fileOf(ctx, done)
        val bytes = if (file.exists()) file.readBytes() else ByteArray(0)
        val first = asked.firstOrNull().orEmpty()
        val line = "TORRENT_CASE a status=${done.status} size=${bytes.size}/${blob.size} requests=${asked.size} error=${done.error} first=${first.substringBefore("&index")}"
        Log.i(TAG, line)
        OnlineDownloads.delete(ctx, id)
        assertEquals(line, Status.DONE, done.status)
        assertArrayEquals(line, blob, bytes)
        val u = first.toHttpUrl()
        assertEquals(magnet, u.queryParameter("link"))
        assertEquals("1", u.queryParameter("index"))
        assertTrue(line, u.queryParameterNames.contains("play"))
        Log.i(TAG, "TORRENT_RESULT PASS a")
    }

    @Test fun b_noServerSaysWhereToAddOne() = runBlocking<Unit> {
        assertTrue(TorrentSettings.setServer(ctx, ""))
        val title = "YuTorrentNoServer ${System.currentTimeMillis()}"
        assertEquals(null, OnlineDownloads.enqueue(ctx, title, "yu-test", "t-torrent-$title", "E1", null, emptyMap(), link(magnet)))
        val id = OnlineDownloads.items.value.first { it.title == title }.id
        val failed = waitFor(id, 60_000, "b failed") { it.status == Status.FAILED || it.status == Status.DONE }
        Log.i(TAG, "TORRENT_CASE b status=${failed.status} error=${failed.error} requests=${asked.size}")
        OnlineDownloads.delete(ctx, id)
        assertEquals(Status.FAILED, failed.status)
        assertEquals("This is a torrent. Add your TorrServer address in Settings › Network to download it", failed.error)
        assertEquals(0, asked.size)
        Log.i(TAG, "TORRENT_RESULT PASS b")
    }

    private companion object { const val TAG = "YuTorrentTest" }
}
