/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import dev.asfakul.yumeiro.stream.StarvationSafeLoadControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R15, api33 device: an audio switch deadlocked because the byte budget (filled by buffered video)
 * refused to load the new audio track. The real DefaultLoadControl is used with a full budget.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class StarvationLoadControlTest {
    private val player = PlayerId.UNSET

    private val timeline = androidx.media3.exoplayer.source.SinglePeriodTimeline(
        600_000_000L, true, false, false, null, androidx.media3.common.MediaItem.fromUri("https://example.org/v.m3u8"),
    )

    private fun params(bufferedUs: Long) = LoadControl.Parameters(
        player, timeline, MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)), 0L, bufferedUs, 1f, true, true, androidx.media3.common.C.TIME_UNSET, androidx.media3.common.C.TIME_UNSET,
    )

    private fun fullBudget(): Pair<DefaultLoadControl, StarvationSafeLoadControl> {
        val inner = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 50_000, 1_500, 3_000)
            .setTargetBufferBytes(256 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        inner.onPrepared(player)
        val alloc = inner.getAllocator(player)
        repeat(16) { alloc.allocate() } // 16 x 64 KiB = 1 MiB buffered: over the 256 KiB budget
        return inner to StarvationSafeLoadControl(inner)
    }

    @Test fun fullBudgetAndStarvingTrackStillLoads() {
        val (inner, safe) = fullBudget()
        assertFalse("the plain control refuses (the old deadlock)", inner.shouldContinueLoading(params(300_000)))
        assertTrue("a starving track may load", safe.shouldContinueLoading(params(300_000)))
    }

    @Test fun fullBudgetWithEnoughMediaStaysCapped() {
        val (_, safe) = fullBudget()
        assertFalse("byte cap still applies once playable", safe.shouldContinueLoading(params(8_000_000)))
    }

    @Test fun everythingElseIsTheInnerControl() {
        val (inner, safe) = fullBudget()
        assertEquals(inner.retainBackBufferFromKeyframe(player), safe.retainBackBufferFromKeyframe(player))
        assertEquals(inner.getBackBufferDurationUs(player), safe.getBackBufferDurationUs(player))
        // Track selection reaches the inner control (it sizes its budget there).
        safe.onTracksSelected(params(0), androidx.media3.exoplayer.source.TrackGroupArray.EMPTY, arrayOf())
        safe.onStopped(player); safe.onReleased(player)
        assertTrue(StarvationSafeLoadControl.starving(3_000_000, 2f))
        assertFalse(StarvationSafeLoadControl.starving(3_000_000, 1f))
    }
}
