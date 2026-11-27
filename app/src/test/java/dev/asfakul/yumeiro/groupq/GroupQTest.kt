/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groupq

import dev.asfakul.yumeiro.ui.theme.UiScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F12: compact text and corners (pure rules). */
class GroupQTest {
    @Test fun f12_textFactorRespectsLargeSystemText() {
        assertEquals(0.92f, UiScale.textFactor(1f, compact = true), 0f)
        assertEquals(0.92f, UiScale.textFactor(0.85f, compact = true), 0f)
        assertEquals(0.92f, UiScale.textFactor(1.15f, compact = true), 0f)
        // A person who chose large text keeps it.
        assertEquals(1f, UiScale.textFactor(1.3f, compact = true), 0f)
        assertEquals(1f, UiScale.textFactor(2f, compact = true), 0f)
        assertEquals(1f, UiScale.textFactor(1f, compact = false), 0f)
        // Body 13 sp draws at no more than 12 sp.
        assertTrue(13f * UiScale.textFactor(1f, true) <= 12f)
    }

    @Test fun f12_cornersTrimOnlyAbove8dp() {
        val want = mapOf(4 to 4, 6 to 6, 8 to 8, 10 to 8, 12 to 9, 14 to 11, 16 to 12, 18 to 14, 20 to 16, 22 to 17, 24 to 19, 28 to 22, 36 to 28)
        for ((r, c) in want) assertEquals("radius $r", c.toFloat(), UiScale.corner(r.toFloat(), true), 0f)
        for (r in listOf(4, 12, 18, 28)) assertEquals(r.toFloat(), UiScale.corner(r.toFloat(), false), 0f)
        // Never larger than it was, never below 8 dp once above it.
        for (r in 9..64) { val c = UiScale.corner(r.toFloat(), true); assertTrue(c <= r && c >= 8f) }
    }

    @Test fun f12_phonesCompactTvStandard() {
        assertTrue(UiScale.defaultCompact(tv = false))
        assertTrue(!UiScale.defaultCompact(tv = true))
    }
}
