/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.backup.BackupMeta
import dev.asfakul.yumeiro.online.OnlineSession
import dev.asfakul.yumeiro.player.VideoPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G9 SE-6: the choice rows in Settings › Backup, storage & reset and Settings › Quality and audio
 * that had no effect test. Each test sets the value through the setter the row calls and checks
 * the code that reads it. The player choices (speed, quality, renderer, decoder, default player)
 * and the backup frequency are proven on device in `ChoiceSettingsEffectDeviceTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChoiceSettingsEffectTest {
    private lateinit var app: Context
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        DataCache.init(app); DataCache.clearAll()
        DataCache.Settings.set(enabled = true, freshHours = 6, keepDays = 7, cookieDays = 30, maxMb = 128)
    }
    @After fun tearDown() {
        DataCache.Settings.set(enabled = true, freshHours = 6, keepDays = 7, cookieDays = 30, maxMb = 128)
        DataCache.clearAll()
    }

    private fun age(ns: String, key: String, ms: Long) {
        val f = DataCache.fileFor(ns, key)!!
        assertTrue(f.setLastModified(System.currentTimeMillis() - ms))
    }

    /** cache_fresh: a page 2 h old is stale with "Refresh after 1 h" and fresh with "6 h". */
    @Test fun refreshAfterDecidesWhatIsFresh() {
        DataCache.write("home", "k", "page")
        age("home", "k", 2 * hour); DataCache.clearMemory()
        DataCache.Settings.set(freshHours = 1)
        assertNull("1 h: a 2 h old page must be refetched", DataCache.read("home", "k"))
        DataCache.Settings.set(freshHours = 6)
        assertEquals("page", DataCache.read("home", "k"))
    }

    /** cache_keep: with "Delete after 3 days" a 4-day-old page goes and a 2-day-old one stays. */
    @Test fun deleteAfterRemovesOldPages() {
        DataCache.write("feed", "old", "o"); DataCache.write("feed", "new", "n")
        age("feed", "old", 4 * day); age("feed", "new", 2 * day)
        DataCache.Settings.set(keepDays = 3)
        DataCache.purge()
        assertTrue(!DataCache.fileFor("feed", "old")!!.exists())
        assertTrue(DataCache.fileFor("feed", "new")!!.exists())
        DataCache.Settings.set(keepDays = 7)
        DataCache.write("feed", "old", "o"); age("feed", "old", 4 * day)
        DataCache.purge()
        assertTrue("7 days: a 4-day-old page stays", DataCache.fileFor("feed", "old")!!.exists())
    }

    /** cache_max: 20 pages of 1 MB with "Up to 16 MB" (the smallest the setter allows) → under 16 MB, oldest gone. */
    @Test fun upToCapsTheFolder() {
        val mb = "x".repeat(1024 * 1024)
        repeat(20) { DataCache.write("big", "p$it", mb); age("big", "p$it", (20 - it) * 60_000L) }
        DataCache.Settings.set(maxMb = 16)
        DataCache.purge()
        assertTrue("used ${DataCache.bytesUsed()}", DataCache.bytesUsed() <= 16L * 1024 * 1024)
        assertTrue("the oldest page goes first", !DataCache.fileFor("big", "p0")!!.exists())
        assertTrue("the newest page stays", DataCache.fileFor("big", "p19")!!.exists())
    }

    /** cookie_days: the cookie clear runs once the chosen days have passed, and never with "Never". */
    @Test fun clearCookiesEveryFollowsTheChoice() {
        val t0 = 1_000_000_000_000L
        DataCache.Settings.set(cookieDays = 7)
        DataCache.Settings.lastCookieClear = t0
        DataCache.runJanitor(app, now = t0 + 6 * day)
        assertEquals("6 of 7 days: not yet", t0, DataCache.Settings.lastCookieClear)
        DataCache.runJanitor(app, now = t0 + 8 * day)
        assertEquals("8 of 7 days: cleared", t0 + 8 * day, DataCache.Settings.lastCookieClear)
        DataCache.Settings.set(cookieDays = 0)
        DataCache.runJanitor(app, now = t0 + 400 * day)
        assertEquals("Never: no clear", t0 + 8 * day, DataCache.Settings.lastCookieClear)
    }

    /** qa_audio: the version that plays when the user didn't pick one. */
    @Test fun defaultAudioPicksSubOrDub() {
        VideoPrefs.init(app)
        val before = VideoPrefs.audio.value
        try {
            VideoPrefs.setAudio(app, VideoPrefs.AudioDefault.DUB)
            assertEquals("English Dub", OnlineSession.defaultVersion(listOf("English Sub", "English Dub")))
            VideoPrefs.setAudio(app, VideoPrefs.AudioDefault.SUB)
            assertEquals("English Sub", OnlineSession.defaultVersion(listOf("English Dub", "English Sub")))
        } finally { VideoPrefs.setAudio(app, before) }
    }

    /** backup_auto_keep: "Keep 3" leaves the 3 newest automatic backups, "Keep 5" leaves 5 (the list the worker deletes). */
    @Test fun backupKeepDecidesHowManyStay() {
        fun item(n: Int) = dev.asfakul.yumeiro.backup.BackupStore.Item(android.net.Uri.parse("file:///yumeiro-auto-$n.zip"), "yumeiro-auto-$n.zip", 1, n.toLong(), dev.asfakul.yumeiro.backup.BackupStore.Where.APP)
        val all = (1..8).map(::item)
        val before = BackupMeta.autoKeep(app)
        try {
            BackupMeta.setAutoKeep(app, 3)
            val gone3 = dev.asfakul.yumeiro.backup.BackupStore.pruneList(all, "auto", BackupMeta.autoKeep(app))
            assertEquals((1..5).map { "yumeiro-auto-$it.zip" }, gone3.map { it.name }.sorted())
            BackupMeta.setAutoKeep(app, 5)
            assertEquals(3, dev.asfakul.yumeiro.backup.BackupStore.pruneList(all, "auto", BackupMeta.autoKeep(app)).size)
        } finally { BackupMeta.setAutoKeep(app, before) }
    }
}
