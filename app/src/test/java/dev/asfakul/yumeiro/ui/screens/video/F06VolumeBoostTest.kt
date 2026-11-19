package dev.asfakul.yumeiro.ui.screens.video

// F-06 (ledger F-06, docs/proposals/FEATURE-F-06-volume-booster.md): the volume swipe continues past 100 % into
// a boost range up to 200 % only when boost is allowed; swiping down takes the boost away first. Fail-before:
// VolumeAccumulator has no boost range and VolumeBoost does not exist.

import dev.asfakul.yumeiro.player.VolumeBoost
import org.junit.Assert.assertEquals
import org.junit.Test

class F06VolumeBoostTest {
    @Test fun withoutBoostTheSwipeStopsAtTheSystemMaximum() {
        val a = VolumeAccumulator(15)
        a.begin(15)
        assertEquals(15, a.add(0.8f, 15))
        assertEquals(0f, a.boost, 0f)
    }

    @Test fun atMaxTheSwipeGoesIntoTheBoostRangeUpTo200Percent() {
        val a = VolumeAccumulator(15, boostAllowed = true)
        a.begin(15)
        assertEquals(15, a.add(0.5f, 15))
        assertEquals(0.5f, a.boost, 1e-4f)
        a.add(2f, 15)
        assertEquals(1f, a.boost, 0f) // capped at 200 %
        assertEquals(200, VolumeBoost.percent(15, 15, a.boost))
    }

    @Test fun belowMaxASwipeFillsTheSystemVolumeFirst() {
        val a = VolumeAccumulator(10, boostAllowed = true)
        a.begin(5)
        assertEquals(10, a.add(0.75f, 5))
        assertEquals(0.25f, a.boost, 1e-4f)
    }

    @Test fun swipingDownRemovesBoostBeforeLoweringTheSystemVolume() {
        val a = VolumeAccumulator(10, boostAllowed = true)
        a.begin(10, currentBoost = 0.5f)
        assertEquals(10, a.add(-0.3f, 10))
        assertEquals(0.2f, a.boost, 1e-4f)
        assertEquals(8, a.add(-0.4f, 10))
        assertEquals(0f, a.boost, 0f)
    }

    @Test fun aBoostIsIgnoredWhenTheSystemVolumeIsNotAtMax() {
        val a = VolumeAccumulator(10, boostAllowed = true)
        a.begin(6, currentBoost = 0.9f) // hardware buttons lowered the volume since the last swipe
        assertEquals(0f, a.boost, 0f)
    }

    @Test fun gainIsLinearInMillibelsAndCapped() {
        assertEquals(0, VolumeBoost.gainMb(0f))
        assertEquals(500, VolumeBoost.gainMb(0.5f))
        assertEquals(VolumeBoost.MAX_GAIN_MB, VolumeBoost.gainMb(3f))
        assertEquals(0, VolumeBoost.gainMb(-1f))
        assertEquals(50, VolumeBoost.percent(5, 10, 0f))
    }
}
