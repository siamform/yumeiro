package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.player.HeatGuard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E4 part 2: background feed work steps back on a hot phone or under memory pressure (and while playing, FE-P);
 * picked / on-screen work is untouched (the gate's other lanes).
 */
class FeedThrottleTest {
    @After fun tearDown() { HeatGuard.set(HeatGuard.Level.COOL); FeedThrottle.reset() }

    @Test fun limits() {
        assertEquals(3, FeedThrottle.limit(3, playing = false, heat = HeatGuard.Level.COOL, memoryLow = false))
        assertEquals(2, FeedThrottle.limit(3, false, HeatGuard.Level.WARM, false))
        assertEquals(1, FeedThrottle.limit(3, false, HeatGuard.Level.HOT, false))
        assertEquals(1, FeedThrottle.limit(3, false, HeatGuard.Level.COOL, memoryLow = true))
        assertEquals(1, FeedThrottle.limit(3, playing = true, heat = HeatGuard.Level.COOL, memoryLow = false))
        assertEquals("never 0", 1, FeedThrottle.limit(1, false, HeatGuard.Level.WARM, false))
        assertEquals(5, FeedThrottle.limit(6, false, HeatGuard.Level.WARM, false))
    }

    @Test fun trimLevels() {
        assertFalse("RUNNING_MODERATE", FeedThrottle.isPressure(5))
        assertTrue("RUNNING_LOW", FeedThrottle.isPressure(10))
        assertTrue("RUNNING_CRITICAL", FeedThrottle.isPressure(15))
        assertFalse("UI_HIDDEN", FeedThrottle.isPressure(20))
        assertFalse("BACKGROUND", FeedThrottle.isPressure(40))
        assertTrue("MODERATE", FeedThrottle.isPressure(60))
        assertTrue("COMPLETE", FeedThrottle.isPressure(80))
    }

    @Test fun memoryLowLastsAMinute() {
        FeedThrottle.memoryLow(now = 1_000)
        assertTrue(FeedThrottle.isMemoryLow(now = 1_000 + FeedThrottle.MEMORY_LOW_MS - 1))
        assertFalse(FeedThrottle.isMemoryLow(now = 1_000 + FeedThrottle.MEMORY_LOW_MS))
    }

    /** The real gates read it: Yumix background slots and the shared extension-call limit both use backgroundLimit. */
    @Test fun gatesStepBackWhenHot() {
        assertEquals(3, PlayingNow.backgroundLimit(3))
        HeatGuard.set(HeatGuard.Level.HOT)
        assertEquals(1, PlayingNow.backgroundLimit(3))
        HeatGuard.set(HeatGuard.Level.WARM)
        assertEquals(2, PlayingNow.backgroundLimit(3))
        HeatGuard.set(HeatGuard.Level.COOL)
        FeedThrottle.memoryLow()
        assertEquals(1, PlayingNow.backgroundLimit(3))
    }
}
