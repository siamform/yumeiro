/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NBUG-16 NB16-6 / NB16-7 and B11: when the background checks run, and what interrupts the user. */
class BackgroundUpdatesTest {
    @Test fun runsOnEveryReturnButNotTwiceInAMinute() {
        assertTrue(BackgroundUpdates.due(now = 1_000, last = 0))
        assertFalse(BackgroundUpdates.due(now = 30_000, last = 1_000))
        assertTrue(BackgroundUpdates.due(now = 1_000 + BackgroundUpdates.MIN_GAP_MS, last = 1_000))
        assertTrue("clock moved back", BackgroundUpdates.due(now = 500, last = 1_000))
    }

    @Test fun heldBackUpdatesAreReported() {
        val none = BackgroundUpdates.Outcome()
        assertTrue(BackgroundUpdates.messages(none).isEmpty())
        val one = BackgroundUpdates.Outcome(ExtensionManager.SyncResult(heldBack = listOf("StreamPlay")))
        assertEquals(listOf("StreamPlay update was held back by the safety check. Open Extensions and tap Update to review"), BackgroundUpdates.messages(one))
    }

    /** B11: a successful automatic update or playlist refresh puts nothing on screen. */
    @Test fun successfulUpdatesAreSilent() {
        val ok = BackgroundUpdates.Outcome(ExtensionManager.SyncResult(updated = listOf("A", "B")), listOf("Sports TV"))
        assertEquals(emptyList<String>(), BackgroundUpdates.messages(ok))
        assertEquals(listOf("2 extensions updated: A, B", "Updated Sports TV"), BackgroundUpdates.quietNotes(ok))
        val mixed = BackgroundUpdates.Outcome(ExtensionManager.SyncResult(updated = listOf("A"), heldBack = listOf("C", "D")))
        assertEquals(listOf("2 extension updates were held back by the safety check. Open Extensions and tap Update to review"), BackgroundUpdates.messages(mixed))
    }

    /** B11: one failed update (a network blip) is retried quietly; three checks in a row is a real failure, told once. */
    @Test fun onlyARepeatedFailureSpeaksUp() {
        val fail = ExtensionManager.SyncResult(failed = listOf("X"))
        var streaks = emptyMap<String, Int>()
        val told = ArrayList<String>()
        repeat(5) { val (n, reached) = BackgroundUpdates.nextStreaks(streaks, fail); streaks = n; told += reached }
        assertEquals(listOf("X"), told)
        assertEquals(listOf("X couldn't update after 3 tries. Open Extensions to update or reinstall"),
            BackgroundUpdates.messages(BackgroundUpdates.Outcome(stuck = listOf("X"))))
        // A success ends the streak; offline (the list could not be read) changes nothing.
        assertEquals(emptyMap<String, Int>(), BackgroundUpdates.nextStreaks(streaks, ExtensionManager.SyncResult(updated = listOf("X"))).first)
        assertEquals(streaks, BackgroundUpdates.nextStreaks(streaks, ExtensionManager.SyncResult(error = "offline")).first)
    }
}
