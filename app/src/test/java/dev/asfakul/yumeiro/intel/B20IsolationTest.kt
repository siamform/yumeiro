/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.intel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.profiles.Profiles
import dev.asfakul.yumeiro.sources.novel.NovelChapter
import dev.asfakul.yumeiro.sources.novel.NovelItem
import dev.asfakul.yumeiro.sources.novel.NovelLibrary
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
import java.io.File

/**
 * B20 S1 (audit B20-A1, B20-A2): the novel library was one file for every profile and never in a backup; music
 * favourites, playlists and plays, Home "Most played" and the activity statistics were shared by every profile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class B20IsolationTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val item = NovelItem("yu_b20", "YuB20 Novel", "/novel/1", null)
    private val chapter = NovelChapter("Chapter 1", "/novel/1/c1", 1.0, null)

    @Before fun setUp() { Profiles.resetForTests(ctx); NovelLibrary.resetForTests(); File(ctx.filesDir, "novel").deleteRecursively() }
    @After fun tearDown() { NovelLibrary.resetForTests(); Profiles.resetForTests(ctx); File(ctx.filesDir, "novel").deleteRecursively() }

    private fun switchTo(id: String) { Profiles.select(ctx, id); NovelLibrary.resetForTests(); NovelLibrary.ensureLoaded(ctx) }

    @Test fun novelReadingStaysInItsProfile() {
        val kid = Profiles.add(ctx, "Kids", AccentTone.AO)
        switchTo(kid.id)
        NovelLibrary.progress(ctx, item, chapter, 0, 0.4f)
        assertNotNull(NovelLibrary.get(item.pluginId, item.path))
        switchTo(Profiles.DEFAULT)
        assertNull("B20-A1: the Kids profile's novel leaked into the default profile", NovelLibrary.get(item.pluginId, item.path))
        switchTo(kid.id)
        assertNotNull("the Kids profile lost its own novel", NovelLibrary.get(item.pluginId, item.path))
        assertTrue(File(ctx.filesDir, "profiles/${kid.id}/novel/library.json").isFile)
    }

    @Test fun defaultProfileKeepsTheOldNovelFile() {
        switchTo(Profiles.DEFAULT)
        NovelLibrary.progress(ctx, item, chapter, 0, 0.1f)
        assertTrue(File(ctx.filesDir, "novel/library.json").isFile)
    }

    @Test fun novelsAreInBackups() {
        assertEquals("B20-A1: the novel library was never backed up", Backup.Section.LIBRARY, Backup.sectionOf("files/novel/library.json"))
        assertTrue("novel" in Backup.dataDirs)
        switchTo(Profiles.DEFAULT)
        NovelLibrary.progress(ctx, item, chapter, 0, 0.1f)
        assertTrue(Backup.collect(ctx, setOf(Backup.Section.LIBRARY)).any { it.first == "files/novel/library.json" })
    }

    @Test fun musicAndActivityArePerProfile() {
        for (name in listOf("play_counts", "mt_player_music_prefs", "activity_log")) {
            assertTrue("B20-A2: $name must be kept per profile", name in Profiles.SCOPED_PREFS)
        }
        val kid = Profiles.add(ctx, "Kids", AccentTone.AO)
        Profiles.select(ctx, kid.id)
        assertEquals("play_counts@p${kid.id}", Profiles.prefsName(ctx, "play_counts"))
        dev.asfakul.yumeiro.data.PlayCounts.resetForTests()
        dev.asfakul.yumeiro.data.PlayCounts.record(ctx, dev.asfakul.yumeiro.data.PlayCounts.Kind.YOUTUBE, "yt1", "Clip", "Chan", null)
        Thread.sleep(50)
        val own = ctx.getSharedPreferences("play_counts@p${kid.id}", Context.MODE_PRIVATE).all
        val shared = ctx.getSharedPreferences("play_counts", Context.MODE_PRIVATE).all
        assertTrue("the Kids profile's play was not saved in its own file", own.isNotEmpty())
        assertTrue("B20-A2: the Kids profile's play went into the default profile's file", shared.values.none { it.toString().contains("yt1") })
    }

    @Test fun lockedDefaultProfileKeepsItsReadingOutOfOtherBackups() {
        val others = setOf(Profiles.DEFAULT)
        assertTrue(Profiles.hiddenFromBackup(ctx, "files/manga/library.json", others))
        assertTrue(Profiles.hiddenFromBackup(ctx, "files/novel/library.json", others))
        assertTrue(Profiles.prefsHiddenFromBackup(ctx, "mt_player_music_prefs", others))
    }
}
