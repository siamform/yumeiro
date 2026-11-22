/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import dev.asfakul.yumeiro.FeatureFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ST-17-BACK: stress 44998769417 run 7, Back pressed 4 times on a vertical (ReelShort) video and nothing happened. */
class VerticalBackTest {
    @Test fun backLeavesVerticalModeEvenWhenFullScreenIsAlreadyOff() {
        // The stuck state of run 7: docked watch page (not full screen) but still vertical.
        val after = VerticalMode.afterBack(VerticalMode.State(vertical = true, fullscreen = false, userLeft = false))
        assertFalse("Back must end vertical mode", after.vertical)
        assertTrue("and not come back by itself", after.userLeft)
    }

    @Test fun backFromFullScreenVerticalLeavesBoth() {
        val after = VerticalMode.afterBack(VerticalMode.State(vertical = true, fullscreen = true, userLeft = false))
        assertEquals(VerticalMode.State(vertical = false, fullscreen = false, userLeft = true), after)
    }

    @Test fun aSecondBackIsNeverSwallowed() {
        // Two Backs from a vertical video: the first ends vertical mode, so the second reaches the normal player rule.
        val once = VerticalMode.afterBack(VerticalMode.State(vertical = true, fullscreen = false, userLeft = false))
        assertFalse(once.vertical)
    }

    @Test fun notVerticalIsLeftToTheNormalBackRule() {
        val s = VerticalMode.State(vertical = false, fullscreen = true, userLeft = false)
        assertEquals(s, VerticalMode.afterBack(s))
    }

    @Test fun theFixIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.VERTICAL_BACK_LEAVES.default)
    }
}
