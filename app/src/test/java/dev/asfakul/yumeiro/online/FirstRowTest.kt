/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * FE-3 (docs/FEED-ENGINE-2.1.md): an extension's home shows at its first row. Before, the home came back only when
 * the slowest row of the batch answered (up to the 25 s row timeout), so a fast row sat hidden behind a slow one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRowTest {
    private class Mixed(override var name: String) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        override val hasMainPage = true
        override val mainPage = mainPageOf("fast" to "Fast", "s1" to "Slow 1", "s2" to "Slow 2", "s3" to "Slow 3")
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            if (request.data != "fast") delay(3_000)
            return newHomePageResponse(request.name, listOf(newMovieSearchResponse("T ${request.data}", "$mainUrl/${request.data}", TvType.Movie)))
        }
    }

    @Test fun firstRowIsHandedOverBeforeTheSlowRows() = runBlocking {
        val api = Mixed("YuFe3 ${System.nanoTime()}")
        val started = System.currentTimeMillis()
        val seen = Collections.synchronizedList(mutableListOf<Pair<Long, List<String>>>())
        val all = OnlineRepository.home(api, force = true) { rows -> seen += (System.currentTimeMillis() - started) to rows.map { it.title } }
        assertEquals(listOf("Fast", "Slow 1", "Slow 2", "Slow 3"), all.map { it.title })
        val first = seen.first()
        assertEquals(listOf("Fast"), first.second)
        assertTrue("first row after ${first.first} ms", first.first < 1_500)
        assertEquals("last update has every row in page order", all.map { it.title }, seen.last().second)
    }
}
