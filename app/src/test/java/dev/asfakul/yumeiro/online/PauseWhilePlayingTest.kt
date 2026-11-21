/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * FE-P (docs/FEED-ENGINE-2.1.md, owner order playing > picked > on screen > background): while something plays,
 * Yumix background fetches run one at a time; a picked extension still gets a slot at once; when playback stops the
 * waiting background fetches get every slot back. Before, the feed kept three background fetches going during playback.
 */
class PauseWhilePlayingTest {
    private suspend fun settle() = repeat(20) { yield() }

    @After fun stop() { PlayingNow.video(false); PlayingNow.music(false) }

    @Test fun backgroundStepsBackWhilePlayingAndComesBackAfter() = runBlocking {
        val gate = PriorityGate(3) { PlayingNow.backgroundLimit(3) }
        PlayingNow.video(true)
        val running = AtomicInteger(); val hold = CompletableDeferred<Unit>()
        val bg = (1..3).map { i -> launch { gate.run("bg$i", PriorityGate.BACKGROUND) { running.incrementAndGet(); hold.await() } } }
        settle()
        assertEquals("background jobs while playing", 1, running.get())
        val picked = launch { gate.run("picked", PriorityGate.PICKED) { running.incrementAndGet(); hold.await() } }
        settle()
        assertEquals("picked runs at once", 2, running.get())
        PlayingNow.video(false); gate.poke(); settle()
        assertEquals("all back after playback", 4, running.get())
        hold.complete(Unit); (bg + picked).forEach { it.join() }
        assertEquals(0, gate.waitingCount())
    }

    @Test fun musicCountsAsPlayingToo() {
        assertEquals(3, PlayingNow.backgroundLimit(3))
        PlayingNow.music(true)
        assertEquals(1, PlayingNow.backgroundLimit(3))
        PlayingNow.music(false)
        assertEquals(3, PlayingNow.backgroundLimit(3))
    }
}
