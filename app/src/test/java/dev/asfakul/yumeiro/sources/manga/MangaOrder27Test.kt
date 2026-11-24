/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** MANGA-ORDER-1 (owner item 27): saved chapters in chapter order, and Previous / Next offline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MangaOrder27Test {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val root get() = File(ctx.filesDir, "manga_offline")

    @Before fun clean() { root.deleteRecursively() }

    /** A saved chapter on disk as MangaOffline writes it; [number] null = a save from before this fix. */
    private fun save(name: String, at: Long, number: Float? = null, manga: String = "/m/1") {
        val url = "/c/" + name.replace(' ', '_')
        val dir = File(root, MangaOffline.folderName("p", url)).apply { mkdirs() }
        File(dir, "0000.img").writeBytes(byteArrayOf(1))
        val o = JSONObject().put("provider", "p").put("manga", manga).put("title", "T").put("chapter", url).put("name", name)
            .put("pages", 1).put("bytes", 1).put("at", at)
        number?.let { o.put("number", it.toDouble()) }
        File(dir, "meta.json").writeText(o.toString())
    }

    @Test fun numbersAreReadFromNames() {
        assertEquals(12.5f, MangaChapterOrder.number("Chapter 12.5"))
        assertEquals(13f, MangaChapterOrder.number("Ch. 13 - The Rain"))
        assertEquals(15f, MangaChapterOrder.number("Vol. 2 Ch. 15"))
        assertEquals(15f, MangaChapterOrder.number("Vol.2 15"))
        assertEquals(7f, MangaChapterOrder.number("#7"))
        assertEquals(4f, MangaChapterOrder.number("Episode 4"))
        assertEquals(21f, MangaChapterOrder.number("21 - Title"))
        assertEquals(-1f, MangaChapterOrder.number("Extra: Beach Day"))
        assertEquals(3.5f, MangaChapterOrder.number("Chapter 3,5"))
    }

    @Test fun downloadOrderNeverDecidesTheList() {
        // Owner's example: downloaded as 15, 12, 14, 13 (old saves without a number, so names decide).
        save("Chapter 15", 1); save("Chapter 12", 2); save("Chapter 14", 3); save("Chapter 13", 4)
        save("Chapter 12.5", 5); save("Extra: Beach Day", 6); save("Chapter 2", 7, number = 2f)
        save("Chapter 99", 8, manga = "/m/other")
        val names = MangaOffline.chapters(ctx, "p", "/m/1").map { it.name }
        assertEquals(listOf("Extra: Beach Day", "Chapter 15", "Chapter 14", "Chapter 13", "Chapter 12.5", "Chapter 12", "Chapter 2"), names)
    }

    @Test fun previousAndNextWorkOfflineAcrossSavedChapters() = runBlocking {
        save("Chapter 15", 1); save("Chapter 12", 2); save("Chapter 14", 3)
        // The reader's offline provider lists every saved chapter of the title, not only the open one.
        val offline = MangaOffline.provider(ctx, "p", "/c/Chapter_14", "Saved")!!
        val list = offline.chapters("/m/1")
        assertEquals(3, list.size)
        val (prev, next) = MangaChapterNav.neighbours(list, "/c/Chapter_14")
        assertEquals("Chapter 12", prev?.name) // 13 is not saved: the adjacent saved chapter, no network
        assertEquals("Chapter 15", next?.name)
        val (p2, n2) = MangaChapterNav.neighbours(list, "/c/Chapter_15")
        assertEquals("Chapter 14", p2?.name)
        assertEquals(null, n2) // the reader shows "End of your saved chapters"
    }

    @Test fun namesDecideNavigationWhenTheSourceGaveNoNumbers() {
        // Source order newest first but with an out-of-place entry; names carry the numbers.
        val list = listOf(MangaChapter("/3", "Chapter 3"), MangaChapter("/1", "Chapter 1"), MangaChapter("/2", "Chapter 2"))
        val (prev, next) = MangaChapterNav.neighbours(list, "/2")
        assertEquals("/1", prev?.url)
        assertEquals("/3", next?.url)
    }

    @Test fun newSavesKeepTheSourceNumber() {
        assertEquals(40f, MangaChapterOrder.numberOf(MangaChapter("/x", "Final", 40f)))
        assertEquals(41f, MangaChapterOrder.numberOf(MangaChapter("/y", "Chapter 41")))
    }
}
