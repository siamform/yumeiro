package dev.asfakul.yumeiro.online

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineHistoryFeedTest {
    private fun e(name: String, adult: Boolean = false, type: String? = "Movie") =
        OnlineHistory.Entry("api", "u/$name", name, null, emptyMap(), type, "1", null, null, 0, 0, 0, adult)

    private val list = listOf(e("a"), e("b", adult = true), e("c", type = "NSFW"))

    @Test fun adultOffHidesAdult() = assertEquals(listOf("a"), OnlineHistory.forFeed(list, adultOn = false, private = false).map { it.name })
    @Test fun privateHidesAdult() = assertEquals(listOf("a"), OnlineHistory.forFeed(list, adultOn = true, private = true).map { it.name })
    @Test fun publicShowsAll() = assertEquals(listOf("a", "b", "c"), OnlineHistory.forFeed(list, adultOn = true, private = false).map { it.name })
}
