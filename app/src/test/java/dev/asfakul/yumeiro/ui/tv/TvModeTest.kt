/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvModeTest {
    private fun d(ui: Boolean = false, lb: Boolean = false, vendor: Boolean = false, touch: Boolean = true, pc: Boolean = false, sw: Int = 411) =
        TvMode.decide(ui, lb, vendor, touch, pc, sw)

    @Test fun phoneIsNotTv() = assertFalse(d())
    @Test fun androidTvByUiMode() = assertTrue(d(ui = true, touch = false, sw = 540))
    @Test fun leanbackBoxEvenWithFakeTouch() = assertTrue(d(lb = true, touch = true))
    @Test fun fireTvMarker() = assertTrue(d(vendor = true, touch = false))
    @Test fun cheapBoxWithPhoneImageAndNoTouch() = assertTrue(d(touch = false, sw = 540))
    @Test fun chromebookStaysNormal() = assertFalse(d(touch = false, pc = true, sw = 800))
    @Test fun tinyNoTouchDeviceStaysNormal() = assertFalse(d(touch = false, sw = 240))

    @Test fun userChoiceWins() {
        assertTrue(TvMode.resolve(TvMode.Pref.TV, detected = false))
        assertFalse(TvMode.resolve(TvMode.Pref.PHONE, detected = true))
        assertTrue(TvMode.resolve(TvMode.Pref.AUTO, detected = true))
    }
}
