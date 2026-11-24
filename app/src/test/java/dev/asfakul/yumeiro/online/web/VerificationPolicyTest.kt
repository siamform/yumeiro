/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.online.web.VerificationCenter.Outcome
import dev.asfakul.yumeiro.online.web.VerificationCenter.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VERIFY-1 (ADR-059, owner list 22): one state per site for every security check, so a site asks once,
 * a firewall block stops opening windows, and a check the user closed does not come straight back.
 */
class VerificationPolicyTest {
    private val t0 = 1_800_000_000_000L
    private val host = "animepahe.pw"

    @Test fun aPassClearsTheSiteAndResetsThePause() {
        val closed = VerificationPolicy.next(null, host, "AnimePahe", Outcome.CLOSED_BY_USER, t0)
        val passed = VerificationPolicy.next(closed, host, null, Outcome.PASSED, t0 + 1_000)
        assertEquals(State.CLEAR, passed.state)
        assertEquals(0, passed.closes)
        assertEquals(1, passed.passes)
        assertEquals("AnimePahe", passed.label) // the extension's name is kept when a later report has none
        assertTrue(VerificationPolicy.mayAsk(passed, t0 + 2_000))
    }

    @Test fun eachClosedCheckPausesLongerUpToADay() {
        var s: VerificationCenter.Site? = null
        val expected = listOf(10 * 60_000L, 30 * 60_000L, 2 * 3_600_000L, 6 * 3_600_000L, 24 * 3_600_000L, 24 * 3_600_000L)
        expected.forEachIndexed { i, pause ->
            val now = t0 + i * 100_000_000L
            s = VerificationPolicy.next(s, host, "AnimePahe", Outcome.CLOSED_BY_USER, now)
            assertEquals(State.PAUSED, s!!.state)
            assertEquals("close #${i + 1}", now + pause, s!!.until)
            assertFalse(VerificationPolicy.mayAsk(s, now + pause - 1))
            assertTrue(VerificationPolicy.mayAsk(s, now + pause))
        }
    }

    @Test fun aFirewallBlockHoldsChecksForHalfAnHourAndSaysWhy() {
        val s = VerificationPolicy.next(null, host, "AnimePahe", Outcome.BLOCKED, t0)
        assertEquals(State.BLOCKED, s.state)
        assertFalse(VerificationPolicy.mayAsk(s, t0 + 29 * 60_000L))
        assertTrue(VerificationPolicy.mayAsk(s, t0 + 30 * 60_000L))
        assertTrue(VerificationPolicy.describe(s, t0).contains("firewall blocks this network"))
    }

    @Test fun beingAskedAgainDoesNotLiftAPauseOrABlock() {
        val blocked = VerificationPolicy.next(null, host, null, Outcome.BLOCKED, t0)
        assertEquals(State.BLOCKED, VerificationPolicy.next(blocked, host, null, Outcome.NEEDS_PERSON, t0 + 5_000).state)
        val paused = VerificationPolicy.next(null, host, null, Outcome.CLOSED_BY_USER, t0)
        assertEquals(State.PAUSED, VerificationPolicy.next(paused, host, null, Outcome.NEEDS_PERSON, t0 + 5_000).state)
        assertEquals(State.PAUSED, VerificationPolicy.next(paused, host, null, Outcome.FAILED, t0 + 5_000).state)
        // After the hold, a new request for a person is recorded.
        assertEquals(State.NEEDS_YOU, VerificationPolicy.next(paused, host, null, Outcome.NEEDS_PERSON, t0 + 11 * 60_000L).state)
    }

    @Test fun repeatedBlockAnswersAreNotSavedAgain() {
        val a = VerificationPolicy.next(null, host, null, Outcome.BLOCKED, t0)
        val b = VerificationPolicy.next(a, host, null, Outcome.BLOCKED, t0 + 5_000)
        assertFalse(VerificationPolicy.changed(a, b)) // every refused request: no disk write, no log line
        assertTrue(VerificationPolicy.changed(a, VerificationPolicy.next(a, host, null, Outcome.BLOCKED, t0 + 120_000)))
        assertTrue(VerificationPolicy.changed(a, VerificationPolicy.next(a, host, null, Outcome.PASSED, t0 + 5_000)))
    }

    @Test fun statesSurviveARestart() {
        val list = listOf(
            VerificationPolicy.next(null, host, "AnimePahe\tTab", Outcome.BLOCKED, t0),
            VerificationPolicy.next(null, "www.Example.COM", null, Outcome.PASSED, t0 - 1_000),
        )
        val back = VerificationPolicy.decode(VerificationPolicy.encode(list))
        assertEquals(2, back.size)
        assertEquals(State.BLOCKED, back[0].state)
        assertEquals("AnimePahe Tab", back[0].label)
        assertEquals(list[0].until, back[0].until)
        assertEquals(State.CLEAR, back[1].state)
        assertTrue(VerificationPolicy.decode("garbage\nmore\tgarbage").isEmpty())
        assertTrue(VerificationPolicy.decode(null).isEmpty())
    }

    @Test fun oldEntriesAreDroppedAndClockChangesReleaseHolds() {
        val s = VerificationPolicy.next(null, host, null, Outcome.CLOSED_BY_USER, t0)
        assertFalse(VerificationPolicy.keep(s, t0 + VerificationPolicy.FORGET_AFTER_MS))
        assertTrue(VerificationPolicy.keep(s, t0 + 1_000))
        assertTrue(VerificationPolicy.mayAsk(s, t0 - 2 * 60_000L)) // clock moved back: no hold stuck for hours
    }

    @Test fun hostsAreNormalised() {
        assertEquals("animepahe.pw", VerificationPolicy.hostOf("https://WWW.AnimePahe.pw/api?m=airing"))
        assertNull(VerificationPolicy.hostOf("about:blank"))
        assertNull(VerificationPolicy.hostOf(null))
        assertEquals("example.com", VerificationPolicy.normalize("example.com."))
    }

    @Test fun descriptionsSayWhatHappensNext() {
        val paused = VerificationPolicy.next(null, host, null, Outcome.CLOSED_BY_USER, t0)
        assertEquals("You closed the check. It won't open by itself for 10 min.", VerificationPolicy.describe(paused, t0))
        assertEquals("You closed the check. It can ask again.", VerificationPolicy.describe(paused, t0 + 11 * 60_000L))
        val clear = VerificationPolicy.next(null, host, null, Outcome.PASSED, t0)
        assertEquals("Verified. Yumeiro reuses this pass until the site asks again.", VerificationPolicy.describe(clear, t0))
    }

    // ---- real pages saved on 2026-11-24 (test/resources/pages) ----

    private fun page(name: String) = javaClass.classLoader!!.getResource("pages/$name")!!.readText()

    @Test fun theRealAnimePaheBlockPageIsABlockNotACaptcha() {
        // curl https://animepahe.pw/ from a data-centre address: 403, Server: cloudflare, "Sorry, you have been blocked".
        val body = page("cloudflare-block-animepahe-2026-11-24.html")
        assertEquals(BrowserCheck.Verdict.BLOCKED, BrowserCheck.classify(403, null, "cloudflare", true, body))
    }

    @Test fun theRealJustAMomentPageIsAChallenge() {
        // A Cloudflare managed challenge (cf-mitigated: challenge, "Just a moment..."), with and without the header.
        val body = page("cloudflare-challenge-just-a-moment-2026-11-24.html")
        assertEquals(BrowserCheck.Verdict.CHALLENGE, BrowserCheck.classify(403, "challenge", "cloudflare", true, body))
        assertEquals(BrowserCheck.Verdict.CHALLENGE, BrowserCheck.classify(403, null, "cloudflare", true, body))
    }
}
