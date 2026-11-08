package dev.asfakul.yumeiro.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // org.json is an Android stub on the plain JVM
class AppFeedTest {
    private val json = """
        {"items":[
          {"id":"a","kind":"extension","title":"New extension","body":"x","at":"2026-11-01T00:00:00Z","link":"market"},
          {"id":"b","kind":"release","title":"v2","at":1800000000000,"link":"javascript:alert(1)"},
          {"id":"","title":"no id"},
          {"id":"c","title":"","at":1}
        ]}
    """.trimIndent()

    @Test fun parsesAndSortsNewestFirst() {
        val items = AppFeed.parse(json)
        assertEquals(listOf("b", "a"), items.map { it.id })
        assertEquals(Inbox.Kind.UPDATE, items[0].kind)
        assertEquals(Inbox.Kind.EXTENSION, items[1].kind)
        assertEquals(1_793_491_200_000L, items[1].at)
    }

    @Test fun unsafeLinksAreDropped() {
        val items = AppFeed.parse(json)
        assertNull(items[0].link)
        assertEquals("market", items[1].link)
        assertEquals("https://example.com/x", AppFeed.safeLink("https://example.com/x"))
        assertNull(AppFeed.safeLink("http://example.com"))
        assertNull(AppFeed.safeLink("intent://evil"))
    }

    @Test fun freshSkipsOldAndKnown() {
        val items = AppFeed.parse(json)
        assertEquals(listOf("b"), AppFeed.fresh(items, 1_793_491_200_000L, emptySet()).map { it.id })
        assertEquals(emptyList<String>(), AppFeed.fresh(items, 0, setOf("a", "b")).map { it.id })
    }

    private fun item(id: String, at: Long) = AppFeed.FeedItem(id, Inbox.Kind.NEWS, id, "", at, null)

    @Test fun firstSyncStillPushesTheNewestRecentItem() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val add = listOf(item("new", now - day), item("old", now - 10 * day))
        assertEquals(setOf(0), AppFeed.shadePlan(add, first = true, now = now))
        assertEquals(emptySet<Int>(), AppFeed.shadePlan(listOf(item("old", now - 10 * day)), first = true, now = now))
    }

    @Test fun laterSyncsPushUpToTwo() {
        val add = (1..4).map { item("i$it", 1000L - it) }
        assertEquals(setOf(0, 1), AppFeed.shadePlan(add, first = false, now = 2000L))
    }
}
