/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

/**
 * F-12 (ADR-056): vertical mode in the normal video player.
 *
 * A tall video (9:16 and similar) that does not come from YouTube's Shorts shelf (a phone clip, a
 * vertical episode from an extension, a Telegram video) switches the normal player to vertical
 * mode on a phone: full screen in portrait, cropped to fill the screen, and a swipe up or down in
 * the centre third goes to the next or previous item in the queue. Side drags stay brightness and
 * volume. Back (or the fullscreen button) leaves vertical mode and returns to the normal layout;
 * after that the player does not enter it again by itself for that session.
 * Pure; `F12VerticalModeTest`.
 */
internal object VerticalMode {
    /** Height at least 1.5 x width once shown: 9:16 (1.78) and 9:19.5 count, 3:4 (1.33) and square do not. */
    const val MIN_RATIO = 1.5f

    fun isPortrait(width: Int, height: Int, rotationDegrees: Int = 0, pixelRatio: Float = 1f): Boolean {
        if (width <= 0 || height <= 0) return false
        val w0 = width * (if (pixelRatio > 0f) pixelRatio else 1f)
        val h0 = height.toFloat()
        val turned = (rotationDegrees / 90) % 2 != 0
        val w = if (turned) h0 else w0
        val h = if (turned) w0 else h0
        return h >= w * MIN_RATIO
    }

    /** Whether the player should switch to vertical mode on its own now. */
    fun autoEnter(
        enabled: Boolean, tv: Boolean, tablet: Boolean, minimized: Boolean, pip: Boolean,
        portrait: Boolean, userLeft: Boolean, already: Boolean,
    ): Boolean = enabled && !tv && !tablet && !minimized && !pip && portrait && !userLeft && !already

    /** What a centre swipe does in vertical mode. Down with no previous item leaves vertical mode. */
    enum class Swipe { NEXT, PREVIOUS, LEAVE, NONE }

    fun swipeUp(hasNext: Boolean, locked: Boolean): Swipe = if (!locked && hasNext) Swipe.NEXT else Swipe.NONE
    fun swipeDown(hasPrevious: Boolean, locked: Boolean): Swipe = when {
        locked -> Swipe.NONE
        hasPrevious -> Swipe.PREVIOUS
        else -> Swipe.LEAVE
    }
}
