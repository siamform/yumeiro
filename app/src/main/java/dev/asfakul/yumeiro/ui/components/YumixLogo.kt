/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Yumix mark: an open "mix" ring (a stream of sources coming together) around a play
 * triangle, with a small four-point spark — the 彩 colour burst — sitting in the ring's gap.
 * Drawn as a single-colour [ImageVector] so it tints like any icon (dock, tiles, menus).
 */
object YumixLogo {
    val Icon: ImageVector by lazy {
        ImageVector.Builder(
            name = "YumixLogo",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Ring: outer r=10, inner r=7.8, open between -80° and -10° (top right).
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
                moveTo(21.85f, 10.26f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 13.74f, y1 = 2.15f)
                lineTo(13.35f, 4.32f)
                arcTo(7.8f, 7.8f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 19.68f, y1 = 10.65f)
                close()
            }
            // Play triangle with softened corners.
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.2f, 8.1f)
                quadTo(9.4f, 7.7f, 9.4f, 8.6f)
                lineTo(9.4f, 15.4f)
                quadTo(9.4f, 16.3f, 10.2f, 15.9f)
                lineTo(15.9f, 12.6f)
                quadTo(16.6f, 12f, 15.9f, 11.4f)
                close()
            }
            // Spark in the gap.
            path(fill = SolidColor(Color.Black)) {
                moveTo(19.5f, 1.2f)
                quadTo(19.9f, 4.1f, 22.8f, 4.5f)
                quadTo(19.9f, 4.9f, 19.5f, 7.8f)
                quadTo(19.1f, 4.9f, 16.2f, 4.5f)
                quadTo(19.1f, 4.1f, 19.5f, 1.2f)
                close()
            }
        }.build()
    }

    /**
     * Round 8: solid version for the active dock tab (Snapchat-style "the glyph fills with
     * colour"). A filled disc with the play triangle cut out, plus the spark.
     */
    val Filled: ImageVector by lazy {
        ImageVector.Builder(
            name = "YumixLogoFilled",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // Disc: centre (11, 13), r = 8.8.
                moveTo(19.8f, 13f)
                arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 2.2f, y1 = 13f)
                arcTo(8.8f, 8.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 19.8f, y1 = 13f)
                close()
                // Play triangle hole (same shape as the outline mark, centred in the disc).
                moveTo(9.2f, 9.1f)
                quadTo(8.4f, 8.7f, 8.4f, 9.6f)
                lineTo(8.4f, 16.4f)
                quadTo(8.4f, 17.3f, 9.2f, 16.9f)
                lineTo(14.9f, 13.6f)
                quadTo(15.6f, 13f, 14.9f, 12.4f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 0.6f)
                quadTo(20.4f, 3.2f, 23f, 3.6f)
                quadTo(20.4f, 4f, 20f, 6.6f)
                quadTo(19.6f, 4f, 17f, 3.6f)
                quadTo(19.6f, 3.2f, 20f, 0.6f)
                close()
            }
        }.build()
    }
}
