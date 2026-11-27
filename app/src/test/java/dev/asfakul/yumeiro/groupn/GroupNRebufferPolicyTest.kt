/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groupn

import dev.asfakul.yumeiro.stream.StreamMediaFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B15 (owner): playback stutter. After a stall the player must refill enough to ride out network jitter instead
 * of playing a second and stalling again; the first start stays quick.
 */
class GroupNRebufferPolicyTest {
    @Test fun afterAStallEnoughIsBufferedToKeepPlaying() {
        assertTrue("resume after a stall at ${StreamMediaFactory.RESUME_AFTER_REBUFFER_MS} ms", StreamMediaFactory.RESUME_AFTER_REBUFFER_MS >= 5_000)
        assertTrue("the first start stays quick", StreamMediaFactory.START_AFTER_MS <= 2_000)
        assertTrue("resume waits longer than the first start", StreamMediaFactory.RESUME_AFTER_REBUFFER_MS > StreamMediaFactory.START_AFTER_MS)
    }
}
