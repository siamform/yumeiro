package dev.asfakul.yumeiro.r15

import dev.asfakul.yumeiro.sources.drama.GateLane
import dev.asfakul.yumeiro.sources.drama.OnShortGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** R16/R17: a request the user waits for never queues behind probes or feed loads on the drama host. */
class GateLaneTest {
    private fun gate() = OnShortGate(maxConcurrent = 2, minGapMs = 0)

    @Test fun userCallStartsWhileProbesAreQueued() = runBlocking(Dispatchers.Default) {
        val g = gate()
        val bgRunning = AtomicInteger(); var bgPeak = 0
        val probes = (1..6).map {
            launch(GateLane(true)) { g.call("probe$it") { bgPeak = maxOf(bgPeak, bgRunning.incrementAndGet()); delay(600); bgRunning.decrementAndGet(); 200 to "" } }
        }
        delay(150)
        val t0 = System.currentTimeMillis()
        val waited = async { g.call("user") { (System.currentTimeMillis() - t0).toInt() to "" } }.await().first
        assertTrue("user call waited ${waited}ms behind probes", waited < 300)
        probes.forEach { it.join() }
        assertEquals("at most one probe on the host at a time", 1, bgPeak)
    }

    @Test fun probesWaitWhileUserCallsAreQueued() = runBlocking(Dispatchers.Default) {
        val g = gate()
        val order = java.util.Collections.synchronizedList(ArrayList<String>())
        // Fill all user slots, queue a probe and more user calls; the users go first.
        val users = (1..5).map { i -> launch { g.call("u$i") { order += "u$i"; delay(200); 200 to "" } } }
        delay(50)
        val probe = launch(GateLane(true)) { g.call("p") { order += "p"; 200 to "" } }
        users.forEach { it.join() }; probe.join()
        assertEquals("probe ran last: $order", "p", order.last())
    }

    @Test fun promotedProbeTakesAUserSlot() = runBlocking(Dispatchers.Default) {
        val g = gate()
        val hold = launch(GateLane(true)) { g.call("hold") { delay(1_500); 200 to "" } }
        delay(100)
        val lane = GateLane(true)
        val t0 = System.currentTimeMillis()
        val joined = async(lane) { g.call("shared") { (System.currentTimeMillis() - t0).toInt() to "" } }
        delay(200)
        lane.promote(GateLane.INTERACTIVE) // a user joined this request
        val waited = joined.await().first
        assertTrue("promoted request waited ${waited}ms for the probe", waited < 700)
        hold.join()
    }

    /** R17 (run 43664241241): Play waited 216 s behind shelves and prefetches of ten studios. */
    @Test fun tapStartsAtOnceWhileTheFeedFillsTheHost() = runBlocking(Dispatchers.Default) {
        val g = gate()
        val feedPeak = AtomicInteger(); val feedRunning = AtomicInteger()
        // Ten shelf loads that hang (the site stalls for 25 s under load).
        val feed = (1..10).map { i -> launch { g.call("shelf$i") { feedPeak.accumulateAndGet(feedRunning.incrementAndGet(), ::maxOf); delay(1_500); feedRunning.decrementAndGet(); 200 to "" } } }
        delay(150)
        val t0 = System.currentTimeMillis()
        val waited = withContext(GateLane(GateLane.INTERACTIVE)) { g.call("play") { (System.currentTimeMillis() - t0).toInt() to "" } }.first
        assertTrue("Play waited ${waited}ms behind the feed", waited < 300)
        feed.forEach { it.join() }
        assertEquals("feed never takes the slot kept for the user", 2, feedPeak.get())
    }

    @Test fun tapsGoBeforeQueuedFeedCalls() = runBlocking(Dispatchers.Default) {
        val g = gate()
        val order = java.util.Collections.synchronizedList(ArrayList<String>())
        val feed = (1..6).map { i -> launch { g.call("f$i") { order += "f$i"; delay(200); 200 to "" } } }
        delay(50)
        // Three taps: one takes the kept slot, the other two must still beat the queued feed calls.
        val taps = (1..3).map { i -> launch(GateLane(GateLane.INTERACTIVE)) { g.call("t$i") { order += "t$i"; delay(200); 200 to "" } } }
        (feed + taps).forEach { it.join() }
        val lastTap = order.indexOfLast { it.startsWith("t") }
        val queuedFeed = order.drop(2).indexOfFirst { it.startsWith("f") } + 2
        assertTrue("taps ran after queued feed calls: $order", lastTap < queuedFeed)
    }

    @Test fun cancelledWaiterGivesItsSlotBack() = runBlocking(Dispatchers.Default) {
        val g = gate()
        val hold = (1..2).map { launch { g.call("h$it") { delay(400); 200 to "" } } }
        delay(50)
        val gone = launch { g.call("gone") { 200 to "" } }
        delay(50); gone.cancel()
        hold.forEach { it.join() }
        // Both normal slots are free again: two feed calls run side by side.
        val running = AtomicInteger(); val peak = AtomicInteger()
        (1..2).map { launch { g.call("n$it") { peak.accumulateAndGet(running.incrementAndGet(), ::maxOf); delay(200); running.decrementAndGet(); 200 to "" } } }.forEach { it.join() }
        assertEquals(2, peak.get())
    }

    /** R16 G4 (device logs): feed loads waited up to 20 s while a probe held one of two feed slots. */
    @Test fun feedLoadsDontWaitForARunningProbe() = runBlocking(Dispatchers.Default) {
        val g = gate()
        val probe = launch(GateLane(true)) { g.call("probe") { delay(1_500); 200 to "" } }
        delay(100)
        val running = AtomicInteger(); val peak = AtomicInteger()
        val t0 = System.currentTimeMillis()
        val waits = (1..2).map { i -> async { g.call("shelf$i") { val w = (System.currentTimeMillis() - t0).toInt(); peak.accumulateAndGet(running.incrementAndGet(), ::maxOf); delay(300); running.decrementAndGet(); w to "" }.first } }.map { it.await() }
        assertTrue("feed waited ${waits}ms behind a probe", waits.all { it < 300 })
        assertEquals("both feed slots used next to the probe", 2, peak.get())
        // The user's extra slot is still free too.
        probe.join()
    }
}
