/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.asfakul.mtrevanced.R

/** Manrope (SIL OFL 1.1), bundled as static instances so text renders identically offline. */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

/** Unbounded (SIL OFL 1.1) — wide, geometric display face for titles and hero text. */
val Unbounded = FontFamily(
    Font(R.font.unbounded_500, FontWeight.Medium),
    Font(R.font.unbounded_600, FontWeight.SemiBold),
    Font(R.font.unbounded_700, FontWeight.Bold),
    Font(R.font.unbounded_800, FontWeight.ExtraBold),
)

private fun d(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = Unbounded, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Display style for hero titles ("For You", artist names, big numbers). */
val DisplayStyle = d(30, 38, FontWeight.Bold, -0.03f)

private fun s(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Overline style used for "NOW PLAYING", "CONTINUE LISTENING", section eyebrows. */
val OverlineStyle = s(11, 16, FontWeight.Bold, 0.14f)

val Typography = Typography(
    displaySmall = d(30, 40, FontWeight.Bold, -0.03f),
    headlineLarge = d(26, 34, FontWeight.Bold, -0.03f),
    headlineMedium = d(23, 31, FontWeight.Bold, -0.03f),
    headlineSmall = d(18, 26, FontWeight.SemiBold, -0.02f),
    titleLarge = d(16, 23, FontWeight.SemiBold, -0.02f),
    titleMedium = s(15, 21, FontWeight.Bold),
    titleSmall = s(13, 19, FontWeight.Bold),
    bodyLarge = s(15, 22, FontWeight.Medium),
    bodyMedium = s(13, 19, FontWeight.Medium),
    bodySmall = s(12, 17, FontWeight.Medium),
    labelLarge = s(14, 20, FontWeight.Bold),
    labelMedium = s(12, 17, FontWeight.Bold, 0.02f),
    labelSmall = s(11, 16, FontWeight.Bold, 0.06f),
)
