/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import androidx.media3.ui.SubtitleView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R11 bug: captions sat under the seek bar while the player controls were showing. The bottom
 * padding of the captions now clears the top of the controls and drops back when they hide.
 */
class CaptionsAboveControlsTest {
    private val base = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
    // A 1080 px tall picture starting at the top of the window (full-screen landscape).
    private val top = 0
    private val h = 1080

    @Test fun controlsHiddenKeepsTheUsersHeight() {
        assertEquals(base, SubtitleStyleSettings.bottomPaddingFraction(SubtitleStyle(), top, h, null), 0f)
        val lifted = SubtitleStyle(followSystem = false, lift = 0.1f)
        assertEquals(base + 0.1f, SubtitleStyleSettings.bottomPaddingFraction(lifted, top, h, null), 1e-6f)
    }

    @Test fun visibleSeekBarLiftsCaptionsAboveIt() {
        // The seek bar row starts 180 px above the bottom edge.
        val barTop = h - 180
        val f = SubtitleStyleSettings.bottomPaddingFraction(SubtitleStyle(), top, h, barTop)
        val captionBottomPx = top + h - f * h
        assertTrue("caption bottom ($captionBottomPx) must be above the bar top ($barTop)", captionBottomPx < barTop)
        assertEquals(180f / h + SubtitleStyleSettings.CONTROLS_GAP, f, 1e-6f)
    }

    @Test fun aHigherUserLiftIsNeverLowered() {
        val high = SubtitleStyle(followSystem = false, lift = 0.30f)
        assertEquals(base + 0.30f, SubtitleStyleSettings.bottomPaddingFraction(high, top, h, h - 100), 1e-6f)
    }

    @Test fun controlsBelowThePictureChangeNothing() {
        // Portrait: a 16:9 picture in the middle of a tall screen, controls far below it.
        val picTop = 700; val picH = 608
        assertEquals(base, SubtitleStyleSettings.bottomPaddingFraction(SubtitleStyle(), picTop, picH, picTop + picH + 400), 0f)
    }

    @Test fun shortDockedPlayerIsCapped() {
        // The controls cover most of a tiny picture: captions stop at the cap instead of leaving it.
        assertEquals(SubtitleStyleSettings.MAX_BOTTOM_PADDING, SubtitleStyleSettings.bottomPaddingFraction(SubtitleStyle(), 0, 200, 20), 0f)
    }

    @Test fun unmeasuredViewFallsBackToTheBase() {
        assertEquals(base, SubtitleStyleSettings.bottomPaddingFraction(SubtitleStyle(), 0, 0, 10), 0f)
    }
}
