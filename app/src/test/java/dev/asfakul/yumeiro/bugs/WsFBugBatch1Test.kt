package dev.asfakul.yumeiro.bugs

// Workstream F batch 1 (2026-11-19): X13-6, NB15D-OB1, NB14-OB1, NB11-OB1. Each test fails on the code before
// its fix (see docs/UNVERIFIED-QUEUE.md rows and docs/SYSTEM-DESIGN.md ADR-035).

import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.stream.StreamRepository
import dev.asfakul.yumeiro.stream.StreamRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WsFBugBatch1Test {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun tearDown() { dev.asfakul.yumeiro.security.Privacy.setIncognito(ctx, false) }

    /** X13-6 (ADR-035): the phone build uses Jackson 2.18.11 (0 OSV advisories), not the unpatched 2.19.4. */
    @Test fun phoneJacksonIsThePatchedLine() {
        assertEquals("2.18.11", com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION.toString())
    }

    /** NB15D-OB1: incognito keeps stream links and Live TV channels out of Recents. */
    @Test fun incognitoDoesNotRecordStreamRecents() {
        StreamRepository.clear(ctx); StreamRepository.clearLive(ctx)
        dev.asfakul.yumeiro.security.Privacy.setIncognito(ctx, true)
        StreamRepository.addToHistory(ctx, StreamRequest(url = "https://example.com/a.m3u8"))
        StreamRepository.addLive(ctx, StreamRequest(url = "https://example.com/b.m3u8"))
        assertTrue(StreamRepository.history.value.isEmpty())
        assertTrue(StreamRepository.liveHistory.value.isEmpty())
    }

    /** NB15D-OB1: "Clear watch history" also clears stream and Live TV recents. */
    @Test fun clearWatchHistoryClearsStreamRecents() {
        StreamRepository.addToHistory(ctx, StreamRequest(url = "https://example.com/a.m3u8"))
        StreamRepository.addLive(ctx, StreamRequest(url = "https://example.com/b.m3u8"))
        dev.asfakul.yumeiro.ui.screens.settings.WatchHistoryClear.streams(ctx)
        assertTrue(StreamRepository.history.value.isEmpty())
        assertTrue(StreamRepository.liveHistory.value.isEmpty())
    }

    /** NB14-OB1 (ADR-035): SVG channel logos decode (Coil SVG decoder in the app's image loader). */
    @Test fun imageLoaderHasAnSvgDecoder() {
        assertTrue(dev.asfakul.yumeiro.ui.image.ImageDecoders.factories().any { it is coil.decode.SvgDecoder.Factory })
    }

    /** NB11-OB1 (ADR-035): 18+ on but private keeps NSFW titles of mixed extensions out of feed rows only. */
    @Test fun privateAdultDropsNsfwFromFeedsButNotSearch() {
        val d = dev.asfakul.yumeiro.online.AdultContent
        assertTrue(d.dropNsfw(nsfw = true, feed = true, adultOn = true, private = true))
        assertFalse(d.dropNsfw(nsfw = true, feed = false, adultOn = true, private = true))
        assertTrue(d.dropNsfw(nsfw = true, feed = false, adultOn = false, private = false))
        assertFalse(d.dropNsfw(nsfw = true, feed = true, adultOn = true, private = false))
        assertFalse(d.dropNsfw(nsfw = false, feed = true, adultOn = false, private = true))
    }
}
