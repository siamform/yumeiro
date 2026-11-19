package dev.asfakul.yumeiro.online

// NF-1 (ledger NF-1, docs/proposals/FEATURE-NF-1-episode-status.md): every episode row says
// Watched, "N min left" (in progress) or Fresh (added since the last visit, worked out on the
// phone only). Fail-before: EpisodeStatus does not exist.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NF1EpisodeStatusTest {
    private fun p(pos: Long, dur: Long) = OnlineHistory.Progress(pos, dur, 0L)

    @Test fun finishedIsWatched() {
        assertEquals(EpisodeStatus.Watched, EpisodeStatus.of(p(23 * 60_000L, 24 * 60_000L), index = 0, seenBefore = null))
    }

    @Test fun startedShowsMinutesLeftRoundedUpAndNeverZero() {
        assertEquals(EpisodeStatus.InProgress(12), EpisodeStatus.of(p(12 * 60_000L, 24 * 60_000L - 30_000L), 0, null))
        val nearEnd = EpisodeStatus.of(p(60_000L, 90_000L), 0, null) // 30 s left of a 1.5 min clip
        assertEquals(EpisodeStatus.InProgress(1), nearEnd)
        assertEquals("12 min left", EpisodeStatus.InProgress(12).label)
    }

    @Test fun freshOnlyForEpisodesAddedSinceTheLastVisit() {
        // Last visit saw 10 episodes: index 10 and later are fresh, earlier ones are not.
        assertEquals(EpisodeStatus.Fresh, EpisodeStatus.of(null, index = 10, seenBefore = 10))
        assertNull(EpisodeStatus.of(null, index = 9, seenBefore = 10))
        // First visit: nothing is fresh (everything would be).
        assertNull(EpisodeStatus.of(null, index = 3, seenBefore = null))
    }

    @Test fun progressBeatsFresh() {
        assertEquals(EpisodeStatus.InProgress(10), EpisodeStatus.of(p(10 * 60_000L, 20 * 60_000L), index = 12, seenBefore = 10))
    }

    @Test fun aBlipOfPlaybackIsNotInProgress() {
        assertNull(EpisodeStatus.of(p(2_000L, 24 * 60_000L), 0, null))
        assertNull(EpisodeStatus.of(p(5_000L, 0L), 0, null))
    }

    @Test fun seenCountOnlyGrowsSoAShortListFromAFlakySourceDoesNotResetFresh() {
        assertEquals(12, EpisodeStatus.nextSeen(previous = 12, now = 5))
        assertEquals(14, EpisodeStatus.nextSeen(previous = 12, now = 14))
        assertEquals(7, EpisodeStatus.nextSeen(previous = null, now = 7))
    }
}
