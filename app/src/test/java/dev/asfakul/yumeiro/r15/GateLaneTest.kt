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

/** R16: a user request never queues behind health probes on the drama host. */
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
        lane.background = false // a user joined this request
        val waited = joined.await().first
        assertTrue("promoted request waited ${waited}ms for the probe", waited < 700)
        hold.join()
    }
}
