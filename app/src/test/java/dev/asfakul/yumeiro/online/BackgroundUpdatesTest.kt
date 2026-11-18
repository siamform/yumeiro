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

/** NBUG-16 NB16-6 / NB16-7: when the background checks run, and what the user is told. */
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
        val mixed = BackgroundUpdates.Outcome(ExtensionManager.SyncResult(updated = listOf("A", "B"), heldBack = listOf("C", "D")), listOf("Sports TV"))
        assertEquals(
            listOf("2 extensions updated", "2 extension updates were held back by the safety check. Open Extensions and tap Update to review", "Updated Sports TV"),
            BackgroundUpdates.messages(mixed),
        )
        // Old wording kept for a single update.
        assertEquals(listOf("A extension updated"), BackgroundUpdates.messages(BackgroundUpdates.Outcome(ExtensionManager.SyncResult(updated = listOf("A")))))
    }
}
