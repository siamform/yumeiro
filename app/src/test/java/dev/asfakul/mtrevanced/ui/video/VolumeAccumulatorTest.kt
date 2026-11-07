/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.mtrevanced.ui.video

import dev.asfakul.mtrevanced.ui.screens.video.VolumeAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VolumeAccumulatorTest {
    /** A real swipe: many tiny per-frame deltas. The old code rounded each to 0 steps. */
    @Test fun smallFrameDeltas_addUp() {
        val v = VolumeAccumulator(15)
        var level = 5
        v.begin(level)
        repeat(100) { level = v.add(0.004f, level) } // 40% of the screen upwards
        assertEquals(11, level) // 5/15 + 0.4 = 0.733 → 11
    }

    @Test fun swipeDown_reachesZero_andClamps() {
        val v = VolumeAccumulator(15)
        var level = 3
        repeat(200) { level = v.add(-0.01f, level) }
        assertEquals(0, level)
        level = v.add(0.01f, level) // turning back works immediately (no hidden negative level)
        assertEquals(0, level) // 0.01 * 15 = 0.15 → still rounds to 0
        repeat(10) { level = v.add(0.01f, level) }
        assertEquals(2, level)
    }

    @Test fun clampsAtMax() {
        val v = VolumeAccumulator(25)
        var level = 24
        repeat(50) { level = v.add(0.05f, level) }
        assertEquals(25, level)
    }

    @Test fun endResets_andNextGestureStartsFromSystemLevel() {
        val v = VolumeAccumulator(10)
        v.add(0.5f, 0)
        v.end()
        assertFalse(v.active)
        assertEquals(8, v.add(0.1f, 7)) // hardware buttons moved it to 7 meanwhile
    }

    @Test fun zeroMaxIsSafe() {
        assertEquals(0, VolumeAccumulator(0).add(0.5f, 0))
    }
}
