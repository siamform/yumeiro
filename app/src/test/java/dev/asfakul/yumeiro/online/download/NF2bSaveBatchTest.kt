/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import android.media.MediaFormat
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.inspector.MediaExtractorCompat
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.stream.DrmScheme
import dev.asfakul.yumeiro.stream.StreamAudio
import dev.asfakul.yumeiro.stream.StreamDownload
import dev.asfakul.yumeiro.stream.StreamFormat
import dev.asfakul.yumeiro.stream.StreamRequest
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * NF-2b save bugs (docs/proposals/FEATURE-NF-2b-universal-stream-download.md, commits 1-4): one test per
 * finding. Each failed on the code before the fixes (fail-before run id in the proposal).
 *  - NF2b-1 a DASH link or website video was refused, and a link with a separate audio track was saved silent;
 *  - NF2b-2 the downloader kept the ad breaks the player removes;
 *  - NF2b-3 an MPEG-TS HLS download stayed .ts (no TS to MP4 step);
 *  - NF2b-4 HLS was named .ts up front and the worker had no remux / fallback step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(markerClass = [UnstableApi::class])
class NF2bSaveBatchTest {
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private val server = MockWebServer()
    private val seen: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val dir = File(System.getProperty("java.io.tmpdir"), "nf2b-${System.nanoTime()}").apply { mkdirs() }

    @After fun tearDown() { runCatching { server.close() }; dir.deleteRecursively() }

    private fun bytes(n: Int, seed: Int) = ByteArray(n) { ((it * 31 + seed) % 251).toByte() }

    private fun asset(name: String): File {
        val f = File(dir, name)
        javaClass.classLoader!!.getResourceAsStream("nf2b/$name")!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        return f
    }

    private fun tracks(f: File): List<String> {
        val ex = MediaExtractorCompat(DefaultExtractorsFactory(), FileDataSource.Factory())
        try {
            ex.setDataSource(f.absolutePath)
            return (0 until ex.trackCount).map { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
        } finally { ex.release() }
    }

    private fun isMp4(f: File): Boolean = f.length() > 12 && f.inputStream().use { s -> val b = ByteArray(8); s.read(b); String(b, 4, 4, Charsets.US_ASCII) == "ftyp" }

    // ---- NF2b-1: StreamDownload refused DASH and dropped separate audio ----

    @Test fun nf2b_1_dashLinkIsSavedAsDash() {
        val req = StreamRequest(url = "https://cdn.example.com/v/manifest.mpd", title = "Clip")
        assertTrue("NF2b-1: a DASH link was refused", StreamDownload.canSave(req))
        assertTrue("NF2b-1: the DASH link was queued as a plain file", StreamDownload.linkFor(req).isDash)
        assertTrue(StreamDownload.linkFor(StreamRequest(url = "https://x.test/a", format = StreamFormat.DASH)).isDash)
    }

    @Test fun nf2b_1_separateAudioTrackIsKept() {
        val req = StreamRequest(
            url = "https://cdn.example.com/v/video.mp4", title = "Clip",
            audioTracks = listOf(StreamAudio("https://cdn.example.com/v/audio.m4a", headers = mapOf("Referer" to "https://example.com/"))),
        )
        val link = StreamDownload.linkFor(req)
        assertEquals("NF2b-1: the separate audio track was dropped (saved silent)",
            listOf("https://cdn.example.com/v/audio.m4a" to mapOf("Referer" to "https://example.com/")), link.audioTracks)
        assertEquals("mp4", DownloadNaming.extensionFor(link))
    }

    @Test fun nf2b_1_protectedAndSmoothAreRefusedWithTheReason() {
        val wv = StreamDownload.refusal(StreamRequest(url = "https://x.test/a.mpd", drmScheme = DrmScheme.WIDEVINE))
        val ck = StreamDownload.refusal(StreamRequest(url = "https://x.test/a.mpd", drmScheme = DrmScheme.CLEARKEY))
        val ss = StreamDownload.refusal(StreamRequest(url = "https://x.test/a.ism/Manifest", format = StreamFormat.SMOOTH))
        assertEquals("NF2b-1: Widevine reason", "Protected video, can't be saved. You can still play it.", wv)
        assertEquals("NF2b-1: ClearKey reason", "Protected video, can't be saved. You can still play it.", ck)
        assertEquals("NF2b-1: Smooth Streaming reason", "This stream type (Smooth Streaming) can't be saved. You can still play it.", ss)
        assertEquals(null, StreamDownload.refusal(StreamRequest(url = "https://x.test/a.mpd")))
    }

    // ---- NF2b-2: spliced ad breaks were saved into the file ----

    @Test fun nf2b_2_downloadSkipsSplicedAdBreak() = runBlocking {
        val media = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n#EXT-X-PLAYLIST-TYPE:VOD\n" +
            (0 until 3).joinToString("") { "#EXTINF:4.0,\nm$it.ts\n" } +
            "#EXT-X-DISCONTINUITY\n#EXTINF:4.0,\nad/break0.ts\n#EXT-X-DISCONTINUITY\n" +
            (3 until 6).joinToString("") { "#EXTINF:4.0,\nm$it.ts\n" } + "#EXT-X-ENDLIST\n"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                seen += path
                return when {
                    path == "/v.m3u8" -> MockResponse.Builder().body(media).build()
                    path.startsWith("/m") -> MockResponse.Builder().body(Buffer().write(bytes(1000, path[2].code))).build()
                    path == "/ad/break0.ts" -> MockResponse.Builder().body(Buffer().write(bytes(700, 99))).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        val out = File(dir, "ads.ts")
        HlsDownloader(client, emptyMap()).download(server.url("/v.m3u8").toString(), out) { _, _ -> }
        val expected = (0 until 6).map { bytes(1000, "$it"[0].code) }.reduce { a, b -> a + b }
        assertFalse("NF2b-2: the ad segment was fetched: $seen", "/ad/break0.ts" in seen)
        assertEquals("NF2b-2: saved ${out.length()} bytes, main content is ${expected.size}", expected.size.toLong(), out.length())
        assertArrayEquals(expected, out.readBytes())
    }

    // ---- NF2b-3: TS stayed TS ----

    @Test fun nf2b_3_tsIsRemuxedToMp4WithBothTracks() {
        val ts = asset("av.ts")
        val out = File(dir, "av.mp4")
        MediaMux.remux(ts, out)
        assertTrue("NF2b-3: output is not an MP4 file", isMp4(out))
        val mimes = tracks(out)
        assertTrue("NF2b-3: tracks $mimes", mimes.any { it == "video/avc" } && mimes.any { it.startsWith("audio/") })
        // A broken input throws and leaves no output behind (the caller keeps the .ts).
        val bad = File(dir, "bad.ts").apply { writeBytes(bytes(4096, 5)) }
        val badOut = File(dir, "bad.mp4")
        assertTrue("NF2b-3: a broken TS did not throw", runCatching { MediaMux.remux(bad, badOut) }.isFailure)
        assertFalse("NF2b-3: a broken remux left a file", badOut.exists())
    }

    // ---- NF2b-4: naming and the worker's remux / fallback step ----

    @Test fun nf2b_4_hlsIsNamedMp4UpFront() {
        val link = OnlineLink("v", "Link", "https://x.test/a.m3u8", null, 720, isHls = true, isDash = false, headers = emptyMap())
        assertEquals("NF2b-4: HLS still named .ts", "mp4", DownloadNaming.extensionFor(link))
    }

    @Test fun nf2b_4_tsPartBecomesMp4OrStaysTs() {
        val ts = asset("av.ts")
        val part = File(dir, "x.mp4.part").also { ts.copyTo(it) }
        assertEquals("NF2b-4: TS part was not converted", "mp4", MediaMux.tsToMp4(part, enabled = true, freeBytes = Long.MAX_VALUE))
        assertTrue("NF2b-4: converted part is not MP4", isMp4(part))
        assertFalse("NF2b-4: temp file left", File(dir, "x.mp4.part.remux").exists())
        // Kill switch off, not enough space, or a broken file: the part stays the same TS bytes.
        val keep = File(dir, "k.mp4.part").also { ts.copyTo(it) }
        assertEquals("ts", MediaMux.tsToMp4(keep, enabled = false, freeBytes = Long.MAX_VALUE))
        assertEquals("ts", MediaMux.tsToMp4(keep, enabled = true, freeBytes = 10))
        assertArrayEquals(ts.readBytes(), keep.readBytes())
        val bad = File(dir, "b.mp4.part").apply { writeBytes(bytes(4096, 5)) }
        val badBytes = bad.readBytes()
        assertEquals("ts", MediaMux.tsToMp4(bad, enabled = true, freeBytes = Long.MAX_VALUE))
        assertArrayEquals(badBytes, bad.readBytes())
    }
}
