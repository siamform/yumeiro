/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.anime

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import dev.asfakul.yumeiro.online.ExtensionSpeed
import dev.asfakul.yumeiro.sources.hub.YumeiroHubProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ANIME-2 (field probe 45421421959): searching "Yumeiro" showed "Yumeiro (!)" with no results, because a slow part
 * could use the hub's whole 25 s, which is also the app's own limit for one source, so the app cut the hub off first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Anime2SearchBudgetTest {
    private fun part(name: String, wait: Long, title: String) = object : MainAPI() {
        override var name = name
        override suspend fun search(query: String, page: Int): SearchResponseList {
            delay(wait)
            return newSearchResponseList(listOf(newTvSeriesSearchResponse(title, "$name:1", TvType.Anime, fix = false)), false)
        }
    }

    @Test fun aHangingPartDoesNotCostTheOthersTheirResults() = runBlocking {
        val hub = YumeiroHubProvider(listOf(part("fast", 50, "Fast show"), part("hang", 60_000, "Never")))
        val t0 = System.currentTimeMillis()
        val r = hub.search("show", 1)
        val took = System.currentTimeMillis() - t0
        assertEquals(listOf("Fast show"), r.items.map { it.name })
        assertTrue("hub answered in $took ms", took < ExtensionSpeed.MIN_SEARCH_MS)
        assertTrue(YumeiroHubProvider.PART_MS < ExtensionSpeed.MIN_SEARCH_MS)
    }
}
