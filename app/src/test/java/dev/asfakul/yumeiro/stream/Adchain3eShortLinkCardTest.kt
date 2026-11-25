/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import dev.asfakul.yumeiro.adchain.AdChainHttp
import dev.asfakul.yumeiro.adchain.AdChainPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ADCHAIN-3e: field probe 45537876436 opened srnky.com/0T1EnbeuLf. The walk logged "NeedsBrowser at srnky.com:
 * the site's button needs the browser" on the link's own first page, and the bridge's "same URL" branch kept
 * the resolver's generic "a web page, not a media file … start playback" card.
 */
@RunWith(RobolectricTestRunner::class)
class Adchain3eShortLinkCardTest {
    private val link = "https://srnky.com/0T1EnbeuLf"
    private val request = StreamRequest(url = link)
    private val generic = Resolution.NeedsBrowser(link, "This link is a web page, not a media file", request)

    @Test fun `short link stopped on its own first page says it is a short link`() {
        val out = AdChainHttp.Outcome.NeedsBrowser(link, "the site's button needs the browser", null, emptyList())
        val r = AdChainBridge.needsBrowser(generic, out, request, "UA") as Resolution.NeedsBrowser
        assertEquals(link, r.pageUrl)
        assertEquals(AdChainBridge.SHORT_LINK_TITLE, r.title)
        assertEquals(AdChainBridge.SHORT_LINK_REASON, r.reason)
        assertTrue(!r.reason.contains("not a media file"))
    }

    @Test fun `walk that moved on opens the later step`() {
        val step = "https://step.example/go"
        val out = AdChainHttp.Outcome.NeedsBrowser(step, "button", null, emptyList())
        val r = AdChainBridge.needsBrowser(generic, out, request, "UA") as Resolution.NeedsBrowser
        assertEquals(step, r.pageUrl)
        assertEquals(AdChainBridge.SHORT_LINK_TITLE, r.title)
    }

    @Test fun `a check page says so`() {
        val out = AdChainHttp.Outcome.NeedsBrowser(link, "check", AdChainPages.Check.CLOUDFLARE_PAGE, emptyList())
        val r = AdChainBridge.needsBrowser(generic, out, request, "UA") as Resolution.NeedsBrowser
        assertEquals(AdChainBridge.CHECK_PAGE_TITLE, r.title)
        assertEquals(StreamResolver.CHECK_PAGE_REASON, r.reason)
    }

    @Test fun `failed resolve becomes a page-browser card with the walker's user agent`() {
        val out = AdChainHttp.Outcome.NeedsBrowser(link, "button", null, emptyList())
        val r = AdChainBridge.needsBrowser(Resolution.Failed("x", "y"), out, request, "UA") as Resolution.NeedsBrowser
        assertEquals("UA", r.base.userAgent)
        assertEquals(AdChainBridge.SHORT_LINK_TITLE, r.title)
    }
}
