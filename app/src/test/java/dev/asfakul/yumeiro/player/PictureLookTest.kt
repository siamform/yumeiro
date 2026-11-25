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

/** A4K-3: presets, controls, Anime4K filter section, device guard, global / per-title saving and Reset all. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PictureLookTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    @After fun reset() = PictureLook.resetForTests()

    @Test fun elevenPresetsAllDistinctExceptTheTwoNeutralOnes() {
        val looks = PictureLook.Look.entries
        assertEquals(11, looks.size)
        val named = looks.filter { it != PictureLook.Look.ORIGINAL && it != PictureLook.Look.CUSTOM }
        assertEquals(named.size, named.map { it.base }.toSet().size)
        named.forEach { assertFalse("$it must change the picture", it.base.neutral) }
        assertTrue(PictureLook.Look.ORIGINAL.base.neutral)
        assertTrue(PictureLook.resolve(PictureLook.Profile()).neutral)
    }

    @Test fun controlsStackOnThePresetAndClamp() {
        val p = PictureLook.Profile(PictureLook.Look.VIVID, PictureLook.Controls(saturation = 1.5f, brightness = 0.9f, hue = 200f))
        val g = PictureLook.resolve(p)
        assertEquals(1.22f * 1.5f, g.saturation, 1e-4f)
        assertEquals(0.5f, g.brightness, 1e-4f)
        assertEquals(180f, g.hue, 1e-4f)
        assertEquals(PictureLook.VideoFilter.SEPIA, PictureLook.resolve(PictureLook.Profile(controls = PictureLook.Controls(filter = PictureLook.VideoFilter.SEPIA))).filter)
    }

    @Test fun hotDeviceDropsSharpnessKeepsColour() {
        val p = PictureLook.Profile(PictureLook.Look.NEON, PictureLook.Controls(sharpness = 0.8f))
        assertEquals(0.8f, PictureLook.resolve(p, HeatGuard.Level.COOL).sharpness, 1e-4f)
        val hot = PictureLook.resolve(p, HeatGuard.Level.HOT)
        assertEquals(0f, hot.sharpness, 0f)
        assertEquals(PictureLook.resolve(p).saturation, hot.saturation, 0f)
    }

    @Test fun iterationsCappedByTierAndHeat() {
        val t = VideoPrefs.GpuTier.entries
        assertEquals(1, PictureLook.allowedIterations(3, VideoPrefs.GpuTier.LOW, HeatGuard.Level.COOL))
        assertEquals(2, PictureLook.allowedIterations(3, VideoPrefs.GpuTier.MID, HeatGuard.Level.COOL))
        assertEquals(3, PictureLook.allowedIterations(3, VideoPrefs.GpuTier.HIGH, HeatGuard.Level.COOL))
        t.forEach { assertEquals(1, PictureLook.allowedIterations(3, it, HeatGuard.Level.WARM)) }
        t.forEach { assertEquals(0, PictureLook.allowedIterations(3, it, HeatGuard.Level.HOT)) }
        assertEquals(1, PictureLook.allowedIterations(0, VideoPrefs.GpuTier.HIGH, HeatGuard.Level.COOL))
    }

    @Test fun filterDefaultsReproduceTheOldShaderConstants() {
        val t = PictureLook.Tuning()
        assertEquals(0.02f, t.gateLow, 1e-6f)
        assertEquals(0.015f, t.clampMargin, 1e-6f)
        assertEquals(1, t.iterations); assertEquals(0f, t.grain, 0f)
    }

    @Test fun perTitleOverrideGlobalAndResetAllSurviveReload() {
        PictureLook.init(ctx)
        val warm = PictureLook.Profile(PictureLook.Look.WARM)
        val cine = PictureLook.Profile(PictureLook.Look.CINEMATIC, PictureLook.Controls(contrast = 1.2f), PictureLook.Tuning(iterations = 2, threshold = 0.5f, grain = 0.3f))
        PictureLook.save(ctx, warm, perTitle = false, key = "frieren")
        PictureLook.save(ctx, cine, perTitle = true, key = "one piece")
        PictureLook.resetForTests(); PictureLook.init(ctx)
        assertEquals(warm, PictureLook.profileFor("frieren"))
        assertEquals(cine, PictureLook.profileFor("one piece"))
        assertTrue(PictureLook.hasOverride("one piece")); assertFalse(PictureLook.hasOverride("frieren"))
        PictureLook.clearOverride(ctx, "one piece")
        assertEquals(warm, PictureLook.profileFor("one piece"))
        PictureLook.save(ctx, cine, perTitle = true, key = "one piece")
        PictureLook.resetAll(ctx)
        PictureLook.resetForTests(); PictureLook.init(ctx)
        assertTrue(PictureLook.profileFor("one piece").isDefault)
        assertTrue(PictureLook.profileFor(null).isDefault)
    }

    @Test fun encodeDecodeRoundTripAndBadInputIsSafe() {
        val p = PictureLook.Profile(PictureLook.Look.GHIBLI, PictureLook.Controls(-0.1f, 0.7f, 1.3f, 1.4f, -45f, 0.6f, PictureLook.VideoFilter.VINTAGE), PictureLook.Tuning(3, 0.1f, 0.9f, 0.4f))
        assertEquals(p, PictureLook.decode(PictureLook.encode(p)))
        assertTrue(PictureLook.decode(org.json.JSONObject("{\"look\":\"NOPE\"}")).isDefault)
    }

    @Test fun titleKeyAndLabels() {
        assertEquals("frieren", PictureLook.titleKey("Frieren · S1E3"))
        assertEquals("dune", PictureLook.titleKey(" Dune "))
        assertEquals("Vivid", PictureLook.label(PictureLook.Profile(PictureLook.Look.VIVID)))
        assertEquals("Custom (based on Vivid)", PictureLook.label(PictureLook.Profile(PictureLook.Look.VIVID, PictureLook.Controls(gamma = 1.2f))))
        assertEquals("+12", PictureLook.show(0.12f, PictureLook.Kind.SIGNED))
        assertEquals("-30°", PictureLook.show(-30f, PictureLook.Kind.DEGREES))
    }
}
