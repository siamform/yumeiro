/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanaTest {
    @Test fun matchesWholeWordsAndPrefersLongerKeys() {
        assertEquals("設定", Kana.forTitle("Settings"))
        assertEquals("一緒に", Kana.forTitle("Watch together"))
        assertEquals("状態", Kana.forTitle("Source status"))
        assertEquals("保存", Kana.forTitle("Downloads"))
        assertEquals("彩り", Kana.forTitle("Look & feel"))
    }
    @Test fun noAccentWhenNothingFits() {
        assertNull(Kana.forTitle("Frieren: Beyond Journey's End"))
        assertNull(Kana.forTitle("Musicals")) // "music" must be a whole word
    }
    @Test fun overlineRespectsToggle() {
        assertEquals("音楽 · Music", Kana.overline("音楽", "Music", true))
        assertEquals("Music", Kana.overline("音楽", "Music", false))
        assertEquals("設定", Kana.overline("設定", null, true))
        assertNull(Kana.overline(null, null, true))
        assertNull(Kana.overline("設定", null, false))
    }
}
