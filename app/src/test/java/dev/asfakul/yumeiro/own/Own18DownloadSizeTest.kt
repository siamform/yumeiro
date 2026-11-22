/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.own

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.OnlineLink
import dev.asfakul.yumeiro.online.download.DownloadSettings
import dev.asfakul.yumeiro.online.download.DownloadSize
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPrompt
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadPromptHost
import dev.asfakul.yumeiro.ui.screens.downloads.DownloadSheet
import dev.asfakul.yumeiro.ui.screens.online.downloadFacts
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * OWN-18 (owner 2026-11-22: "in the download popup it never shows the file size ... every download popup should show
 * quality, size, sub / dub and all needed info"): the size is found for the picked quality and shown with the facts.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class Own18DownloadSizeTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val mb = 1024L * 1024

    @Test fun labelSaysExactOrAbout() {
        assertEquals("340 MB", DownloadSize.label(DownloadSize.Size(340 * mb, exact = true)))
        assertEquals("about 1.5 GB", DownloadSize.label(DownloadSize.Size(1536 * mb, exact = false)))
        assertNull(DownloadSize.label(DownloadSize.Size(0, exact = true)))
        assertNull(DownloadSize.label(null))
    }

    @Test fun totalReadsContentRangeFirst() {
        assertEquals(12345L, DownloadSize.total("bytes 0-0/12345", "1"))
        assertEquals(99L, DownloadSize.total(null, "99"))
        assertEquals(99L, DownloadSize.total("bytes 0-0/*", "99"))
        assertEquals(0L, DownloadSize.total(null, null))
    }

    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
        360.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2800000,AVERAGE-BANDWIDTH=2400000,RESOLUTION=1280x720
        720.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
        https://cdn.example/1080.m3u8
    """.trimIndent()

    @Test fun hlsVariantsAndPickFollowTheChosenQuality() {
        val v = DownloadSize.variants(master, "https://cdn.example/path/master.m3u8")
        assertEquals(listOf(360, 720, 1080), v.map { it.height })
        assertEquals("https://cdn.example/path/360.m3u8", v[0].uri)
        assertEquals("average bandwidth wins", 2_400_000L, v[1].bandwidth)
        assertEquals(1080, DownloadSize.pick(v, 0)?.height)
        assertEquals(720, DownloadSize.pick(v, 720)?.height)
        assertEquals(720, DownloadSize.pick(v, 1000)?.height)
        assertEquals("nothing small enough: the smallest", 360, DownloadSize.pick(v, 240)?.height)
        assertNull(DownloadSize.pick(emptyList(), 720))
    }

    @Test fun hlsMediaGivesSecondsAndExactBytesWhenRanged() {
        val plain = "#EXTM3U\n#EXTINF:6.0,\na.ts\n#EXTINF:4.5,\nb.ts\n#EXT-X-ENDLIST"
        assertEquals(10.5 to 0L, DownloadSize.media(plain))
        val ranged = "#EXTM3U\n#EXTINF:6.0,\n#EXT-X-BYTERANGE:1000@0\nv.mp4\n#EXTINF:6.0,\n#EXT-X-BYTERANGE:2000@1000\nv.mp4"
        assertEquals(12.0 to 3000L, DownloadSize.media(ranged))
        // 2.4 Mbit/s for 60 s = 18,000,000 bytes.
        assertEquals(18_000_000L, DownloadSize.estimate(2_400_000, 60.0))
        assertEquals(0L, DownloadSize.estimate(0, 60.0))
    }

    @Test fun dashUsesDurationTimesBandwidth() {
        assertEquals(3723.5, DownloadSize.isoSeconds("PT1H2M3.5S"), 0.001)
        assertEquals(0.0, DownloadSize.isoSeconds("bad"), 0.0)
        val mpd = """<MPD mediaPresentationDuration="PT100S"><Period>
            <Representation id="v1" bandwidth="1000000" height="480"/>
            <Representation id="v2" bandwidth="4000000" height="1080"/>
            <Representation id="a1" bandwidth="128000"/>
            </Period></MPD>"""
        assertEquals((4_128_000L * 100 / 8), DownloadSize.dash(mpd, 0)?.bytes)
        assertEquals((1_128_000L * 100 / 8), DownloadSize.dash(mpd, 720)?.bytes)
        assertNull(DownloadSize.dash("<MPD/>", 0))
    }

    @Test fun songsEstimateIs128Kbps() {
        assertEquals(DownloadSize.Size(128_000L * 300 / 8, exact = false), DownloadSize.songsEstimate(listOf(100, 200, 0)))
        assertNull(DownloadSize.songsEstimate(listOf(0)))
    }

    @Test fun sizeLineAndFacts() {
        val free = 2L * 1024 * mb
        assertEquals("Size: 300 MB · 2.0 GB free", DownloadSheet.sizeLine(DownloadSheet.SizeState.Known(DownloadSize.Size(300 * mb, true)), free, null))
        assertEquals("Size: checking…", DownloadSheet.sizeLine(DownloadSheet.SizeState.Checking, 0, null))
        assertTrue(DownloadSheet.sizeLine(DownloadSheet.SizeState.Unknown, 0, "known when each starts").contains("known when each starts"))
        assertEquals(listOf("720p", "Dub", "HLS stream"), DownloadSheet.facts("720p", listOf("Dub", " dub ", "", "HLS stream", "720P")))
    }

    @Test fun onlineFactsSaySubDubFormatAndSource() {
        val link = OnlineLink("Server 1", "Vidstream", "https://x/a.m3u8", null, 720, isHls = true, isDash = false, headers = emptyMap())
        assertEquals(listOf("Dub", "HLS stream", "Source: Vidstream"), downloadFacts("Dub", link))
        assertEquals(listOf("Video file"), downloadFacts("default", link.copy(isHls = false, source = " ")))
        assertEquals(emptyList<String>(), downloadFacts(null, null))
    }

    @Test fun popupShowsTheProbedSize() {
        DownloadSettings.init(ctx); DownloadSettings.setAskFirst(ctx, true)
        val asked = mutableListOf<Int>()
        rule.setContent { MTPlayerTheme { DownloadPromptHost() } }
        rule.waitForIdle()
        rule.runOnIdle {
            DownloadPrompt.ask(ctx, DownloadPrompt.Request(
                type = DownloadPrompt.Type.CURRENT, title = "Frieren", what = "Episode 3", count = 1, folder = "Frieren", quality = "1080p", defaultKeep = false,
                facts = listOf("Sub", "HLS stream"),
                sizeFor = { h -> asked += h; DownloadSize.Size(300 * mb, exact = true) },
            ) { null })
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Size: 300 MB", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("download_size").assertIsDisplayed()
        rule.onNodeWithTag("download_facts").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Sub").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("HLS stream").fetchSemanticsNodes().isNotEmpty())
        assertTrue("the size was asked for", asked.isNotEmpty())
    }

    @Test fun everyDownloadPopupCallerGivesFacts() {
        val root = "src/main/java/dev/asfakul/yumeiro/ui/screens/"
        for (f in listOf("online/OnlinePlayerUi.kt", "online/DownloadSheets.kt", "downloads/StreamDownloadAsk.kt", "youtube/YtMusicBrowse.kt")) {
            assertTrue("$f passes facts", File(root + f).readText().contains("facts ="))
        }
    }
}
