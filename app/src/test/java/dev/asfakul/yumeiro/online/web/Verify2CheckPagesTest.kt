/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.online.web.BrowserCheck.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** VERIFY-2: check pages served as 200 or by a non-Cloudflare script wall are passed like any other check. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Verify2CheckPagesTest {
    private val justAMoment = "<!DOCTYPE html><html><head><title>Just a moment...</title></head><body><script>window._cf_chl_opt={}</script></body></html>"
    private val wsid = "<html><head><title>One moment, please...</title></head><body><form id=\"wsidchk-form\" method=\"get\"></form></body></html>"

    @Test fun a200CheckPageIsACheck() {
        assertTrue(BrowserCheck.okPageIsCheck("text/html; charset=UTF-8", justAMoment))
        assertTrue(BrowserCheck.okPageIsCheck("text/html", wsid))
    }

    @Test fun ordinaryPagesAndNonHtmlAreLeftAlone() {
        assertFalse(BrowserCheck.okPageIsCheck("text/html", "<html><title>Episode 1</title><body>Just a moment, loading player</body></html>"))
        assertFalse(BrowserCheck.okPageIsCheck("application/json", justAMoment))
        assertFalse("a big real page that mentions the marker", BrowserCheck.okPageIsCheck("text/html", justAMoment + "x".repeat(40_000)))
    }

    @Test fun scriptWallFromANonCloudflareSiteIsACheck() {
        assertEquals(Verdict.CHALLENGE, BrowserCheck.classify(403, null, "nginx", false, wsid))
        assertEquals(Verdict.NONE, BrowserCheck.classify(403, null, "nginx", false, "<html>Forbidden</html>"))
    }

    @Test fun warmUpRunsAtMostEvery90Seconds() {
        assertTrue(BrowserCheckPolicy.mayWarm(null, 1_000_000))
        assertFalse(BrowserCheckPolicy.mayWarm(1_000_000, 1_000_000 + 30_000))
        assertTrue(BrowserCheckPolicy.mayWarm(1_000_000, 1_000_000 + BrowserCheckPolicy.WARM_COOLDOWN_MS))
    }
}
