/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

/** When the video player should stop the screen from sleeping (pure, unit-tested). */
object ScreenWake {
    /**
     * On while frames are moving, or while buffering with the user still wanting playback
     * (so a rebuffer never lets the phone dim). Off when paused or finished.
     */
    fun shouldKeepOn(isPlaying: Boolean, isBuffering: Boolean, playWhenReady: Boolean, isEnded: Boolean): Boolean =
        !isEnded && (isPlaying || (isBuffering && playWhenReady))
}
