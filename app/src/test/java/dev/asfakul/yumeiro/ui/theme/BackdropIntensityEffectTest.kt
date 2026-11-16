/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** G9 SE-6: Settings › Backdrop › Intensity slider (`backdrop_intensity`) changes what is drawn. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackdropIntensityEffectTest {
    @Test fun moreIntensityShowsMoreOfThePhoto() {
        for (dark in listOf(true, false)) {
            assertTrue(backdropScrim(dark, 1.6f) < backdropScrim(dark, 1f))
            assertTrue(backdropScrim(dark, 1f) < backdropScrim(dark, 0.3f))
            for (k in listOf(0.3f, 1f, 1.6f)) assertTrue(backdropScrim(dark, k) in 0.42f..0.84f)
        }
    }
    @Test fun moreIntensityGlowsMore() {
        assertTrue(backdropGlow(true, 1.3f) > backdropGlow(true, 1f))
        assertTrue(backdropGlow(true, 1f) > backdropGlow(true, 0.5f))
    }
    @Test fun sliderValueIsKeptAndClamped() {
        val app: Context = ApplicationProvider.getApplicationContext()
        BackdropSettings.init(app)
        val before = BackdropSettings.intensity.value
        try {
            BackdropSettings.setIntensity(app, 1.4f); assertEquals(1.4f, BackdropSettings.intensity.value, 0.001f)
            BackdropSettings.setIntensity(app, 9f); assertEquals(1.6f, BackdropSettings.intensity.value, 0.001f)
        } finally { BackdropSettings.setIntensity(app, before) }
    }
}
