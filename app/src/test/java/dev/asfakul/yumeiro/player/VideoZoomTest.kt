/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoZoomTest {
    private val w = 1000f; private val h = 500f

    @Test fun clampsScaleBetween100And400() {
        var s = VideoZoom.State()
        repeat(20) { s = VideoZoom.step(s, 1.5f, 0f, 0f, w, h) }
        assertEquals(4f, s.scale, 0.0001f)
        repeat(40) { s = VideoZoom.step(s, 0.5f, 0f, 0f, w, h) }
        assertEquals(1f, s.scale, 0.0001f)
        assertFalse(s.zoomed)
    }

    @Test fun panNeverOpensABlackGap() {
        val s = VideoZoom.step(VideoZoom.State(scale = 2f), 1f, 9999f, -9999f, w, h)
        assertEquals(500f, s.panX, 0.01f)   // (2-1)*1000/2
        assertEquals(-250f, s.panY, 0.01f)  // (2-1)*500/2
        val unzoomed = VideoZoom.step(VideoZoom.State(), 1f, 300f, 300f, w, h)
        assertEquals(0f, unzoomed.panX, 0f); assertEquals(0f, unzoomed.panY, 0f)
    }

    @Test fun badZoomFactorsAreIgnored() {
        val s = VideoZoom.State(scale = 2f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -3f).forEach {
            assertEquals(2f, VideoZoom.step(s, it, 0f, 0f, w, h).scale, 0f)
        }
    }

    @Test fun releaseNearNormalSnapsBack() {
        assertEquals(VideoZoom.State(), VideoZoom.settle(VideoZoom.State(1.04f, 10f, 5f)))
        val kept = VideoZoom.settle(VideoZoom.State(1.5f, 10f, 5f))
        assertEquals(1.5f, kept.scale, 0f); assertTrue(kept.zoomed)
        assertEquals("150%", VideoZoom.label(1.5f))
    }
}
