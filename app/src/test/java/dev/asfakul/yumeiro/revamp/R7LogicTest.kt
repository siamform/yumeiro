/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.revamp

import dev.asfakul.yumeiro.data.model.AudioTrack
import dev.asfakul.yumeiro.online.download.SongDownloads
import dev.asfakul.yumeiro.ui.screens.music.mixDeviceMatches
import dev.asfakul.yumeiro.ui.screens.watch.WatchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7: Mix search, kind-aware watch skeletons and the shared song download request. */
class R7LogicTest {
    private fun track(id: String, title: String, artist: String = "A", album: String = "B") =
        AudioTrack(id = id, title = title, artist = artist, album = album, durationMs = 180_000, uri = "content://$id")

    @Test fun mixSearchFoldsAccentsAndNeedsEveryWord() {
        val all = listOf(track("1", "Pokémon Theme", "Jason"), track("2", "Theme of Love"), track("3", "Other", "Pokemon Band"))
        assertEquals(listOf("1"), mixDeviceMatches(all, "pokemon theme").map { it.id })
        assertEquals(setOf("1", "3"), mixDeviceMatches(all, "POKÉMON").map { it.id }.toSet())
        assertTrue(mixDeviceMatches(all, "   ").isEmpty())
    }

    @Test fun mixSearchPutsTitlePrefixFirstAndDropsDuplicates() {
        val all = listOf(track("a", "Blue moon", "Theme"), track("b", "Theme song"), track("b", "Theme song"))
        assertEquals(listOf("b", "a"), mixDeviceMatches(all, "theme").map { it.id })
    }

    @Test fun watchKindFromExtensionTypes() {
        assertEquals(WatchKind.MOVIE, WatchKind.of("Movie"))
        assertEquals(WatchKind.MOVIE, WatchKind.of("AnimeMovie"))
        assertEquals(WatchKind.SERIES, WatchKind.of("TvSeries"))
        assertEquals(WatchKind.SERIES, WatchKind.of("Anime"))
        assertEquals(WatchKind.SERIES, WatchKind.of("OVA"))
        assertEquals(WatchKind.SERIES, WatchKind.of("AsianDrama"))
        assertEquals(WatchKind.GENERIC, WatchKind.of(null))
        assertEquals(WatchKind.GENERIC, WatchKind.of("Live"))
    }

    @Test fun songDownloadOnlyForYouTubeMusicTracks() {
        assertNull(SongDownloads.fromTrack(track("42", "Local song")))
        val yt = SongDownloads.fromTrack(track("yt:abc123", "Online song", artist = "Singer").copy(albumArtUri = "https://i.ytimg.com/x.jpg"))!!
        assertEquals("abc123", yt.id)
        assertTrue(yt.music)
        assertEquals("https://music.youtube.com/watch?v=abc123", yt.url)
        assertEquals(180L, yt.durationSec)
        SongDownloads.ask(yt.copy(music = false))
        assertTrue(SongDownloads.request.value!!.music)
        SongDownloads.dismiss()
        assertNull(SongDownloads.request.value)
    }
}
