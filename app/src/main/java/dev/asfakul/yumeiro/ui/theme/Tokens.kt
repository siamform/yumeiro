/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Tsuki no Michi" (月の道, the moon's path on water) — the design tokens of the redesigned
 * Yumeiro. Colours are NOT defined here: they stay in [Palettes] exactly as before (Tsuki Night,
 * Washi Day, Afterglow, the time-of-day moods, Dynamic and AMOLED). These tokens only describe
 * rhythm, shape, depth and motion so every screen speaks the same language.
 */
@Immutable
object Yume {
    // ---- Spacing (4dp grid) -----------------------------------------------------------------
    val space2 = 2.dp
    val space4 = 4.dp
    val space6 = 6.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
    /** Page side margin used by every tab and sub-page. */
    val gutter = 16.dp
    /** Gap between two sections of a page. */
    val sectionGap = 24.dp

    // ---- Touch -------------------------------------------------------------------------------
    /** Minimum touch target (Android accessibility guidance: 48×48dp). */
    val touch = 48.dp

    // ---- Shape scale ---------------------------------------------------------------------------
    // Fewer, intentional radii instead of the 6–36dp mix the old screens used.
    val radiusXs = 6.dp     // badges, track artwork in dense lists
    val radiusSm: Dp get() = UiScale.cornerDp(10)    // list artwork, small chips
    val radiusMd: Dp get() = UiScale.cornerDp(16)    // cards, tiles, panels
    val radiusLg: Dp get() = UiScale.cornerDp(22)    // the dock, sheets, hero panels
    val radiusXl: Dp get() = UiScale.cornerDp(28)    // dialogs, Now Playing artwork

    val shapeXs = RoundedCornerShape(radiusXs)
    val shapeSm get() = yuRound(10)
    val shapeMd get() = yuRound(16)
    val shapeLg get() = yuRound(22)
    val shapeXl get() = yuRound(28)
    val shapeSheet get() = RoundedCornerShape(topStart = radiusXl, topEnd = radiusXl)

    // ---- Depth ---------------------------------------------------------------------------------
    // Depth comes from tone (ink → lifted ink) and one gold hairline, not from heavy shadows.
    val elevationDock = 8.dp
    val elevationDialog = 14.dp

    /** Width of the gold "moon path" stroke used for selection and progress. */
    val moonStroke = 3.dp

    // ---- Motion --------------------------------------------------------------------------------
    /** Calm, slightly springy movement for selection indicators and panels. */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    /** Quick response for presses and small state flips. */
    fun <T> quick(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
    val settleOffset: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}
