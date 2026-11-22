package dev.asfakul.yumeiro.online

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** FE-2 (docs/FEED-ENGINE-2.1.md): the Yumix queue serves the picked extension first, even after loading began. */
class PriorityGateTest {
    private suspend fun settle() = repeat(20) { yield() }

    @Test fun pickedExtensionJumpsTheQueueAndBumpMovesAWaitingOne() = runBlocking {
        val gate = PriorityGate(1)
        val order = Collections.synchronizedList(mutableListOf<String>())
        val hold = CompletableDeferred<Unit>()
        val first = launch { gate.run("busy", PriorityGate.BACKGROUND) { hold.await() } }
        settle()
        val jobs = listOf("a" to PriorityGate.BACKGROUND, "b" to PriorityGate.BACKGROUND, "c" to PriorityGate.BACKGROUND, "picked" to PriorityGate.PICKED)
            .map { (k, p) -> launch { gate.run(k, p) { order += k } }.also { settle() } }
        assertEquals(4, gate.waitingCount())
        assertTrue(gate.bump("c", PriorityGate.PICKED))
        hold.complete(Unit)
        (jobs + first).forEach { it.join() }
        assertEquals(listOf("c", "picked", "a", "b"), order)
    }

    @Test fun cancelledWaiterLeavesTheQueueAndPermitsAreNotLost() = runBlocking {
        val gate = PriorityGate(1)
        val hold = CompletableDeferred<Unit>()
        val first = launch { gate.run("busy", PriorityGate.BACKGROUND) { hold.await() } }
        settle()
        val gone = launch { gate.run("gone", PriorityGate.PICKED) { error("must not run") } }
        settle()
        gone.cancelAndJoin()
        assertEquals(0, gate.waitingCount())
        hold.complete(Unit); first.join()
        var ran = false
        gate.run("next", PriorityGate.BACKGROUND) { ran = true }
        assertTrue(ran)
    }
}
