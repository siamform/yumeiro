/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DL-11 part 2 (ADR-016), pure logic: RequestPace times a window by the time spent waiting on the
 * host, and PaceGate stops pace restarts that do not help. The socket-level proof is in
 * DownloadThroughputTest (ownPausesAreNotAHostSlowdown, linkWideDipsStopUselessRestarts).
 */
class Dl11PaceTest {
    private val mb = 1024L * 1024
    private val s = 1_000_000_000L

    /** 64 KiB reads that each wait [waitNs] for the host, plus [pauseNs] of our own after every [pauseEvery] bytes. */
    private fun feed(p: RequestPace, bytes: Long, waitNs: Long, from: Long, pauseEvery: Long = 0, pauseNs: Long = 0, waited: Boolean = true): Pair<Boolean, Long> {
        var t = from; var any = false; var got = 0L
        while (got < bytes) {
            val n = minOf(64L * 1024, bytes - got); got += n
            t += waitNs
            if (p.slowed(n, t, if (waited) waitNs else -1L)) any = true
            if (pauseEvery > 0 && got % pauseEvery == 0L) t += pauseNs
        }
        return any to t
    }

    private val fast = 64L * 1024 * s / (12 * mb) // a 64 KiB read at 12 MiB/s

    @Test fun ownPausesBetweenReadsDoNotTrip() {
        val p = RequestPace()
        val (a, t) = feed(p, mb, fast, s)
        val (b, _) = feed(p, 8 * mb, fast, t, pauseEvery = 256 * 1024, pauseNs = s / 5) // 200 ms of ours per window
        assertFalse(a || b)
        assertTrue("the wall clock alone would have tripped", p.ownPauses > 0)
    }

    @Test fun theSamePausesStillTripWithoutWaitTimes() {
        val p = RequestPace()
        val (_, t) = feed(p, mb, fast, s, waited = false)
        val (b, _) = feed(p, 2 * mb, fast, t, pauseEvery = 256 * 1024, pauseNs = s / 5, waited = false)
        assertTrue("old behaviour kept for callers without wait times", b)
    }

    @Test fun aHostThatCapsTheRequestStillTrips() {
        val p = RequestPace()
        val (_, t) = feed(p, 3 * mb, 64L * 1024 * s / (200 * mb), s)
        val (b, _) = feed(p, 512 * 1024, 64L * 1024 * s / (1536 * 1024), t) // waits on the host: 1.5 MiB/s
        assertTrue(b)
        assertTrue(p.lastWindowRate > 0 && p.lastWindowRate * 3 < p.baselineRate)
    }

    /**
     * DL-11 part 2 (G8 pass-after 44280021547, real host 1.127x): the first MiB of a fast link comes out of
     * the socket buffer in about 2 ms, so the baseline read hundreds of MB/s and the link's own 20 MiB/s
     * looked like "under a third": the request was closed and its bytes in flight thrown away.
     */
    @Test fun aFastLinkAtItsOwnSpeedDoesNotTrip() {
        val p = RequestPace()
        val (a, t) = feed(p, mb, 64L * 1024 * s / (500 * mb), s) // the first MiB from the buffer
        val (b, _) = feed(p, 8 * mb, 64L * 1024 * s / (20 * mb), t) // then the link: 20 MiB/s, every read waits
        assertFalse("a 20 MiB/s link tripped against a buffer-speed baseline (${p.baselineRate * s / mb} MiB/s)", a || b)
    }

    @Test fun gateStaysOpenWhenRestartsHelp() {
        val g = PaceGate()
        repeat(20) { assertTrue(g.judge(newBaseline = 10.0, slowRate = 1.0)) }
        assertFalse(g.judge(1.0, 1.0)); assertFalse(g.judge(1.0, 1.0))
        assertTrue("two misses after twenty helps keep it open", g.open)
    }

    @Test fun gateClosesAfterRestartsThatDoNotHelp() {
        val g = PaceGate()
        assertFalse(g.judge(newBaseline = 1.2, slowRate = 1.0))
        assertTrue(g.open)
        assertFalse(g.judge(1.4, 1.0))
        assertFalse(g.open)
        assertEquals(0, g.helped); assertEquals(2, g.wasted)
    }

    @Test fun gainThresholdIsOnePointFive() {
        val g = PaceGate()
        assertTrue(g.judge(1.5, 1.0))
        assertFalse(g.judge(1.49, 1.0))
    }

    /** ADR-016 F (config, unit only): byte ranges never negotiate HTTP/2; the test control can. */
    @Test fun rangeClientIsHttp1Only() {
        val base = okhttp3.OkHttpClient()
        assertEquals(listOf(okhttp3.Protocol.HTTP_1_1), ForceGrab.rangeClient(base).protocols)
        ForceGrab.rangesAllowHttp2ForTest = true
        try { assertTrue(okhttp3.Protocol.HTTP_2 in ForceGrab.rangeClient(base).protocols) } finally { ForceGrab.rangesAllowHttp2ForTest = false }
    }
}
