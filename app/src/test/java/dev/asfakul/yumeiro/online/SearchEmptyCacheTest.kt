package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * FE-S1 (SpeedDeviceTest run 44883372732: a repeated search still took 15.1 s for every extension to answer):
 * an extension that has nothing for a query was never remembered, so each repeat asked it live again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchEmptyCacheTest {
    private class Empty(override var name: String) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        val calls = AtomicInteger()
        override suspend fun search(query: String): List<SearchResponse> { calls.incrementAndGet(); return emptyList() }
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    }

    @Test fun emptyAnswerIsRememberedAndNotAskedAgain() = runBlocking {
        val q = "nothing here ${System.nanoTime()}"
        val api = Empty("YuFeS1 ${System.nanoTime()}")
        val first = OnlineRepository.search(q, listOf(api)).first()
        assertTrue(first.items.isEmpty())
        assertEquals(1, api.calls.get())
        val again = OnlineRepository.search(q, listOf(api)).first()
        assertTrue(again.items.isEmpty())
        assertEquals("asked the extension again", 1, api.calls.get())
    }
}
