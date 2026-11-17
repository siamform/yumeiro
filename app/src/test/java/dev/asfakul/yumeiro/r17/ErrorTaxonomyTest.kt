/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r17

import dev.asfakul.yumeiro.online.OnlineErrors
import dev.asfakul.yumeiro.ui.components.ErrorHelp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R17 G10 (ledger ER-1): the shared mapper (OnlineErrors.friendly) and the shared tips
 * (ErrorHelp.suggestionsFor, shown under error cards and the player's error dialog) must agree:
 * every message the mapper produces gets tips for that cause, and HTTP 4xx answers are not called
 * "Server error".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ErrorTaxonomyTest {

    private data class Case(val name: String, val error: Throwable, val text: String, val tip: String, val notTip: String? = null)

    private val cases = listOf(
        Case("refused", java.io.IOException("x", java.net.ConnectException("refused")),
            "Your network refused the connection. The site may be blocked here — try a VPN", "VPN", notTip = "Referer"),
        Case("reset", java.net.SocketException("Connection reset"),
            "Your network refused the connection. The site may be blocked here — try a VPN", "VPN", notTip = "Referer"),
        Case("timeout", java.io.IOException("call failed", java.net.SocketTimeoutException("read")),
            "The server took too long to answer", "slow", notTip = "Retry — many problems are temporary"),
        Case("ssl", java.io.IOException("x", javax.net.ssl.SSLHandshakeException("bad cert")),
            "Secure connection failed", "date & time", notTip = "Check that you're online"),
        Case("404", java.io.IOException("HTTP 404"), "Not found (404)", "removed or moved"),
        Case("401", java.io.IOException("HTTP 401"), "Access denied: the server needs authorization (HTTP 401)", "Cookie"),
        Case("403", java.io.IOException("HTTP 403 Forbidden"), "Access denied by the server (HTTP 403)", "Referer"),
        Case("410", java.io.IOException("HTTP 410"), "Removed from the server (HTTP 410)", "removed or moved"),
        Case("429", java.io.IOException("HTTP 429"), "Too many requests, wait a minute and retry (HTTP 429)", "rate-limiting"),
        Case("451", java.io.IOException("HTTP 451"), "Not available in your country (HTTP 451)", "countries"),
        Case("503", java.io.IOException("HTTP 503"), "Server error (HTTP 503)", "having trouble"),
        Case("json", java.io.IOException("x", org.json.JSONException("bad")), "The server sent unreadable data", "extension"),
        Case("disk full in cause", java.io.IOException("backup failed", java.io.IOException("write failed: ENOSPC (No space left on device)")),
            "Not enough storage. Free up some space, then try again", "storage space"),
        Case("disk full text", java.io.IOException("write failed: ENOSPC (No space left on device)"),
            "Not enough storage. Free up some space, then try again", "storage space"),
        Case("file not writable (LT-2)", java.io.FileNotFoundException("/data/user/0/dev.asfakul.yumeiro/files/streams/playlists.json.tmp: open failed: EACCES (Permission denied)"),
            "The app couldn't use its storage on this device. Restart the app, then try again", "storage space"),
        Case("read-only storage in cause", java.io.IOException("save failed", java.io.IOException("write failed: EROFS (Read-only file system)")),
            "The app couldn't use its storage on this device. Restart the app, then try again", "storage space"),
        Case("broken link", IllegalArgumentException("Expected URL scheme 'http' or 'https' but no scheme was found for /x"),
            "The site sent a broken link for this source — try another source", "another source"),
    )

    @Test fun `mapper texts`() {
        cases.forEach { c -> assertEquals(c.name, c.text, OnlineErrors.friendly(c.error)) }
    }

    @Test fun `every mapper text gets tips for its own cause`() {
        cases.forEach { c ->
            val tips = ErrorHelp.suggestionsFor(c.text)
            assertTrue("${c.name}: tips $tips should mention '${c.tip}'", tips.any { c.tip in it })
            c.notTip?.let { n -> assertFalse("${c.name}: tips $tips should not mention '$n'", tips.any { n in it }) }
        }
    }

    @Test fun `no 4xx is called a server error`() {
        listOf(401, 403, 410, 429, 451).forEach { code ->
            assertFalse("$code", OnlineErrors.friendly(java.io.IOException("HTTP $code")).startsWith("Server error"))
        }
    }

    @Test fun `an app signature mismatch still gets the install tip`() {
        assertTrue(ErrorHelp.suggestionsFor("Update failed: certificate mismatch").any { "GitHub releases" in it })
    }
}
