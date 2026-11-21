/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FE-P (docs/FEED-ENGINE-2.1.md, owner order: playing > picked > on screen > background): whether a video or a song
 * is playing now. Feed work in the background lane reads it and steps back to one job at a time while it is true,
 * so the stream gets the network and the CPU. Set from the players' onIsPlayingChanged (main thread, no I/O).
 */
object PlayingNow {
    @Volatile private var video = false
    @Volatile private var music = false
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun video(on: Boolean) { video = on; publish() }
    fun music(on: Boolean) { music = on; publish() }

    /**
     * Background feed jobs allowed at once: 1 while something plays, else [normal]. E4 part 2 ([FeedThrottle], flag
     * e4_throttle): also 1 when the phone is hot or short of memory, and one less when warm.
     */
    fun backgroundLimit(normal: Int): Int {
        val playing = _active.value && dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.FE_PAUSE_WHILE_PLAYING)
        if (!dev.asfakul.yumeiro.FeatureFlags.isOn(dev.asfakul.yumeiro.FeatureFlags.Flag.E4_THROTTLE)) return if (playing) 1 else normal
        return FeedThrottle.limit(normal, playing, dev.asfakul.yumeiro.player.HeatGuard.level.value, FeedThrottle.isMemoryLow())
    }

    private fun publish() { _active.value = video || music }
}
