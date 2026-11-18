/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.stream.LiveChannelTile
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * NBUG-14 (NB14-2, ADR-026): a channel logo on a host that refuses library User-Agents shows.
 *
 * Local server: `/wm/<n>.png` behaves like upload.wikimedia.org (403 "Please set a user-agent" to an empty
 * or `okhttp/…` User-Agent, the picture otherwise), `/ok/<n>.png` always serves its picture (control).
 * Two real `LiveChannelTile`s through the app's own Coil image loader; the tile is captured and its
 * pixels must contain the logo's colour (red for wm, green for ok). Old code: the wm tile stays on the
 * initials (request answered 403); the control shows.
 *
 * Log tag YuNb14: NB14_UA, NB14_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class Nbug14ChannelLogoDeviceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var server: MockWebServer
    private val agents = ConcurrentLinkedQueue<String>()

    private fun log(s: String) = Log.i("YuNb14", s)

    private fun png(color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Before fun setUp() {
        val red = png(android.graphics.Color.rgb(230, 20, 20)); val green = png(android.graphics.Color.rgb(20, 200, 40))
        server = MockWebServer()
        server.dispatcher = object : mockwebserver3.Dispatcher() {
            override fun dispatch(request: mockwebserver3.RecordedRequest): MockResponse {
                val ua = request.headers["User-Agent"].orEmpty()
                val path = request.url.encodedPath
                agents.add("$path ua=$ua")
                fun image(b: ByteArray) = MockResponse.Builder().addHeader("Content-Type", "image/png").body(Buffer().write(b)).build()
                return when {
                    path.startsWith("/wm/") && (ua.isBlank() || ua.startsWith("okhttp", ignoreCase = true)) ->
                        MockResponse.Builder().code(403).addHeader("Content-Type", "text/plain").body("Please set a user-agent and respect our robot policy").build()
                    path.startsWith("/wm/") -> image(red)
                    path.startsWith("/ok/") -> image(green)
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { runCatching { server.close() } }

    /** Share of pixels close to [r],[g],[b] in tile [index]. */
    private fun share(index: Int, r: Int, g: Int, b: Int): Double {
        val bmp = compose.onAllNodesWithTag("live_channel", true)[index].captureToImage().asAndroidBitmap()
        var hit = 0; var all = 0
        for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
            val p = bmp.getPixel(x, y); all++
            if (kotlin.math.abs(android.graphics.Color.red(p) - r) < 40 && kotlin.math.abs(android.graphics.Color.green(p) - g) < 40 &&
                kotlin.math.abs(android.graphics.Color.blue(p) - b) < 40) hit++
        }
        return if (all == 0) 0.0 else hit.toDouble() / all
    }

    @Test fun logoOnAHostThatRefusesLibraryAgentsShows() {
        val run = System.currentTimeMillis()
        val wm = StreamRequest(url = "http://127.0.0.1:9/a.m3u8", title = "Wiki Logo TV", logoUrl = server.url("/wm/$run.png").toString())
        val ok = StreamRequest(url = "http://127.0.0.1:9/b.m3u8", title = "Plain Logo TV", logoUrl = server.url("/ok/$run.png").toString())
        compose.setContent {
            Row {
                LiveChannelTile(wm, {}, Modifier.width(180.dp))
                LiveChannelTile(ok, {}, Modifier.width(180.dp))
            }
        }
        val okShown = runCatching { compose.waitUntil(20_000) { share(1, 20, 200, 40) > 0.05 }; true }.getOrDefault(false)
        val wmShown = runCatching { compose.waitUntil(if (okShown) 10_000 else 1_000) { share(0, 230, 20, 20) > 0.05 }; true }.getOrDefault(false)
        FirstRun.shot("nb14_tiles")
        agents.forEach { log("NB14_UA $it") }
        log("NB14_RESULT control=${if (okShown) "shown" else "missing"} wm=${if (wmShown) "shown" else "missing"} red=${"%.3f".format(share(0, 230, 20, 20))} green=${"%.3f".format(share(1, 20, 200, 40))}")
        assertTrue("control logo (any User-Agent) never showed · requests=${agents.toList()}", okShown)
        assertTrue("NB14-2: the logo on a host that refuses library User-Agents never showed · requests=${agents.toList()}", wmShown)
        assertTrue("NB14-2: the image request carried no named User-Agent · requests=${agents.toList()}",
            agents.any { it.startsWith("/wm/") && it.contains("ua=Yumeiro/") })
    }
}
