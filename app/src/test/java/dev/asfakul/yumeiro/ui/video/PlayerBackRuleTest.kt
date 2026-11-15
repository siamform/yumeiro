/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import dev.asfakul.yumeiro.ui.screens.video.PlayerBackRule.Step
import org.junit.Assert.assertEquals
import org.junit.Test

/** R17 PL-18: one Back leaves on a phone; a TV remote keeps the stepped Back. */
class PlayerBackRuleTest {
    private fun step(key: Boolean = false, controls: Boolean = true, locked: Boolean = false, fs: Boolean = false, watch: Boolean = false, ended: Boolean = false) =
        PlayerBackRule.step(key, controls, locked, ended, false, false, fs, watch)

    @Test fun phoneFullscreenOneBackLeaves() = assertEquals(Step.LEAVE, step(fs = true))
    @Test fun phoneWithControlsOneBackLeaves() = assertEquals(Step.LEAVE, step(controls = true))
    @Test fun phoneFullscreenOnWatchPageReturnsToPage() = assertEquals(Step.EXIT_FULLSCREEN, step(fs = true, watch = true))
    @Test fun lockedShowsUnlockEverywhere() {
        assertEquals(Step.SHOW_UNLOCK, step(locked = true, fs = true))
        assertEquals(Step.SHOW_UNLOCK, step(key = true, locked = true))
    }
    @Test fun remoteHidesControlsFirst() = assertEquals(Step.HIDE_CONTROLS, step(key = true, controls = true, fs = true))
    @Test fun remoteThenExitsFullscreen() = assertEquals(Step.EXIT_FULLSCREEN, step(key = true, controls = false, fs = true))
    @Test fun remoteThenLeaves() = assertEquals(Step.LEAVE, step(key = true, controls = false, fs = false))
    @Test fun remoteOnEndScreenDoesNotJustHideControls() = assertEquals(Step.EXIT_FULLSCREEN, step(key = true, controls = true, fs = true, ended = true))
}
