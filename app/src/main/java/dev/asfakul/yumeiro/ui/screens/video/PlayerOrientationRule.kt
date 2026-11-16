/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import android.content.pm.ActivityInfo

/**
 * BUG-5 TV-5: which screen orientation the video player asks for, and when it is pinned full screen.
 *
 * Phones keep the old behaviour: the normal (watch-page) layout in portrait, full screen in
 * landscape (or immersive portrait for a vertical video). Tablets keep their orientation.
 *
 * TV layout (Yumeiro TV, or Layout = TV): the player never asks for an orientation and is always
 * full screen. Before, a TV went down the phone branch because "phone" meant smallestScreenWidthDp
 * < 600 and a 1080p TV is 540 dp: the player requested USER_PORTRAIT, the TV turned the whole
 * activity into a 608 × 1080 portrait box and showed the phone watch page (TvBug5ReproDeviceTest a,
 * tv-api34 run 43902905156). A TV screen cannot rotate, so any request is wrong there.
 * Pure; `PlayerOrientationRuleTest`.
 */
internal object PlayerOrientationRule {
    fun request(tv: Boolean, fullscreen: Boolean, verticalVideo: Boolean, tablet: Boolean): Int = when {
        tv -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        fullscreen -> if (verticalVideo) ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        tablet -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    }

    /** TV layout plays full screen whenever the player is not floating; there is no watch-page layout to go back to. */
    fun pinnedFullscreen(tv: Boolean, minimized: Boolean): Boolean = tv && !minimized
}
