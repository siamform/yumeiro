/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * F11 (Beta chapter): Yumeiro's own icon pack for the bottom bar. One 24-unit grid, one 1.8-unit
 * rounded line for the outline set, the same silhouettes filled for the selected tab, so the four
 * tabs read as a family at the bar's smaller 22 dp size (the stock Material set mixed a sharp house,
 * a single note, a film stack and a music library). Drawn for Yumeiro; tinted by Icon.
 */
object NavIcons {
    private const val LINE = 1.8f

    private fun icon(name: String, vararg parts: Pair<String, Boolean>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            parts.forEach { (d, filled) ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    pathFillType = PathFillType.EvenOdd,
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = LINE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private const val HOUSE = "M4.5 10.2 L12 4 L19.5 10.2 V18.5 A2 2 0 0 1 17.5 20.5 H14.5 V15.5 A2.5 2.5 0 0 0 9.5 15.5 V20.5 H6.5 A2 2 0 0 1 4.5 18.5 Z"
    private const val NOTE_HEAD = "M5 17 A3 3 0 1 0 11 17 A3 3 0 1 0 5 17 Z"
    private const val NOTE_STEM = "M11 17 V5.6 L19 3.8 V14.5"
    private const val NOTE_HEAD2 = "M13 14.5 A3 3 0 1 0 19 14.5 A3 3 0 1 0 13 14.5 Z"
    private const val SCREEN = "M3.5 7.5 A3 3 0 0 1 6.5 4.5 H17.5 A3 3 0 0 1 20.5 7.5 V16.5 A3 3 0 0 1 17.5 19.5 H6.5 A3 3 0 0 1 3.5 16.5 Z"
    private const val PLAY = "M10.2 9.2 V14.8 L15 12 Z"
    /** [SCREEN] grown by half a line on every side (2.6..21.4 x 3.6..20.4, corner 3.9). */
    internal const val SCREEN_SOLID = "M2.6 7.5 A3.9 3.9 0 0 1 6.5 3.6 H17.5 A3.9 3.9 0 0 1 21.4 7.5 V16.5 A3.9 3.9 0 0 1 17.5 20.4 H6.5 A3.9 3.9 0 0 1 2.6 16.5 Z"
    /** The play triangle as the outline draws it (its 1.8 line included), so both states share one glyph. */
    internal const val PLAY_HOLE = "M9.5 8.2 V15.8 L16 12 Z"
    private const val SHELF_A = "M4.5 5 V19.5"
    private const val SHELF_B = "M8.8 5 V19.5"
    private const val BOOK = "M12.6 6.2 L16.2 5.2 L19.8 18.6 L16.2 19.6 Z"

    val HomeOutline = icon("yu_home", HOUSE to false)
    val HomeFilled = icon("yu_home_filled", HOUSE to true)
    val MusicOutline = icon("yu_music", NOTE_HEAD to false, NOTE_HEAD2 to false, NOTE_STEM to false)
    val MusicFilled = icon("yu_music_filled", NOTE_HEAD to true, NOTE_HEAD2 to true, NOTE_STEM to false)
    val VideosOutline = icon("yu_videos", SCREEN to false, PLAY to true)
    /**
     * Filled screen with the play triangle cut out (even-odd, one path). B11: fill only, no stroke.
     * With the 1.8 stroke the line ran round the inside of the cut-out too and closed it to a dot,
     * so the lit Videos tab showed a plain brown block (device shots gb11_a_nav_tab_videos, run
     * 46066256387). The screen is drawn half a line wider to match the outline's size.
     */
    val VideosFilled: ImageVector = ImageVector.Builder("yu_videos_filled", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = PathParser().parsePathString("$SCREEN_SOLID $PLAY_HOLE").toNodes(),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        )
    }.build()
    val LibraryOutline = icon("yu_library", SHELF_A to false, SHELF_B to false, BOOK to false)
    val LibraryFilled = icon("yu_library_filled", SHELF_A to false, SHELF_B to false, BOOK to true)
}
