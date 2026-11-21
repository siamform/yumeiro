package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * FE-S3 (SpeedDeviceTest lowram run 44884969625: a repeated search still waited 15.2 s for a site that had just
 * timed out, because failures are not saved): a failure is remembered for 2 minutes in memory; Retry asks again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchFailMemoryTest {
    private class Broken(override var name: String) : MainAPI() {
        override var mainUrl = "https://example.invalid"
        val calls = AtomicInteger()
        override suspend fun search(query: String): List<SearchResponse> { calls.incrementAndGet(); throw java.io.IOException("down") }
        override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
    }

    @Test fun recentFailureAnswersAtOnceAndRetryAsksAgain() = runBlocking {
        val q = "fails ${System.nanoTime()}"
        val api = Broken("YuFeS3 ${System.nanoTime()}")
        assertNotNull(OnlineRepository.search(q, listOf(api)).first().error)
        assertEquals(1, api.calls.get())
        assertNotNull(OnlineRepository.search(q, listOf(api)).first().error)
        assertEquals("asked a site that just failed again", 1, api.calls.get())
        assertNotNull(OnlineRepository.search(q, listOf(api), retry = true).first().error)
        assertEquals("Retry must ask again", 2, api.calls.get())
    }
}
