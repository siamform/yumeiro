/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.grouph

import androidx.media3.common.PlaybackException
import dev.asfakul.yumeiro.online.YumixDefaults
import dev.asfakul.yumeiro.online.meta.TitleFacts
import dev.asfakul.yumeiro.online.upcoming.MovieReleases
import dev.asfakul.yumeiro.player.ErrorFacts
import dev.asfakul.yumeiro.player.PlaybackRecovery
import dev.asfakul.yumeiro.player.RecoveryAction
import dev.asfakul.yumeiro.player.SongFacts
import dev.asfakul.yumeiro.player.VideoPrefs
import dev.asfakul.yumeiro.stream.DecoderAuto
import dev.asfakul.yumeiro.ui.navigation.NavPrefs
import dev.asfakul.yumeiro.ui.screens.stream.StreamLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Group H (owner list 2026-11-25 items 3, 5, 8, 9, 11, 12): the pure parts. Device tests cover the screens. */
class GroupHTest {
    // H-3: Auto decoder.
    @Test fun h3_autoIsFirstAndTheDefault() {
        assertEquals(VideoPrefs.Decoder.AUTO, VideoPrefs.Decoder.entries.first())
        assertEquals(VideoPrefs.Decoder.AUTO, VideoPrefs.decoder.value)
        assertTrue(VideoPrefs.Decoder.AUTO.detail.startsWith("Recommended"))
        assertFalse(VideoPrefs.Decoder.HARDWARE_PLUS.detail.contains("Recommended"))
    }

    @Test fun h3_autoRetriesOnceOnSoftwareThenFails() {
        DecoderAuto.reset()
        val r = PlaybackRecovery(autoSoftware = { DecoderAuto.switchToSoftware() })
        val e = ErrorFacts(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, message = "no decoder")
        val first = r.decide(e, true, true)
        assertTrue("first decode failure under Auto retries: $first", first is RecoveryAction.Retry)
        assertEquals(PlaybackRecovery.AUTO_SOFTWARE, (first as RecoveryAction.Retry).reason)
        assertTrue(DecoderAuto.softwareFirst)
        assertTrue("second failure explains: ${r.decide(e, true, true)}", r.decide(e, true, true) is RecoveryAction.Fail)
        DecoderAuto.reset()
        assertFalse("a new video starts on the phone's decoders", DecoderAuto.softwareFirst)
    }

    @Test fun h3_otherDecodersStillFailWithTheTip() {
        val r = PlaybackRecovery()
        val f = r.decide(ErrorFacts(PlaybackException.ERROR_CODE_DECODING_FAILED), true, true)
        assertTrue(f is RecoveryAction.Fail)
    }

    // H-5: Yumix default extensions.
    @Test fun h5_defaultsShapeTheMix() {
        val all = listOf("A", "B", "C")
        assertEquals(all, YumixDefaults.inMix(all, emptySet()))
        assertEquals(listOf("A", "C"), YumixDefaults.inMix(all, setOf("C", "A")))
        assertEquals("uninstalled defaults fall back to every extension", all, YumixDefaults.inMix(all, setOf("Z")))
        assertEquals(setOf("A", "B"), YumixDefaults.decode(YumixDefaults.encode(setOf("B", "A"))))
        assertEquals(emptySet<String>(), YumixDefaults.decode(null))
    }

    // H-8: song facts.
    @Test fun h8_durationAndSizeFallBack() {
        assertEquals(1000L, SongFacts.durationMs(1000, 5, 7))
        assertEquals(5L, SongFacts.durationMs(0, 5, 7))
        assertEquals(7L, SongFacts.durationMs(0, null, 7))
        assertNull(SongFacts.durationMs(0, null, 0))
        assertEquals(9L, SongFacts.sizeBytes(0, 9))
        assertNull(SongFacts.sizeBytes(0, 0))
        assertEquals("1 B", SongFacts.sizeLabel(1, "content://x") { "$it B" })
        assertEquals("Streamed (no file)", SongFacts.sizeLabel(null, "https://music.youtube.com/watch?v=x") { "" })
        assertEquals("Unknown", SongFacts.sizeLabel(null, "content://media/1") { "" })
    }

    // H-9: movies by country.
    @Test fun h9_movieQueryAndParse() {
        val today = LocalDate.parse("2026-11-26")
        val q = MovieReleases.query("IN", today, today.plusDays(14))
        assertTrue(q.contains("wdt:P297 \"IN\"") && q.contains("2026-11-26T00:00:00Z") && q.contains("2026-12-10T00:00:00Z"))
        val body = """{"results":{"bindings":[
          {"film":{"value":"http://www.wikidata.org/entity/Q1"},"label":{"value":"King"},"d":{"value":"2026-12-25T00:00:00Z"},"links":{"value":"12"}},
          {"film":{"value":"http://www.wikidata.org/entity/Q1"},"label":{"value":"King"},"d":{"value":"2026-12-04T00:00:00Z"},"img":{"value":"http://commons.wikimedia.org/wiki/Special:FilePath/K.jpg"},"links":{"value":"12"}},
          {"film":{"value":"http://www.wikidata.org/entity/Q2"},"label":{"value":"Eetha"},"d":{"value":"2026-11-30T00:00:00Z"}},
          {"film":{"value":"http://www.wikidata.org/entity/Q3"},"label":{"value":"Past"},"d":{"value":"2026-11-01T00:00:00Z"}},
          {"film":{"value":"http://www.wikidata.org/entity/Q4"},"d":{"value":"2026-11-30T00:00:00Z"}}
        ]}}"""
        val list = MovieReleases.parse(body, "IN", today)
        assertEquals(listOf("Eetha", "King"), list.map { it.title })
        val king = list.last()
        assertEquals(LocalDate.parse("2026-12-04"), king.day)
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/K.jpg?width=300", king.posterUrl)
        assertEquals("IN", king.country)
        assertTrue(king.rank in 1..999)
        assertEquals(emptyList<Any>(), MovieReleases.parse("not json", "IN", today))
    }

    // H-11: IMDb id by title.
    @Test fun h11_imdbPickIsStrict() {
        val hits = TitleFacts.parseSearch("""{"metas":[{"id":"tt0133093","name":"The Matrix","releaseInfo":"1999"},
            {"id":"tt10838180","name":"The Matrix Resurrections","releaseInfo":"2021"},{"id":"kitsu:1","name":"The Matrix"}]}""")
        assertEquals(2, hits.size)
        assertEquals("tt0133093", TitleFacts.pickImdb(hits, "the matrix", 1999))
        assertEquals("year within one", "tt0133093", TitleFacts.pickImdb(hits, "The Matrix", 2000))
        assertNull("wrong year", TitleFacts.pickImdb(hits, "The Matrix", 2010))
        assertEquals("one exact match, no year", "tt0133093", TitleFacts.pickImdb(hits, "The  Matrix!", null))
        assertNull("no partial matches", TitleFacts.pickImdb(hits, "Matrix", null))
        assertEquals("amelie", TitleFacts.norm("Amélie"))
        assertEquals("fast and furious", TitleFacts.norm("Fast & Furious"))
    }

    // H-12: Network stream shortcut.
    @Test fun h12_networkStreamShortcut() {
        val s = NavPrefs.Shortcut.of("network_stream")
        assertEquals(NavPrefs.Shortcut.NETWORK_STREAM, s)
        assertFalse(s!!.newer)
        assertEquals(NavPrefs.Shortcut.entries.size, NavPrefs.Shortcut.entries.map { it.item.testTag }.toSet().size)
        assertFalse(StreamLaunch.take())
        StreamLaunch.openLink()
        assertTrue(StreamLaunch.take())
        assertFalse("one shot", StreamLaunch.take())
    }
}
