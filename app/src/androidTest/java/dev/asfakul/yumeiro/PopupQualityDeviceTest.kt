/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.OnlineDownloads
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt
import dev.asfakul.yumeiro.ui.screens.downloads.StreamDownloadAsk
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

/**
 * NF-2b + OL-5 + OL-5-HEIGHT + OL-6 on a device. A local server offers an HLS master with 1080p, 720p and 540p;
 * every variant's segments carry its height, so the saved bytes show which variant was downloaded.
 *  a: the popup lists the real heights; picking 540p (not in the Settings list) downloads the 540p variant.
 *  b: the popup is the OL-6 layout: summary line, one Offline / Permanent switch, details folded, Download in view.
 * Lines: POPQ_CASE ….
 */
@RunWith(AndroidJUnit4::class)
class PopupQualityDeviceTest {
    @get:Rule val hang = HangDumpRule()
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private fun log(s: String) = Log.i("YuPopupQ", s)
    private fun has(tag: String) = runCatching { rule.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    private lateinit var server: MockWebServer
    private val heights = listOf(1080, 720, 540)
    private fun seg(h: Int, i: Int) = ByteArray(8192) { ((h / 10 + i * 3 + it % 7) and 0xff).toByte() }
    private fun variant(h: Int) = (0 until 3).fold(ByteArray(0)) { acc, i -> acc + seg(h, i) }
    private var quality = 0
    private val title = "YuPopupQ clip"

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path.endsWith("/master.m3u8") -> MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl").body(
                        "#EXTM3U\n" + heights.joinToString("") { h -> "#EXT-X-STREAM-INF:BANDWIDTH=${h * 3000},RESOLUTION=${h * 16 / 9}x$h\nv$h.m3u8\n" },
                    ).build()
                    path.contains("/v") && path.endsWith(".m3u8") -> {
                        val h = path.substringAfterLast("/v").removeSuffix(".m3u8").toInt()
                        MockResponse.Builder().addHeader("Content-Type", "application/vnd.apple.mpegurl").body(
                            "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n" + (0 until 3).joinToString("") { "#EXTINF:2.0,\ns${h}_$it.ts\n" } + "#EXT-X-ENDLIST\n",
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
        inst.runOnMainSync { DownloadSettings.init(ctx); quality = DownloadSettings.quality.value; DownloadSettings.setQuality(ctx, 1080); DownloadSettings.setAskFirst(ctx, true) }
        OnlineDownloads.ensureLoaded(ctx)
        FirstRun.toTabs(rule)
        runCatching { rule.waitUntil(20_000) { has("home_screen_root") } }
    }

    @After fun tearDown() {
        runCatching { DownloadPrompt.dismiss() }
        runCatching { OnlineDownloads.items.value.filter { it.title.startsWith(title) }.forEach { OnlineDownloads.delete(ctx, it.id) } }
        runCatching { inst.runOnMainSync { DownloadSettings.setQuality(ctx, quality) } }
        runCatching { server.close() }
    }

    private fun openPopup(): Boolean {
        val req = StreamRequest(url = server.url("/hls/master.m3u8").toString(), title = title, pageUrl = server.url("/watch").toString())
        rule.runOnUiThread { StreamDownloadAsk.ask(ctx, req) }
        return runCatching { rule.waitUntil(15_000) { has("download_confirm") }; true }.getOrDefault(false)
    }

    private fun savedBytes(item: OnlineDownloads.Item?): ByteArray {
        if (item == null) return ByteArray(0)
        item.publicUri?.let { u -> runCatching { ctx.contentResolver.openInputStream(android.net.Uri.parse(u))?.use { return it.readBytes() } } }
        return OnlineDownloads.fileOf(ctx, item).takeIf { it.exists() }?.readBytes() ?: ByteArray(0)
    }

    @Test fun a_pickedHeightIsTheOneDownloaded() {
        val popup = openPopup()
        // Chips: 0 = Best, then the master's heights high to low (1080, 720, 540).
        val chips = (0..3).all { has("download_track_quality_$it") }
        FirstRun.shot("popq_a_popup")
        var done = false; var got = -1; var status: OnlineDownloads.Status? = null; var error: String? = null
        if (popup && chips) {
            rule.onNodeWithTag("download_track_quality_3", true).performScrollTo().performClick()
            rule.onNodeWithTag("download_confirm_start", true).performClick()
            done = runCatching { rule.waitUntil(60_000) { OnlineDownloads.items.value.any { it.title.startsWith(title) && it.status == OnlineDownloads.Status.DONE } }; true }.getOrDefault(false)
            val item = OnlineDownloads.items.value.firstOrNull { it.title.startsWith(title) }
            status = item?.status; error = item?.error
            val bytes = savedBytes(item)
            got = heights.firstOrNull { variant(it).contentEquals(bytes) } ?: -1
        }
        log("POPQ_CASE a popup=$popup chips=$chips done=$done got=${got}p status=$status error=$error")
        assertTrue("NF-2b/OL-5: no download popup for an HLS link", popup)
        assertTrue("OL-5: the popup does not list Best + the 3 real heights", chips)
        assertTrue("download did not finish in 60 s: $status $error", done)
        assertEquals("OL-5-HEIGHT: picked 540p (Settings says 1080p)", 540, got)
    }

    @Test fun b_popupIsTheShortSheet() {
        val popup = openPopup()
        val summary = has("download_summary")
        val switch = has("download_keep_switch") && has("download_mode_offline") && has("download_mode_permanent")
        val folded = !has("download_details_body")
        val button = runCatching { rule.onNodeWithTag("download_confirm_start", true).fetchSemanticsNode().let { n ->
            val root = rule.onAllNodesWithTag("download_confirm", true).fetchSemanticsNodes().first().boundsInRoot
            n.boundsInRoot.bottom <= root.bottom + 1f && n.boundsInRoot.height > 0f
        } }.getOrDefault(false)
        if (has("download_details")) rule.onNodeWithTag("download_details", true).performClick()
        rule.waitForIdle()
        val opens = has("download_details_body")
        FirstRun.shot("popq_b_sheet")
        log("POPQ_CASE b popup=$popup summary=$summary switch=$switch folded=$folded button=$button opens=$opens tv=${Edition.isTv}")
        assertTrue("no popup", popup)
        assertTrue("OL-6: no one-line summary", summary)
        assertTrue("OL-6: Offline / Permanent is not one switch", switch)
        assertTrue("OL-6: details must start folded", folded)
        assertTrue("OL-6: the Download button is not inside the popup's visible area", button)
        assertTrue("OL-6: details do not open", opens)
    }
}
