/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import androidx.media3.common.PlaybackException
import dev.asfakul.yumeiro.player.ErrorFacts
import dev.asfakul.yumeiro.player.RecoveryRouter
import dev.asfakul.yumeiro.player.RecoveryRouter.Step
import dev.asfakul.yumeiro.player.StallWatch
import dev.asfakul.yumeiro.player.StartWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R15 (ADR-006 step 3b): start / stall clocks and the error routing order. */
class RecoveryStateTest {
    private val net = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    private val http = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    private val codes = setOf(401, 403, 405, 407, 410, 428, 451)

    private fun input(code: Int, status: Int? = null, online: Boolean = true, network: Boolean = true, refresh: Boolean = false,
                      refreshes: Int = 0, onlineSource: Boolean = true, quick: Int = 0, gpu: Boolean = false) =
        RecoveryRouter.Input(ErrorFacts(code, status), gpu, network, online, refresh, refreshes, onlineSource, quick, codes)

    @Test fun startWatchGivesUpOnASilentSourceAfter12s() {
        val w = StartWatch()
        var v = StartWatch.Verdict.WAIT; var t = 0
        while (v == StartWatch.Verdict.WAIT) { v = w.tick(false, false, true, 0L); t++ }
        assertEquals(StartWatch.Verdict.GIVE_UP, v); assertEquals(12, t)
    }

    @Test fun startWatchGivesASlowButWorkingSourceUpTo45s() {
        val w = StartWatch()
        var v = StartWatch.Verdict.WAIT; var t = 0
        while (v == StartWatch.Verdict.WAIT) { v = w.tick(false, false, true, t * 100L); t++ }
        assertEquals(StartWatch.Verdict.GIVE_UP, v); assertEquals(45, t)
    }

    @Test fun startWatchIgnoresPausedTimeAndStopsAtFirstFrame() {
        val w = StartWatch()
        repeat(100) { assertEquals(StartWatch.Verdict.WAIT, w.tick(false, false, false, 0L)) }
        assertEquals(0L, w.waitedMs)
        repeat(5) { w.tick(false, false, true, 0L) }
        assertEquals(StartWatch.Verdict.STARTED, w.tick(true, false, true, 0L))
        assertEquals("stays done", StartWatch.Verdict.STARTED, w.tick(false, false, true, 0L))
        assertEquals(StartWatch.Verdict.STARTED, StartWatch().tick(false, true, true, 0L))
    }

    @Test fun stallWatchFiresAfter25sOfFrozenBufferOnly() {
        val w = StallWatch()
        var t = 0
        while (!w.tick(true, true, true, 10_000L)) t++
        assertEquals("first tick sets the baseline, then 25 frozen ticks", 25, t)
        // Growth, offline, paused or no first frame reset the clock.
        repeat(24) { w.tick(true, true, true, 10_000L) }
        assertFalse(w.tick(true, true, false, 10_000L))
        repeat(24) { assertFalse(w.tick(true, true, true, 10_000L)) }
        repeat(100) { i -> assertFalse(w.tick(true, true, true, 20_000L + i * 1_000L)) }
        repeat(100) { assertFalse(w.tick(true, false, true, 0L)) }
    }

    @Test fun routeOrder() {
        val none: () -> String? = { null }
        assertEquals(Step.DropGpuEffect, RecoveryRouter.route(input(4001, gpu = true), none))
        assertEquals(Step.LocalFailure, RecoveryRouter.route(input(net, network = false), none))
        assertEquals(Step.WaitForNetwork, RecoveryRouter.route(input(net, online = false, refresh = true), none))
        assertEquals(Step.RefreshLink, RecoveryRouter.route(input(http, 403, refresh = true, refreshes = 1), none))
        // Refresh budget spent → header ladder, then the next source.
        assertEquals(Step.HeaderVariant("referer"), RecoveryRouter.route(input(http, 403, refresh = true, refreshes = 2)) { "referer" })
        assertEquals(Step.NextSource(refused = true), RecoveryRouter.route(input(http, 403), none))
        // Online source, network blip: one quick retry, then rotate.
        assertEquals(Step.QuickRetry, RecoveryRouter.route(input(net), none))
        assertEquals(Step.NextSource(refused = false), RecoveryRouter.route(input(net, quick = 1), none))
        // Dead source the user picked by hand (decoder / parse error) rotates too.
        assertEquals(Step.NextSource(refused = false), RecoveryRouter.route(input(4001), none))
        // Stream-tab link: 451 is a legal block, no header games; generic recovery instead.
        var asked = false
        assertEquals(Step.Generic, RecoveryRouter.route(input(http, 451, onlineSource = false)) { asked = true; "x" })
        assertFalse(asked)
        assertEquals(Step.HeaderVariant("x"), RecoveryRouter.route(input(http, 403, onlineSource = false)) { "x" })
        assertEquals(Step.Generic, RecoveryRouter.route(input(http, 403, onlineSource = false), none))
        assertTrue(RecoveryRouter.route(input(http, 451)) { "y" } is Step.HeaderVariant)
    }
}
