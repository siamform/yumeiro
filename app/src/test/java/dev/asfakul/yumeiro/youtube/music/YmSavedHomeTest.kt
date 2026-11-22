package dev.asfakul.yumeiro.youtube.music

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
 * FE-7b (docs/FEED-ENGINE-2.1.md): YouTube Music Home lived only in memory, so every cold start waited for the
 * live page. The last Home is now saved per account state and shown at once; never after an account change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YmSavedHomeTest {
    @Before fun setUp() { DataCache.init(ApplicationProvider.getApplicationContext()); DataCache.clearAll() }

    private val homeJson get() = javaClass.classLoader!!.getResource("ym/home.json")!!.readText()

    @Test fun savedHomeShowsAfterAColdStartAndNeverAfterAnAccountChange() = runBlocking {
        val home = YmBrowse(YtMusicApi.HOME)
        assertNull(YtMusicApi.savedBrowse(home))
        YtMusicApi.saveForTests(home, homeJson)
        val file = DataCache.fileFor("ymhome", YtMusicApi.savedKey(home)!!)
        assertTrue(file != null && file.isFile)
        assertTrue(file!!.setLastModified(System.currentTimeMillis() - 48 * 3_600_000L))
        DataCache.clearMemory()
        val back = YtMusicApi.savedBrowse(home)
        assertEquals("Quick picks", back?.shelves?.first()?.title)
        assertTrue(back!!.chips.isNotEmpty())
        assertNull("playlists are not saved", YtMusicApi.savedKey(YmBrowse("VLPL123")))
        YtMusicApi.invalidate()
        assertNull("shown after an account change", YtMusicApi.savedBrowse(home))
    }
}
