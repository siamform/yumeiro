/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.download.MediaMux
import dev.asfakul.yumeiro.online.download.YtDownloads
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * G8 D6 (YouTube downloads) on a device, for the parts that need Android itself. The CI emulators
 * can't reach YouTube (data-centre IPs get "confirm you're not a bot", see YouTube.seedForTests)
 * and a download always extracts fresh links, so the extraction step is not run here; the quality
 * list it feeds is covered by YtQualityDeviceTest / YtQualityOfflineDeviceTest.
 *
 * What runs: a YouTube-style video-only MP4 and audio-only M4A are served with Range answers
 * like googlevideo; `YtDownloads.fetch` saves them (one of them resumed from a half-written file),
 * `MediaMux.merge` joins them with the phone's MediaMuxer, and `YtDownloads.publish` puts the
 * result in Movies/Yumeiro (and an audio file in Music/Yumeiro) through MediaStore.
 */
@RunWith(AndroidJUnit4::class)
class YtDownloadPipelineDeviceTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext.applicationContext
    private lateinit var server: MockWebServer
    private val video by lazy { inst.context.assets.open("yt_video_only.mp4").use { it.readBytes() } }
    private val audio by lazy { inst.context.assets.open("yt_audio_only.m4a").use { it.readBytes() } }
    private val ranges = Collections.synchronizedList(ArrayList<String>())
    private val dir by lazy { File(ctx.cacheDir, "yt-pipeline-test").apply { deleteRecursively(); mkdirs() } }
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val published = ArrayList<android.net.Uri>()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.url.encodedPath) { "/videoplayback/v" -> video; "/videoplayback/a" -> audio; else -> null }
                    ?: return MockResponse.Builder().code(404).build()
                val range = request.headers["Range"]?.removePrefix("bytes=")
                ranges += "${request.url.encodedPath} ${range ?: "-"}"
                val from = range?.substringBefore('-')?.toLongOrNull() ?: return MockResponse.Builder().body(Buffer().write(body)).build()
                if (from >= body.size) return MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */${body.size}").build()
                val to = (range.substringAfter('-').toLongOrNull() ?: (body.size - 1L)).coerceAtMost(body.size - 1L)
                return MockResponse.Builder().code(206).addHeader("Content-Range", "bytes $from-$to/${body.size}")
                    .body(Buffer().write(body, from.toInt(), (to - from + 1).toInt())).build()
            }
        }
        server.start()
    }

    @After fun tearDown() {
        published.forEach { runCatching { ctx.contentResolver.delete(it, null, null) } }
        runCatching { server.close() }
        dir.deleteRecursively()
    }

    private fun tracks(f: File): Pair<List<String>, Long> {
        val ex = MediaExtractor().apply { setDataSource(f.absolutePath) }
        try {
            val mimes = (0 until ex.trackCount).map { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
            val dur = (0 until ex.trackCount).maxOf { i -> ex.getTrackFormat(i).let { if (it.containsKey(MediaFormat.KEY_DURATION)) it.getLong(MediaFormat.KEY_DURATION) else 0L } }
            return mimes to dur
        } finally { ex.release() }
    }

    private fun relativePath(uri: android.net.Uri): String? = ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    @Test fun a_fetchResumeMergeAndPublish() = runBlocking<Unit> {
        val vFile = File(dir, "clip.mp4.v"); val aFile = File(dir, "clip.mp4.a"); val out = File(dir, "Yu D6 clip-abc.mp4")
        // The video part was half saved before (app killed, network lost): only the rest is asked for.
        vFile.writeBytes(video.copyOfRange(0, video.size / 2))
        val vBytes = YtDownloads.fetch(client, server.url("/videoplayback/v").toString(), emptyMap(), vFile, video.size.toLong()) { }
        val aBytes = YtDownloads.fetch(client, server.url("/videoplayback/a").toString(), emptyMap(), aFile, audio.size.toLong()) { }
        Log.i(TAG, "YTDL_CASE a fetch v=$vBytes/${video.size} a=$aBytes/${audio.size} ranges=$ranges")
        assertArrayEquals("video bytes", video, vFile.readBytes())
        assertArrayEquals("audio bytes", audio, aFile.readBytes())
        assertTrue("resume asked from the saved point: $ranges", ranges.first().startsWith("/videoplayback/v ${video.size / 2}-"))

        MediaMux.merge(vFile, aFile, out, webm = false)
        val (mimes, durUs) = tracks(out)
        Log.i(TAG, "YTDL_CASE a merged size=${out.length()} tracks=$mimes durationUs=$durUs")
        assertTrue("video track: $mimes", mimes.any { it.startsWith("video/") })
        assertTrue("audio track: $mimes", mimes.any { it.startsWith("audio/") })
        assertEquals("one video + one audio", 2, mimes.size)
        assertTrue("whole clip kept: $durUs", durUs > 11_000_000L)

        if (Build.VERSION.SDK_INT >= 29) {
            val uri = YtDownloads.publish(ctx, out, "Yu D6 clip ${System.currentTimeMillis()}.mp4", "video/mp4", music = false)
            assertNotNull("published to Movies/Yumeiro", uri); published += uri!!
            val back = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val path = relativePath(uri)
            Log.i(TAG, "YTDL_CASE a published uri=$uri path=$path bytes=${back.size}/${out.length()}")
            assertArrayEquals(out.readBytes(), back)
            assertEquals("Movies/Yumeiro", path?.trimEnd('/'))
        }
        Log.i(TAG, "YTDL_RESULT PASS a")
    }

    @Test fun b_audioOnlyGoesToMusic() = runBlocking<Unit> {
        val aFile = File(dir, "song-abc.m4a")
        YtDownloads.fetch(client, server.url("/videoplayback/a").toString(), emptyMap(), aFile, audio.size.toLong()) { }
        assertArrayEquals(audio, aFile.readBytes())
        assertEquals("audio/mp4", YtDownloads.mimeOf("m4a", audioOnly = true))
        if (Build.VERSION.SDK_INT >= 29) {
            val uri = YtDownloads.publish(ctx, aFile, "Yu D6 song ${System.currentTimeMillis()}.m4a", "audio/mp4", music = true)
            assertNotNull(uri); published += uri!!
            val path = relativePath(uri)
            Log.i(TAG, "YTDL_CASE b uri=$uri path=$path")
            assertEquals("Music/Yumeiro", path?.trimEnd('/'))
        }
        // Already complete: a second fetch asks nothing new and keeps the file as it is.
        val before = ranges.size
        val again = YtDownloads.fetch(client, server.url("/videoplayback/a").toString(), emptyMap(), aFile, audio.size.toLong()) { }
        assertEquals(audio.size.toLong(), again)
        assertEquals("no request for a finished file", before, ranges.size)
        Log.i(TAG, "YTDL_RESULT PASS b")
    }

    private companion object { const val TAG = "YuYtDlTest" }
}
