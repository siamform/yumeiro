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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADCHAIN-2 (owner list 23, ADR-062): the destination the site reveals survives an ad that takes the
 * page over; media at the end plays in Yumeiro; pop-ups follow the person, not the ad scripts; the
 * filter switch restores a normal browser.
 */
@RunWith(RobolectricTestRunner::class) // org.json, android.net.Uri in LinkHosts
@Config(sdk = [36])
class Adchain2ResolverTest {
    private var now = 2_000_000L
    private var st = LinkChainPolicy.State()

    private fun step(url: String, scan: Scan?, ad: Boolean = false): Action {
        now += 1_000
        val (n, a) = LinkChainPolicy.next(st, scan, url, java.net.URI(url).host, now, ad)
        st = n
        return a
    }

    @Test fun parseReadsTheRevealedDestinationOnlyWhenItIsAWebAddress() {
        val ok = LinkChain.parse("\"{\\\"chain\\\":true,\\\"step\\\":\\\"timer\\\",\\\"dest\\\":\\\"https://pixeldrain.com/u/abc123\\\"}\"")
        assertEquals("https://pixeldrain.com/u/abc123", ok?.dest)
        val bad = LinkChain.parse("\"{\\\"chain\\\":true,\\\"step\\\":\\\"timer\\\",\\\"dest\\\":\\\"javascript:alert(1)\\\"}\"")
        assertEquals(null, bad?.dest)
    }

    @Test fun anAdThatTakesOverAfterTheRevealOpensTheRealLink() {
        step("https://srnky.com/abc", Scan(true, Step.TIMER, secs = 5))
        step("https://srnky.com/abc", Scan(true, Step.TIMER, dest = "https://example.org/file"))
        assertEquals("https://example.org/file", st.revealed)
        // The site's own "go" request answered; an ad script then moved the page to an ad host.
        val a = step("https://www.popads.net/landing", Scan(false), ad = true)
        assertTrue(a is Action.Open)
        assertEquals("https://example.org/file", (a as Action.Open).url)
        // The real link loads and is the destination, with its address.
        step("https://example.org/file", Scan(false))
        val d = step("https://example.org/file", Scan(false))
        assertTrue(d is Action.Done)
        assertEquals("https://example.org/file", (d as Action.Done).url)
    }

    @Test fun anAdBeforeTheRevealGoesBackAndALoopStops() {
        step("https://srnky.com/abc", Scan(true, Step.TIMER, secs = 5))
        repeat(LinkChainPolicy.MAX_RECOVERIES) { assertTrue(step("https://popads.net/x", Scan(false), ad = true) is Action.Back) }
        val stop = step("https://popads.net/x", Scan(false), ad = true)
        assertTrue(stop is Action.GiveUp)
        assertTrue(st.stopped)
        // Stopped means stopped: nothing more is tapped or opened.
        assertEquals(Action.Idle, step("https://popads.net/x", Scan(false), ad = true))
    }

    @Test fun anOrdinaryPageIsNeverTreatedAsAnAdOrAChain() {
        assertEquals(Action.Idle, step("https://example.com/", Scan(false)))
        assertEquals(Action.Idle, step("https://example.com/", Scan(false)))
    }

    @Test fun destinationKinds() {
        assertTrue(ChainDestination.of("https://cdn.example.net/v/ep1.mp4?token=1") is ChainDestination.Kind.Media)
        assertTrue(ChainDestination.of("https://cdn.example.net/live/master.m3u8") is ChainDestination.Kind.Media)
        val px = ChainDestination.of("https://pixeldrain.com/u/abc123")
        assertTrue(px is ChainDestination.Kind.FileHost)
        assertEquals("https://pixeldrain.com/api/file/abc123", (px as ChainDestination.Kind.FileHost).url)
        assertEquals(ChainDestination.Kind.HostPage("Gofile"), ChainDestination.of("https://gofile.io/d/AbC12"))
        assertEquals(ChainDestination.Kind.Page, ChainDestination.of("https://example.com/article"))
        assertEquals(ChainDestination.Kind.Page, ChainDestination.of(null))
        assertEquals(ChainDestination.Kind.Page, ChainDestination.of("intent://x"))
    }

    @Test fun sameSiteAndHosts() {
        assertEquals("example.com", ChainDestination.hostOf("https://www.example.com:8443/a?b#c"))
        assertTrue(ChainDestination.sameSite("https://a.example.com/x", "https://example.com/y"))
        assertFalse(ChainDestination.sameSite("https://srnky.com/x", "https://popads.net/y"))
        assertFalse(ChainDestination.sameSite(null, "https://popads.net/y"))
    }

    @Test fun popupsFollowThePersonNotTheAdScripts() {
        val cur = "https://news.example.com/story"
        // A real link with target=_blank, tapped: opens here.
        assertEquals(PopupPolicy.Decision.OPEN_HERE, PopupPolicy.window(cur, "https://other.org/a", gesture = true, link = true, filter = true, chainActive = false))
        // A script window with no tap, or with an unknown address: held.
        assertEquals(PopupPolicy.Decision.BLOCK, PopupPolicy.window(cur, "https://other.org/a", gesture = false, link = true, filter = true, chainActive = false))
        assertEquals(PopupPolicy.Decision.BLOCK, PopupPolicy.window(cur, null, gesture = true, link = false, filter = true, chainActive = false))
        // Ad hosts never, even on a tap.
        assertEquals(PopupPolicy.Decision.BLOCK, PopupPolicy.window(cur, "https://popads.net/x", gesture = true, link = true, filter = true, chainActive = false))
        // Same site always (sign-in windows, players).
        assertEquals(PopupPolicy.Decision.OPEN_HERE, PopupPolicy.window(cur, "https://example.com/login", gesture = false, link = false, filter = true, chainActive = false))
        // On a shortener page every tap opens an ad tab: off-site windows are held while the walk runs.
        assertEquals(PopupPolicy.Decision.BLOCK, PopupPolicy.window("https://srnky.com/a", "https://other.org/a", gesture = true, link = true, filter = true, chainActive = true))
    }

    @Test fun filterOffIsANormalBrowser() {
        val cur = "https://news.example.com/story"
        assertEquals(PopupPolicy.Decision.OPEN_HERE, PopupPolicy.window(cur, "https://popads.net/x", gesture = true, link = true, filter = false, chainActive = false))
        assertEquals(PopupPolicy.Decision.CAPTURE, PopupPolicy.window(cur, null, gesture = true, link = false, filter = false, chainActive = false))
        // Even with the filter off, a window nobody tapped for is not opened.
        assertEquals(PopupPolicy.Decision.BLOCK, PopupPolicy.window(cur, "https://other.org", gesture = false, link = false, filter = false, chainActive = false))
        assertFalse(PopupPolicy.blockJump(cur, "https://popads.net/x", filter = false))
    }

    @Test fun adRedirectsAreHeldOrdinaryNavigationIsNot() {
        assertTrue(PopupPolicy.blockJump("https://srnky.com/a", "https://www.popads.net/x", filter = true))
        assertFalse(PopupPolicy.blockJump("https://srnky.com/a", "https://themezon.net/post", filter = true))
        assertFalse(PopupPolicy.blockJump("https://example.com/a", "https://example.com/b", filter = true))
        assertFalse(PopupPolicy.blockJump("https://example.com/a", null, filter = true))
    }

    @Test fun downloadsThatArePlayable() {
        assertTrue(WebDownloads.isPlayable("video/mp4", "clip.mp4", "https://h/x"))
        assertTrue(WebDownloads.isPlayable("audio/mpeg", null, "https://h/x"))
        assertTrue(WebDownloads.isPlayable("application/vnd.apple.mpegurl", null, "https://h/x"))
        assertTrue(WebDownloads.isPlayable("application/octet-stream", "Episode 01.mkv", "https://h/x"))
        assertTrue(WebDownloads.isPlayable(null, null, "https://h/v/ep.webm?sig=1"))
        assertFalse(WebDownloads.isPlayable("application/zip", "pack.zip", "https://h/pack.zip"))
        assertFalse(WebDownloads.isPlayable("application/vnd.android.package-archive", "app.apk", "https://h/app.apk"))
        assertFalse(WebDownloads.isPlayable("application/octet-stream", "setup.exe", "https://h/setup.exe"))
        // A media-looking name with a declared non-media type is trusted to the type.
        assertFalse(WebDownloads.isPlayable("application/pdf", "trailer.mp4", "https://h/x"))
    }
}
