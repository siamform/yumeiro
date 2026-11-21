/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import dev.asfakul.yumeiro.stream.MediaKind
import dev.asfakul.yumeiro.ui.screens.stream.CapturedStream
import dev.asfakul.yumeiro.ui.screens.stream.DetectedStreamsPanel
import dev.asfakul.yumeiro.ui.screens.stream.PageStreamCapture
import dev.asfakul.yumeiro.ui.screens.stream.StreamPanel
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OL-3: the website stream list shows only the video playing now, with its title. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StreamCurrentOnlyTest {
    @get:Rule val rule = createComposeRule()

    private var now = 10_000L
    private fun capture() = PageStreamCapture(clock = { now })

    @Test fun aVideoSwitchDropsTheOldVideosStreams() {
        val c = capture()
        c.onRequest("https://cdn.example.com/one/master.m3u8", "GET", emptyMap())
        c.onRequest("https://cdn.example.com/one/720.m3u8", "GET", emptyMap())
        now += 60_000 // the first video played for a minute
        c.onRequest("https://cdn.example.com/two/master.m3u8", "GET", emptyMap()) // new video's manifest, just before the switch is seen
        now += 300
        assertTrue(c.newMedia("Episode 2"))
        assertEquals(listOf("https://cdn.example.com/two/master.m3u8"), c.all().map { it.url })
        assertEquals("Episode 2", c.all().single().title)
    }

    @Test fun capturesTakeTheTitleOfTheVideoPlayingNow() {
        val c = capture()
        c.retitle("Big Buck Bunny")
        val s = c.onRequest("https://cdn.example.com/bbb.mp4", "GET", emptyMap())!!
        assertEquals("Big Buck Bunny", s.title)
        assertTrue(c.retitle("Big Buck Bunny (HD)"))
        assertEquals("Big Buck Bunny (HD)", c.all().single().title)
        val r = c.toRequest(c.all().single(), "https://site.example/watch/1", c.all().single().title, null, null, null)
        assertEquals("Big Buck Bunny (HD)", r.title)
    }

    @Test fun anInPageNavigationWithNoTitleStillClearsTheOldVideo() {
        val c = capture()
        c.onRequest("https://cdn.example.com/one/master.m3u8", "GET", emptyMap())
        now += 5_000
        assertTrue(c.newMedia(null))
        assertEquals(0, c.all().size)
    }

    @Test fun thePanelShowsTheTitle() {
        rule.setContent {
            MTPlayerTheme {
                DetectedStreamsPanel(
                    found = listOf(CapturedStream("https://cdn.example.com/a.m3u8", MediaKind.HLS, "HLS", emptyMap(), "Episode 2")),
                    drmNote = null, state = StreamPanel.OPEN, onState = {}, onRescan = {}, onPlay = {}, nowTitle = "Episode 2",
                )
            }
        }
        rule.onNodeWithTag("page_finder_now_title", useUnmergedTree = true).assertTextEquals("Now playing: Episode 2")
    }
}
