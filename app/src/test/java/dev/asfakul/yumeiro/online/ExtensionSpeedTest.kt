/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import dev.asfakul.yumeiro.online.ExtensionSpeed.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E4: extension homes load fastest-first; three failures in a row quarantine an extension for six hours. */
class ExtensionSpeedTest {
    private val now = 1_000_000_000L

    @Test fun averageMovesTowardsNewTimesAndSuccessClearsFailures() {
        var s = ExtensionSpeed.next(null, 2_000, ok = true, now = now)
        assertEquals(2_000, s.avgMs)
        s = ExtensionSpeed.next(s, 1_000, ok = true, now = now)
        assertEquals(1_700, s.avgMs)
        s = ExtensionSpeed.next(s, 9_000, ok = false, now = now)
        assertEquals(1 to 1_700L, s.fails to s.avgMs)
        s = ExtensionSpeed.next(s, 1_000, ok = true, now = now)
        assertEquals(0, s.fails)
    }

    @Test fun threeFailuresInARowQuarantineForSixHours() {
        var s: Stat? = null
        repeat(2) { s = ExtensionSpeed.next(s, 30_000, ok = false, now = now) }
        assertFalse(ExtensionSpeed.quarantined(s, now))
        s = ExtensionSpeed.next(s, 30_000, ok = false, now = now)
        assertTrue(ExtensionSpeed.quarantined(s, now + 1))
        assertFalse(ExtensionSpeed.quarantined(s, now + ExtensionSpeed.QUARANTINE_MS))
    }

    @Test fun orderIsSelectedThenFastThenUnknownThenSlowThenQuarantined() {
        val stats = mapOf(
            "Fast" to Stat(800, 0, 0), "Slow" to Stat(9_000, 0, 0),
            "Dead" to Stat(500, 3, now - 1_000), "Picked" to Stat(20_000, 0, 0),
        )
        val names = listOf("Dead", "Slow", "New1", "Picked", "Fast", "New2")
        val got = ExtensionSpeed.order(names, { it }, "Picked", { stats[it] }, now)
        assertEquals(listOf("Picked", "Fast", "New1", "New2", "Slow", "Dead"), got)
    }

    @Test fun withNoMeasurementsTheOrderIsUnchangedApartFromTheSelectedOne() {
        val names = listOf("A", "B", "C", "D")
        assertEquals(listOf("C", "A", "B", "D"), ExtensionSpeed.order(names, { it }, "C", { null }, now))
        assertEquals(names, ExtensionSpeed.order(names, { it }, null, { null }, now))
    }
}
