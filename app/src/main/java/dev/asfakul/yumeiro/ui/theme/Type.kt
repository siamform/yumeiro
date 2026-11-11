/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.R

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

// Titles use Manrope too: one calm, readable family across the app. Unbounded stays bundled for
// the Yumeiro wordmark only (see MTTopBar), not for headings.
private fun d(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Display style for hero titles ("For You", artist names, big numbers). */
val DisplayStyle = d(28, 36, FontWeight.ExtraBold, -0.02f)

private fun s(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Overline style used for "NOW PLAYING", "CONTINUE LISTENING", section eyebrows. */
val OverlineStyle = s(12, 16, FontWeight.SemiBold, 0.01f)

val Typography = Typography(
    displaySmall = d(30, 38, FontWeight.ExtraBold, -0.02f),
    headlineLarge = d(26, 34, FontWeight.ExtraBold, -0.02f),
    headlineMedium = d(22, 30, FontWeight.Bold, -0.01f),
    headlineSmall = d(19, 26, FontWeight.Bold, -0.01f),
    titleLarge = d(17, 24, FontWeight.Bold, -0.01f),
    titleMedium = s(15, 21, FontWeight.Bold),
    titleSmall = s(13, 19, FontWeight.Bold),
    bodyLarge = s(15, 22, FontWeight.Medium),
    bodyMedium = s(13, 19, FontWeight.Medium),
    bodySmall = s(12, 17, FontWeight.Medium),
    labelLarge = s(14, 20, FontWeight.Bold),
    labelMedium = s(12, 17, FontWeight.Bold, 0.02f),
    labelSmall = s(11, 16, FontWeight.Bold, 0.06f),
)
