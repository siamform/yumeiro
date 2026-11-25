/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.stremio

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SEARCH-CAP-2 (A0): a Stremio catalog add-on without search is searched on its catalog pages. Real Cinemeta pages. */
class StremioIndexTest {
    private fun fx(n: String) = requireNotNull(javaClass.classLoader!!.getResource("fixtures/a0/$n")).readText()
    private val metas: List<JsonObject> =
        Json.parseToJsonElement(fx("cinemeta_search_movie_inception.json")).jsonObject["metas"]!!.jsonArray.map { it.jsonObject } +
            Json.parseToJsonElement(fx("cinemeta_search_series_breaking_bad.json")).jsonObject["metas"]!!.jsonArray.map { it.jsonObject }

    @Test fun matchRanksByTitle() {
        val r = StremioIndex.match("breaking bad", metas)
        assertTrue(r.isNotEmpty())
        assertEquals("Breaking Bad", r.first()["name"].toString().trim('"'))
        assertTrue("unrelated words find nothing", StremioIndex.match("zzqx nothing", metas).isEmpty())
    }

    @Test fun pagesAreReadOnceThenReusedUntilStale() = runBlocking {
        var now = 0L
        var reads = 0
        val idx = StremioIndex { now }
        repeat(3) { idx.search("inception") { reads++; metas } }
        assertEquals(1, reads)
        now += StremioIndex.TTL_MS + 1
        idx.search("inception") { reads++; metas }
        assertEquals(2, reads)
    }

    @Test fun aFailedRefreshKeepsTheOldIndex() = runBlocking {
        var now = 0L
        val idx = StremioIndex { now }
        assertTrue(idx.search("inception") { metas }.isNotEmpty())
        now += StremioIndex.TTL_MS + 1
        assertTrue(idx.search("inception") { throw java.io.IOException("offline") }.isNotEmpty())
    }

    @Test fun imdbIdsAreRecognised() {
        assertEquals("tt1375666", StremioIndex.imdbOf("tt1375666", null))
        assertEquals("tt0903747", StremioIndex.imdbOf("tt0903747:1:2", null))
        assertEquals("tt0903747", StremioIndex.imdbOf("kitsu:123", "tt0903747"))
        assertNull(StremioIndex.imdbOf("kitsu:123", null))
        assertNull(StremioIndex.imdbOf("ttabc", null))
    }
}
