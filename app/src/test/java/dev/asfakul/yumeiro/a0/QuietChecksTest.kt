/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.online.ExtensionCheckWindows
import dev.asfakul.yumeiro.online.ExtensionCheckWindows.Page
import dev.asfakul.yumeiro.online.ExtensionCheckWindows.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4 (NET-2) and A5 (VERIFY-3): the firewall note is said only for real blocks and once for many sites; an
 * extension's own check window is never put over the screen by itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuietChecksTest {
    private fun v(code: Int, body: String) = BrowserCheck.classify(code, null, "cloudflare", true, body)

    @Test fun aRateLimitIsNotAFirewallBlock() {
        assertEquals(BrowserCheck.Verdict.RATE_LIMITED, v(429, "<html><title>Access denied | example.com used Cloudflare to restrict access</title>error code: 1015</html>"))
        assertEquals(BrowserCheck.Verdict.RATE_LIMITED, v(429, ""))
        assertEquals(BrowserCheck.Verdict.RATE_LIMITED, v(403, "error code: 1015"))
    }

    @Test fun realBlocksAndChallengesStayWhatTheyAre() {
        assertEquals(BrowserCheck.Verdict.BLOCKED, v(403, "<div id=\"cf-error-details\">Sorry, you have been blocked</div> error code: 1020"))
        assertEquals(BrowserCheck.Verdict.CHALLENGE, v(403, "<title>Just a moment...</title><script>window._cf_chl_opt={}</script>"))
        assertEquals(BrowserCheck.Verdict.CHALLENGE, BrowserCheck.classify(429, "challenge", "cloudflare", true, ""))
        assertEquals(BrowserCheck.Verdict.NONE, v(404, "error code: 1015"))
        assertEquals(BrowserCheck.Verdict.NONE, BrowserCheck.classify(429, null, "nginx", false, "slow down"))
    }

    @Test fun manyBlockedSitesMakeOneNote() {
        assertEquals(BrowserCheck.blockedMessage("a.example"), BrowserCheck.blockedMessage(listOf("a.example")))
        assertTrue(BrowserCheck.blockedMessage(listOf("a.example", "b.example")).contains("a.example and b.example"))
        val many = BrowserCheck.blockedMessage(listOf("a.example", "b.example", "c.example", "d.example"))
        assertTrue(many.contains("a.example and 3 other sites"))
        assertFalse(many.contains("b.example"))
    }

    @Test fun extensionChecksAreHandedOffNotShown() {
        assertEquals(Step.HAND_OFF, ExtensionCheckWindows.step(Page.HUMAN, 0, mayAsk = true, quiet = true))
        assertEquals(Step.WAIT, ExtensionCheckWindows.step(Page.CHALLENGE, ExtensionCheckWindows.REVEAL_AFTER_MS + 1, true, quiet = true))
        assertEquals(Step.HAND_OFF, ExtensionCheckWindows.step(Page.CHALLENGE, ExtensionCheckWindows.SHOW_ANYWAY_MS, true, quiet = true))
        assertEquals(Step.WAIT, ExtensionCheckWindows.step(Page.PAGE, 1_000, true, quiet = true))
        assertEquals(Step.CLOSE_BLOCKED, ExtensionCheckWindows.step(Page.BLOCK, 0, true, quiet = true))
        assertEquals(Step.KEEP, ExtensionCheckWindows.step(Page.HUMAN, 0, mayAsk = false, quiet = true))
        for (p in Page.entries) for (t in listOf(0L, 7_000L, 25_000L)) {
            assertTrue("never REVEAL when quiet ($p, $t)", ExtensionCheckWindows.step(p, t, true, quiet = true) != Step.REVEAL)
        }
        // Old behaviour kept behind the flag.
        assertEquals(Step.REVEAL, ExtensionCheckWindows.step(Page.HUMAN, 0, true))
    }

    @Test fun theVerifyNoteIsShownOncePerSiteAndOnlyOnScreen() {
        val now = 10_000_000L
        assertTrue(ExtensionCheckWindows.mayNoteHandOff(null, now, onScreen = true))
        assertFalse(ExtensionCheckWindows.mayNoteHandOff(null, now, onScreen = false))
        assertFalse(ExtensionCheckWindows.mayNoteHandOff(now - 60_000, now, true))
        assertTrue(ExtensionCheckWindows.mayNoteHandOff(now - ExtensionCheckWindows.HAND_OFF_NOTE_MS, now, true))
        assertTrue(ExtensionCheckWindows.handOffText("AnimePahe", "animepahe.ru").contains("Verify"))
    }
}
