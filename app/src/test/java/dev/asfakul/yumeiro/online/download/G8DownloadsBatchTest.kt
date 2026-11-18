/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * G8 group batch (§13a): one regression test per finding of the G8 Downloads audit
 * (docs/groups/GROUP-8-DOWNLOADS.md, findings G8-1 … G8-6). Every test here failed on the code
 * before the fixes (fail-before run in the group doc). Only APIs that existed before are used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class G8DownloadsBatchTest {
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private val server = MockWebServer()
    private val seen: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val dir = File(System.getProperty("java.io.tmpdir"), "g8batch-${System.nanoTime()}").apply { mkdirs() }

    @After fun tearDown() { runCatching { server.close() }; dir.deleteRecursively() }

    private fun bytes(n: Int, seed: Int) = ByteArray(n) { ((it * 31 + seed) % 251).toByte() }

    /** A 200 / 206 answer for [body] honouring a `Range: bytes=a-b` header (a 1-byte probe included). */
    private fun ranged(req: RecordedRequest, body: ByteArray): MockResponse {
        val range = req.headers["Range"]?.removePrefix("bytes=") ?: return MockResponse.Builder().body(Buffer().write(body)).build()
        val a = range.substringBefore('-').toInt()
        val b = range.substringAfter('-').takeIf { it.isNotBlank() }?.toInt()?.coerceAtMost(body.size - 1) ?: (body.size - 1)
        if (a >= body.size) return MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${body.size}").build()
        return MockResponse.Builder().code(206).addHeader("Content-Range", "bytes $a-$b/${body.size}").addHeader("Accept-Ranges", "bytes")
            .body(Buffer().write(body.copyOfRange(a, b + 1))).build()
    }

    private fun serve(route: (RecordedRequest, String) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                seen += path + (request.headers["Range"]?.let { " $it" } ?: "")
                return route(request, path)
            }
        }
        server.start()
    }

    private fun text(s: String) = MockResponse.Builder().body(s).build()
    private val notFound get() = MockResponse.Builder().code(404).build()

    // ---- G8-1: #EXT-X-BYTERANGE (and the MAP BYTERANGE attribute) were ignored ----

    @Test fun g8_1_hlsByteRangesSaveEachRangeOnce() = runBlocking {
        val file = bytes(3500, 7)
        val media = """#EXTM3U
#EXT-X-VERSION:4
#EXT-X-TARGETDURATION:4
#EXT-X-MAP:URI="all.mp4",BYTERANGE="200@0"
#EXTINF:4.0,
#EXT-X-BYTERANGE:1000@200
all.mp4
#EXTINF:4.0,
#EXT-X-BYTERANGE:1000
all.mp4
#EXTINF:4.0,
#EXT-X-BYTERANGE:800@2200
all.mp4
#EXTINF:4.0,
#EXT-X-BYTERANGE:500
all.mp4
#EXT-X-ENDLIST
"""
        serve { req, path -> when (path) { "/index.m3u8" -> text(media); "/all.mp4" -> ranged(req, file); else -> notFound } }
        val out = File(dir, "br.mp4")
        HlsDownloader(client, emptyMap()).download(server.url("/index.m3u8").toString(), out) { _, _ -> }
        // init 0..199, then 200..1199, 1200..2199, 2200..2999, 3000..3499: the whole file once.
        assertEquals("G8-1: saved ${out.length()} bytes for a ${file.size}-byte file; requests $seen", file.size.toLong(), out.length())
        assertArrayEquals("G8-1: content", file, out.readBytes())
    }

    // ---- G8-2: an HLS variant with a separate audio rendition was saved without its audio ----

    @Test fun g8_2_hlsSeparateAudioRenditionIsFetched() = runBlocking {
        val master = """#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="audio/index.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2",AUDIO="aud"
video/index.m3u8
"""
        fun pl(prefix: String) = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4.0,\n${prefix}0.ts\n#EXTINF:4.0,\n${prefix}1.ts\n#EXT-X-ENDLIST\n"
        serve { _, path ->
            when (path) {
                "/master.m3u8" -> text(master)
                "/video/index.m3u8" -> text(pl("v"))
                "/audio/index.m3u8" -> text(pl("a"))
                "/video/v0.ts", "/video/v1.ts" -> MockResponse.Builder().body(Buffer().write(bytes(4000, 1))).build()
                "/audio/a0.ts", "/audio/a1.ts" -> MockResponse.Builder().body(Buffer().write(bytes(1000, 2))).build()
                else -> notFound
            }
        }
        // The join itself (MediaMuxer) needs a device: DownloadHlsMediaDeviceTest. Here: is the audio fetched at all?
        runCatching { HlsDownloader(client, emptyMap()).download(server.url("/master.m3u8").toString(), File(dir, "aud.ts")) { _, _ -> } }
        val paths = seen.map { it.substringBefore(' ') }
        assertTrue("G8-2: the audio playlist was never read: $paths", "/audio/index.m3u8" in paths)
        assertTrue("G8-2: no audio segment was fetched: $paths", "/audio/a0.ts" in paths && "/audio/a1.ts" in paths)
    }

    // ---- G8-3: a finished track was downloaded again when the job ran again ----

    @Test fun g8_3_segmentedFinishedFileIsNotFetchedAgain() = runBlocking {
        val file = bytes(3 * 1024 * 1024, 3)
        serve { req, path -> if (path == "/f.bin") ranged(req, file) else notFound }
        val out = File(dir, "seg.bin")
        val s = Segmented(client, 2, segmentFrom = 1024L * 1024, chunk = 1024L * 1024)
        s.download(server.url("/f.bin").toString(), emptyMap(), out) { _, _ -> }
        assertArrayEquals(file, out.readBytes())
        val first = seen.size
        // A split video/audio job whose other track failed runs again: this track is complete on disk.
        s.download(server.url("/f.bin").toString(), emptyMap(), out) { _, _ -> }
        val again = seen.drop(first)
        assertEquals("G8-3: a complete track was fetched again (only the size probe expected): $again", 1, again.size)
        assertArrayEquals(file, out.readBytes())
    }

    @Test fun g8_3_hlsFinishedTrackIsNotFetchedAgain() = runBlocking {
        val media = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n" + (0 until 6).joinToString("") { "#EXTINF:4.0,\ns$it.ts\n" } + "#EXT-X-ENDLIST\n"
        serve { _, path ->
            when {
                path == "/v.m3u8" -> text(media)
                path.startsWith("/s") -> MockResponse.Builder().body(Buffer().write(bytes(2000, path[2].code))).build()
                else -> notFound
            }
        }
        val out = File(dir, "v.ts")
        HlsDownloader(client, emptyMap()).download(server.url("/v.m3u8").toString(), out) { _, _ -> }
        val size = out.length()
        val first = seen.size
        HlsDownloader(client, emptyMap()).download(server.url("/v.m3u8").toString(), out) { _, _ -> }
        val again = seen.drop(first).filter { it.startsWith("/s") }
        assertEquals("G8-3: segments of a finished HLS track were fetched again: $again", 0, again.size)
        assertEquals(size, out.length())
    }

    private fun mpd(audio: Boolean) = """<?xml version="1.0"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT20S" minBufferTime="PT2S" profiles="urn:mpeg:dash:profile:isoff-live:2011">
 <Period id="0" start="PT0S">
  <AdaptationSet contentType="video" mimeType="video/mp4">
   <SegmentTemplate timescale="1000" duration="4000" startNumber="1" initialization="v/init.mp4" media="v/${'$'}Number${'$'}.m4s"/>
   <Representation id="360" codecs="avc1.4d401e" width="640" height="360" bandwidth="800000"/>
  </AdaptationSet>
  ${if (audio) """<AdaptationSet contentType="audio" mimeType="audio/mp4" lang="en">
   <SegmentTemplate timescale="1000" duration="4000" startNumber="1" initialization="a/init.mp4" media="a/${'$'}Number${'$'}.m4s"/>
   <Representation id="a128" codecs="mp4a.40.2" audioSamplingRate="44100" bandwidth="128000"/>
  </AdaptationSet>""" else ""}
 </Period>
</MPD>"""

    private fun dashSegment(path: String): ByteArray = bytes(1500, path.hashCode() and 0x7f)

    @Test fun g8_3_dashFinishedVideoTrackIsNotFetchedAgain() = runBlocking {
        val audioUp = java.util.concurrent.atomic.AtomicBoolean(false)
        serve { _, path ->
            when {
                path == "/m.mpd" -> text(mpd(audio = true))
                path.startsWith("/v/") -> MockResponse.Builder().body(Buffer().write(dashSegment(path))).build()
                path.startsWith("/a/") -> if (audioUp.get()) MockResponse.Builder().body(Buffer().write(dashSegment(path))).build() else MockResponse.Builder().code(500).build()
                else -> notFound
            }
        }
        val out = File(dir, "d.mp4.part")
        // Run 1: the audio track fails after its retries; the video track finished.
        runCatching { DashGrab(client, emptyMap()).download(server.url("/m.mpd").toString(), out, 0, false) { _, _ -> } }
        assertTrue("video track fetched in run 1: $seen", seen.count { it.startsWith("/v/") } == 6)
        val first = seen.size
        audioUp.set(true)
        // Run 2 (WorkManager retry). The join needs a device (MediaMuxer); the requests are the point here.
        runCatching { DashGrab(client, emptyMap()).download(server.url("/m.mpd").toString(), out, 0, false) { _, _ -> } }
        val again = seen.drop(first).filter { it.startsWith("/v/") }
        assertEquals("G8-3: the finished DASH video track was fetched again: $again", 0, again.size)
    }

    // ---- G8-4: the DASH resume point lived in two files written one after the other ----

    @Test fun g8_4_dashResumeAfterAKillBetweenTheTwoSideFilesRepeatsNoSegment() = runBlocking {
        serve { _, path ->
            when {
                path == "/m.mpd" -> text(mpd(audio = false))
                path.startsWith("/v/") -> MockResponse.Builder().body(Buffer().write(dashSegment(path))).build()
                else -> notFound
            }
        }
        val names = listOf("/v/init.mp4") + (1..5).map { "/v/$it.m4s" }
        val whole = names.map(::dashSegment).reduce { a, b -> a + b }
        val out = File(dir, "k.mp4.part")
        val track = File(dir, out.name + ".v")
        // Killed after part 3 (index 2) was appended and the length side file written, before the index file.
        val three = names.take(3).map(::dashSegment).reduce { a, b -> a + b }
        track.writeBytes(three)
        File(dir, track.name + ".len").writeText(three.size.toString())
        File(dir, track.name + ".idx").writeText("2")
        DashGrab(client, emptyMap()).download(server.url("/m.mpd").toString(), out, 0, false) { _, _ -> }
        assertEquals("G8-4: saved ${out.length()} bytes, the stream is ${whole.size}: a segment was appended twice", whole.size.toLong(), out.length())
        assertArrayEquals("G8-4: content", whole, out.readBytes())
    }

    // ---- G8-5: a pause during an HLS segment retry waited out the whole back-off ----

    @Test fun g8_5_hlsRetryBackOffStopsWhenTheDownloadIsCancelled() = runBlocking {
        val media = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4.0,\ns0.ts\n#EXTINF:4.0,\ns1.ts\n#EXT-X-ENDLIST\n"
        serve { _, path -> when (path) { "/v.m3u8" -> text(media); "/s0.ts" -> MockResponse.Builder().code(503).build(); else -> notFound } }
        val job = CoroutineScope(Dispatchers.IO).launch {
            runCatching { HlsDownloader(client, emptyMap()).download(server.url("/v.m3u8").toString(), File(dir, "c.ts")) { _, _ -> } }
        }
        delay(300) // inside the first back-off
        val t0 = System.nanoTime()
        job.cancelAndJoin()
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("G8-5: the cancelled download kept going for $ms ms (requests $seen)", ms < 1500)
    }
}
