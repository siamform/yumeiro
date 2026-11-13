/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How subtitles look (text size, colour, outline / box, bold, height above the bottom).
 *
 * The default follows Android's own caption settings (Settings → Accessibility → Captions), which is
 * exactly how the player looked before this existed. Picking any option switches to Yumeiro's style.
 * ASS / SSA files drawn by libass keep their own styling.
 */
data class SubtitleStyle(
    /** Use Android's caption settings (the previous behaviour). */
    val followSystem: Boolean = true,
    /** Text size multiplier: 0.75 – 2.0. */
    val size: Float = 1f,
    val color: TextColor = TextColor.WHITE,
    val edge: Edge = Edge.OUTLINE,
    val bold: Boolean = false,
    /** Extra height above the bottom edge, as a fraction of the picture (0 – 0.30). */
    val lift: Float = 0f,
    /** Keep colours / positions / sizes that come inside SRT / VTT / TTML files. */
    val keepFileStyling: Boolean = true,
) {
    enum class TextColor(val label: String, val argb: Int) {
        WHITE("White", Color.WHITE),
        YELLOW("Yellow", 0xFFFFE14D.toInt()),
        CYAN("Cyan", 0xFF7DE8FF.toInt()),
        GREEN("Green", 0xFF8CF29A.toInt()),
        PINK("Pink", 0xFFFFA8D6.toInt()),
    }
    enum class Edge(val label: String) { OUTLINE("Outline"), SHADOW("Shadow"), BOX("Box"), NONE("None") }

    companion object {
        val SIZES = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        const val MAX_LIFT = 0.30f
    }
}

object SubtitleStyleSettings {
    private const val PREFS = "mt_subtitle_style"
    private val _style = MutableStateFlow(SubtitleStyle())
    val style: StateFlow<SubtitleStyle> = _style.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _style.value = sanitize(
            SubtitleStyle(
                followSystem = p.getBoolean("system", true),
                size = p.getFloat("size", 1f),
                color = SubtitleStyle.TextColor.entries.firstOrNull { it.name == p.getString("color", null) } ?: SubtitleStyle.TextColor.WHITE,
                edge = SubtitleStyle.Edge.entries.firstOrNull { it.name == p.getString("edge", null) } ?: SubtitleStyle.Edge.OUTLINE,
                bold = p.getBoolean("bold", false),
                lift = p.getFloat("lift", 0f),
                keepFileStyling = p.getBoolean("keep_file", true),
            )
        )
        loaded = true
    }

    fun set(context: Context, value: SubtitleStyle) {
        val v = sanitize(value)
        _style.value = v
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("system", v.followSystem).putFloat("size", v.size).putString("color", v.color.name)
            .putString("edge", v.edge.name).putBoolean("bold", v.bold).putFloat("lift", v.lift)
            .putBoolean("keep_file", v.keepFileStyling).apply()
    }

    /** Any change made in the style editor switches off "follow Android". */
    fun customise(context: Context, change: (SubtitleStyle) -> SubtitleStyle) =
        set(context, change(_style.value).copy(followSystem = false))

    fun reset(context: Context) = set(context, SubtitleStyle())

    /** Clamps values read from disk or the UI (corrupt prefs never break playback). */
    fun sanitize(s: SubtitleStyle): SubtitleStyle = s.copy(
        size = if (s.size.isFinite()) s.size.coerceIn(0.75f, 2f) else 1f,
        lift = if (s.lift.isFinite()) s.lift.coerceIn(0f, SubtitleStyle.MAX_LIFT) else 0f,
    )

    fun captionStyle(s: SubtitleStyle): CaptionStyleCompat {
        val bg = if (s.edge == SubtitleStyle.Edge.BOX) 0xB3000000.toInt() else Color.TRANSPARENT
        val edgeType = when (s.edge) {
            SubtitleStyle.Edge.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            SubtitleStyle.Edge.SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            SubtitleStyle.Edge.BOX, SubtitleStyle.Edge.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
        }
        return CaptionStyleCompat(s.color.argb, bg, Color.TRANSPARENT, edgeType, Color.BLACK,
            if (s.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
    }

    /** Space kept between the captions and the top of the seek bar, as a fraction of the picture. */
    const val CONTROLS_GAP = 0.02f
    /** Captions never climb above the middle of the picture, even on a very short docked player. */
    const val MAX_BOTTOM_PADDING = 0.6f

    /**
     * Bottom padding of the captions (fraction of the subtitle view's height).
     *
     * [subtitleTopPx] / [subtitleHeightPx] place the subtitle view in window pixels; [obstructionTopPx]
     * is the window y of the top of the visible player controls (seek bar row), or null while they are
     * hidden. While the controls show, the captions are lifted just above them (YouTube does the same);
     * when they hide, the captions drop back to the user's height. Pure, so it is unit-tested.
     */
    fun bottomPaddingFraction(s: SubtitleStyle, subtitleTopPx: Int, subtitleHeightPx: Int, obstructionTopPx: Int?): Float {
        val base = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION + if (s.followSystem) 0f else s.lift
        if (obstructionTopPx == null || subtitleHeightPx <= 0) return base
        val overlapPx = subtitleTopPx + subtitleHeightPx - obstructionTopPx
        if (overlapPx <= 0) return base
        val needed = overlapPx.toFloat() / subtitleHeightPx + CONTROLS_GAP
        return maxOf(base, needed).coerceAtMost(MAX_BOTTOM_PADDING)
    }

    /**
     * Applies [s] to the player's subtitle view. Cheap; safe to call on every recomposition.
     * [obstructionTopPx]: window y of the top of the visible controls, null while they are hidden.
     */
    fun applyTo(view: PlayerView, s: SubtitleStyle, obstructionTopPx: Int? = null) {
        val sv = view.subtitleView ?: return
        val loc = IntArray(2).also { sv.getLocationInWindow(it) }
        val padding = bottomPaddingFraction(s, loc[1], sv.height, obstructionTopPx)
        val key = s to padding
        if (view.getTag(dev.asfakul.yumeiro.R.id.subtitle_style_tag) == key) return
        view.setTag(dev.asfakul.yumeiro.R.id.subtitle_style_tag, key)
        if (s.followSystem) {
            sv.setUserDefaultStyle()
            sv.setUserDefaultTextSize()
            sv.setBottomPaddingFraction(padding)
            sv.setApplyEmbeddedStyles(true)
            sv.setApplyEmbeddedFontSizes(true)
            return
        }
        sv.setStyle(captionStyle(s))
        sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * s.size)
        sv.setBottomPaddingFraction(padding)
        sv.setApplyEmbeddedStyles(s.keepFileStyling)
        // Our size choice wins over sizes inside the file, so "Bigger" always means bigger.
        sv.setApplyEmbeddedFontSizes(false)
    }
}
