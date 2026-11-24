/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ROT-1 (owner list 29): 90° steps, reset, and a turned picture that still fits its player box. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoRotationTest {
    @Test fun stepsGoClockwiseAndWrap() {
        assertEquals(listOf(90, 180, 270, 0), generateSequence(90) { VideoRotation.next(it) }.take(4).toList())
        assertEquals(270, VideoRotation.normalize(-90))
        assertEquals("180°", VideoRotation.label(540))
    }

    @Test fun uprightAndUpsideDownKeepTheirSize() {
        assertEquals(1f, VideoRotation.fitScale(0, 1920f, 1080f, 1920f, 1080f, cover = false))
        assertEquals(1f, VideoRotation.fitScale(180, 1920f, 1080f, 1920f, 1080f, cover = false))
        assertFalse(VideoRotation.quarterTurn(180))
        assertTrue(VideoRotation.quarterTurn(270))
    }

    @Test fun quarterTurnFitsInsideTheBox() {
        // 16:9 picture filling a 1920x1080 landscape box, turned 90°: 1080 wide x 1920 tall must shrink to 1080 tall.
        val s = VideoRotation.fitScale(90, 1920f, 1080f, 1920f, 1080f, cover = false)
        assertEquals(0.5625f, s, 1e-4f)
        assertTrue(1920f * s <= 1080f + 0.01f && 1080f * s <= 1920f + 0.01f)
        // Zoom mode fills the box instead (no black bars), so the scale is the larger one.
        assertEquals(1920f / 1080f, VideoRotation.fitScale(90, 1920f, 1080f, 1920f, 1080f, cover = true), 1e-4f)
    }

    @Test fun portraitPhoneVideoTurnedOnLandscapeScreen() {
        // A sideways phone clip letterboxed to 608x1080 in a 1920x1080 box, turned 270°: 1080x608 fits at 1.0..1.78.
        val s = VideoRotation.fitScale(270, 608f, 1080f, 1920f, 1080f, cover = false)
        assertEquals(minOf(1920f / 1080f, 1080f / 608f), s, 1e-4f)
    }

    @Test fun transformTurnsAndScalesARealView() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val box = View(ctx).apply { layout(0, 0, 1920, 1080) }
        val surface = View(ctx).apply { layout(0, 0, 1920, 1080) }
        val t = VideoRotation.SurfaceTransform().apply { degrees = 90 }
        t.apply(surface, box)
        assertEquals(90f, surface.rotation)
        assertEquals(0.5625f, surface.scaleX, 1e-4f)
        assertEquals(surface.scaleX, surface.scaleY)
        // Zoom 2x on top of the turn, pan clamped to the turned picture (607.5 x 1080 visual).
        t.zoom = VideoZoom.State(scale = 2f, panX = 5000f, panY = -5000f)
        t.apply(surface, box)
        assertEquals(1.125f, surface.scaleX, 1e-4f)
        assertEquals(1080f * 0.5625f / 2f, surface.translationX, 0.5f)
        assertEquals(-1920f * 0.5625f / 2f, surface.translationY, 0.5f)
        // Reset: back to the plain picture.
        t.degrees = 0; t.zoom = VideoZoom.State(); t.apply(surface, box)
        assertEquals(0f, surface.rotation); assertEquals(1f, surface.scaleX); assertEquals(0f, surface.translationX)
    }
}
