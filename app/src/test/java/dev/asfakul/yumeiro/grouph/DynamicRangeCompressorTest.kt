/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.grouph

import dev.asfakul.yumeiro.player.DynamicRangeCompressor
import dev.asfakul.yumeiro.player.DynamicRangeCompressor.Dsp
import dev.asfakul.yumeiro.player.DynamicRangeCompressor.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/** H-1: the compressor's maths on generated PCM (no device needed). */
class DynamicRangeCompressorTest {
    private val rate = 48_000
    private val night = DynamicRangeCompressor.Preset.NIGHT.settings!!

    private fun tone(amp: Float, seconds: Float = 1f, ch: Int = 2): FloatArray {
        val n = (rate * seconds).toInt()
        return FloatArray(n * ch) { i -> (amp * sin(2 * PI * 440 * (i / ch) / rate)).toFloat() }
    }
    private fun peakDb(b: FloatArray, from: Int = b.size / 2) = 20f * log10(b.drop(from).maxOf { abs(it) })

    @Test fun belowTheKneeOnlyMakeUpGainIsApplied() {
        val s = night.copy(makeupDb = 0f)
        val d = Dsp(rate)
        assertEquals(0f, d.reductionDb(-50f, s), 0.001f)
        assertEquals(0f, d.reductionDb(s.thresholdDb - s.kneeDb / 2f - 0.1f, s), 0.001f)
    }

    @Test fun aboveTheThresholdTheRatioHolds() {
        val s = night.copy(kneeDb = 0f)
        // 12 dB over at 3:1 → 4 dB out, so 8 dB of reduction.
        assertEquals(-8f, Dsp(rate).reductionDb(s.thresholdDb + 12f, s), 0.01f)
    }

    @Test fun theKneeIsContinuous() {
        val s = night
        val d = Dsp(rate)
        val edge = s.thresholdDb + s.kneeDb / 2f
        assertEquals(d.reductionDb(edge - 0.001f, s), d.reductionDb(edge + 0.001f, s), 0.01f)
    }

    @Test fun loudGetsQuieterAndQuietGetsLouder() {
        val loud = tone(0.9f); val quiet = tone(0.02f)
        val inLoud = peakDb(loud); val inQuiet = peakDb(quiet)
        Dsp(rate).process(loud, loud.size / 2, 2, night)
        Dsp(rate).process(quiet, quiet.size / 2, 2, night)
        val outLoud = peakDb(loud); val outQuiet = peakDb(quiet)
        assertTrue("loud comes down ($inLoud → $outLoud)", outLoud < inLoud)
        assertTrue("quiet comes up ($inQuiet → $outQuiet)", outQuiet > inQuiet + 6f)
        assertTrue("the gap shrinks", (outLoud - outQuiet) < (inLoud - inQuiet) - 10f)
    }

    @Test fun makeUpGainNeverClips() {
        val b = tone(1f)
        Dsp(rate).process(b, b.size / 2, 2, DynamicRangeCompressor.Preset.STRONG.settings!!)
        assertTrue("no sample above full scale", b.all { abs(it) <= 1f })
    }

    @Test fun channelsStayLinked() {
        val b = FloatArray(rate * 2) { i -> if (i % 2 == 0) (0.8f * sin(2 * PI * 440 * (i / 2) / rate)).toFloat() else (0.4f * sin(2 * PI * 440 * (i / 2) / rate)).toFloat() }
        Dsp(rate).process(b, rate, 2, night)
        val l = b.filterIndexed { i, _ -> i % 2 == 0 && i > rate }.maxOf { abs(it) }
        val r = b.filterIndexed { i, _ -> i % 2 == 1 && i > rate }.maxOf { abs(it) }
        assertEquals("left/right balance is kept", 2f, l / r, 0.05f)
    }

    @Test fun customSettingsAreClampedToSafeRanges() {
        val c = Settings(thresholdDb = 10f, ratio = 0.5f, kneeDb = 99f, attackMs = 0f, releaseMs = 1e6f, makeupDb = 60f).clamped()
        assertEquals(0f, c.thresholdDb); assertEquals(1f, c.ratio); assertEquals(24f, c.kneeDb)
        assertEquals(0.5f, c.attackMs); assertEquals(2000f, c.releaseMs); assertEquals(24f, c.makeupDb)
    }

    @Test fun offPresetHasNoSettings() {
        assertEquals(null, DynamicRangeCompressor.Preset.OFF.settings)
    }
}
