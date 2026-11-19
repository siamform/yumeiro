/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.l5

import dev.asfakul.yumeiro.stream.M3uParser
import dev.asfakul.yumeiro.ui.screens.music.tracksLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Small bugs from the UNVERIFIED-QUEUE (G7-L5-A, G7-L5-B, G7-L5-D). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QueueSmallFixesTest {
    /** G7-L5-A: `tvg-logo='…'` (single quotes) is common in hand-made playlists; logo and group were lost. */
    @Test fun singleQuotedAttributesAreRead() {
        val ch = M3uParser.parseLines(sequenceOf(
            "#EXTM3U",
            "#EXTINF:-1 tvg-id='a.b' tvg-logo='https://l/1.png' group-title='News',Channel One",
            "https://s/1.m3u8",
        )).single()
        assertEquals("https://l/1.png", ch.logoUrl)
        assertEquals("News", ch.group)
        assertEquals("Channel One", ch.title)
    }

    /** G7-L5-B: a channel line without a scheme is relative to the playlist's own address. */
    @Test fun relativeChannelLinesResolveAgainstThePlaylist() {
        val list = M3uParser.parseLines(sequenceOf(
            "#EXTM3U",
            "#EXTINF:-1,Rel",
            "live/one.m3u8",
            "#EXTINF:-1,Root",
            "/two.m3u8",
            "#EXTINF:-1,Junk",
            "404: Not Found",
        ), baseUrl = "https://host.example/lists/tv.m3u")
        assertEquals(listOf("https://host.example/lists/live/one.m3u8", "https://host.example/two.m3u8"), list.map { it.url })
        // Without a base (a file picked on the phone) nothing changes: such lines are skipped as before.
        assertEquals(0, M3uParser.parseLines(sequenceOf("#EXTINF:-1,Rel", "live/one.m3u8")).size)
    }

    /** G7-L5-D: "1 tracks". */
    @Test fun trackCountIsSingularForOne() {
        assertEquals("1 track", tracksLabel(1))
        assertEquals("0 tracks", tracksLabel(0))
        assertEquals("12 tracks", tracksLabel(12))
    }
}
