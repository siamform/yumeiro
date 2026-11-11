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

// "夢色 editorial" type system (R5). Unbounded — the wide wordmark face — sets page titles and
// section headings so Yumeiro reads like a magazine masthead; Manrope carries everything you
// read. The scale is deliberately compact (phone-first, 8dp rhythm): 26 / 22 / 18 / 16 / 14 / 13 / 12.
private fun u(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = Unbounded, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private fun d(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Display style for hero titles ("For You", artist names, big numbers). */
val DisplayStyle = d(26, 32, FontWeight.ExtraBold, -0.02f)

private fun s(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Overline style used for "NOW PLAYING", "CONTINUE LISTENING", section eyebrows. */
val OverlineStyle = s(11, 15, FontWeight.Bold, 0.08f)

/** Section heading on Home and tab pages (Unbounded, 16sp). */
val SectionTitleStyle = u(16, 22, FontWeight.SemiBold, -0.01f)

/** Page title in the top bar (Unbounded). */
val PageTitleStyle = u(22, 28, FontWeight.SemiBold, -0.02f)
val PageTitleCompactStyle = u(19, 24, FontWeight.SemiBold, -0.02f)

val Typography = Typography(
    displaySmall = u(26, 32, FontWeight.SemiBold, -0.02f),
    headlineLarge = u(22, 28, FontWeight.SemiBold, -0.02f),
    headlineMedium = d(20, 26, FontWeight.ExtraBold, -0.01f),
    headlineSmall = d(18, 24, FontWeight.Bold, -0.01f),
    titleLarge = d(16, 22, FontWeight.ExtraBold, -0.01f),
    titleMedium = s(14, 20, FontWeight.Bold),
    titleSmall = s(13, 18, FontWeight.Bold),
    bodyLarge = s(14, 20, FontWeight.Medium),
    bodyMedium = s(13, 18, FontWeight.Medium),
    bodySmall = s(12, 16, FontWeight.Medium),
    labelLarge = s(13, 18, FontWeight.Bold),
    labelMedium = s(12, 16, FontWeight.Bold, 0.02f),
    labelSmall = s(11, 14, FontWeight.Bold, 0.06f),
)
