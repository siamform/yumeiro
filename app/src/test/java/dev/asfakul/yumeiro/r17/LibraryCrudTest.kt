/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.r17

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.data.repository.OfflineMediaRepository
import dev.asfakul.yumeiro.data.repository.likedInOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * G5 (R17): playlist create/rename/add/remove/reorder/delete, duplicates, liked-song order,
 * play history order, restart (a new repository on the same files), a 10,000-song playlist,
 * and that the music library is part of the backup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryCrudTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private fun prefs() = ctx.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE)
    private fun repo() = OfflineMediaRepository(ctx)
    private suspend fun OfflineMediaRepository.mine() = playlists.first().filter { it.id != "favorites_default" }

    @Before fun clean() { prefs().edit().clear().commit() }

    @Test fun emptyProfileHasNothing() = runBlocking {
        val r = repo()
        assertEquals(listOf("favorites_default"), r.playlists.first().map { it.id })
        assertTrue(r.playlists.first().single().trackIds.isEmpty())
        assertTrue(r.recentlyPlayedTracks.first().isEmpty())
        assertTrue(r.mostPlayedTracks.first().isEmpty())
    }

    @Test fun crudSurvivesRestartAndKeepsOrder() = runBlocking {
        val r = repo()
        r.createPlaylist("Road", listOf("1", "2", "2", "3"))
        val id = r.mine().single().id
        assertEquals("duplicates dropped on create", listOf("1", "2", "3"), r.mine().single().trackIds)
        r.addTracksToPlaylist(id, listOf("3", "4", "5"))
        assertEquals("duplicates dropped on add", listOf("1", "2", "3", "4", "5"), r.mine().single().trackIds)
        r.reorderPlaylist(id, 4, 0)
        r.reorderPlaylist(id, 9, 0) // out of range: nothing happens
        r.removeTrackFromPlaylist(id, "2")
        r.renamePlaylist(id, "Road trip")
        val again = repo().mine().single() // a new repository = app restarted
        assertEquals("Road trip", again.name)
        assertEquals(listOf("5", "1", "3", "4"), again.trackIds)
        repo().deletePlaylist(id)
        assertTrue(repo().mine().isEmpty())
    }

    @Test fun likedSongsKeepTheOrderTheyWereLikedAcrossRestart() = runBlocking {
        val r = repo()
        for (t in listOf("c", "a", "e", "b", "d")) r.toggleFavorite(t)
        r.toggleFavorite("e") // unlike
        val fav = { x: OfflineMediaRepository -> runBlocking { x.playlists.first().first { it.id == "favorites_default" }.trackIds } }
        assertEquals(listOf("c", "a", "b", "d"), fav(r))
        val r2 = repo()
        r2.toggleFavorite("z") // a like after restart used to re-sort the list in set order
        assertEquals(listOf("c", "a", "b", "d", "z"), fav(r2))
        assertEquals(listOf("c", "a", "b", "d", "z"), fav(repo()))
    }

    @Test fun likedInOrderRules() {
        assertEquals(listOf("a", "b", "c"), likedInOrder(listOf("a", "x", "b"), listOf("c", "b", "a")))
        assertEquals(listOf("a"), likedInOrder(listOf("a", "a"), listOf("a")))
        assertEquals(emptyList<String>(), likedInOrder(listOf("a"), emptyList()))
    }

    @Test fun tenThousandSongPlaylistRoundTrips() = runBlocking {
        val ids = (1..10_000).map { "t$it" }
        val r = repo()
        val t0 = System.nanoTime()
        r.createPlaylist("Big", ids)
        val id = r.mine().single().id
        r.reorderPlaylist(id, 9_999, 0)
        val saveMs = (System.nanoTime() - t0) / 1_000_000
        val t1 = System.nanoTime()
        val got = repo().mine().single().trackIds
        val loadMs = (System.nanoTime() - t1) / 1_000_000
        println("LIBRARY_10K save+reorder=${saveMs}ms load=${loadMs}ms bytes=${prefs().getString("playlists_json", "")!!.length}")
        assertEquals(10_000, got.size)
        assertEquals("t10000", got.first()); assertEquals("t1", got[1]); assertEquals("t9999", got.last())
        assertTrue("load took ${loadMs} ms", loadMs < 3_000)
    }

    @Test fun playHistoryOrderAndCountsSurviveRestart() = runBlocking {
        val r = repo()
        for (t in listOf("a", "b", "a", "c")) r.recordTrackPlayed(t)
        assertEquals("most recent first, no repeats", "c\na\nb", prefs().getString("recently_played_ids", null))
        val counts = org.json.JSONObject(prefs().getString("play_counts_json", "{}")!!)
        assertEquals(2, counts.getInt("a")); assertEquals(1, counts.getInt("b"))
        val r2 = repo()
        for (i in 1..60) r2.recordTrackPlayed("n$i")
        val recent = prefs().getString("recently_played_ids", null)!!.split('\n')
        assertEquals("capped at 50", 50, recent.size); assertEquals("n60", recent.first())
        assertEquals("counts kept across restart", 2, org.json.JSONObject(prefs().getString("play_counts_json", "{}")!!).getInt("a"))
    }

    @Test fun musicLibraryIsInTheBackup() {
        assertTrue(Backup.prefIncluded("mt_player_music_prefs"))
        assertTrue(Backup.prefIncluded("mt_player_video_prefs"))
    }
}
