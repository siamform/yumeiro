/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

val MTPlayerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun MtPalette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent, primaryContainer = surfaceContainerHigh, onPrimaryContainer = accentBright,
        secondary = accent2, onSecondary = onAccent, tertiary = accentBright,
        background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary, surfaceVariant = surfaceVariant, onSurfaceVariant = textSecondary,
        surfaceContainerLowest = background, surfaceContainerLow = surfaceContainer, surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh, surfaceContainerHighest = surfaceVariant,
        outline = outline, outlineVariant = outlineVariant,
    )
}

private fun ColorScheme.toPalette(isDark: Boolean) = MtPalette(
    isDark = isDark, accent = primary, accentBright = if (isDark) primary.copy(alpha = 1f) else primary, accent2 = tertiary,
    onAccent = onPrimary, background = background, surface = surfaceContainerLow, surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh, surfaceVariant = surfaceContainerHighest, outline = outline,
    outlineVariant = outlineVariant, textPrimary = onSurface, textSecondary = onSurfaceVariant,
    textTertiary = onSurfaceVariant.copy(alpha = 0.78f),
    scene = DreamScene.YOU,
).withAutoGradient()

private fun MtPalette.amoled(): MtPalette = if (!isDark) this else copy(
    background = Color.Black, surfaceContainer = Color(0xFF050507), surface = Color(0xFF0D0D11),
    bgTop = Color.Black, bgMid = Color.Black, bgBottom = Color.Black, navBar = Color.Black,
)

/** Resolves the palette for the current appearance settings (re-evaluated every minute for TIME). */
@Composable
fun rememberMtPalette(): MtPalette {
    val context = LocalContext.current
    remember(context) { ThemeSettings.init(context); ThemeSettings }
    val mode by ThemeSettings.mode.collectAsStateWithLifecycle()
    val amoled by ThemeSettings.amoled.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dayPart by produceState(currentDayPart(), mode) {
        while (mode == ThemeMode.TIME) { value = currentDayPart(); delay(60_000) }
    }
    val palette = when (mode) {
        ThemeMode.LIGHT -> Palettes.Light
        ThemeMode.DARK -> Palettes.Dark
        ThemeMode.SYSTEM -> if (systemDark) Palettes.Dark else Palettes.Light
        ThemeMode.TIME -> dayPart.palette()
        ThemeMode.AFTERGLOW -> Palettes.Afterglow
        ThemeMode.DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).toPalette(systemDark)
        } else if (systemDark) Palettes.Dark else Palettes.Light
    }
    return if (amoled) palette.amoled() else palette
}

/** The palette a theme mode would use, for previews in Settings (Material You shows the current one). */
fun previewPaletteFor(mode: ThemeMode, systemDark: Boolean, current: MtPalette): MtPalette = when (mode) {
    ThemeMode.LIGHT -> Palettes.Light
    ThemeMode.DARK -> Palettes.Dark
    ThemeMode.SYSTEM -> if (systemDark) Palettes.Dark else Palettes.Light
    ThemeMode.TIME -> currentDayPart().palette()
    ThemeMode.AFTERGLOW -> Palettes.Afterglow
    ThemeMode.DYNAMIC -> if (current.scene == DreamScene.YOU) current else (if (systemDark) Palettes.Dark else Palettes.Light).copy(scene = DreamScene.YOU)
}

@Composable
fun MTPlayerTheme(palette: MtPalette = rememberMtPalette(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMtPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = Typography,
            shapes = MTPlayerShapes,
        ) {
            val pressTint = (if (palette.isDark) Color.White else Color.Black).copy(alpha = 0.07f)
            // D-pad / keyboard focus ring in the accent colour; the TV layout also lifts the focused item.
            val tv = dev.asfakul.yumeiro.ui.tv.LocalTvMode.current
            val focusRing = if (palette.isDark) palette.accentBright else palette.accent
            val indication = remember(pressTint, focusRing, tv) { SoftPressIndication(pressTint, focusRing, if (tv) 1.04f else 1f) }
            // Material components that still draw ripples get a soft accent-tinted one.
            val rippleConfig = remember(palette.accent) {
                androidx.compose.material3.RippleConfiguration(
                    color = palette.accent,
                    rippleAlpha = androidx.compose.material.ripple.RippleAlpha(0.10f, 0.08f, 0.06f, 0.12f),
                )
            }
            CompositionLocalProvider(
                androidx.compose.foundation.LocalIndication provides indication,
                androidx.compose.material3.LocalRippleConfiguration provides rippleConfig,
                content = content,
            )
        }
    }
}

/**
 * The palette picked in Settings → Appearance, even inside dark-only layers (video controls).
 * Watch pages, the player top bar and sheets read it so they always follow the user's theme.
 */
val LocalAppPalette = androidx.compose.runtime.staticCompositionLocalOf<MtPalette?> { null }

/**
 * Palette for controls drawn over video: dark surfaces (video is always on black) but the accent
 * of the user's theme, so Washi Day / Material You / Time of day keep their colour in the player.
 */
fun playerPaletteFor(app: MtPalette): MtPalette =
    if (app.isDark) app
    else Palettes.Dark.copy(accent = app.accentBright, accentBright = app.accentBright, accent2 = app.accent2)

/** Dark palette for layers over video, tinted with the user's accent (see [playerPaletteFor]). */
@Composable
fun ForceDarkTheme(content: @Composable () -> Unit) {
    val current = LocalMtPalette.current
    val app = LocalAppPalette.current ?: current
    CompositionLocalProvider(LocalAppPalette provides app) {
        MTPlayerTheme(palette = playerPaletteFor(current), content = content)
    }
}

/** Re-applies the user's own theme inside a dark layer (watch page under the docked player). */
@Composable
fun AppThemeLayer(content: @Composable () -> Unit) {
    val app = LocalAppPalette.current ?: LocalMtPalette.current
    MTPlayerTheme(palette = app, content = content)
}
