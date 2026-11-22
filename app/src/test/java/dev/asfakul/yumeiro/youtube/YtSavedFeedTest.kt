package dev.asfakul.yumeiro.youtube

import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.cache.DataCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FE-7a (docs/FEED-ENGINE-2.1.md): YouTube / YouTube Music shelves were cached only in memory (20 min), so every
 * cold start showed skeletons and fetched every shelf live. A shelf's first page is now saved on the phone and
 * shown at once on the next start, whatever its age (up to keepDays).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YtSavedFeedTest {
    @Before fun setUp() { DataCache.init(ApplicationProvider.getApplicationContext()); DataCache.clearAll() }

    @Test fun savedShelfComesBackAfterAColdStartEvenWhenOld() = runBlocking {
        val feed = YtFeed(YtFeed.Kind.QUERY, "lofi fe7 ${System.nanoTime()}", "Lofi", "Your topic", true)
        val other = YtFeed(YtFeed.Kind.QUERY, "other fe7 ${System.nanoTime()}", "Other", "Your topic", true)
        assertNull(YouTube.savedFeed(feed))
        val items = listOf(YtItem("abc123", "https://youtu.be/abc123", "Song", "Artist", channelUrl = "https://youtube.com/@a", music = true))
        YouTube.rememberFeed(feed, items)
        val file = DataCache.fileFor("ytfeed", feed.id)
        assertTrue(file != null && file.isFile)
        assertTrue(file!!.setLastModified(System.currentTimeMillis() - 30 * 3_600_000L))
        DataCache.clearMemory()
        val back = YouTube.savedFeed(feed)
        assertEquals(listOf("abc123"), back?.map { it.id })
        assertEquals("https://youtube.com/@a", back?.first()?.channelUrl)
        assertNull(YouTube.savedFeed(other))
    }
}
