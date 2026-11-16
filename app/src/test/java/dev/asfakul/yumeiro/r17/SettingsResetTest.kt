/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r17

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.backup.PendingRestore
import dev.asfakul.yumeiro.backup.PrefSections
import dev.asfakul.yumeiro.backup.ResetCenter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * G9 S24/S25: "Reset settings" says the library stays, and a backup restores only the ticked
 * sections. Playlists, favourites and resume points live in the player's preference files, next
 * to real settings; before R17 they went with the switches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsResetTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun p(n: String) = ctx.getSharedPreferences(n, Context.MODE_PRIVATE)

    @Before fun clean() {
        PendingRestore.discard(ctx)
        Backup.prefsDir(ctx).listFiles()?.forEach { f -> p(f.nameWithoutExtension).edit().clear().commit() }
    }

    /** A profile with a little of everything. */
    private fun seed(speed: Int = 150, playlist: String = "[{\"id\":\"p1\",\"name\":\"Mine\"}]") {
        p("mt_player").edit().putInt("speed", speed).commit()
        p("mt_player_music_prefs").edit().putString("playlists_json", playlist).putStringSet("favorite_track_ids", setOf("t1"))
            .putString("recently_played_ids", "t1\nt2").putString("play_counts_json", "{\"t1\":3}").commit()
        p("mt_player_video_prefs").edit().putStringSet("favorite_video_ids", setOf("v1")).putLong("pos_v1", 61_000L)
            .putBoolean("watched_v2", true).putStringSet("hidden_video_folders", setOf("/x")).commit()
        p("yu_sites").edit().putString("sites", "[{\"url\":\"https://a.example\",\"name\":\"A\"}]").commit()
        p("yumeiro_listening").edit().putLong("total_ms", 3_600_000L).commit()
        p("yu_last_played").edit().putString("yt", "{\"abc\":1}").commit()
        p("scrobble").edit().putString("queue", "[{\"a\":\"x\"}]").commit()
        p("online").edit().putString("online_search_history", "naruto").commit()
        p("tachiyomi_bridge").edit().putString("repos", "r").commit()
    }

    @Test fun settingsResetKeepsTheLibraryAndSources() {
        seed()
        ResetCenter.stage(ctx, setOf(ResetCenter.Scope.SETTINGS))
        assertTrue(PendingRestore.applyIfAny(ctx))
        // Settings went back to default ...
        assertEquals(0, p("mt_player").getInt("speed", 0))
        assertNull(p("mt_player_video_prefs").getStringSet("hidden_video_folders", null))
        // ... and nothing of the library or sources did.
        assertEquals("[{\"id\":\"p1\",\"name\":\"Mine\"}]", p("mt_player_music_prefs").getString("playlists_json", null))
        assertEquals(setOf("t1"), p("mt_player_music_prefs").getStringSet("favorite_track_ids", null))
        assertEquals(setOf("v1"), p("mt_player_video_prefs").getStringSet("favorite_video_ids", null))
        assertEquals(61_000L, p("mt_player_video_prefs").getLong("pos_v1", 0L))
        assertTrue(p("yu_sites").getString("sites", null)!!.contains("a.example"))
        assertEquals(3_600_000L, p("yumeiro_listening").getLong("total_ms", 0L))
        assertEquals("[{\"a\":\"x\"}]", p("scrobble").getString("queue", null))
        assertEquals("r", p("tachiyomi_bridge").getString("repos", null))
    }

    @Test fun historyResetClearsEveryHistoryAndKeepsTheRest() {
        seed()
        ResetCenter.stage(ctx, setOf(ResetCenter.Scope.HISTORY))
        assertTrue(PendingRestore.applyIfAny(ctx))
        assertFalse(p("mt_player_video_prefs").contains("pos_v1"))
        assertFalse(p("mt_player_video_prefs").contains("watched_v2"))
        assertFalse(p("mt_player_music_prefs").contains("recently_played_ids"))
        assertFalse(p("mt_player_music_prefs").contains("play_counts_json"))
        assertFalse(p("online").contains("online_search_history"))
        assertFalse(p("yu_last_played").contains("yt"))
        // Not history: playlists, favourites, hidden folders, settings.
        assertTrue(p("mt_player_music_prefs").contains("playlists_json"))
        assertEquals(setOf("v1"), p("mt_player_video_prefs").getStringSet("favorite_video_ids", null))
        assertEquals(setOf("/x"), p("mt_player_video_prefs").getStringSet("hidden_video_folders", null))
        assertEquals(150, p("mt_player").getInt("speed", 0))
    }

    @Test fun eachRestoreTakesOnlyItsOwnValues() = runBlocking {
        seed(speed = 150, playlist = "[\"backup\"]")
        val out = ByteArrayOutputStream()
        Backup.export(ctx, out, Backup.Section.entries.toSet())
        val bytes = out.toByteArray()
        seed(speed = 90, playlist = "[\"now\"]")
        p("mt_player_video_prefs").edit().putStringSet("hidden_video_folders", setOf("/now")).commit()

        // Settings only: the switch and the hidden folder come back, the playlist made since stays.
        Backup.stage(ctx, { bytes.inputStream() }, setOf(Backup.Section.SETTINGS))
        assertTrue(PendingRestore.applyIfAny(ctx))
        assertEquals(150, p("mt_player").getInt("speed", 0))
        assertEquals(setOf("/x"), p("mt_player_video_prefs").getStringSet("hidden_video_folders", null))
        assertEquals("[\"now\"]", p("mt_player_music_prefs").getString("playlists_json", null))
        assertEquals(61_000L, p("mt_player_video_prefs").getLong("pos_v1", 0L))

        // Library only: the playlist comes back, the switch stays as it is now.
        p("mt_player").edit().putInt("speed", 90).commit()
        Backup.stage(ctx, { bytes.inputStream() }, setOf(Backup.Section.LIBRARY))
        assertTrue(PendingRestore.applyIfAny(ctx))
        assertEquals("[\"backup\"]", p("mt_player_music_prefs").getString("playlists_json", null))
        assertEquals(90, p("mt_player").getInt("speed", 0))
        assertEquals(setOf("/x"), p("mt_player_video_prefs").getStringSet("hidden_video_folders", null))
    }

    @Test fun classification() {
        assertEquals(Backup.Section.LIBRARY, PrefSections.sectionOf("mt_player_music_prefs", "playlists_json"))
        assertEquals(Backup.Section.LIBRARY, PrefSections.sectionOf("mt_player_video_prefs", "pos_abc"))
        assertEquals(Backup.Section.SETTINGS, PrefSections.sectionOf("mt_player_video_prefs", "hidden_video_folders"))
        assertEquals(Backup.Section.SOURCES, PrefSections.sectionOf("js_source_12", "k"))
        assertEquals(Backup.Section.SETTINGS, PrefSections.sectionOf("mt_player", "speed"))
        assertTrue(PrefSections.isHistory("mt_player_video_prefs", "watched_x"))
        assertFalse(PrefSections.isHistory("mt_player_music_prefs", "playlists_json"))
    }
}
