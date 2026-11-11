/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Yumeiro palette ("Tsuki" design system: sumi-ink neutrals + one moonlight accent). Every screen reads colors through the
 * composable accessors below, so the whole UI follows the active theme mode.
 */
data class MtPalette(
    val isDark: Boolean,
    val accent: Color,
    val accentBright: Color,
    val accent2: Color,
    val onAccent: Color,
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Page background gradient (top → middle → bottom). */
    val bgTop: Color = background,
    val bgMid: Color = background,
    val bgBottom: Color = background,
    /** Bottom navigation bar fill. */
    val navBar: Color = surface,
    /** Translucent "glass" fill for cards, pills and round icon buttons. */
    val glass: Color = textPrimary.copy(alpha = 0.08f),
    /** Floating mini player fill + its content color. */
    val miniPlayer: Color = surfaceContainerHigh,
    val onMiniPlayer: Color = textPrimary,
    /** Selected filter pill fill + text. */
    val chipSelected: Color = textPrimary,
    val onChipSelected: Color = background,
)

/** Pale moonlight "spark" used for live badges, sparkles and focus rings. */
val SparkCyan = Color(0xFFFFE2A8)

/** Brand colors (logo): moonlight gold on sumi ink. */
val BrandGold = Color(0xFFF5C46B)
val BrandAmber = Color(0xFFD9963F)
val BrandInk = Color(0xFF0E0E11)
// Legacy names kept for older call sites; they now resolve to the single-hue brand family.
val BrandViolet = BrandAmber
val BrandPink = BrandGold
val BrandDeep = Color(0xFF1C1C21)
val BrandCyan = Color(0xFFFFE2A8)
val BrandCoral = Color(0xFFE8A35A)

val LocalMtPalette = staticCompositionLocalOf { Palettes.Dark }

// ---- Accessors used by every screen ---------------------------------------------------------
val SpotifyGreen: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.accent
/** Amber for warnings; darker on light surfaces so the text keeps its contrast. */
val WarnTone: Color @Composable @ReadOnlyComposable get() = if (LocalMtPalette.current.isDark) Color(0xFFFFB74D) else Color(0xFFB45309)
val SpotifyGreenBright: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.accentBright
val AccentSecondary: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.accent2
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.onAccent
val DarkBackground: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.background
val DarkSurface: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.surface
val DarkSurfaceContainer: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.surfaceContainer
val DarkSurfaceContainerHigh: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.surfaceContainerHigh
val DarkSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.surfaceVariant
val DarkOutline: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.outline
val DarkOutlineVariant: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.outlineVariant
val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.textPrimary
val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.textSecondary
val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.textTertiary
val GlassFill: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.glass
/** Text / hairline colour on pages (white on dark themes, ink on light ones). Use instead of Color.White off-video. */
val PageInk: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.textPrimary
val TrackCardOverlay: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.accent.copy(alpha = 0.18f)
val PlayerScrim: Color @Composable @ReadOnlyComposable get() = LocalMtPalette.current.background.copy(alpha = 0.92f)

/** Gives palettes without a hand-tuned gradient a soft accent-tinted one. */
fun MtPalette.withAutoGradient(): MtPalette = copy(
    bgTop = lerp(background, accent, if (isDark) 0.22f else 0.12f),
    bgMid = background,
    bgBottom = if (isDark) lerp(background, Color.Black, 0.35f) else lerp(background, Color.White, 0.6f),
    navBar = if (isDark) lerp(background, Color.Black, 0.25f) else surface,
    glass = textPrimary.copy(alpha = if (isDark) 0.10f else 0.06f),
    miniPlayer = if (isDark) lerp(surfaceContainerHigh, accent, 0.12f).copy(alpha = 0.97f) else surface,
    onMiniPlayer = textPrimary,
    chipSelected = textPrimary,
    onChipSelected = background,
)

object Palettes {
    /**
     * "Tsuki Night" (月夜) — the signature dark theme. Neutral sumi-ink surfaces (no tinted
     * purple), a single moonlight-gold accent and its deeper amber tone. Colour on the page comes
     * from the content itself (ambient glow from the current artwork), not from a rainbow of brand
     * hues. Tuned for WCAG AA on every text tier.
     */
    val Dark = MtPalette(
        isDark = true,
        accent = Color(0xFFF5C46B), accentBright = Color(0xFFFFDB94), accent2 = Color(0xFFD9963F), onAccent = Color(0xFF1C1407),
        background = Color(0xFF0E0E11), surface = Color(0xFF16161A), surfaceContainer = Color(0xFF121215),
        surfaceContainerHigh = Color(0xFF1E1E23), surfaceVariant = Color(0xFF2A2A30),
        outline = Color(0xFF3A3A42), outlineVariant = Color(0xFF232328),
        textPrimary = Color(0xFFF4F4F6), textSecondary = Color(0xFFC4C4CC), textTertiary = Color(0xFF8F8F99),
        bgTop = Color(0xFF18171C), bgMid = Color(0xFF0E0E11), bgBottom = Color(0xFF09090B),
        navBar = Color(0xF5141418), glass = Color(0x12FFFFFF),
        miniPlayer = Color(0xF71C1C21), onMiniPlayer = Color.White,
        chipSelected = Color(0xFFF5C46B), onChipSelected = Color(0xFF1C1407),
    )

    /** "Washi Day" (和紙) — the light theme: warm paper neutrals, deep amber accent. */
    val Light = MtPalette(
        isDark = false,
        accent = Color(0xFF8A5A00), accentBright = Color(0xFFB07A1C), accent2 = Color(0xFF6E4700), onAccent = Color.White,
        background = Color(0xFFF6F5F2), surface = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFEFEEEA),
        surfaceContainerHigh = Color(0xFFE8E6E1), surfaceVariant = Color(0xFFE1DFD9),
        outline = Color(0xFFD2CFC7), outlineVariant = Color(0xFFE6E4DE),
        textPrimary = Color(0xFF17161A), textSecondary = Color(0xFF4E4C55), textTertiary = Color(0xFF6B6972),
        bgTop = Color(0xFFEFEBE2), bgMid = Color(0xFFF6F5F2), bgBottom = Color(0xFFFAF9F7),
        navBar = Color(0xF7FFFFFF), glass = Color(0xCCFFFFFF),
        miniPlayer = Color(0xF71A191E), onMiniPlayer = Color.White,
        chipSelected = Color(0xFF17161A), onChipSelected = Color.White,
    )

    /** Prototype "Afterglow" — warm editorial dark variant. */
    val Afterglow = MtPalette(
        isDark = true,
        accent = Color(0xFFE8743B), accentBright = Color(0xFFF29A6B), accent2 = Color(0xFF8F3E1F), onAccent = Color(0xFF1A0D08),
        background = Color(0xFF0C0B0A), surface = Color(0xFF161412), surfaceContainer = Color(0xFF12100E),
        surfaceContainerHigh = Color(0xFF1F1B18), surfaceVariant = Color(0xFF2A2420),
        outline = Color(0xFF3A322C), outlineVariant = Color(0xFF221D1A),
        textPrimary = Color(0xFFEDE8DF), textSecondary = Color(0xFFA39D92), textTertiary = Color(0xFF857F75),
        bgTop = Color(0xFF3A1A0D), bgMid = Color(0xFF140D0A), bgBottom = Color(0xFF0C0B0A),
        navBar = Color(0xFF0A0908), glass = Color(0x14EDE8DF),
        miniPlayer = Color(0xF52A1A12), onMiniPlayer = Color(0xFFEDE8DF),
        chipSelected = Color(0xFFEDE8DF), onChipSelected = Color(0xFF0C0B0A),
    )

    // Time-of-day moods
    val Morning = Light.copy(   // 05–11: soft peach daylight
        accent = Color(0xFFC2471F), accentBright = Color(0xFFFF8A65), accent2 = Color(0xFFF59E0B),
        background = Color(0xFFFFF8F4), surfaceContainer = Color(0xFFFFF0E8), surfaceContainerHigh = Color(0xFFFCE6DA),
        surfaceVariant = Color(0xFFF8E3D8), outline = Color(0xFFEBD3C6), outlineVariant = Color(0xFFF6E7DE),
        textPrimary = Color(0xFF241510), textSecondary = Color(0xFF6B5148), textTertiary = Color(0xFF8A6E64),
    ).withAutoGradient()
    val Noon = Light.copy(      // 11–15: crisp, bright sky
        accent = Color(0xFF0B6FBF), accentBright = Color(0xFF38A3F1), accent2 = Color(0xFF06B6D4),
        background = Color(0xFFF5FAFF), surfaceContainer = Color(0xFFEAF4FD), surfaceContainerHigh = Color(0xFFDDEDFB),
        surfaceVariant = Color(0xFFDCEAF7), outline = Color(0xFFC9DCEE), outlineVariant = Color(0xFFE3EEF8),
        textPrimary = Color(0xFF0F1B2A), textSecondary = Color(0xFF4B5C70), textTertiary = Color(0xFF64768A),
    ).withAutoGradient()
    val Afternoon = Light.copy( // 15–18: golden hour
        accent = Color(0xFF9A6216), accentBright = Color(0xFFD69E2E), accent2 = Color(0xFFE8613C),
        background = Color(0xFFFFFBF2), surfaceContainer = Color(0xFFFDF3DF), surfaceContainerHigh = Color(0xFFF9EACB),
        surfaceVariant = Color(0xFFF5E6C8), outline = Color(0xFFE8D6B0), outlineVariant = Color(0xFFF4E9D2),
        textPrimary = Color(0xFF221A0C), textSecondary = Color(0xFF65563A), textTertiary = Color(0xFF7F6E50),
    ).withAutoGradient()
    val Evening = Dark       // 18–22: the signature Tsuki Night
    val Midnight = Dark.copy(   // 22–05: deep navy, low glare
        accent = Color(0xFF7FA6FF), accentBright = Color(0xFF9DBBFF), accent2 = BrandCyan, onAccent = Color(0xFF05070D),
        background = Color(0xFF0B1020), surface = Color(0xFF111830), surfaceContainer = Color(0xFF0E1428),
        surfaceContainerHigh = Color(0xFF18213D), surfaceVariant = Color(0xFF1E2848),
        outline = Color(0xFF2A3658), outlineVariant = Color(0xFF18213D),
        textSecondary = Color(0xFFB8C2DC), textTertiary = Color(0xFF8D98B5),
        bgTop = Color(0xFF1C2A5A), bgMid = Color(0xFF0B1020), bgBottom = Color(0xFF05070D),
        navBar = Color(0xFF070A14), miniPlayer = Color(0xF5222E58), onChipSelected = Color(0xFF0B1020),
    )
}
