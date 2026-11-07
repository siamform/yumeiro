package dev.asfakul.mtrevanced.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryWorkerTest {
    @Test fun firstRunIsNeverNew() = assertEquals(emptyList<String>(), DiscoveryWorker.newItems(emptySet(), listOf("a", "b")))
    @Test fun onlyUnseenAreNew() = assertEquals(listOf("c"), DiscoveryWorker.newItems(setOf("a", "b"), listOf("a", "b", "c")))
    @Test fun nudgeOnlyInEveningAndSpaced() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(DiscoveryWorker.nudgeDue(10 * day, 6 * day, 19))
        assertFalse(DiscoveryWorker.nudgeDue(10 * day, 6 * day, 9))
        assertFalse(DiscoveryWorker.nudgeDue(10 * day, 9 * day, 19))
    }
    @Test fun summaryShortens() = assertEquals("a, b, c and 2 more", DiscoveryWorker.summary(listOf("a", "b", "c", "d", "e")))
}
