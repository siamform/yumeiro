/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleTrackerTest {
    private fun play(t: ScrobbleTracker, key: String?, seconds: Int, durMs: Long, startPos: Long = 0, artist: String = "Artist", playing: Boolean = true, startNow: Long = 0): List<ScrobbleTracker.Event> {
        val out = mutableListOf<ScrobbleTracker.Event>()
        for (i in 0..seconds) out += t.feed(key, artist, "Song", "Album", playing, startPos + i * 1000L, durMs, startNow + i * 1000L)
        return out
    }

    @Test fun nowPlayingOnceThenScrobbleAtHalf() {
        val t = ScrobbleTracker()
        val ev = play(t, "1:a", 100, 180_000)
        assertEquals(1, ev.count { it is ScrobbleTracker.Event.NowPlaying })
        assertEquals(1, ev.count { it is ScrobbleTracker.Event.Scrobble })
        val s = ev.filterIsInstance<ScrobbleTracker.Event.Scrobble>().single().play
        assertEquals("Artist", s.artist); assertEquals(180, s.durationSec)
    }

    @Test fun notBeforeHalf() {
        assertTrue(play(ScrobbleTracker(), "1:a", 80, 180_000).none { it is ScrobbleTracker.Event.Scrobble })
    }

    @Test fun longTracksNeedFourMinutes() {
        val t = ScrobbleTracker()
        assertEquals(240_000, t.neededMs(600_000))
        assertTrue(play(t, "1:a", 230, 600_000).none { it is ScrobbleTracker.Event.Scrobble })
        assertTrue(play(t, "1:a", 15, 600_000, startPos = 231_000, startNow = 231_000).any { it is ScrobbleTracker.Event.Scrobble })
    }

    @Test fun shortTracksAndUnknownArtistsNeverScrobble() {
        assertTrue(play(ScrobbleTracker(), "1:a", 29, 29_000).none { it is ScrobbleTracker.Event.Scrobble })
        assertTrue(play(ScrobbleTracker(), "1:a", 200, 180_000, artist = "<unknown>").isEmpty())
    }

    @Test fun pausedTimeDoesNotCount() {
        assertTrue(play(ScrobbleTracker(), "1:a", 200, 180_000, playing = false).isEmpty())
    }

    @Test fun seekingAheadDoesNotCount() {
        val t = ScrobbleTracker()
        // A jump of 100 s in wall-clock between two ticks counts as at most 2 s.
        t.feed("1:a", "A", "S", "", true, 0, 180_000, 0)
        assertTrue(t.feed("1:a", "A", "S", "", true, 170_000, 180_000, 100_000).none { it is ScrobbleTracker.Event.Scrobble })
    }

    @Test fun restartIsANewListen() {
        val t = ScrobbleTracker()
        assertEquals(1, play(t, "1:a", 100, 180_000).count { it is ScrobbleTracker.Event.Scrobble })
        assertEquals(1, play(t, "1:a", 100, 180_000, startNow = 200_000).count { it is ScrobbleTracker.Event.Scrobble })
    }

    @Test fun youtubeNamesAreCleaned() {
        val t = ScrobbleTracker()
        val ev = mutableListOf<ScrobbleTracker.Event>()
        for (i in 0..100) ev += t.feed("1:y", "Daft Punk - Topic", "Daft Punk - One More Time (Official Video)", "", true, i * 1000L, 180_000, i * 1000L)
        val p = ev.filterIsInstance<ScrobbleTracker.Event.Scrobble>().single().play
        assertEquals("Daft Punk", p.artist); assertEquals("One More Time", p.track); assertEquals("", p.album)
    }
}
