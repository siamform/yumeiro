/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BUG-5 TV-5. Before the fix a TV (sw 540 dp, "not a tablet") got USER_PORTRAIT on open. */
class PlayerOrientationRuleTest {
    @Test fun tvNeverRequestsAnOrientation() {
        for (fs in listOf(true, false)) for (vertical in listOf(true, false)) for (tablet in listOf(true, false))
            assertEquals("tv fs=$fs vertical=$vertical tablet=$tablet", SCREEN_ORIENTATION_UNSPECIFIED,
                PlayerOrientationRule.request(tv = true, fullscreen = fs, verticalVideo = vertical, tablet = tablet))
    }

    @Test fun phoneAndTabletUnchanged() {
        assertEquals(SCREEN_ORIENTATION_USER_PORTRAIT, PlayerOrientationRule.request(false, fullscreen = false, verticalVideo = false, tablet = false))
        assertEquals(SCREEN_ORIENTATION_UNSPECIFIED, PlayerOrientationRule.request(false, fullscreen = false, verticalVideo = false, tablet = true))
        assertEquals(SCREEN_ORIENTATION_SENSOR_LANDSCAPE, PlayerOrientationRule.request(false, fullscreen = true, verticalVideo = false, tablet = false))
        assertEquals(SCREEN_ORIENTATION_SENSOR_LANDSCAPE, PlayerOrientationRule.request(false, fullscreen = true, verticalVideo = false, tablet = true))
        assertEquals(SCREEN_ORIENTATION_USER_PORTRAIT, PlayerOrientationRule.request(false, fullscreen = true, verticalVideo = true, tablet = false))
    }

    @Test fun tvIsPinnedFullScreenUnlessFloating() {
        assertTrue(PlayerOrientationRule.pinnedFullscreen(tv = true, minimized = false))
        assertFalse(PlayerOrientationRule.pinnedFullscreen(tv = true, minimized = true))
        assertFalse(PlayerOrientationRule.pinnedFullscreen(tv = false, minimized = false))
    }
}
