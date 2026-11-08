/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

/**
 * Detects the "audio keeps going but the picture is black" state: the playback clock advances
 * while the video decoder stops putting out frames (spliced ad segments in another codec, a lost
 * surface after PiP / rotation, a wedged hardware decoder). Pure logic, fed once a second.
 *
 * First it asks for a [Action.NUDGE] (re-attach the surface + seek in place, which flushes the
 * decoder and fixes most cases); if frames still don't come back it reports [Action.FAIL] so the
 * player can switch to the next source instead of showing black forever.
 */
class BlackFrameWatchdog(
    private val nudgeAfterMs: Long = 4_000,
    private val failAfterMs: Long = 7_000,
) {
    enum class Action { NONE, NUDGE, FAIL }

    private var lastFrames = -1L
    private var frozenSincePos = -1L
    private var lastPos = -1L
    private var nudged = false
    private var failed = false

    fun reset() { lastFrames = -1; frozenSincePos = -1; lastPos = -1; nudged = false; failed = false }

    /**
     * @param positionMs current playback position
     * @param renderedFrames total frames the video renderer has output so far
     * @param hasVideo whether a video track is selected (audio-only content is ignored)
     * @param playing whether the clock is running (paused / buffering never counts)
     */
    fun observe(positionMs: Long, renderedFrames: Long, hasVideo: Boolean, playing: Boolean): Action {
        if (!hasVideo || !playing) { lastPos = positionMs; frozenSincePos = -1; lastFrames = renderedFrames; return Action.NONE }
        // A seek / new item jumps the clock: start over.
        if (lastPos >= 0 && (positionMs < lastPos || positionMs - lastPos > 5_000)) { frozenSincePos = -1; lastFrames = renderedFrames }
        lastPos = positionMs
        if (renderedFrames != lastFrames) {
            lastFrames = renderedFrames
            frozenSincePos = -1
            nudged = false
            failed = false
            return Action.NONE
        }
        if (frozenSincePos < 0) { frozenSincePos = positionMs; return Action.NONE }
        val frozenFor = positionMs - frozenSincePos
        return when {
            !nudged && frozenFor >= nudgeAfterMs -> { nudged = true; Action.NUDGE }
            nudged && !failed && frozenFor >= nudgeAfterMs + failAfterMs -> { failed = true; Action.FAIL }
            else -> Action.NONE
        }
    }
}
