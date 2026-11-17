/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Network errors are recognised anywhere in the cause chain, as Android wraps them (device run 43702633505). */
class OnlineErrorsChainTest {
    private class GaiLike(msg: String) : Exception(msg)

    @Test fun unknownHostWithAPlatformCauseIsFriendly() {
        val e = java.net.UnknownHostException("Unable to resolve host \"x.invalid\": No address associated with hostname")
            .apply { initCause(GaiLike("android_getaddrinfo failed: EAI_NODATA")) }
        val msg = OnlineErrors.friendly(e)
        assertFalse(msg, msg.contains("Unable to resolve"))
    }

    @Test fun wrappedTimeoutAndTlsAreFriendly() {
        assertEquals("The server took too long to answer", OnlineErrors.friendly(java.io.IOException("call failed", java.net.SocketTimeoutException("read"))))
        assertEquals("Secure connection failed", OnlineErrors.friendly(java.io.IOException("x", javax.net.ssl.SSLHandshakeException("bad cert").apply { initCause(Exception("inner")) })))
    }

    @Test fun refusedWithCauseIsFriendly() {
        val e = java.net.ConnectException("Failed to connect").apply { initCause(Exception("ECONNREFUSED")) }
        assertEquals("Your network refused the connection. The site may be blocked here — try a VPN", OnlineErrors.friendly(e))
    }

    // G7-L1: QuickIPTV (com.phisher98) parsing a web page as a playlist.
    @Test fun notAPlaylistIsFriendly() {
        val e = IllegalStateException("Header doesn't start with #EXTM3U")
        assertEquals("The site sent something that isn't a playlist. It may be down or blocked; try again later", OnlineErrors.friendly(e))
    }
}
