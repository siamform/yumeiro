/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionBulkTest {
    @Test fun runsInOrderOneAtATimeAndCountsFailuresAndSkips() = runBlocking {
        val order = mutableListOf<String>()
        var running = 0; var maxRunning = 0
        val o = ExtensionBulk.runEach(listOf("a", "b", "c", "d"), { it.uppercase() }) {
            running++; maxRunning = maxOf(maxRunning, running)
            order += it
            kotlinx.coroutines.yield()
            running--
            when (it) { "b" -> throw ExtensionManager.ExtensionException("down"); "c" -> throw ExtensionReview.Declined("C") }
        }
        assertEquals(listOf("a", "b", "c", "d"), order)
        assertEquals(1, maxRunning)
        assertEquals(ExtensionBulk.Outcome(ok = 2, skipped = 1, failed = listOf("B")), o)
        assertEquals("Updated 2 of 4 · 1 skipped · failed: B", ExtensionBulk.summary("Updated", o))
    }

    @Test fun summaryForAFullSuccessAndForManyFailures() {
        assertEquals("Removed 3 extensions", ExtensionBulk.summary("Removed", ExtensionBulk.Outcome(3, 0, emptyList())))
        assertEquals("Turned on 1 extension", ExtensionBulk.summary("Turned on", ExtensionBulk.Outcome(1, 0, emptyList())))
        assertEquals("Updated 0 of 5 · failed: A, B, C and 2 more",
            ExtensionBulk.summary("Updated", ExtensionBulk.Outcome(0, 0, listOf("A", "B", "C", "D", "E"))))
    }

    @Test fun cancellationIsNotSwallowed() {
        val r = runCatching { runBlocking { ExtensionBulk.runEach(listOf(1, 2), { "$it" }) { throw kotlinx.coroutines.CancellationException("x") } } }
        assertEquals(true, r.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test fun selectAllTogglesOnlyWhatIsShown() {
        val shown = listOf("a", "b")
        assertEquals(setOf("x", "a", "b"), ExtensionBulk.toggleAll(setOf("x"), shown))
        assertEquals(setOf("x"), ExtensionBulk.toggleAll(setOf("x", "a", "b"), shown))
        assertEquals(setOf("a", "b"), ExtensionBulk.toggleAll(setOf("a"), shown))
        assertEquals(emptySet<String>(), ExtensionBulk.toggleAll(emptySet(), emptyList()))
        assertEquals(setOf("a"), ExtensionBulk.toggle(emptySet(), "a"))
        assertEquals(emptySet<String>(), ExtensionBulk.toggle(setOf("a"), "a"))
        assertEquals(setOf("a"), ExtensionBulk.prune(setOf("a", "gone"), listOf("a", "b")))
    }
}
