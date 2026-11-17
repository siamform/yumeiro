/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** G12 X12-1, X12-2. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataCacheBoundsTest {
    private lateinit var app: Context

    @Before fun setUp() { app = ApplicationProvider.getApplicationContext(); DataCache.init(app); DataCache.clearAll() }
    @After fun tearDown() { DataCache.memoryBudgetOverride = null; DataCache.clearAll() }

    /** X12-1: 48 one-million-character pages must not all stay in memory (before: 48 M chars). */
    @Test fun memoryCopyIsBoundedBySize() {
        DataCache.memoryBudgetOverride = 6_000_000L
        val page = "x".repeat(1_000_000)
        repeat(48) { DataCache.write("feed", "k$it", page) }
        assertTrue("memory holds ${DataCache.memoryCharsForTests()} chars", DataCache.memoryCharsForTests() <= 6_000_000L)
        // Every page is still readable (from disk), newest from memory.
        assertEquals(page, DataCache.read("feed", "k0", maxAgeMs = Long.MAX_VALUE))
        assertEquals(page, DataCache.read("feed", "k47", maxAgeMs = Long.MAX_VALUE))
        assertTrue(DataCache.memoryCharsForTests() <= 6_000_000L)
    }

    /** X12-1: a page bigger than a quarter of the budget is served from disk, never held. */
    @Test fun hugePageIsNotHeldInMemory() {
        DataCache.memoryBudgetOverride = 1_000_000L
        val big = "y".repeat(400_000)
        DataCache.write("detail", "big", big)
        assertEquals(0L, DataCache.memoryCharsForTests())
        assertEquals(big, DataCache.read("detail", "big", maxAgeMs = Long.MAX_VALUE))
        assertEquals(0L, DataCache.memoryCharsForTests())
    }

    /** X12-2: the janitor must not delete a write in progress (a fresh .tmp), only stale ones. */
    @Test fun purgeKeepsFreshTempFiles() {
        DataCache.write("feed", "a", "v")
        val f = DataCache.fileFor("feed", "a")!!
        val now = System.currentTimeMillis()
        val fresh = File(f.parentFile, "fresh.json.tmp").apply { writeText("x"); setLastModified(now) }
        val stale = File(f.parentFile, "stale.json.tmp").apply { writeText("x"); setLastModified(now - 10 * 60_000L) }
        DataCache.purge(now = now)
        assertTrue("fresh .tmp kept", fresh.exists())
        assertTrue("stale .tmp removed", !stale.exists())
        assertTrue(f.exists())
    }

    /**
     * ST-22: invalidateAll (used by the UI) makes every saved page a miss at once, keeps pages
     * saved after it, and deletes the old files off the calling thread.
     */
    @Test fun invalidateAllDropsOldPagesAtOnceAndKeepsNewOnes() {
        repeat(20) { DataCache.write("search", "old$it", "nsfw-$it") }
        val oldFile = DataCache.fileFor("search", "old0")!!
        assertTrue(oldFile.isFile)
        Thread.sleep(5)
        DataCache.invalidateAll()
        assertEquals("old page served after the clear", null, DataCache.read("search", "old0", maxAgeMs = Long.MAX_VALUE))
        assertEquals(null, DataCache.ageOf("search", "old1"))
        Thread.sleep(5)
        DataCache.write("search", "new", "fresh")
        DataCache.awaitClearForTests()
        assertTrue("old file left on disk", !oldFile.exists())
        DataCache.clearMemory()
        assertEquals("page saved after the clear was lost", "fresh", DataCache.read("search", "new", maxAgeMs = Long.MAX_VALUE))
    }
}
