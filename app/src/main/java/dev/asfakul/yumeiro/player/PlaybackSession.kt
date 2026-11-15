/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import dev.asfakul.yumeiro.stream.StreamMediaFactory

/**
 * R15 (ADR-006): the one owner of the video player, for the whole process.
 *
 * Before, every video created its own `ExoPlayer` inside the player composable
 * (`remember(video.id)`), and the old one was released only after the new one had been built.
 * Opening the next episode on a 2 GB phone therefore held two players, two sets of codecs and two
 * media buffers at the same time. Now:
 *
 * - **One player at a time.** [acquire] hands out a [Lease]. The next video reuses the same player
 *   (stopped and reset) when it can, or releases it before a new one is created.
 * - **Leases are fenced.** Listeners go through the lease. When the player moves on to another
 *   video, the old lease is revoked: its listeners are removed first, so the old screen never
 *   reacts to the new video's events, and its last position and duration are frozen for its final
 *   progress save.
 * - **Short grace on close.** When the screen goes away, the player pauses and is released after
 *   [GRACE_MS], unless the same video is opened again (then it resumes where it was, with no reload).
 *
 * Main thread only, like ExoPlayer itself.
 */
@OptIn(UnstableApi::class)
object PlaybackSession {
    const val GRACE_MS = 3_000L
    /** Stop first, release this much later (ST-7: release() then has little left to wait for). */
    const val STOP_TO_RELEASE_MS = 1_000L

    class Lease internal constructor(val key: String, val player: ExoPlayer, val resumed: Boolean) {
        private val listeners = ArrayList<Player.Listener>()
        private val analytics = ArrayList<AnalyticsListener>()
        var revoked = false; private set
        internal var detached = false
        private var frozenPos = C.TIME_UNSET
        private var frozenDur = C.TIME_UNSET

        fun addListener(l: Player.Listener) { if (revoked) return; listeners += l; player.addListener(l) }
        fun removeListener(l: Player.Listener) { listeners -= l; player.removeListener(l) }
        fun addAnalyticsListener(l: AnalyticsListener) { if (revoked) return; analytics += l; player.addAnalyticsListener(l) }
        fun removeAnalyticsListener(l: AnalyticsListener) { analytics -= l; player.removeAnalyticsListener(l) }

        /** Position of this lease's video; frozen once the player moved on to another one. */
        val positionMs: Long get() = if (revoked) frozenPos.coerceAtLeast(0L) else player.currentPosition
        val durationMs: Long get() = if (revoked) frozenDur else player.duration

        // ADR-006 step 3b: recovery timers belong to the lease, not to the screen. A retry
        // scheduled for this video can never fire on the next video, because revoke() and
        // detach() cancel every timer before the player moves on or pauses.
        private val timers = HashMap<String, Runnable>()

        /** Runs [block] after [delayMs] on the main thread, replacing any timer with the same [name]. */
        fun schedule(name: String, delayMs: Long, block: () -> Unit) {
            if (revoked) return
            cancel(name)
            val r = object : Runnable {
                override fun run() {
                    if (timers[name] === this) timers.remove(name)
                    if (!revoked && !detached) block()
                }
            }
            timers[name] = r
            main.postDelayed(r, delayMs)
        }

        fun cancel(name: String) { timers.remove(name)?.let { main.removeCallbacks(it) } }
        fun isScheduled(name: String): Boolean = name in timers
        internal fun cancelTimers() { timers.values.forEach { main.removeCallbacks(it) }; timers.clear() }

        internal fun revoke() {
            if (revoked) return
            cancelTimers()
            frozenPos = runCatching { player.currentPosition }.getOrDefault(0L)
            frozenDur = runCatching { player.duration }.getOrDefault(C.TIME_UNSET)
            listeners.forEach { runCatching { player.removeListener(it) } }
            analytics.forEach { runCatching { player.removeAnalyticsListener(it) } }
            listeners.clear(); analytics.clear()
            revoked = true
        }
    }

    private var current: Lease? = null
    private var network = false
    private var defaults: TrackSelectionParameters? = null
    internal val main by lazy { Handler(Looper.getMainLooper()) }
    private val releaseLater = Runnable { releaseInTwoSteps() }

    /** Time the last [ExoPlayer.release] call held the main thread (diagnostics, ST-7 device runs). */
    @Volatile var lastReleaseMs = -1L; private set

    /**
     * ST-7: [ExoPlayer.release] runs on the main thread (the player's looper) and waits for the
     * playback thread to free the decoders, which held main for hundreds of ms in stall traces.
     * After the grace, the player is first stopped, which frees the decoders on the playback thread
     * without blocking main; release() follows [STOP_TO_RELEASE_MS] later and has little left to do.
     */
    @MainThread
    private fun releaseInTwoSteps() {
        val cur = current ?: return
        current = null
        cur.revoke()
        currentUri = null
        val p = cur.player
        runCatching { p.stop() }
        main.postDelayed({ timedRelease(p) }, STOP_TO_RELEASE_MS)
    }

    private fun timedRelease(p: ExoPlayer) {
        val t0 = android.os.SystemClock.uptimeMillis()
        runCatching { p.release() }
        lastReleaseMs = android.os.SystemClock.uptimeMillis() - t0
        android.util.Log.i("YuSession", "player release held main ${lastReleaseMs} ms")
    }

    /** Key (video uri) the player is serving now: its headers and DRM are looked up with it. */
    @Volatile var currentUri: String? = null
        private set

    /** Counters for the device test and the diagnostics overlay. */
    @Volatile var created = 0; private set
    @Volatile var reused = 0; private set
    @Volatile var resumedCount = 0; private set

    @VisibleForTesting
    var factory: (Context, String, () -> String) -> ExoPlayer = { c, uri, now -> StreamMediaFactory.createPlayer(c, uri, now) }

    /** Players alive right now (0 or 1). */
    val alive: Int get() = if (current != null) 1 else 0

    @MainThread
    fun acquire(context: Context, key: String, uri: String): Lease {
        main.removeCallbacks(releaseLater)
        val cur = current
        val net = StreamMediaFactory.isNetworkUri(uri)
        if (cur != null && cur.key == key && cur.detached && !cur.revoked) {
            // The same video again, before the grace ran out: keep its media and position.
            cur.revoke()
            currentUri = uri
            resumedCount++
            return Lease(key, cur.player, resumed = true).also { current = it }
        }
        if (cur != null) {
            cur.revoke()
            val p = cur.player
            if (net == network && !StreamMediaFactory.usedEffects(p) && runCatching { reset(p) }.isSuccess) {
                currentUri = uri
                reused++
                return Lease(key, p, resumed = false).also { current = it }
            }
            runCatching { p.release() }
            current = null
        }
        currentUri = uri
        val p = factory(context.applicationContext, uri) { currentUri ?: uri }
        defaults = p.trackSelectionParameters
        network = net
        created++
        return Lease(key, p, resumed = false).also { current = it }
    }

    /** The screen that held [lease] is gone: pause now, release after [GRACE_MS] unless reopened. */
    @MainThread
    fun detach(lease: Lease) {
        if (lease.revoked || current !== lease) return
        lease.detached = true
        lease.cancelTimers()
        runCatching { lease.player.pause() }
        main.removeCallbacks(releaseLater)
        main.postDelayed(releaseLater, GRACE_MS)
    }

    /** Releases the player now (memory pressure, app exit, tests). */
    @MainThread
    fun releaseNow() {
        main.removeCallbacks(releaseLater)
        val cur = current ?: return
        current = null
        cur.revoke()
        timedRelease(cur.player)
        currentUri = null
    }

    /** True while the session's player is playing (tests, diagnostics). */
    @MainThread
    fun isPlaying(): Boolean = current?.player?.isPlaying == true

    /** Device tests read the live player (main thread only). */
    internal val playerForTest: ExoPlayer? get() = current?.player

    /** Memory pressure: drop a closed player now instead of waiting for the grace. */
    @MainThread
    fun releaseIfDetached() { if (current?.detached == true) releaseNow() }

    /** Back to a just-built player: no media, no overrides, normal speed and volume. */
    private fun reset(p: ExoPlayer) {
        p.stop()
        p.clearMediaItems()
        p.playWhenReady = false
        p.playbackParameters = PlaybackParameters.DEFAULT
        p.volume = 1f
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.shuffleModeEnabled = false
        p.skipSilenceEnabled = false
        defaults?.let { p.trackSelectionParameters = it }
    }
}
