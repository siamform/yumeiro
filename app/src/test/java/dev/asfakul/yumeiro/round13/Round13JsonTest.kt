package dev.asfakul.yumeiro.round13

import dev.asfakul.yumeiro.notify.Inbox
import dev.asfakul.yumeiro.notify.NotifyPacer
import dev.asfakul.yumeiro.online.EpisodeStills
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // org.json is an Android stub on the plain JVM
class Round13JsonTest {
    @Test fun episode_stills_from_anizip_and_tvmaze() {
        val ani = """{"episodes":{"1":{"image":"https://img/1.jpg","seasonNumber":1,"episodeNumber":1},"2":{"image":"","seasonNumber":1,"episodeNumber":2},"13":{"image":"https://img/13.jpg","seasonNumber":2,"episodeNumber":1}}}"""
        val a = EpisodeStills.parseAniZip(ani)
        assertEquals("https://img/1.jpg", a[":1"]); assertEquals("https://img/13.jpg", a["2:1"]); assertTrue(":2" !in a)
        val maze = """[{"season":1,"number":1,"image":{"medium":"http://static.tvmaze.com/1.jpg"}},{"season":1,"number":2,"image":null}]"""
        val m = EpisodeStills.parseTvMaze(maze)
        assertEquals(mapOf("1:1" to "https://static.tvmaze.com/1.jpg"), m)
    }

    @Test fun pacer_queue_round_trips_and_dedupes() {
        val now = 10_000_000L
        val a = NotifyPacer.Pending(Inbox.Item("ep-1", Inbox.Kind.EPISODE, "A", "a", now, link = null), 1, now)
        val b = NotifyPacer.Pending(Inbox.Item("feed-2", Inbox.Kind.NEWS, "B", "b", now, link = "https://yumeiro.click"), 2, now)
        var q = NotifyPacer.push(emptyList(), a, now)
        q = NotifyPacer.push(q, b, now)
        q = NotifyPacer.push(q, a.copy(item = a.item.copy(title = "A2")), now) // same id replaces
        assertEquals(listOf("feed-2", "ep-1"), q.map { it.item.id })
        assertEquals("A2", q.last().item.title)
        val back = NotifyPacer.decode(NotifyPacer.encode(q))
        assertEquals(q, back)
        val later = now + NotifyPacer.STALE_MS + 1
        val stale = NotifyPacer.push(q, b.copy(queuedAt = later), later)
        assertEquals(listOf("feed-2"), stale.map { it.item.id })
    }
}
