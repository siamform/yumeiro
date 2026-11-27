/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.player.DynamicRangeCompressor.Preset
import dev.asfakul.yumeiro.player.DynamicRangeCompressor.SaveResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Group Y (B8): more compressor presets, Manual with every value, saved presets that survive a restart, reset. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupYDrcPresetsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = ctx.getSharedPreferences("yumeiro_drc", Context.MODE_PRIVATE)
    private val drc = DynamicRangeCompressor

    @Before fun setUp() { prefs.edit().clear().commit(); drc.reloadForTest(ctx) }
    @After fun tearDown() { drc.setPreset(ctx, Preset.OFF); prefs.edit().clear().commit() }

    @Test fun everyPresetIsDistinctValidAndActuallyCompresses() {
        val named = Preset.entries.mapNotNull { p -> p.settings?.let { p to it } }
        assertEquals(listOf(Preset.LIGHT, Preset.NIGHT, Preset.STRONG, Preset.DIALOGUE, Preset.MOVIE, Preset.MUSIC, Preset.LOUD_PLACE), named.map { it.first })
        assertEquals("no two presets share values", named.size, named.map { it.second }.toSet().size)
        named.forEach { (p, s) -> assertEquals("$p inside the slider ranges", s, s.clamped()) }
        // A loud passage (-3 dBFS) is turned down by every preset; Music least, Noisy place most.
        val dsp = DynamicRangeCompressor.Dsp(48_000)
        val cut = named.associate { (p, s) -> p to dsp.reductionDb(-3f, s) }
        cut.forEach { (p, db) -> assertTrue("$p turns a loud sound down ($db dB)", db < -1f) }
        assertTrue("Music is the gentlest: $cut", cut.values.all { it <= cut[Preset.MUSIC]!! })
        assertTrue("Noisy place is the strongest: $cut", cut.values.all { it >= cut[Preset.LOUD_PLACE]!! })
        assertEquals("Manual", Preset.CUSTOM.label)
    }

    @Test fun manualKeepsTheLastOwnValuesAndEveryValueIsSettable() {
        val own = DynamicRangeCompressor.Settings(-33f, 5.5f, 14f, 7f, 640f, 11f)
        drc.setCustom(ctx, own)
        drc.setPreset(ctx, Preset.MOVIE)
        assertEquals(Preset.MOVIE.settings, drc.active)
        drc.selectManual(ctx)
        assertEquals(Preset.CUSTOM, drc.preset.value)
        assertEquals(own, drc.active)
        drc.reloadForTest(ctx)
        assertEquals("Manual and all six values survive a restart", own, drc.active)
        assertEquals(14f, drc.custom.value.kneeDb)
    }

    @Test fun savedPresetsPersistReplaceByNameDeleteAndCap() {
        drc.setPreset(ctx, Preset.NIGHT)
        assertEquals(SaveResult.Saved, drc.savePreset(ctx, "  Late   show "))
        assertEquals("Late show", drc.saved.value.single().name)
        assertEquals(Preset.NIGHT.settings, drc.saved.value.single().settings)
        drc.setCustom(ctx, DynamicRangeCompressor.Settings(-20f, 2.5f, 4f, 3f, 300f, 6f))
        assertEquals(SaveResult.Replaced, drc.savePreset(ctx, "late SHOW"))
        assertEquals(1, drc.saved.value.size)
        assertEquals(-20f, drc.saved.value.single().settings.thresholdDb)
        assertEquals(SaveResult.NoName, drc.savePreset(ctx, "   "))
        drc.reloadForTest(ctx)
        assertEquals("saved presets survive a restart", -20f, drc.saved.value.single().settings.thresholdDb)

        drc.setPreset(ctx, Preset.STRONG)
        drc.applySaved(ctx, drc.saved.value.single())
        assertEquals(Preset.CUSTOM, drc.preset.value)
        assertEquals(-20f, drc.active!!.thresholdDb)
        assertEquals("replacing keeps the name as typed the last time", "late SHOW", drc.activeSaved(drc.preset.value, drc.custom.value, drc.saved.value)?.name)

        (2..DynamicRangeCompressor.MAX_SAVED).forEach { assertEquals(SaveResult.Saved, drc.savePreset(ctx, "P$it")) }
        assertEquals(SaveResult.Full, drc.savePreset(ctx, "one too many"))
        drc.deletePreset(ctx, "Late show")
        assertEquals(DynamicRangeCompressor.MAX_SAVED - 1, drc.saved.value.size)
        drc.reloadForTest(ctx)
        assertTrue(drc.saved.value.none { it.name.equals("Late show", ignoreCase = true) })

        drc.setPreset(ctx, Preset.OFF)
        assertEquals(SaveResult.Off, drc.savePreset(ctx, "nothing"))
    }

    @Test fun applyingASavedPresetThatEqualsABuiltInStaysManual() {
        drc.setPreset(ctx, Preset.LIGHT)
        drc.savePreset(ctx, "Same as light")
        drc.setPreset(ctx, Preset.STRONG)
        drc.applySaved(ctx, drc.saved.value.single())
        assertEquals(Preset.CUSTOM, drc.preset.value)
        assertEquals(Preset.LIGHT.settings, drc.active)
    }

    @Test fun resetGoesBackToNight() {
        drc.setCustom(ctx, DynamicRangeCompressor.Settings(-50f, 10f, 2f, 1f, 50f, 20f))
        drc.reset(ctx)
        assertEquals(Preset.NIGHT, drc.preset.value)
        assertEquals(Preset.NIGHT.settings, drc.active)
        drc.reloadForTest(ctx)
        assertEquals(Preset.NIGHT, drc.preset.value)
    }

    @Test fun damagedSavedValueNeverCrashes() {
        assertEquals(emptyList<DynamicRangeCompressor.UserPreset>(), drc.decodeSaved("{not json"))
        assertEquals(emptyList<DynamicRangeCompressor.UserPreset>(), drc.decodeSaved(null))
        val list = drc.decodeSaved("""[{"name":"A","threshold":-999,"ratio":50},{"name":""},{"name":"a"}]""")
        assertEquals(1, list.size)
        assertEquals(-60f, list[0].settings.thresholdDb)
        assertEquals(20f, list[0].settings.ratio)
        assertNull(drc.cleanName(" \t "))
    }
}
