/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.1.0 Live TV country filter: country from tvg-country or the iptv-org tvg-id, and the feed filter. */
class LiveCountryTest {
    @Test fun countryFromAttributeOrTvgId() {
        assertEquals("BD", LiveCountry.of("bd", null))
        assertEquals("IN", LiveCountry.of("IN;US", "x.us@SD"))
        assertEquals("BD", LiveCountry.of(null, "BTV.bd@SD"))
        assertEquals("IN", LiveCountry.of(null, "sony.in"))
        assertEquals("GB", LiveCountry.of("UK", null))
        assertEquals("US", LiveCountry.of("INT", "CNN.us@East"))
        assertNull(LiveCountry.of(null, "SomeChannel"))
        assertNull(LiveCountry.of("", "bad.zz"))
        assertNull(LiveCountry.of(null, null))
    }

    @Test fun parserReadsAndWritesTheCountry() {
        val list = M3uParser.parseLines(sequenceOf(
            "#EXTM3U",
            "#EXTINF:-1 tvg-id=\"BTV.bd@SD\" group-title=\"News\",BTV",
            "https://s/1.m3u8",
            "#EXTINF:-1 tvg-country=\"IN\",Star",
            "https://s/2.m3u8",
            "#EXTINF:-1,Plain",
            "https://s/3.m3u8",
        ))
        assertEquals(listOf("BD", "IN", null), list.map { it.country })
        val again = M3uParser.parseLines(M3uWriter.write(list).lineSequence())
        assertEquals(listOf("BD", "IN", null), again.map { it.country })
    }

    @Test fun feedListsCountriesAndFiltersByThem() {
        fun ch(url: String, country: String?, group: String) = StreamRequest(url = url, title = url, country = country, group = group)
        val feed = LiveChannelFeed.build(listOf("p"), mapOf("p" to listOf(
            ch("a", "BD", "News"), ch("b", "BD", "Sports"), ch("c", "IN", "News"), ch("d", null, "News"),
        )))
        assertEquals(listOf("BD" to 2, "IN" to 1), feed.countries.map { it.name to it.count })
        assertEquals(setOf("a", "b"), LiveChannelFeed.filter(feed, null, "BD").map { it.channel.url }.toSet())
        assertEquals(listOf("a"), LiveChannelFeed.filter(feed, "News", "bd").map { it.channel.url })
        assertEquals(4, LiveChannelFeed.filter(feed, null, null).size)
    }
}
