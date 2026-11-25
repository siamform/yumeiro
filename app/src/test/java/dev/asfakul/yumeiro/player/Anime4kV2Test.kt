/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A4K-2 (item 26): preset tables, cost, labels, saving, and Classic staying the default effect. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Anime4kV2Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val ladder = listOf(Anime4kV2.Preset.PERFORMANCE, Anime4kV2.Preset.BALANCED, Anime4kV2.Preset.QUALITY, Anime4kV2.Preset.HIGH_QUALITY)

    @After fun reset() = Anime4kV2.resetForTests()

    @Test fun presetsRiseInStrengthAndNeverFallInCost() {
        for (style in Anime4kV2.Style.entries) {
            val ps = ladder.map { Anime4kV2.params(it, style) }
            ps.zipWithNext().forEach { (a, b) ->
                assertTrue("$style sharpen ${a.sharpen} <= ${b.sharpen}", a.sharpen <= b.sharpen)
                assertTrue("$style denoise ${a.denoise} <= ${b.denoise}", a.denoise <= b.denoise)
                assertTrue("$style cost", Anime4kV2.taps(a) <= Anime4kV2.taps(b))
            }
            ps.forEach { assertTrue("$style in range $it", it.sharpen in 0f..1f && it.darken in 0f..1f && it.denoise in 0f..1f) }
        }
        assertEquals(5, Anime4kV2.taps(Anime4kV2.params(Anime4kV2.Preset.PERFORMANCE, Anime4kV2.Style.SHARPEN)))
        assertEquals(9, Anime4kV2.taps(Anime4kV2.params(Anime4kV2.Preset.HIGH_QUALITY, Anime4kV2.Style.SHARPEN)))
    }

    @Test fun stylesSpendTheStrengthDifferently() {
        val sharpen = Anime4kV2.params(Anime4kV2.Preset.QUALITY, Anime4kV2.Style.SHARPEN)
        val deblur = Anime4kV2.params(Anime4kV2.Preset.QUALITY, Anime4kV2.Style.DEBLUR)
        val denoise = Anime4kV2.params(Anime4kV2.Preset.QUALITY, Anime4kV2.Style.DENOISE)
        assertEquals(0f, sharpen.denoise); assertFalse(sharpen.wide)
        assertTrue("de-blur reaches wider", deblur.wide)
        assertTrue("denoise smooths", denoise.denoise > 0.3f)
        assertTrue("denoise restores less", denoise.sharpen < sharpen.sharpen)
        assertFalse("performance de-blur stays narrow (cost)", Anime4kV2.params(Anime4kV2.Preset.PERFORMANCE, Anime4kV2.Style.DEBLUR).wide)
    }

    @Test fun customIsClampedAndUsedAsIs() {
        val c = Anime4kV2.Params(1.7f, -1f, 0.4f, wide = true, diagonals = true)
        val p = Anime4kV2.params(Anime4kV2.Preset.CUSTOM, Anime4kV2.Style.DENOISE, c)
        assertEquals(Anime4kV2.Params(1f, 0f, 0.4f, true, true), p)
    }

    @Test fun labelsShowTheActiveProfile() {
        val t = VideoPrefs.GpuTier.MID
        assertEquals("Off", Anime4kV2.label(Anime4kV2.Engine.V2, false, Anime4kV2.Preset.QUALITY, Anime4kV2.Style.DENOISE, t))
        assertEquals("Classic · Mid-range", Anime4kV2.label(Anime4kV2.Engine.CLASSIC, true, Anime4kV2.Preset.QUALITY, Anime4kV2.Style.DENOISE, t))
        assertEquals("Quality · Denoise", Anime4kV2.label(Anime4kV2.Engine.V2, true, Anime4kV2.Preset.QUALITY, Anime4kV2.Style.DENOISE, t))
        assertEquals("Custom", Anime4kV2.label(Anime4kV2.Engine.V2, true, Anime4kV2.Preset.CUSTOM, Anime4kV2.Style.DENOISE, t))
    }

    @Test fun classicStaysTheDefaultAndChoicesAreSaved() {
        Anime4kV2.resetForTests(); Anime4kV2.init(ctx)
        assertTrue("Classic by default (existing behaviour)", Anime4kV2.currentEffect() is Anime4kEffect)
        Anime4kV2.setEngine(ctx, Anime4kV2.Engine.V2)
        Anime4kV2.setPreset(ctx, Anime4kV2.Preset.HIGH_QUALITY)
        Anime4kV2.setStyle(ctx, Anime4kV2.Style.DEBLUR)
        Anime4kV2.setCustom(ctx, Anime4kV2.Params(0.2f, 0.3f, 0.9f, wide = true, diagonals = false))
        Anime4kV2.resetForTests(); Anime4kV2.init(ctx)
        assertEquals(Anime4kV2.Engine.V2, Anime4kV2.engine.value)
        assertEquals(Anime4kV2.Preset.HIGH_QUALITY, Anime4kV2.preset.value)
        assertEquals(Anime4kV2.Style.DEBLUR, Anime4kV2.style.value)
        assertEquals(Anime4kV2.Params(0.2f, 0.3f, 0.9f, true, false), Anime4kV2.custom.value)
        val fx = Anime4kV2.currentEffect()
        assertTrue(fx is Anime4kV2.Effect)
        assertEquals(Anime4kV2.params(Anime4kV2.Preset.HIGH_QUALITY, Anime4kV2.Style.DEBLUR), (fx as Anime4kV2.Effect).params)
        // V2 keys only; the Classic tier key is not written by V2.
        val sp = ctx.getSharedPreferences("yu_video_prefs", Context.MODE_PRIVATE)
        assertFalse(sp.contains("gpu_tier"))
    }
}
