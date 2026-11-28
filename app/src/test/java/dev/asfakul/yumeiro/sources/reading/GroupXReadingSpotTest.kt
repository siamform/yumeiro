/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.reading

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.sources.manga.MangaChapter
import dev.asfakul.yumeiro.sources.manga.MangaItem
import dev.asfakul.yumeiro.sources.manga.MangaLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Group X (B5): the exact reading spot. A spot inside a long paragraph / a tall webtoon page must come back to the
 * same line, every saved spot of the old format must still open where it did, and the manga library must read the
 * old file (no offset) and keep the new offset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupXReadingSpotTest {
    @Test fun spotInsideAnItemComesBack() {
        for (count in listOf(1, 3, 7, 60, 997, 4000)) for (i in listOf(0, count / 2, count - 1)) for (px in listOf(0, 1, 333, 1799)) {
            val spot = ReadingSpot.encode(i, px, 1800, count)
            val (bi, w) = ReadingSpot.decode(spot, count)
            assertEquals("count=$count i=$i px=$px: wrong item", i, bi)
            assertTrue("count=$count i=$i px=$px: ${ReadingSpot.offsetPx(w, 1800)} px", kotlin.math.abs(ReadingSpot.offsetPx(w, 1800) - px) <= 3)
        }
    }

    @Test fun oldSpotsOpenWhereTheyDid() {
        // The old reader saved index / count: same item, at its top.
        for (count in 1..300) for (i in 0 until count step 7) {
            val (bi, w) = ReadingSpot.decode(i.toFloat() / count, count)
            assertEquals("count=$count i=$i", i, bi); assertTrue("count=$count i=$i within=$w", ReadingSpot.offsetPx(w, 2000) <= 1)
        }
        assertEquals(4 to 0f, ReadingSpot.decode(1f, 5)) // finished → last item
        assertEquals(0 to 0f, ReadingSpot.decode(Float.NaN, 5))
        assertEquals(0f, ReadingSpot.encode(3, 10, 0, 0))
    }

    @Test fun neverRollsIntoTheNextItem() {
        val (i, w) = ReadingSpot.decode(ReadingSpot.encode(2, 1800, 1800, 10), 10)
        assertEquals(2, i); assertTrue(w < 1f)
    }

    @Test fun mangaLibraryKeepsTheOffsetAndReadsOldFiles() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        Profiles.resetForTests(ctx); MangaLibrary.unloadForTests()
        val f = java.io.File(ctx.filesDir, "manga/library.json").apply { parentFile?.mkdirs() }
        f.writeText("""[{"providerId":"yu_x","url":"yux://t","title":"Old","lastChapterUrl":"yux://c1","lastChapterName":"C1","lastPage":4}]""")
        MangaLibrary.load(ctx)
        val old = MangaLibrary.get("yu_x", "yux://t")
        assertEquals(4, old?.lastPage); assertEquals(0f, old?.lastOffset)
        MangaLibrary.progress(ctx, MangaItem("yu_x", "yux://t", "Old", null), MangaChapter("yux://c1", "C1", 1f), 5, finished = false, offset = 0.42f)
        MangaLibrary.flush(); MangaLibrary.unloadForTests(); MangaLibrary.load(ctx)
        val now = MangaLibrary.get("yu_x", "yux://t")
        assertEquals(5, now?.lastPage); assertEquals(0.42f, now?.lastOffset ?: -1f, 0.0001f)
        MangaLibrary.unloadForTests(); f.delete()
    }
}
