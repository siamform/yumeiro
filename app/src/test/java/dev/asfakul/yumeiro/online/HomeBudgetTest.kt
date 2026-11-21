package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.mainPageOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * FE-2b (docs/FEED-ENGINE-2.1.md FE-RC1): a dead site (every row times out) is asked one batch of rows, not three,
 * so it holds a Yumix slot for one row timeout (25 s) instead of 75 s. Rows that fail quickly still get batch 2 and 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeBudgetTest {
    private class Dead(override var name: String, private val hangMs: Long, private val fail: Boolean) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        override val hasMainPage = true
        override val mainPage = mainPageOf(*(1..12).map { "p$it" to "Row $it" }.toTypedArray())
        val calls = AtomicInteger()
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            calls.incrementAndGet(); delay(hangMs)
            if (fail) throw java.io.IOException("busy")
            throw IllegalStateException("unreachable")
        }
    }

    @After fun reset() { OnlineRepository.homeTimeoutMs = 25_000L }

    @Test fun deadSiteIsAskedOneBatchOnly() = runBlocking {
        OnlineRepository.homeTimeoutMs = 300
        val api = Dead("YuFe2 dead ${System.nanoTime()}", hangMs = 5_000, fail = false)
        runCatching { OnlineRepository.home(api, force = true) }
        assertEquals(4, api.calls.get())
    }

    @Test fun quicklyFailingRowsStillGetEveryBatch() = runBlocking {
        val api = Dead("YuFe2 busy ${System.nanoTime()}", hangMs = 0, fail = true)
        runCatching { OnlineRepository.home(api, force = true) }
        assertEquals(12, api.calls.get())
    }
}
