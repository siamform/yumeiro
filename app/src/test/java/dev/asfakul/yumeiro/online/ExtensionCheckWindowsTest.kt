/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.ExtensionCheckWindows.Page
import dev.asfakul.yumeiro.online.ExtensionCheckWindows.Step
import dev.asfakul.yumeiro.online.ExtensionPopups.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VERIFY-1 (ADR-059): an extension's own security-check window goes through the verification center.
 * Texts are the real ones from field probe 45345639530 (api36): AnimePahe's sheet over a Cloudflare block page.
 */
class ExtensionCheckWindowsTest {
    private val animePahe = Snapshot(
        listOf("🛡️", "Quick security check", "AnimePahe · protected by Cloudflare", "✕", "Close", "Tap the checkbox below to continue", "Reload", "Cancel"),
        hasInput = false, hasWeb = true,
    )

    @Test fun animePahesQuickSecurityCheckIsACheckWindow() {
        assertTrue(ExtensionCheckWindows.looksLikeCheck(animePahe))
        assertTrue(ExtensionCheckWindows.looksLikeCheck(Snapshot(listOf("Verify you are human"), hasInput = false, hasWeb = true)))
        assertTrue(ExtensionCheckWindows.looksLikeCheck(Snapshot(listOf("Checking your browser before accessing"), hasInput = false, hasWeb = true)))
    }

    @Test fun otherWindowsAreLeftToTheUsualRules() {
        // No web page: a plain dialog (donation, settings) is ADR-014's business.
        assertFalse(ExtensionCheckWindows.looksLikeCheck(animePahe.copy(hasWeb = false)))
        // A sign-in form with a web page is a login, not a check.
        assertFalse(ExtensionCheckWindows.looksLikeCheck(animePahe.copy(hasInput = true)))
        // A web page without check wording (an extension's in-app browser) is unchanged.
        assertFalse(ExtensionCheckWindows.looksLikeCheck(Snapshot(listOf("Sign in to MyAnimeList", "Done"), hasInput = false, hasWeb = true)))
    }

    @Test fun pageAnswersFromTheScript() {
        assertEquals(Page.BLOCK, ExtensionCheckWindows.page("\"block\""))
        assertEquals(Page.HUMAN, ExtensionCheckWindows.page("\"human\""))
        assertEquals(Page.CHALLENGE, ExtensionCheckWindows.page("\"challenge\""))
        assertEquals(Page.PAGE, ExtensionCheckWindows.page("\"page\""))
        assertEquals(Page.BLANK, ExtensionCheckWindows.page(null))
        assertEquals(Page.BLANK, ExtensionCheckWindows.page("null"))
    }

    @Test fun aFirewallBlockIsClosedWhateverElseIsTrue() {
        assertEquals(Step.CLOSE_BLOCKED, ExtensionCheckWindows.step(Page.BLOCK, 0, mayAsk = true))
        assertEquals(Step.CLOSE_BLOCKED, ExtensionCheckWindows.step(Page.BLOCK, 60_000, mayAsk = false))
    }

    @Test fun aWidgetForAPersonIsShownAtOnceAndARunningCheckAfterAFewSeconds() {
        assertEquals(Step.REVEAL, ExtensionCheckWindows.step(Page.HUMAN, 500, mayAsk = true))
        assertEquals(Step.WAIT, ExtensionCheckWindows.step(Page.CHALLENGE, 2_000, mayAsk = true)) // most pass by themselves
        assertEquals(Step.REVEAL, ExtensionCheckWindows.step(Page.CHALLENGE, ExtensionCheckWindows.REVEAL_AFTER_MS, mayAsk = true))
        assertEquals(Step.REVEAL, ExtensionCheckWindows.step(Page.BLANK, ExtensionCheckWindows.REVEAL_AFTER_MS, mayAsk = true))
    }

    @Test fun anOrdinaryPageWaitsForTheExtensionToCloseItThenIsShown() {
        assertEquals(Step.WAIT, ExtensionCheckWindows.step(Page.PAGE, ExtensionCheckWindows.REVEAL_AFTER_MS + 1, mayAsk = true))
        assertEquals(Step.REVEAL, ExtensionCheckWindows.step(Page.PAGE, ExtensionCheckWindows.SHOW_ANYWAY_MS, mayAsk = true))
    }

    @Test fun aPausedOrBlockedSiteIsNotShown() {
        assertEquals(Step.KEEP, ExtensionCheckWindows.step(Page.HUMAN, 500, mayAsk = false))
        assertEquals(Step.KEEP, ExtensionCheckWindows.step(Page.CHALLENGE, 30_000, mayAsk = false))
    }

    @Test fun theBlockNoteSaysWhatToDo() {
        assertEquals(
            "AnimePahe can't reach animepahe.pw: the site's firewall blocks this network, so its check was closed. Try Wi-Fi or mobile data, or turn off a VPN.",
            ExtensionCheckWindows.blockedText("AnimePahe", "animepahe.pw"),
        )
    }
}
