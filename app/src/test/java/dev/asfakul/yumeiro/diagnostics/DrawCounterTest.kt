package dev.asfakul.yumeiro.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** FREEZE-4: draw counts go into the memory trail, and each sample counts only its own 30 s. Old code: no DrawCounter (compile failure). */
class DrawCounterTest {
    @After fun tearDown() = DrawCounter.resetForTests()

    @Test fun drainCountsSinceTheLastDrain() {
        repeat(3) { DrawCounter.hit("home") }
        DrawCounter.hit("live")
        assertEquals("draws home=3 live=1", DrawCounter.drain())
        assertEquals("", DrawCounter.drain())
        DrawCounter.hit("live")
        assertEquals("draws live=1", DrawCounter.drain())
    }
}
