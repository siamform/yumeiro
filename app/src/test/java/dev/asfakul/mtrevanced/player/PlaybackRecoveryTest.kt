/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryTest {
    private var clock = 1_000_000L
    private fun recovery(alt: Boolean = false) = PlaybackRecovery(now = { clock }, alternativeDns = { alt })
    private fun facts(code: Int, http: Int? = null, retryAfter: Long? = null, dns: Boolean = false) =
        ErrorFacts(code, http, retryAfter, dns)
    private val badHttp = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

    @Test fun serverErrors_boundedExponentialBackoff() {
        val r = recovery()
        val delays = (1..3).map { (r.decide(facts(badHttp, 503), true, true) as RecoveryAction.Retry).delayMs }
        assertEquals(listOf(2_000L, 4_000L, 8_000L), delays)
        assertTrue(r.decide(facts(badHttp, 503), true, true) is RecoveryAction.Fail)
        r.onStablePlayback()
        assertTrue(r.decide(facts(badHttp, 500), true, true) is RecoveryAction.Retry)
    }

    @Test fun tooManyRequests_respectsRetryAfter() {
        val r = recovery()
        assertEquals(15_000L, (r.decide(facts(badHttp, 429, retryAfter = 15), true, true) as RecoveryAction.Retry).delayMs)
        assertEquals(60_000L, (r.decide(facts(badHttp, 429, retryAfter = 600), true, true) as RecoveryAction.Retry).delayMs)
        assertTrue(r.decide(facts(badHttp, 429), true, true) is RecoveryAction.Fail)
    }

    @Test fun forbidden_refreshesOnceThenExplains() {
        val r = recovery()
        assertTrue(r.decide(facts(badHttp, 403), true, true) is RecoveryAction.Refresh)
        val f = r.decide(facts(badHttp, 403), true, true) as RecoveryAction.Fail
        assertTrue(f.message.contains("geo-blocking"))
        clock += 121_000
        assertTrue(r.decide(facts(badHttp, 403), true, true) is RecoveryAction.Refresh)
    }

    @Test fun dns_retriesThenSuggestsAlternative() {
        val r = recovery(alt = false)
        val e = facts(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, dns = true)
        assertTrue(r.decide(e, true, true) is RecoveryAction.Retry)
        assertTrue(r.decide(e, true, true) is RecoveryAction.Retry)
        val f = r.decide(e, true, true) as RecoveryAction.Fail
        assertTrue(f.offerDnsSettings)
        assertTrue(f.message.contains("Settings → Network"))
    }

    @Test fun timeouts_boundedAndLiveWindowJumps() {
        val r = recovery()
        val t = facts(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
        repeat(3) { assertTrue(r.decide(t, true, true) is RecoveryAction.Retry) }
        assertTrue(r.decide(t, true, true) is RecoveryAction.Fail)
        assertEquals(RecoveryAction.JumpToLive, r.decide(facts(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW), true, true))
    }

    @Test fun parsingReprobesOnce_drmAndDecoderFail() {
        val r = recovery()
        val p = facts(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
        assertEquals(RecoveryAction.Reprobe, r.decide(p, true, true))
        assertTrue(r.decide(p, true, true) is RecoveryAction.Fail)
        val drm = r.decide(facts(PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED), true, true) as RecoveryAction.Fail
        assertTrue(drm.message.contains("doesn't bypass DRM"))
        val dec = r.decide(facts(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED), true, true) as RecoveryAction.Fail
        assertEquals("Unsupported Video Format", dec.title)
    }
}
