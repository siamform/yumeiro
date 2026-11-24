/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.web

import dev.asfakul.yumeiro.online.web.LinkChain.Scan
import dev.asfakul.yumeiro.online.web.LinkChain.Step
import dev.asfakul.yumeiro.online.web.LinkChainPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADCHAIN-1 (owner list 23, ADR-060): the walk follows the site (captcha → person, timer → wait,
 * button → one real tap after it settles) and stops at the first page that is no chain step.
 * The sequence below is the srnky.com chain as it ran in a real browser on 2026-11-24.
 */
@RunWith(RobolectricTestRunner::class) // org.json
@Config(sdk = [36])
class LinkChainPolicyTest {
    private var now = 1_000_000L
    private var st = LinkChainPolicy.State()

    private fun step(url: String, scan: Scan?, advance: Long = 1_000): Action {
        now += advance
        val host = java.net.URI(url).host
        val (n, a) = LinkChainPolicy.next(st, scan, url, host, now)
        st = n
        return a
    }

    private fun ready(label: String, final: Boolean = false) = Scan(true, Step.READY, label, final, 200.0, 400.0, 400.0, 800.0)

    @Test fun anOrdinaryPageGetsNoBannerAndNoTap() {
        repeat(5) { assertEquals(Action.Idle, step("https://example.com/news", Scan(chain = false))) }
        assertEquals(false, st.active)
    }

    @Test fun theRealSrnkyChainIsWalkedToItsLink() {
        val land = "https://srnky.com/0T1EnbeuLf"
        assertTrue(step(land, Scan(true, Step.CAPTCHA)) is Action.AskUser)
        // The person ticked the box: "Continue" is on; first scan only notes it, the next one taps.
        assertTrue(step(land, ready("Continue")) is Action.Wait)
        val tap = step(land, ready("Continue"))
        assertTrue(tap is Action.Tap)
        tap as Action.Tap
        assertEquals(200.0, tap.x, 0.0); assertEquals(400.0, tap.y, 0.0)
        // Article pages: "Continue..." starts the site's 15 s timer, then "Get Link".
        val a1 = "https://loanbixby.com/business-loan-vs-personal-loan-what-is-good-for-you/"
        step(a1, ready("Continue..."))
        assertTrue(step(a1, ready("Continue...")) is Action.Tap)
        val wait = step(a1, Scan(true, Step.TIMER, secs = 12))
        assertEquals(Action.Wait("Waiting 12 s (the site's timer)"), wait)
        step(a1, ready("Get Link"))
        assertTrue(step(a1, ready("Get Link")) is Action.Tap)
        // Back on srnky: the site's own countdown, then the final "Get Link" (it wants a real tap).
        step(land, Scan(true, Step.TIMER, secs = 6))
        step(land, ready("Get Link", final = true))
        assertTrue(step(land, ready("Get Link", final = true)) is Action.Tap)
        // The destination: no chain signals on a new host, twice.
        val dest = "https://shortsync.epdexx.com/gateway?t=x"
        assertTrue(step(dest, Scan(chain = false)) is Action.Wait)
        assertEquals(Action.Done("shortsync.epdexx.com"), step(dest, Scan(chain = false)))
        assertEquals(4, st.taps)
        assertEquals("shortsync.epdexx.com", st.destination)
    }

    @Test fun aCaptchaIsNeverTappedOrSolved() {
        repeat(20) { assertTrue(step("https://shrinkme.click/a", Scan(true, Step.CAPTCHA), advance = 30_000) is Action.AskUser) }
        assertEquals(0, st.taps)
        // Waiting ten minutes for the person is not "stuck".
        assertEquals(false, st.stopped)
    }

    @Test fun tapsAreSpacedAndSettled() {
        val u = "https://themezon.net/a"
        step(u, ready("Continue Next"), advance = 100)
        assertTrue(step(u, ready("Continue Next"), advance = 300) is Action.Wait) // < 1 s on the button
        assertTrue(step(u, ready("Continue Next"), advance = 800) is Action.Tap)
        // The same button again at once: not before the gap, and it must settle again.
        assertTrue(step(u, ready("Continue Next"), advance = 200) is Action.Wait)
    }

    @Test fun theSameButtonThreeTimesHandsOverToThePerson() {
        val u = "https://themezon.net/b"
        var taps = 0
        repeat(12) { if (step(u, ready("Generate Link"), advance = 1_300) is Action.Tap) taps++ }
        assertEquals(LinkChainPolicy.SAME_BUTTON_TAPS, taps)
        assertEquals(Action.AskUser("Tap “Generate Link” on the page to continue"), step(u, ready("Generate Link")))
    }

    @Test fun aPageThatStopsMovingStopsTheWalk() {
        val u = "https://srnky.com/x"
        step(u, Scan(true, Step.NONE))
        val a = step(u, Scan(true, Step.NONE), advance = LinkChainPolicy.STUCK_MS + 1)
        assertTrue(a is Action.GiveUp)
        assertTrue(st.stopped)
        assertEquals(Action.Idle, step(u, ready("Get Link")))
    }

    @Test fun anAdPageBetweenStepsOnAKnownChainHostIsNotTheDestination() {
        step("https://srnky.com/0T1EnbeuLf", ready("Continue"))
        repeat(4) { assertTrue(step("https://srnky.com/0T1EnbeuLf", Scan(chain = false)) is Action.Wait) }
        assertNull(st.destination)
    }

    @Test fun parsesWhatEvaluateJavascriptReturns() {
        // evaluateJavascript hands back the script's string as a JSON string literal.
        val raw = "\"{\\\"chain\\\":true,\\\"signals\\\":4,\\\"removed\\\":2,\\\"secs\\\":null,\\\"stepText\\\":null,\\\"vw\\\":412,\\\"vh\\\":800,\\\"step\\\":\\\"ready\\\",\\\"label\\\":\\\"Get Link\\\",\\\"final\\\":true,\\\"x\\\":206.5,\\\"y\\\":512}\""
        val s = LinkChain.parse(raw)!!
        assertEquals(Step.READY, s.step)
        assertEquals("Get Link", s.label)
        assertEquals(true, s.final)
        assertEquals(206.5, s.x, 0.0)
        assertEquals(412.0, s.vw, 0.0)
        assertNull(s.secs)
        assertNull(s.stepText)
        assertEquals(2, s.defused)
        assertEquals(false, LinkChain.parse("\"{\\\"chain\\\":false,\\\"step\\\":\\\"none\\\"}\"")!!.chain)
        assertNull(LinkChain.parse("null"))
        assertNull(LinkChain.parse("not json"))
    }
}
