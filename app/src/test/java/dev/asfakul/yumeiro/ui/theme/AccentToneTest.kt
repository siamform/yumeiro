package dev.asfakul.yumeiro.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TH-1 (owner 2026-11-19): more accent colours next to the moonlight gold (Blue and other curated tones).
 * An accent changes only the accent family of the current theme; every tone keeps WCAG AA (4.5:1) text
 * contrast on the dark and the light theme, and its "on accent" text stays readable.
 * Old code: no AccentTone (gold only).
 */
class AccentToneTest {
    private fun contrast(a: Color, b: Color): Float {
        val (lo, hi) = listOf(a.luminance(), b.luminance()).sorted()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test fun goldIsTheDefaultAndChangesNothing() {
        assertEquals(AccentTone.GOLD, AccentTone.from(null))
        assertEquals(AccentTone.GOLD, AccentTone.from("no-such-tone"))
        assertSame(Palettes.Dark, Palettes.Dark.withAccent(AccentTone.GOLD))
        assertSame(Palettes.Light, Palettes.Light.withAccent(AccentTone.GOLD))
    }

    @Test fun blueReplacesTheAccentFamilyOnly() {
        assertEquals(AccentTone.AO, AccentTone.from("AO"))
        val d = Palettes.Dark.withAccent(AccentTone.AO)
        assertNotEquals(Palettes.Dark.accent, d.accent)
        assertEquals("selected chip follows the accent on dark", d.accent, d.chipSelected)
        assertEquals(Palettes.Dark.background, d.background)
        assertEquals(Palettes.Dark.textPrimary, d.textPrimary)
        val l = Palettes.Light.withAccent(AccentTone.AO)
        assertEquals("ink chip on the light theme is kept", Palettes.Light.chipSelected, l.chipSelected)
    }

    @Test fun everyToneIsReadableOnDarkAndLight() {
        AccentTone.entries.filter { it != AccentTone.GOLD }.forEach { t ->
            listOf(Palettes.Dark, Palettes.Light, Palettes.Afterglow).forEach { base ->
                val p = base.withAccent(t)
                assertTrue("$t accent on ${if (base.isDark) "dark" else "light"} background", contrast(p.accent, p.background) >= 4.5f)
                assertTrue("$t text on its accent", contrast(p.onAccent, p.accent) >= 4.5f)
            }
        }
    }
}
