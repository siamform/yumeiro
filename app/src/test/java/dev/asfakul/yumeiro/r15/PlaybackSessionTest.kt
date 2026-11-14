/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r15

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.player.PlaybackSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

/** R15 (ADR-006): one player per process, fenced leases, grace release. */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34])
class PlaybackSessionTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val built = ArrayList<ExoPlayer>()

    @Before fun setUp() {
        PlaybackSession.releaseNow()
        PlaybackSession.factory = { c, _, _ -> ExoPlayer.Builder(c).setLooper(Looper.getMainLooper()).build().also { built += it } }
    }

    @After fun tearDown() { PlaybackSession.releaseNow() }

    @Test fun nextVideoReusesThePlayerAndFencesTheOldScreen() {
        val a = PlaybackSession.acquire(ctx, "a", "https://x.example/a.mp4")
        var oldEvents = 0
        a.addListener(object : Player.Listener { override fun onTimelineChanged(t: Timeline, reason: Int) { oldEvents++ } })
        a.player.setMediaItem(MediaItem.fromUri("https://x.example/a.mp4")); a.player.playbackParameters = PlaybackParameters(1.5f)
        val before = oldEvents
        val b = PlaybackSession.acquire(ctx, "b", "https://x.example/b.mp4")
        assertSame("same player", a.player, b.player)
        assertEquals(1, built.size)
        assertTrue(a.revoked); assertFalse(b.resumed)
        assertEquals("old screen's listener removed before the reset", before, oldEvents)
        assertEquals("reset to defaults", 1f, b.player.playbackParameters.speed)
        assertEquals(0, b.player.mediaItemCount)
        assertEquals("https://x.example/b.mp4", PlaybackSession.currentUri)
        // The old screen closes after the handover: no effect on the new video.
        PlaybackSession.detach(a)
        shadowOf(Looper.getMainLooper()).idleFor(PlaybackSession.GRACE_MS + 1, TimeUnit.MILLISECONDS)
        assertEquals(1, PlaybackSession.alive)
    }

    @Test fun closeReleasesAfterGraceAndReopenInsideGraceResumes() {
        val a = PlaybackSession.acquire(ctx, "a", "https://x.example/a.mp4")
        a.player.setMediaItem(MediaItem.fromUri("https://x.example/a.mp4"))
        PlaybackSession.detach(a)
        shadowOf(Looper.getMainLooper()).idleFor(PlaybackSession.GRACE_MS / 2, TimeUnit.MILLISECONDS)
        val again = PlaybackSession.acquire(ctx, "a", "https://x.example/a.mp4")
        assertTrue(again.resumed); assertSame(a.player, again.player)
        assertEquals("media kept", 1, again.player.mediaItemCount)
        shadowOf(Looper.getMainLooper()).idleFor(PlaybackSession.GRACE_MS * 2, TimeUnit.MILLISECONDS)
        assertEquals("reopen cancelled the release", 1, PlaybackSession.alive)
        PlaybackSession.detach(again)
        shadowOf(Looper.getMainLooper()).idleFor(PlaybackSession.GRACE_MS + 1, TimeUnit.MILLISECONDS)
        assertEquals(0, PlaybackSession.alive)
    }

    @Test fun localAfterNetworkBuildsAFreshPlayerAndReleasesTheOldFirst() {
        val a = PlaybackSession.acquire(ctx, "a", "https://x.example/a.mp4")
        val b = PlaybackSession.acquire(ctx, "b", "content://media/external/video/1")
        assertNotSame(a.player, b.player)
        assertEquals(2, built.size)
        assertEquals(1, PlaybackSession.alive)
        assertTrue(a.revoked)
    }

    @Test fun frozenPositionBelongsToTheOldVideo() {
        val a = PlaybackSession.acquire(ctx, "a", "https://x.example/a.mp4")
        PlaybackSession.acquire(ctx, "b", "https://x.example/b.mp4")
        assertEquals(0L, a.positionMs)
        // Listeners can't be added to a revoked lease.
        var called = false
        a.addListener(object : Player.Listener { override fun onPlaybackStateChanged(s: Int) { called = true } })
        a.player.setMediaItem(MediaItem.fromUri("https://x.example/b.mp4")); a.player.prepare()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(called)
    }

    @Test fun leaseTimersNeverFireOnTheNextVideoOrAfterClose() {
        val a = PlaybackSession.acquire(ctx, "a", "https://x.example/a.mp4")
        var fired = 0
        a.schedule("retry", 800L) { fired++ }
        a.schedule("retry", 800L) { fired += 10 } // same name replaces the first
        shadowOf(Looper.getMainLooper()).idleFor(801, TimeUnit.MILLISECONDS)
        assertEquals("replaced timer ran once", 10, fired)
        assertFalse(a.isScheduled("retry"))
        a.schedule("retry", 800L) { fired += 100 }
        val b = PlaybackSession.acquire(ctx, "b", "https://x.example/b.mp4")
        shadowOf(Looper.getMainLooper()).idleFor(2_000, TimeUnit.MILLISECONDS)
        assertEquals("revoked lease's retry never touched the next video", 10, fired)
        a.schedule("late", 10L) { fired += 1_000 }
        b.schedule("stable", 500L) { fired += 1 }
        PlaybackSession.detach(b)
        shadowOf(Looper.getMainLooper()).idleFor(1_000, TimeUnit.MILLISECONDS)
        assertEquals("closed screen's timer cancelled; revoked lease can't schedule", 10, fired)
    }
}
