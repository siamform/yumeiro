/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * SPEED-1 / SPEED-2 (owner 2026-11-21: feed, extension loading and search speed are mandatory).
 * Saved home pages and saved search results must come back at once, without asking the extension and without
 * waiting for a permit behind slow live calls. Before: the cache check sat inside the permit, so with six slow
 * extensions searching, a cached answer waited for one of them to finish (up to the 25 s timeout).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeedCacheFirstTest {
    private class Fake(override var name: String, private val slowMs: Long = 0) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        override val hasMainPage = true
        override val mainPage = mainPageOf("a" to "Popular")
        val homeCalls = AtomicInteger(); val searchCalls = AtomicInteger()
        private fun item(): SearchResponse = newMovieSearchResponse("Title $name", "$mainUrl/t/${name.hashCode()}", TvType.Movie)
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            homeCalls.incrementAndGet(); delay(slowMs)
            return newHomePageResponse("Popular", listOf(item()))
        }
        override suspend fun search(query: String): List<SearchResponse> {
            searchCalls.incrementAndGet(); delay(slowMs)
            return listOf(item())
        }
    }

    @Test fun cachedHomeAnswersWithoutAskingTheExtension() = runBlocking {
        val api = Fake("YuSpeed1 home ${System.nanoTime()}")
        assertNull(OnlineRepository.cachedHome(api))
        assertTrue(OnlineRepository.home(api, force = true).isNotEmpty())
        val calls = api.homeCalls.get()
        val cached = OnlineRepository.cachedHome(api)
        assertTrue(!cached.isNullOrEmpty())
        assertEquals(calls, api.homeCalls.get())
    }

    /** FE-1 (docs/FEED-ENGINE-2.1.md FE-RC2): a 7 h old saved home shows at once and is marked for a live refresh. */
    @Test fun oldSavedHomeShowsAtOnceAndIsMarkedStale() = runBlocking {
        val api = Fake("YuFe1 home ${System.nanoTime()}")
        assertTrue(OnlineRepository.home(api, force = true).isNotEmpty())
        val calls = api.homeCalls.get()
        val file = dev.asfakul.yumeiro.cache.DataCache.fileFor("home", "home|${api.name}|${api.mainUrl}|")
        assertTrue("saved home file missing", file != null && file.isFile)
        assertTrue(file!!.setLastModified(System.currentTimeMillis() - 7 * 3_600_000L))
        OnlineRepository.refreshAfterExtensionChange()
        dev.asfakul.yumeiro.cache.DataCache.clearMemory()
        OnlineRepository.takeStaleHome(api.name)
        assertNull("6 h fresh window unchanged", OnlineRepository.cachedHome(api))
        val shown = OnlineRepository.feedHome(api)
        assertTrue("old saved home not shown", !shown.isNullOrEmpty())
        assertEquals(calls, api.homeCalls.get())
        assertTrue("old saved home not marked for refresh", OnlineRepository.takeStaleHome(api.name))
    }

    @Test fun cachedSearchIsSentAtOnceWhileSlowSearchesHoldEveryPermit() = runBlocking {
        val q = "speed ${System.nanoTime()}"
        val fast = Fake("YuSpeed2 cached ${System.nanoTime()}")
        OnlineRepository.search(q, listOf(fast)).first()
        val callsBefore = fast.searchCalls.get()
        val slow = (1..8).map { Fake("YuSpeed2 slow $it ${System.nanoTime()}", slowMs = 20_000) }
        val t0 = System.nanoTime()
        val firstUpdate = withTimeout(10_000) {
            OnlineRepository.search(q, slow + fast).first { it.api == fast.name }
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("cached search waited $ms ms", ms < 3_000)
        assertTrue(firstUpdate.items.isNotEmpty())
        assertEquals(callsBefore, fast.searchCalls.get())
    }
}
