/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groupk

import dev.asfakul.yumeiro.player.VideoZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** GROUP K: the Aspect ratio dialog's − / + / Reset picture size buttons. */
class VideoZoomButtonTest {
    private val w = 1920f; private val h = 1080f

    @Test fun plusSteps25PercentUpTo400() {
        var s = VideoZoom.State()
        val seen = mutableListOf<Float>()
        repeat(14) { s = VideoZoom.button(s, 1, w, h); seen += s.scale }
        assertEquals(listOf(1.25f, 1.5f, 1.75f, 2f), seen.take(4))
        assertEquals(VideoZoom.MAX, s.scale)
    }

    @Test fun minusStopsAt100AndClearsPan() {
        var s = VideoZoom.State(1.25f, 100f, 50f)
        s = VideoZoom.button(s, -1, w, h)
        assertEquals(VideoZoom.State(), s)
        s = VideoZoom.button(s, -1, w, h)
        assertFalse(s.zoomed)
    }

    @Test fun aPinchedScaleSnapsToTheGrid() {
        assertEquals(1.75f, VideoZoom.button(VideoZoom.State(1.43f), 1, w, h).scale)
        assertEquals(1.25f, VideoZoom.button(VideoZoom.State(1.43f), -1, w, h).scale)
    }

    @Test fun resetGoesBackTo100() {
        assertEquals(VideoZoom.State(), VideoZoom.button(VideoZoom.State(3f, 400f, 200f), 0, w, h))
    }

    @Test fun panIsKeptInsideTheSmallerPicture() {
        val s = VideoZoom.button(VideoZoom.State(2f, 900f, 500f), -1, w, h)
        assertEquals(1.75f, s.scale)
        assertEquals(0.75f * w / 2f, s.panX, 0.01f)
        assertEquals(0.75f * h / 2f, s.panY, 0.01f)
    }
}
