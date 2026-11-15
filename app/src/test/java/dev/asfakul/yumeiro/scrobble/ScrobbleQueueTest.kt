/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.scrobble

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScrobbleQueueTest {
    @Test fun oneBadEntryKeepsTheRestOfTheQueue() {
        // R16 (G6): one bad entry used to empty the queue; the next enqueue then saved only the new listen.
        val good = listOf(ScrobblePlay("A", "One", "", 200, 1_700_000_000), ScrobblePlay("B", "Two", "X", 180, 1_700_000_300))
        val a = JSONArray().apply { put(Scrobbler.encode(good[0])); put("oops"); put(org.json.JSONObject().put("t", "no artist")); put(Scrobbler.encode(good[1])) }
        assertEquals(good, Scrobbler.decodeQueue(a.toString()))
        assertTrue(Scrobbler.decodeQueue("not json").isEmpty())
        assertTrue(Scrobbler.decodeQueue(null).isEmpty())
    }
}
