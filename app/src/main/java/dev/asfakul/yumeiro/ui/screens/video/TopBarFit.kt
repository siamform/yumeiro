/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.video

import dev.asfakul.yumeiro.player.VideoPrefs.Control

/**
 * R17 (G3 proposal "More button"): which player top-bar buttons fit on the row, and which go into
 * the More menu.
 *
 * Before, the row scrolled sideways with no hint: on a portrait phone Speed, Aspect, PiP, Info and
 * Lock sat off-screen and most people never found them. Now the row keeps the user's order (Settings
 * → Player controls), shows as many buttons as fit, and always ends with a More button. The More
 * menu lists, with labels, every button that did not fit, plus the video sleep timer.
 *
 * Widths are in dp and match the composables: an icon button takes the 48 dp touch target, the
 * subtitle control is two buttons (CC on/off and the track list), and the quality pill grows with
 * its text. Pure, so it is unit-tested (`TopBarFitTest`).
 */
internal object TopBarFit {
    const val BUTTON_DP = 48f
    const val GAP_DP = 2f

    data class Split(val inline: List<Control>, val overflow: List<Control>)

    fun widthOf(c: Control, qualityLabel: String?): Float = when (c) {
        Control.SUBTITLES -> 2 * BUTTON_DP + GAP_DP
        // labelMedium bold: about 7.5 dp per character, 16 dp padding, never under the touch target.
        Control.QUALITY -> maxOf(BUTTON_DP, 16f + 7.5f * (qualityLabel?.length ?: 7))
        else -> BUTTON_DP
    }

    /**
     * [visible] is the user's order with hidden and unavailable buttons already removed. A button
     * that does not fit goes to the menu, and so does every button after it, so the row reads in the
     * user's order and nothing jumps around when the quality label changes length.
     */
    fun split(visible: List<Control>, availableDp: Float, qualityLabel: String?): Split {
        var left = availableDp - BUTTON_DP // the More button is always there
        val inline = ArrayList<Control>(visible.size)
        for ((i, c) in visible.withIndex()) {
            val w = widthOf(c, qualityLabel) + GAP_DP
            if (w > left) return Split(inline, visible.subList(i, visible.size).toList())
            left -= w
            inline += c
        }
        return Split(inline, emptyList())
    }
}
