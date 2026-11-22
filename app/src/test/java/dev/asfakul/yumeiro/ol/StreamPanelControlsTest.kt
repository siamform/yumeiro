/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ol

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.asfakul.yumeiro.stream.MediaKind
import dev.asfakul.yumeiro.ui.screens.stream.CapturedStream
import dev.asfakul.yumeiro.ui.screens.stream.DetectedStreamsPanel
import dev.asfakul.yumeiro.ui.screens.stream.PageStreamCapture
import dev.asfakul.yumeiro.ui.screens.stream.StreamPanel
import dev.asfakul.yumeiro.ui.theme.MTPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OL-2: the website "Detected streams" panel can be rescanned, collapsed (down arrow) and closed. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StreamPanelControlsTest {
    @get:Rule val rule = createComposeRule()

    private val streams = listOf(
        CapturedStream("https://cdn.example.com/a/master.m3u8", MediaKind.HLS, "HLS", emptyMap()),
        CapturedStream("https://cdn.example.com/a/file.mp4", MediaKind.VIDEO, "MP4", emptyMap()),
    )
    private var rescans = 0

    private fun show() = rule.setContent {
        MTPlayerTheme {
            var state by remember { mutableStateOf(StreamPanel.OPEN) }
            DetectedStreamsPanel(found = streams, drmNote = null, state = state, onState = { state = it }, onRescan = { rescans++ }, onPlay = {})
        }
    }

    @Test fun collapseHidesTheListAndTheUpArrowBringsItBack() {
        show()
        rule.onAllNodesWithTag("page_finder_stream").assertCountEquals(2)
        rule.onNodeWithTag("page_finder_streams_collapse").performClick()
        rule.onAllNodesWithTag("page_finder_stream").assertCountEquals(0)
        rule.onNodeWithTag("page_finder_streams_panel").assertExists()
        rule.onNodeWithTag("page_finder_streams_expand").performClick()
        rule.onAllNodesWithTag("page_finder_stream").assertCountEquals(2)
    }

    @Test fun closeLeavesAPillThatReopens() {
        show()
        rule.onNodeWithTag("page_finder_streams_close").performClick()
        rule.onNodeWithTag("page_finder_streams_panel").assertDoesNotExist()
        rule.onNodeWithText("Streams · 2").assertExists()
        rule.onNodeWithTag("page_finder_streams_pill").performClick()
        rule.onAllNodesWithTag("page_finder_stream").assertCountEquals(2)
    }

    @Test fun rescanButtonAsksAgain() {
        show()
        rule.onNodeWithTag("page_finder_streams_reload").performClick()
        assertEquals(1, rescans)
    }

    @Test fun resetLetsTheSameStreamBeOfferedAgain() {
        val c = PageStreamCapture()
        assertNotNull(c.onRequest("https://cdn.example.com/a/master.m3u8", "GET", emptyMap()))
        assertNull(c.onRequest("https://cdn.example.com/a/master.m3u8", "GET", emptyMap()))
        c.reset()
        assertEquals(0, c.all().size)
        assertNotNull(c.onRequest("https://cdn.example.com/a/master.m3u8", "GET", emptyMap()))
    }
}
