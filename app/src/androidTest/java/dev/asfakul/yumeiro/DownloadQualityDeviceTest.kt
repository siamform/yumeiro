package dev.asfakul.yumeiro

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.online.download.OnlineDownloads.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DL-8 on a device: an HLS download follows Settings → Download quality. A local server offers a
 * master playlist with 1080p, 720p and 480p variants; every variant's segments carry its height,
 * so the saved file shows which variant was downloaded. Rule (HlsDownloader.bestVariant): the best
 * at or under the limit; with nothing under it, the smallest above; 0 = the top variant.
 * Before 7436a94 every HLS download took the top variant whatever the setting.
 */
@RunWith(AndroidJUnit4::class)
class DownloadQualityDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private lateinit var server: MockWebServer
    private val heights = listOf(1080, 720, 480)
    private fun seg(h: Int, i: Int) = ByteArray(8192) { ((h / 10 + i * 3 + it % 7) and 0xff).toByte() }
    private var before = 0

    @Before fun setUp() {
        before = DownloadSettings.quality.value
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path.endsWith("/master.m3u8") -> MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl").body(
                        buildString {
                            append("#EXTM3U\n")
                            // Out of order on purpose: the pick must not depend on the list order.
                            for (h in listOf(720, 1080, 480)) {
                                val w = h * 16 / 9
                                append("#EXT-X-STREAM-INF:BANDWIDTH=${h * 3000},RESOLUTION=${w}x$h\nv$h.m3u8\n")
                            }
                        },
                    ).build()
                    path.contains("/v") && path.endsWith(".m3u8") -> {
                        val h = path.substringAfterLast("/v").removeSuffix(".m3u8").toInt()
                        MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl").body(
                            "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n" +
                                (0 until 3).joinToString("") { "#EXTINF:2.0,\ns${h}_$it.ts\n" } + "#EXT-X-ENDLIST\n",
                        ).build()
                    }
                    path.contains("/s") && path.endsWith(".ts") -> {
                        val (h, i) = path.substringAfterLast("/s").removeSuffix(".ts").split('_').map { it.toInt() }
                        MockResponse.Builder().body(Buffer().write(seg(h, i))).build()
                    }
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        OnlineDownloads.ensureLoaded(ctx)
    }

    @After fun tearDown() {
        DownloadSettings.setQuality(ctx, before)
        runCatching { server.close() }
    }

    private suspend fun downloadWith(quality: Int): ByteArray {
        DownloadSettings.setQuality(ctx, quality)
        val title = "YuDlTest DL8 q$quality ${System.currentTimeMillis()}"
        val link = OnlineLink("Test", "test", server.url("/master.m3u8").toString(), null, 720, true, false, emptyMap())
        assertEquals(null, OnlineDownloads.enqueue(ctx, title, "yu-test", "t-dl8-$title", "E1", null, emptyMap(), link))
        val id = OnlineDownloads.items.value.first { it.title == title }.id
        val until = System.currentTimeMillis() + 60_000
        var item = OnlineDownloads.get(id)
        while (item?.status != Status.DONE && item?.status != Status.FAILED && System.currentTimeMillis() < until) { delay(200); item = OnlineDownloads.get(id) }
        val file = item?.let { OnlineDownloads.fileOf(ctx, it) }
        val bytes = file?.takeIf { it.exists() }?.readBytes() ?: ByteArray(0)
        Log.i("YuDownloadTest", "DL8 quality=$quality status=${item?.status} error=${item?.error} size=${bytes.size}")
        runCatching { OnlineDownloads.delete(ctx, id) }
        assertEquals("quality $quality: ${item?.error}", Status.DONE, item?.status)
        return bytes
    }

    private fun variant(h: Int) = (0 until 3).fold(ByteArray(0)) { acc, i -> acc + seg(h, i) }

    private fun which(bytes: ByteArray) = heights.firstOrNull { variant(it).contentEquals(bytes) } ?: -1

    @Test fun hlsFollowsTheDownloadQualitySetting() = runBlocking<Unit> {
        // setting → expected variant: 720 → 720; 480 → 480; 360 → smallest above (480); 0 → top (1080)
        val cases = listOf(720 to 720, 480 to 480, 360 to 480, 0 to 1080)
        val got = cases.map { (q, _) -> q to which(downloadWith(q)) }
        val line = "DL8_RESULT " + got.joinToString(" ") { (q, h) -> "q$q→${h}p" }
        Log.i("YuDownloadTest", line)
        cases.zip(got).forEach { (c, g) -> assertEquals(line, c.second, g.second) }
        Log.i("YuDownloadTest", "DL8_RESULT PASS")
    }
}
