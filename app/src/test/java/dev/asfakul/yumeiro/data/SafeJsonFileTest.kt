/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.online.OnlineBookmarks
import dev.asfakul.yumeiro.online.OnlineItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** R16 (G5): a personal-data file that can't be read is never silently replaced by an empty one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeJsonFileTest {
    private fun dir() = kotlin.io.path.createTempDirectory("safejson").toFile()
    private val parse: (String) -> JSONObject = { JSONObject(it) }

    @Test fun missingFileIsEmptyWithoutCopies() {
        val f = File(dir(), "a.json")
        val r = SafeJsonFile.read(f, parse)
        assertNull(r.value); assertFalse(r.keptBadCopy); assertFalse(SafeJsonFile.badOf(f).exists())
    }

    @Test fun unreadableFileIsKeptAsBad() {
        val f = File(dir(), "a.json").apply { writeText("{\"items\":[{\"api\":\"x\",") } // cut mid-write
        val r = SafeJsonFile.read(f, parse)
        assertNull(r.value); assertTrue(r.keptBadCopy)
        assertEquals("{\"items\":[{\"api\":\"x\",", SafeJsonFile.badOf(f).readText())
    }

    @Test fun leftoverTempIsUsedWhenTheFileIsGone() {
        // The old write fallback: f.delete() done, killed before tmp.renameTo(f).
        val f = File(dir(), "a.json")
        SafeJsonFile.tmpOf(f).writeText("{\"n\":3}")
        val r = SafeJsonFile.read(f, parse)
        assertEquals(3, r.value!!.getInt("n")); assertEquals("a.json.tmp", r.recoveredFrom)
    }

    @Test fun writeReplacesAndLeavesNoTemp() {
        val f = File(dir(), "a.json").apply { writeText("{\"n\":1}") }
        SafeJsonFile.write(f, "{\"n\":2}")
        assertEquals(2, JSONObject(f.readText()).getInt("n")); assertFalse(SafeJsonFile.tmpOf(f).exists())
    }

    @Test fun myListSurvivesAnUnreadableFile() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val f = File(ctx.filesDir, "online_bookmarks.json")
        val saved = "{\"version\":1,\"items\":[{\"api\":\"A\",\"url\":\"u1\",\"name\":\"One\",\"status\":\"PLAN\",\"at\":1}"
        f.writeText(saved) // truncated: closing brackets missing
        OnlineBookmarks.unloadForTests()
        OnlineBookmarks.save(ctx, OnlineItem("Two", "u2", "A", null, emptyMap(), null))
        // The new save may write a fresh list, but the user's old list is still on disk.
        assertEquals(saved, SafeJsonFile.badOf(f).readText())
        assertEquals(listOf("u2"), OnlineBookmarks.items.value.map { it.url })
        OnlineBookmarks.unloadForTests()
    }

    @Test fun readingLibraryWritesInTheBackgroundAndKeepsAnUnreadableFile() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val lib = dev.asfakul.yumeiro.sources.manga.MangaLibrary
        val f = File(ctx.filesDir, "manga/library.json").apply { parentFile?.mkdirs(); writeText("[{\"providerId\":\"p\",\"url\":\"m1\",") }
        lib.unloadForTests()
        val item = dev.asfakul.yumeiro.sources.manga.MangaItem("p", "m2", "Two", null)
        repeat(20) { page -> lib.progress(ctx, item, dev.asfakul.yumeiro.sources.manga.MangaChapter("c1", "Chapter 1"), page, finished = false) }
        lib.flush()
        assertTrue(SafeJsonFile.badOf(f).readText().startsWith("[{\"providerId\":\"p\",\"url\":\"m1\""))
        // The last page turn is what is on disk, written whole.
        lib.unloadForTests(); lib.load(ctx)
        assertEquals(19, lib.get("p", "m2")!!.lastPage)
        lib.unloadForTests(); f.delete()
    }

    @Test fun myListRecoversFromALeftoverTemp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val f = File(ctx.filesDir, "online_bookmarks.json"); f.delete()
        SafeJsonFile.tmpOf(f).writeText("{\"version\":1,\"items\":[{\"api\":\"A\",\"url\":\"u1\",\"name\":\"One\",\"status\":\"PLAN\",\"at\":1}]}")
        OnlineBookmarks.unloadForTests()
        OnlineBookmarks.ensureLoaded(ctx)
        assertEquals(listOf("u1"), OnlineBookmarks.items.value.map { it.url })
        OnlineBookmarks.unloadForTests()
    }
}
