/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.manga

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.ui.theme.AccentTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PROF-1 (owner master list: "Continue Manga leaking across profiles"): the manga library (Continue reading,
 * saved titles) was one file for every profile. A chapter read in one profile must not show in another,
 * and must still be there when the first profile comes back. The default profile keeps the old file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MangaLibraryProfileTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val item = MangaItem("yu_prof1", "yuprof1://t/1", "YuProf1 Title", null)
    private val chapter = MangaChapter("yuprof1://t/1/c1", "Chapter 1", 1f)

    @Before fun setUp() { Profiles.resetForTests(ctx); MangaLibrary.unloadForTests(); java.io.File(ctx.filesDir, "manga/library.json").delete() }
    @After fun tearDown() { MangaLibrary.unloadForTests(); Profiles.resetForTests(ctx) }

    /** Profile switches restart the app, so the next load reads the new profile's file. */
    private fun switchTo(id: String) { MangaLibrary.flush(); Profiles.select(ctx, id); MangaLibrary.unloadForTests(); MangaLibrary.load(ctx) }

    @Test fun readingInOneProfileStaysInThatProfile() {
        val kid = Profiles.add(ctx, "Kids", AccentTone.AO)
        switchTo(kid.id)
        MangaLibrary.progress(ctx, item, chapter, 3, finished = false)
        MangaLibrary.flush()
        assertEquals("yuprof1://t/1/c1", MangaLibrary.get(item.providerId, item.url)?.lastChapterUrl)

        switchTo(Profiles.DEFAULT)
        assertNull("PROF-1: the Kids profile's reading leaked into the default profile", MangaLibrary.get(item.providerId, item.url))

        switchTo(kid.id)
        assertNotNull("the Kids profile lost its own Continue reading", MangaLibrary.get(item.providerId, item.url))
        assertEquals(3, MangaLibrary.get(item.providerId, item.url)?.lastPage)
    }

    @Test fun defaultProfileKeepsTheOldFile() {
        switchTo(Profiles.DEFAULT)
        MangaLibrary.progress(ctx, item, chapter, 1, finished = false)
        MangaLibrary.flush()
        assertTrue("default profile must keep filesDir/manga/library.json", java.io.File(ctx.filesDir, "manga/library.json").isFile)
    }
}
