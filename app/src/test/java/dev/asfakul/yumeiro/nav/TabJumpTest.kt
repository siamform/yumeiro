/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.nav

import dev.asfakul.yumeiro.FeatureFlags
import dev.asfakul.yumeiro.ui.navigation.TabJump
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TV-FP-1: Home -> Live TV on Android TV API 24 ended on Music (device 44995889578: jump interrupted, settled page 1). */
class TabJumpTest {
    @Test fun aJumpPulledOffItsTargetSnapsAgain() {
        // The logged case: target Videos (page 2), the pager settled on Music (page 1), no drag.
        assertTrue(TabJump.needsRetry(target = 2, current = 1, userDraggedSinceJump = false, retriesDone = 0))
    }

    @Test fun theUsersOwnSwipeIsNeverUndone() {
        assertFalse(TabJump.needsRetry(target = 2, current = 1, userDraggedSinceJump = true, retriesDone = 0))
    }

    @Test fun aJumpOnItsTargetIsLeftAlone() {
        assertFalse(TabJump.needsRetry(target = 2, current = 2, userDraggedSinceJump = false, retriesDone = 0))
    }

    @Test fun retriesStopAtTheLimit() {
        assertTrue(TabJump.MAX_RETRIES >= 1)
        assertTrue(TabJump.needsRetry(2, 0, false, TabJump.MAX_RETRIES - 1))
        assertFalse(TabJump.needsRetry(2, 0, false, TabJump.MAX_RETRIES))
    }

    @Test fun theFixIsOnByDefault() {
        assertTrue(FeatureFlags.Flag.TAB_JUMP_HOLD.default)
    }
}
