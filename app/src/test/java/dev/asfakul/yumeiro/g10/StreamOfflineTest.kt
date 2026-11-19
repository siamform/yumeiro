/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.g10

import dev.asfakul.yumeiro.network.Connectivity
import dev.asfakul.yumeiro.stream.MediaKind
import dev.asfakul.yumeiro.stream.NetworkFailure
import dev.asfakul.yumeiro.stream.ProbeResult
import dev.asfakul.yumeiro.stream.Sniff
import dev.asfakul.yumeiro.stream.StreamRequest
import dev.asfakul.yumeiro.stream.StreamResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G10 ER-26: a stream link or playlist opened with no connection (Play Link, a Live TV channel, a
 * playlist refresh) failed in the probe with UnknownHostException, and `StreamResolver.failureFor`
 * said "Can't find the server … turn on an alternative DNS in Settings → Network" (with the DNS
 * button). Changing DNS can't help an offline phone. The shared mapper already says "No internet
 * connection" for the same exception while offline (OnlineErrors.friendly); the stream screens didn't.
 * Old code: offline DNS gives the DNS advice and suggestDns = true.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamOfflineTest {

    @After fun online() = Connectivity.overrideForTests(true)

    private fun probe(f: NetworkFailure) = ProbeResult(
        "http://tv.example.org/live.m3u8", "http://tv.example.org/live.m3u8", 0, null, Sniff(MediaKind.UNKNOWN, null, "none"),
        null, false, null, null, f, "Unable to resolve host",
    )

    @Test fun offlineSaysNoInternetNotDns() {
        Connectivity.overrideForTests(false)
        listOf(NetworkFailure.DNS, NetworkFailure.TIMEOUT, NetworkFailure.CONNECTION).forEach { f ->
            val r = StreamResolver.failureFor(probe(f), StreamRequest("http://tv.example.org/live.m3u8"))
            assertTrue("$f offline: no failure", r != null)
            assertEquals("$f offline: title", "No internet connection", r!!.title)
            assertFalse("$f offline: DNS advice while offline: ${r.message}", "DNS" in r.message)
            assertFalse("$f offline: the DNS button is offered while offline", r.suggestDns)
        }
    }

    @Test fun onlineDnsFailureKeepsTheDnsAdvice() {
        Connectivity.overrideForTests(true)
        val r = StreamResolver.failureFor(probe(NetworkFailure.DNS), StreamRequest("http://tv.example.org/live.m3u8"))!!
        assertEquals("Can't find the server", r.title)
    }
}
