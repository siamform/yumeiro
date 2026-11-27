/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.groupn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.player.DynamicRangeCompressor
import dev.asfakul.yumeiro.player.DynamicRangeCompressor.Preset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F15 (owner, after CloudStream's compressor dialog): the compressor's controls. Moving a slider starts from the
 * values in use (a preset's), changes the sound at once without a disk write per drag frame, the drag's end saves,
 * values equal to a preset select it, and Reset returns to the recommended Night values. The device test drives
 * the same controls in the player while the video plays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupNCompressorControlsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = ctx.getSharedPreferences("yumeiro_drc", Context.MODE_PRIVATE)
    private val light = Preset.LIGHT.settings!!
    private val night = Preset.NIGHT.settings!!

    @Before fun setUp() { prefs.edit().clear().commit(); DynamicRangeCompressor.reloadForTest(ctx) }
    @After fun tearDown() { DynamicRangeCompressor.setPreset(ctx, Preset.OFF); prefs.edit().clear().commit() }

    @Test fun currentFollowsThePreset() {
        assertNull(DynamicRangeCompressor.current())
        DynamicRangeCompressor.setPreset(ctx, Preset.LIGHT)
        assertEquals(light, DynamicRangeCompressor.current())
        assertEquals(light, DynamicRangeCompressor.active)
    }

    @Test fun aDragIsLiveAndOnlyItsEndIsSaved() {
        DynamicRangeCompressor.setPreset(ctx, Preset.LIGHT)
        val dragged = DynamicRangeCompressor.current()!!.copy(thresholdDb = -40f)
        DynamicRangeCompressor.setCustom(ctx, dragged, persist = false)
        assertEquals("live on the next buffer", -40f, DynamicRangeCompressor.active!!.thresholdDb, 0f)
        assertEquals("the rest stays Light's", light.ratio, DynamicRangeCompressor.active!!.ratio, 0f)
        assertEquals(Preset.CUSTOM, DynamicRangeCompressor.preset.value)
        assertEquals("not saved mid-drag", Preset.LIGHT.name, prefs.getString("preset", null))
        DynamicRangeCompressor.setCustom(ctx, DynamicRangeCompressor.current()!!)
        assertEquals(Preset.CUSTOM.name, prefs.getString("preset", null))
        assertEquals(-40f, prefs.getFloat("threshold", 0f), 0f)
        DynamicRangeCompressor.reloadForTest(ctx)
        assertEquals("kept after a restart", dragged, DynamicRangeCompressor.active)
        assertEquals(Preset.CUSTOM, DynamicRangeCompressor.preset.value)
    }

    @Test fun valuesEqualToAPresetSelectIt() {
        DynamicRangeCompressor.setCustom(ctx, light)
        assertEquals(Preset.LIGHT, DynamicRangeCompressor.preset.value)
    }

    @Test fun resetReturnsToNight() {
        DynamicRangeCompressor.setCustom(ctx, night.copy(thresholdDb = -50f, makeupDb = 20f))
        DynamicRangeCompressor.reset(ctx)
        assertEquals(Preset.NIGHT, DynamicRangeCompressor.preset.value)
        assertEquals(night, DynamicRangeCompressor.active)
        DynamicRangeCompressor.reloadForTest(ctx)
        assertEquals(Preset.NIGHT, DynamicRangeCompressor.preset.value)
        assertEquals(night, DynamicRangeCompressor.custom.value)
    }

    @Test fun summaryShowsTheOwnValues() {
        DynamicRangeCompressor.setCustom(ctx, night.copy(thresholdDb = -30f))
        assertEquals("Manual · -30 dB, 3:1, +8 dB", DynamicRangeCompressor.summary())
    }
}
