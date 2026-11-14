package dev.asfakul.yumeiro.round2

import dev.asfakul.yumeiro.online.GatherCap
import dev.asfakul.yumeiro.player.HeatGuard
import dev.asfakul.yumeiro.player.HeatGuard.Level
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Round 2, order 6: throttling follows thermal status, battery saver and visibility. */
class HeatGuardTest {
    @After fun reset() = HeatGuard.set(Level.COOL)

    @Test fun levels() {
        assertEquals(Level.COOL, HeatGuard.levelOf(0, false))
        assertEquals(Level.COOL, HeatGuard.levelOf(1, false)) // LIGHT
        assertEquals(Level.WARM, HeatGuard.levelOf(0, true)) // battery saver
        assertEquals(Level.WARM, HeatGuard.levelOf(2, false)) // MODERATE
        assertEquals(Level.HOT, HeatGuard.levelOf(3, false)) // SEVERE
        assertEquals(Level.HOT, HeatGuard.levelOf(6, true)) // SHUTDOWN
    }

    @Test fun intervals() {
        assertEquals(300L, HeatGuard.interval(300, level = Level.COOL))
        assertEquals(600L, HeatGuard.interval(300, level = Level.WARM))
        assertEquals(1200L, HeatGuard.interval(300, level = Level.HOT))
        assertEquals(2000L, HeatGuard.interval(300, foreground = false, level = Level.COOL))
        assertEquals(4000L, HeatGuard.interval(1000, foreground = false, level = Level.HOT))
    }

    @Test fun gpuAndMotionAndCaps() {
        assertTrue(HeatGuard.allowGpuUpscale(Level.WARM)); assertFalse(HeatGuard.allowGpuUpscale(Level.HOT))
        assertTrue(HeatGuard.allowDecorativeMotion(Level.COOL)); assertFalse(HeatGuard.allowDecorativeMotion(Level.WARM))
        assertEquals(listOf(6, 4, 2), Level.entries.map { HeatGuard.parallel(it) })
        assertEquals(listOf(12, 8, 5), Level.entries.map { HeatGuard.linkCap(it) })
    }

    private fun peak(level: Level): Int = runBlocking {
        val now = AtomicInteger(); val max = AtomicInteger()
        (1..20).map { async(kotlinx.coroutines.Dispatchers.Default) {
            HeatGuard.limited(level) { val n = now.incrementAndGet(); max.accumulateAndGet(n, ::maxOf); delay(30); now.decrementAndGet() }
        } }.awaitAll()
        max.get()
    }

    @Test fun parallelCallsAreCapped() {
        assertTrue(peak(Level.COOL) <= 6)
        assertTrue(peak(Level.WARM) <= 4)
        assertTrue(peak(Level.HOT) <= 2)
    }

    @Test fun gatheringStopsOnlyWhenPlayingWithEnoughBackups() {
        assertFalse(GatherCap.enough(started = false, playable = 50, subtitles = 3, sinceFirstMs = 60_000, cap = 12))
        assertFalse(GatherCap.enough(true, 11, 3, 60_000, 12))
        assertFalse("waits for late subtitles", GatherCap.enough(true, 12, 0, 5_000, 12))
        assertTrue(GatherCap.enough(true, 12, 0, 20_000, 12))
        assertTrue(GatherCap.enough(true, 12, 1, 1_000, 12))
    }
}
