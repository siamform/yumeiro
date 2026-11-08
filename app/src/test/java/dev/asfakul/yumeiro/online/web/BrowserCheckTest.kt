/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.online.web.BrowserCheck.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Verify you're human" must open only for real challenges, never for firewall blocks or plain errors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrowserCheckTest {
    private val injectedScript = "<html><body>{\"error\":\"forbidden\"}<script src=\"/cdn-cgi/challenge-platform/scripts/jsd/main.js\"></script></body></html>"

    @Test fun cfMitigatedHeaderIsAlwaysAChallenge() {
        assertEquals(Verdict.CHALLENGE, BrowserCheck.classify(403, "challenge", "cloudflare", true, ""))
    }

    @Test fun injectedBotScriptOnAnOrdinary403IsNotAChallenge() {
        // The old check matched "challenge-platform" here and opened an endless check page.
        assertEquals(Verdict.NONE, BrowserCheck.classify(403, null, "cloudflare", true, injectedScript))
    }

    @Test fun realJustAMomentPageIsAChallenge() {
        val body = "<!DOCTYPE html><html><head><title>Just a moment...</title></head><body><script>window._cf_chl_opt={}</script></body></html>"
        assertEquals(Verdict.CHALLENGE, BrowserCheck.classify(403, null, "cloudflare", true, body))
    }

    @Test fun firewallBlockIsBlockedNotChallenge() {
        val body = "<title>Attention Required! | Cloudflare</title><div id=\"cf-error-details\"><h1>Sorry, you have been blocked</h1>" +
            "<script src=\"/cdn-cgi/challenge-platform/scripts/jsd/main.js\"></script>"
        assertEquals(Verdict.BLOCKED, BrowserCheck.classify(403, null, "cloudflare", true, body))
    }

    @Test fun nonCloudflareAndOkResponsesPassThrough() {
        assertEquals(Verdict.NONE, BrowserCheck.classify(403, null, "nginx", false, "<title>Just a moment...</title>cf_chl_opt"))
        assertEquals(Verdict.NONE, BrowserCheck.classify(200, null, "cloudflare", true, "cf_chl_opt"))
        assertEquals(Verdict.NONE, BrowserCheck.classify(404, null, "cloudflare", true, "cf_chl_opt"))
    }

    @Test fun ddosGuardNeedsItsOwnMarkers() {
        assertEquals(Verdict.CHALLENGE, BrowserCheck.classify(403, null, "ddos-guard", false, "<script src=\"https://check.ddos-guard.net/check.js\">"))
        assertEquals(Verdict.NONE, BrowserCheck.classify(403, null, "ddos-guard", false, "forbidden"))
    }

    @Test fun apiPostAndMediaAreSolvedOnTheSiteRoot() {
        assertEquals("https://www.miruro.to/", BrowserCheck.challengePage("https://www.miruro.to/api/episodes?id=1"))
        assertEquals("https://www.miruro.to/", BrowserCheck.challengePage("https://www.miruro.to/watch/1", method = "POST"))
        assertEquals("https://cdn.example.com/", BrowserCheck.challengePage("https://cdn.example.com/hls/a/master.m3u8"))
        assertEquals("https://x.example:8443/", BrowserCheck.challengePage("https://x.example:8443/data.json"))
    }

    @Test fun ordinaryPagesAreSolvedInPlace() {
        assertEquals("https://www.miruro.to/watch/1", BrowserCheck.challengePage("https://www.miruro.to/watch/1"))
        assertEquals("https://a.example/api/x", BrowserCheck.challengePage("https://a.example/api/x", accept = "text/html,application/xhtml+xml"))
    }
}
