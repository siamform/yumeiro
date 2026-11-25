/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import dev.asfakul.yumeiro.player.SeekRecovery.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class SeekRecoveryTest {
    private fun d(code: Int, since: Long = 2_000L, tries: Int = 0, online: Boolean = true, net: Boolean = true, target: Long? = 60_000L, dur: Long = 600_000L) =
        SeekRecovery.decide(code, net, online, target, since, tries, dur)

    @Test fun ioErrorsRightAfterASeekReopenAtTheTarget() {
        assertEquals(Decision.Reopen(60_000L, 0L, 1), d(2008))
        assertEquals(Decision.Reopen(60_000L, 1_200L, 2), d(2004, tries = 1))
        assertEquals(Decision.Reopen(60_000L, 0L, 1), d(2001))
        assertEquals(Decision.Reopen(60_000L, 0L, 1), d(3001))
    }

    @Test fun boundedAndOnlyForSeeks() {
        assertEquals(Decision.NotASeekError, d(2008, tries = 2))
        assertEquals(Decision.NotASeekError, d(2008, since = 25_000L))
        assertEquals(Decision.NotASeekError, d(2008, target = null))
        assertEquals(Decision.NotASeekError, d(2001, online = false)) // offline: wait for the network instead
        assertEquals(Decision.NotASeekError, d(2008, net = false))    // local files
        assertEquals(Decision.NotASeekError, d(4001))                 // decoder
        assertEquals(Decision.NotASeekError, d(6001))                 // DRM
    }

    @Test fun aSeekPastTheEndReopensJustBeforeIt() {
        assertEquals(Decision.Reopen(598_500L, 0L, 1), d(2008, target = 600_000L))
    }
}
