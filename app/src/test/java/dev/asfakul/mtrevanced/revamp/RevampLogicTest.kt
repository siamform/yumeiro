/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.revamp

import dev.asfakul.mtrevanced.online.AccessLadder
import dev.asfakul.mtrevanced.online.ExtensionGuard
import dev.asfakul.mtrevanced.online.OnlineItem
import dev.asfakul.mtrevanced.online.OnlineRow
import dev.asfakul.mtrevanced.stream.StreamRequest
import dev.asfakul.mtrevanced.ui.screens.online.FeedEntry
import dev.asfakul.mtrevanced.ui.screens.online.buildMixFeed
import dev.asfakul.mtrevanced.youtube.YtHistory
import dev.asfakul.mtrevanced.youtube.YtItem
import dev.asfakul.mtrevanced.youtube.formatYtDuration
import dev.asfakul.mtrevanced.youtube.formatYtViews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RevampLogicTest {

    private fun item(api: String, n: Int) = OnlineItem(name = "$api $n", url = "https://$api.example/t/$n", api = api, posterUrl = "https://img/$api/$n.jpg")
    private fun row(api: String, title: String, count: Int, offset: Int = 0) =
        OnlineRow(api, title, (0 until count).map { item(api, it + offset) }, false)

    // region mixed feed (multi-extension "For you")

    @Test fun mixFeedInterleavesChannelsAndKeysAreUnique() {
        val feed = buildMixFeed(
            listOf("A" to listOf(row("A", "Popular", 10)), "B" to listOf(row("B", "Latest", 10)), "C" to listOf(row("C", "Top", 10))),
            maxCards = 9, cardsPerShelf = 100,
        )
        val cards = feed.filterIsInstance<FeedEntry.Card>()
        assertEquals(9, cards.size)
        assertEquals(listOf("A", "B", "C", "A", "B", "C"), cards.take(6).map { it.item.api })
        assertEquals(feed.size, feed.map { it.key }.toSet().size)
    }

    @Test fun mixFeedDeduplicatesAndInsertsShelves() {
        // Same titles in two rows of one channel: shown once.
        val feed = buildMixFeed(listOf("A" to listOf(row("A", "R1", 4), row("A", "R2", 4)), "B" to listOf(row("B", "R1", 4))), maxCards = 50, cardsPerShelf = 2)
        val cards = feed.filterIsInstance<FeedEntry.Card>()
        assertEquals(cards.size, cards.map { it.item.key }.toSet().size)
        assertEquals(8, cards.size)
        assertTrue(feed.any { it is FeedEntry.Shelf })
        assertEquals(feed.size, feed.map { it.key }.toSet().size)
    }

    @Test fun mixFeedSkipsHeroAndHandlesEmpty() {
        assertTrue(buildMixFeed(emptyList(), 10).isEmpty())
        assertTrue(buildMixFeed(listOf("A" to listOf(row("A", "x", 0))), 10).isEmpty())
        val skip = setOf(item("A", 0).key)
        val feed = buildMixFeed(listOf("A" to listOf(row("A", "x", 3))), 10, skip = skip)
        assertFalse(feed.filterIsInstance<FeedEntry.Card>().any { it.item.key in skip })
    }

    // endregion

    // region 403 access ladder

    @Test fun ladderTriesEveryVariantOnceThenGivesUp() {
        val url = "https://cdn.host.example/v/master.m3u8?t=1"
        val original = StreamRequest(url = url, title = "x", referer = "https://embed.example/e/1")
        AccessLadder.reset(url)
        AccessLadder.hint(url, "https://site.example")
        val steps = generateSequence { AccessLadder.next(original) }.take(10).toList()
        assertEquals(AccessLadder.Step.entries.toList(), steps.map { it.step })
        assertNull(AccessLadder.next(original))
        AccessLadder.reset(url)
        assertNotNull(AccessLadder.next(original))
    }

    @Test fun ladderVariantsCarryTheRightHeaders() {
        val r = StreamRequest(url = "https://cdn.host.example/a.m3u8", title = "x", referer = "https://embed.example/e/1")
        val browser = AccessLadder.variant(r, AccessLadder.Step.BROWSER, null)!!
        assertEquals("https://embed.example", browser.origin)
        assertEquals("cors", browser.headers["Sec-Fetch-Mode"])
        assertEquals("cross-site", browser.headers["Sec-Fetch-Site"])
        val site = AccessLadder.variant(r, AccessLadder.Step.SITE_REFERER, "https://site.example/home")!!
        assertEquals("https://site.example/", site.referer)
        assertNull(AccessLadder.variant(r, AccessLadder.Step.SITE_REFERER, null))
        val self = AccessLadder.variant(r, AccessLadder.Step.SELF_REFERER, null)!!
        assertEquals("https://cdn.host.example/", self.referer)
        val none = AccessLadder.variant(r, AccessLadder.Step.NO_REFERER, null)!!
        assertNull(none.referer); assertNull(none.origin)
        assertFalse(none.requestHeaders().keys.any { it.equals("Referer", true) })
        assertEquals(AccessLadder.PLAYER_UA, AccessLadder.variant(r, AccessLadder.Step.PLAYER_UA, null)!!.userAgent)
    }

    // endregion

    @Test fun extensionFramesAreRecognised() {
        assertTrue(ExtensionGuard.frameIsExtension("com.example.hianime.HiAnimeProvider"))
        assertTrue(ExtensionGuard.frameIsExtension("recloudstream.AnimePahe\$load\$1"))
        assertFalse(ExtensionGuard.frameIsExtension("dev.asfakul.mtrevanced.MainActivity"))
        assertFalse(ExtensionGuard.frameIsExtension("androidx.compose.runtime.Recomposer"))
        assertFalse(ExtensionGuard.frameIsExtension("kotlinx.coroutines.DispatchedTask"))
        assertFalse(ExtensionGuard.frameIsExtension("java.lang.Thread"))
    }

    @Test fun ytHistoryRoundTrips() {
        val items = listOf(
            YtItem("abc", "https://www.youtube.com/watch?v=abc", "Song", "Artist - Topic", thumbnail = "https://i/abc.jpg", durationSec = 215, music = true),
            YtItem("live1", "https://www.youtube.com/watch?v=live1", "Live", "Ch", live = true),
        )
        val back = YtHistory.decode(YtHistory.encode(items))
        assertEquals(items.map { it.id }, back.map { it.id })
        assertEquals(215, back[0].durationSec)
        assertTrue(back[0].music); assertTrue(back[1].live)
        assertTrue(YtHistory.decode(null).isEmpty())
        assertTrue(YtHistory.decode("not json").isEmpty())
    }

    @Test fun ytFormatting() {
        assertEquals("3:45", formatYtDuration(225))
        assertEquals("1:02:03", formatYtDuration(3723))
        assertEquals("", formatYtDuration(0))
        assertEquals("1.2M views", formatYtViews(1_234_567))
        assertEquals("1K views", formatYtViews(1_000))
        assertEquals("999 views", formatYtViews(999))
    }
}
