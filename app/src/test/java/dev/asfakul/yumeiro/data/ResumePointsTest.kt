/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.data.repository.OfflineMediaRepository
import dev.asfakul.yumeiro.online.OnlineDetail
import dev.asfakul.yumeiro.online.OnlineEpisode
import dev.asfakul.yumeiro.online.OnlineHistory
import dev.asfakul.yumeiro.online.OnlineKind
import dev.asfakul.yumeiro.security.Privacy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUG-1 (docs/proposals/FEATURE-BUG-1-watch-progress.md). A "new repository" stands for the next
 * app session: it reads the same SharedPreferences again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResumePointsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val unset = Long.MIN_VALUE + 1 // androidx.media3.common.C.TIME_UNSET

    @Before fun clean() = runBlocking { Privacy.setIncognito(ctx, false); OfflineMediaRepository(ctx).clearWatchHistory(); OnlineHistory.clear(ctx) }
    @After fun tidy() = runBlocking { OfflineMediaRepository(ctx).clearWatchHistory(); OnlineHistory.clear(ctx) }

    @Test fun rules() {
        assertEquals(ResumeRules.Write.SKIP, ResumeRules.decide(0, unset))
        assertEquals(ResumeRules.Write.SKIP, ResumeRules.decide(0, 0))
        assertEquals(ResumeRules.Write.SKIP, ResumeRules.decide(0, 600_000))
        assertEquals(ResumeRules.Write.SKIP, ResumeRules.decide(42_000, unset)) // live / unknown
        assertEquals(ResumeRules.Write.SAVE, ResumeRules.decide(60_000, 600_000))
        assertEquals(ResumeRules.Write.CLEAR, ResumeRules.decide(570_000, 600_000))
        assertEquals(ResumeRules.Write.CLEAR, ResumeRules.decide(600_000, 600_000))
    }

    /** D1: a YouTube video opened without a position starts where it was left. */
    @Test fun youTubeOpensAtItsSavedPositionInTheNextSession() = runBlocking {
        OfflineMediaRepository(ctx).updateVideoProgress("yt:abc", 245_000, 900_000)
        val next = OfflineMediaRepository(ctx)
        assertEquals(245_000L, ResumeRules.startPosition("yt:abc", 0L, next::savedVideoPosition))
        // An explicit position wins; online ids never read the device store.
        assertEquals(10_000L, ResumeRules.startPosition("yt:abc", 10_000L, next::savedVideoPosition))
        assertEquals(0L, ResumeRules.startPosition("online:1f:2e", 0L) { 99_000L })
        assertEquals(0L, ResumeRules.startPosition("yt:never", 0L, next::savedVideoPosition))
    }

    /** D2: opening an item and leaving before it loads must not wipe its resume point. */
    @Test fun anUnloadedPlayerDoesNotWipeTheResumePoint() = runBlocking {
        val repo = OfflineMediaRepository(ctx)
        repo.updateVideoProgress("dev:42", 360_000, 1_200_000)
        repo.updateVideoProgress("dev:42", 0, unset) // onDispose of a player that never prepared
        repo.updateVideoProgress("dev:42", 0, 1_200_000) // prepared, not started
        assertEquals(360_000L, OfflineMediaRepository(ctx).savedVideoPosition("dev:42"))
    }

    @Test fun finishingClearsAndSeekingForwardIsKept() = runBlocking {
        val repo = OfflineMediaRepository(ctx)
        repo.updateVideoProgress("dev:7", 100_000, 1_000_000)
        repo.updateVideoProgress("dev:7", 700_000, 1_000_000) // seek saved at once (D3 save point)
        assertEquals(700_000L, OfflineMediaRepository(ctx).savedVideoPosition("dev:7"))
        repo.updateVideoProgress("dev:7", 1_000_000, 1_000_000)
        assertNull(OfflineMediaRepository(ctx).savedVideoPosition("dev:7"))
    }

    /** D2, Yumix: position 0 (not loaded or not started) never replaces a saved episode position. */
    @Test fun onlineZeroDoesNotReplaceTheSavedPosition() {
        val d = OnlineDetail(
            "Demo", "https://x/t", "T", OnlineKind.SERIES, "TvSeries", null, null, emptyMap(), null, 2020,
            emptyList(), null, null, null, null, emptyMap(), emptyMap(), emptyList(),
        )
        val e = OnlineEpisode("ep-1", null, 1, 1, null, null, 24)
        OnlineHistory.record(ctx, d, "", e, 400_000, 1_400_000)
        OnlineHistory.record(ctx, d, "", e, 0, 1_400_000)
        assertEquals(400_000L, OnlineHistory.progressOf("Demo", "ep-1")?.positionMs)
    }
}
