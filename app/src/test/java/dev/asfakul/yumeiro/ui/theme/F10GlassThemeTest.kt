package dev.asfakul.yumeiro.ui.theme

// F-10 (docs/proposals/FEATURE-F-10-glassmorphism-theme.md, ADR-017): the Kasumi glass theme. Fail-before: no
// ThemeMode.GLASS, GlassTier or Palettes.kasumi.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class F10GlassThemeTest {
    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05; val l2 = b.luminance() + 0.05
        return (maxOf(l1, l2) / minOf(l1, l2)).toDouble()
    }

    @Test fun kasumiIsAThemeChoice() {
        assertEquals("Kasumi", ThemeMode.GLASS.label)
        assertTrue(ThemeMode.GLASS in ThemeMode.entries)
    }

    @Test fun lowEndTvHotBatterySaverAndReduceTransparencyGetTheSolidTint() {
        assertEquals(GlassTier.GLASS, GlassTier.choose(lowEnd = false, tv = false, saver = false, hot = false, reduceTransparency = false))
        assertEquals(GlassTier.TINT, GlassTier.choose(lowEnd = true, tv = false, saver = false, hot = false, reduceTransparency = false))
        assertEquals(GlassTier.TINT, GlassTier.choose(lowEnd = false, tv = true, saver = false, hot = false, reduceTransparency = false))
        assertEquals(GlassTier.TINT, GlassTier.choose(lowEnd = false, tv = false, saver = true, hot = false, reduceTransparency = false))
        assertEquals(GlassTier.TINT, GlassTier.choose(lowEnd = false, tv = false, saver = false, hot = true, reduceTransparency = false))
        assertEquals(GlassTier.TINT, GlassTier.choose(lowEnd = false, tv = false, saver = false, hot = false, reduceTransparency = true))
    }

    @Test fun tintIsMoreOpaqueThanGlass() {
        assertTrue(Palettes.kasumi(GlassTier.TINT).navBar.alpha > Palettes.kasumi(GlassTier.GLASS).navBar.alpha)
        assertTrue(Palettes.kasumi(GlassTier.TINT).navBar.alpha >= 0.9f)
    }

    @Test fun textOnGlassBarsKeepsFourPointFiveToOneOverTheBrightestBackdrop() {
        for (tier in GlassTier.entries) {
            val p = Palettes.kasumi(tier)
            // Worst case behind a bar: the brightest backdrop colour, or a white poster.
            for (behind in listOf(p.bgTop, p.bgMid, p.bgBottom, Color.White)) {
                val bar = p.navBar.compositeOver(behind)
                assertTrue("$tier primary over $behind", contrast(p.textPrimary, bar) >= 4.5)
                assertTrue("$tier secondary over $behind", contrast(p.textSecondary, bar) >= 4.5)
                val mini = p.miniPlayer.compositeOver(behind)
                assertTrue("$tier mini player over $behind", contrast(p.onMiniPlayer, mini) >= 4.5)
            }
        }
    }

    @Test fun kasumiKeepsTheYumeiroMoonlightAccent() {
        assertEquals(Palettes.Dark.accent, Palettes.kasumi(GlassTier.GLASS).accent)
        assertTrue(Palettes.kasumi(GlassTier.GLASS).isDark)
    }
}
