/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// R19 THEME-1 (owner 2026-11-23): the Kasumi glass theme is removed for now. Fail-before: ThemeMode still had
// GLASS, and a saved "GLASS" fell back to the System theme (light on a light phone) instead of a dark one.
class GlassRemovedTest {
    @Test fun noGlassThemeChoice() {
        assertFalse(ThemeMode.entries.any { it.name == "GLASS" || it.label == "Kasumi" })
    }

    @Test fun savedGlassThemeBecomesTsukiNight() {
        assertEquals(ThemeMode.DARK, ThemeSettings.modeFrom("GLASS"))
        assertEquals(ThemeMode.SYSTEM, ThemeSettings.modeFrom(null))
        assertEquals(ThemeMode.LIGHT, ThemeSettings.modeFrom("LIGHT"))
        assertEquals(ThemeMode.SYSTEM, ThemeSettings.modeFrom("NOPE"))
    }

    /** Dark themes stay dark and light themes stay light; page backgrounds are solid on every theme. */
    @Test fun themesKeepTheirToneAndSolidPages() {
        for (p in listOf(Palettes.Dark, Palettes.Afterglow, Palettes.Evening, Palettes.Midnight)) assertTrue(p.isDark)
        for (p in listOf(Palettes.Light, Palettes.Morning, Palettes.Noon, Palettes.Afternoon)) assertFalse(p.isDark)
        for (p in listOf(Palettes.Dark, Palettes.Light, Palettes.Afterglow, Palettes.Morning, Palettes.Noon, Palettes.Afternoon, Palettes.Evening, Palettes.Midnight)) {
            assertEquals(1f, p.background.alpha)
            assertEquals(1f, p.bgTop.alpha); assertEquals(1f, p.bgMid.alpha); assertEquals(1f, p.bgBottom.alpha)
        }
    }

    /** No see-through card fill, dock or mini player on any theme (they let other pages show through). */
    @Test fun sharedSurfacesAreSolidOnEveryTheme() {
        for (base in listOf(Palettes.Dark, Palettes.Light, Palettes.Afterglow, Palettes.Morning, Palettes.Noon, Palettes.Afternoon, Palettes.Evening, Palettes.Midnight)) {
            val p = base.solidSurfaces()
            assertEquals(1f, p.glass.alpha); assertEquals(1f, p.navBar.alpha); assertEquals(1f, p.miniPlayer.alpha)
            assertEquals(base.isDark, p.isDark)
            // Same tone: a dark theme's card stays dark, a light theme's stays light.
            assertEquals(base.isDark, p.glass.luminance() < 0.5f)
        }
    }
}
