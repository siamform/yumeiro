/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import dev.asfakul.yumeiro.online.OnlineBookmarks.Bookmark
import dev.asfakul.yumeiro.online.OnlineBookmarks.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAlertsPlanTest {
    private fun b(name: String, status: Status = Status.WATCHING, type: String? = "Anime", notify: Boolean = true) =
        Bookmark("ext", "/$name", name, null, emptyMap(), type, status, 0L, notify = notify)

    @Test fun watchedMatchesNewEpisodeCheck() {
        val all = listOf(b("a"), b("b", Status.PLAN), b("c", Status.ON_HOLD), b("d", Status.COMPLETED),
            b("e", Status.DROPPED), b("f", type = "Movie"), b("g", notify = false), b("h", type = null))
        assertEquals(listOf("a", "b", "c", "h"), CloudAlertsPlan.watched(all).map { it.name })
    }

    @Test fun followsAreCleanSortedCapped() {
        assertEquals(listOf(3, 5, 21), CloudAlertsPlan.follows(listOf(21, 5, 0, -2, 5, 3)))
        assertEquals(CloudAlertsPlan.MAX_FOLLOWS, CloudAlertsPlan.follows((1..1000).toList()).size)
    }

    @Test fun fingerprintTracksEndpointAndFollows() {
        val a = CloudAlertsPlan.fingerprint("https://ntfy.sh/upX?up=1", listOf(1, 2))
        assertEquals(a, CloudAlertsPlan.fingerprint("https://ntfy.sh/upX?up=1", listOf(1, 2)))
        assertNotEquals(a, CloudAlertsPlan.fingerprint("https://ntfy.sh/upY?up=1", listOf(1, 2)))
        assertNotEquals(a, CloudAlertsPlan.fingerprint("https://ntfy.sh/upX?up=1", listOf(1, 3)))
        assertEquals(24, a.length)
    }

    @Test fun dueOnlyWhenChangedOrStale() {
        val now = 1_800_000_000_000L
        assertFalse(CloudAlertsPlan.due("x", "x", now, now - 3_600_000))
        assertTrue(CloudAlertsPlan.due("x", "y", now, now))
        assertTrue(CloudAlertsPlan.due("x", null, now, 0))
        assertTrue(CloudAlertsPlan.due("x", "x", now, now - CloudAlertsPlan.REFRESH_MS))
        assertTrue(CloudAlertsPlan.due("x", "x", now, now + 1)) // clock went back
    }

    @Test fun retryBacksOffAndCapsAtRefresh() {
        val h = 3600_000L
        assertEquals(6 * h, CloudAlertsPlan.retryDelay(1))
        assertEquals(12 * h, CloudAlertsPlan.retryDelay(2))
        assertEquals(48 * h, CloudAlertsPlan.retryDelay(4))
        assertEquals(CloudAlertsPlan.REFRESH_MS, CloudAlertsPlan.retryDelay(5))
        assertEquals(CloudAlertsPlan.REFRESH_MS, CloudAlertsPlan.retryDelay(50))
        assertEquals(6 * h, CloudAlertsPlan.retryDelay(0))
    }

    @Test fun airingKeepsSaneTimesOnly() {
        val now = 1_800_000_000L
        val got = CloudAlertsPlan.airing(
            listOf(5 to now + 600, 5 to now + 900, 3 to null, 0 to now, 9 to now - 7200, 7 to now + 16 * 86_400L, 2 to now - 60),
            now,
        )
        assertEquals(listOf(2 to now - 60, 5 to now + 600), got)
    }
}
