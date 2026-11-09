/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.lyrics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcLibTest {
    private fun hit(d: Double, synced: String?, plain: String? = "p", inst: Boolean = false) = LrcLib.Hit("t", "a", "al", d, synced, plain, inst)

    @Test fun prefersSameLengthThenSynced() {
        val best = LrcLib.pick(listOf(hit(200.0, null), hit(207.0, "[00:01.00]x"), hit(201.0, "[00:01.00]y")), 200)
        assertEquals("[00:01.00]y", best?.synced)
    }

    @Test fun rejectsOtherVersions() {
        assertNull(LrcLib.pick(listOf(hit(260.0, "[00:01.00]live")), 200))
    }

    @Test fun unknownLengthTakesSynced() {
        assertEquals("[00:01.00]s", LrcLib.pick(listOf(hit(100.0, null), hit(300.0, "[00:01.00]s")), 0)?.synced)
    }

    @Test fun parsesNullLyrics() {
        val h = LrcLib.parse(JSONObject("""{"trackName":"t","artistName":"a","albumName":"b","duration":233.0,"instrumental":true,"plainLyrics":null,"syncedLyrics":null}"""))
        assertNull(h.text); assertEquals(true, h.instrumental); assertEquals(233.0, h.durationSec, 0.0)
    }

    @Test fun cacheKeyIsStableAndCaseInsensitive() {
        assertEquals(LrcLib.cacheKey("A", "B", 1), LrcLib.cacheKey("a", "b", 1))
    }
}
