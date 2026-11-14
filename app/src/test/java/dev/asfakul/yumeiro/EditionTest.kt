/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import dev.asfakul.yumeiro.ui.tv.TvMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs in both apps (testMobileDebugUnitTest and testTvDebugUnitTest). */
class EditionTest {
    @Test
    fun editionMatchesTheBuild() {
        assertEquals(BuildConfig.IS_TV_APP, Edition.isTv)
        assertEquals(if (Edition.isTv) "tv" else "phone", Edition.name)
        if (Edition.isTv) {
            assertTrue(Edition.imageMemoryShare < 0.2)
            // The TV app is always in TV layout, whatever the saved choice says.
            for (p in TvMode.Pref.entries) assertTrue(TvMode.resolve(p, detected = false))
        }
    }

    @Test
    fun phoneAppFollowsTheChoice() {
        assertEquals(false, TvMode.resolve(TvMode.Pref.PHONE, detected = true, tvApp = false))
        assertEquals(true, TvMode.resolve(TvMode.Pref.TV, detected = false, tvApp = false))
        assertEquals(true, TvMode.resolve(TvMode.Pref.AUTO, detected = true, tvApp = false))
        assertEquals(true, TvMode.resolve(TvMode.Pref.PHONE, detected = false, tvApp = true))
    }
}
