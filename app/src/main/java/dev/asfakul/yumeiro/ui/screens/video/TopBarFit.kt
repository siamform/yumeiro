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
    /** OL-7: the title keeps at least this much; the buttons may use the rest of the bar. */
    const val TITLE_MIN_DP = 120f
    const val TITLE_MIN_COMPACT_DP = 96f

    /**
     * OL-7: room for the buttons in a bar [totalDp] wide: minus the side padding, Back, the two 6 dp
     * spacers and the title's minimum. Before, the buttons got a fixed 1.4 : 1 share whether they
     * used it or not, so a gap sat left of Quality / Audio / More and the title was cut early.
     */
    fun buttonsWidth(totalDp: Float, compact: Boolean, edgeDp: Float = if (compact) 4f else 12f): Float {
        val pad = 2 * edgeDp
        val back = 48f // Back is an IconButton: the 48 dp touch target wins over its 40/44 dp size
        val title = if (compact) TITLE_MIN_COMPACT_DP else TITLE_MIN_DP
        return (totalDp - pad - back - 12f - title).coerceAtLeast(BUTTON_DP)
    }

    /**
     * OWN-19 (owner 2026-11-22: "in full screen there are lots of buttons, it doesn't look good; show only the
     * necessary ones and move the others into the 3-dot menu"): at most this many controls stay on the bar, the first
     * ones in the user's order (Settings -> Player controls; default Quality, Audio, Subtitles). YouTube, Netflix
     * and MX Player keep two to four controls on the full-screen bar and put the rest behind one menu.
     */
    const val ESSENTIAL_MAX = 3

    /** OWN-19: [split] with only the first [max] inline; the rest go to the front of the menu, in order. Pure. */
    fun essentials(split: Split, max: Int = ESSENTIAL_MAX): Split =
        if (split.inline.size <= max) split else Split(split.inline.take(max), split.inline.drop(max) + split.overflow)

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
