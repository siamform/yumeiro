/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.data.repository.OfflineMediaRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * G5 on real storage, across a process death (LB-8, LB-3).
 *
 * Phase a (`-e phase a`): through the app's own store, like three songs in the order t3, t1, t2,
 * create a playlist from a list with repeats, then write one broken playlist entry (no id) next to
 * a good one straight into the saved JSON, and kill the process with no lifecycle callback.
 * Phase b (`-e phase b`, a new process): a fresh store must show the liked songs in the order they
 * were liked (before LB-8 they came back in hash order), the playlist without repeats, the good
 * playlist kept and the broken one skipped (before LB-3 one bad entry reset every playlist). One
 * more save must not bring the defaults back. Then it removes everything it added.
 * `scripts/device-check.sh` runs both phases; without `phase` both tests are skipped.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LibraryPersistDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val phase: String? get() = InstrumentationRegistry.getArguments().getString("phase")
    private fun log(s: String) = Log.i("YuLibPersist", s)
    private val prefs get() = ctx.getSharedPreferences("mt_player_music_prefs", Context.MODE_PRIVATE)
    private val ids = listOf(T3, T1, T2)

    @Test fun a_saveThenDie() {
        assumeTrue(phase == "a")
        val repo = OfflineMediaRepository(ctx)
        runBlocking {
            ids.forEach { repo.toggleFavorite(it) }
            repo.createPlaylist(DUP, listOf(T1, T1, T2, T1))
        }
        val liked = repo.playlists.value.first { it.id == "favorites_default" }.trackIds.filter { it in ids }
        assertEquals("liked order in memory", ids, liked)
        Thread.sleep(1_500) // the store saves with apply(); a person does not kill the app within 1 s of a tap
        val raw = prefs.getString("playlists_json", null)
        assertTrue("playlists were not saved", raw != null && raw.contains(DUP))
        val arr = org.json.JSONArray(raw)
        arr.put(org.json.JSONObject().put("name", "YuLB broken").put("trackIds", org.json.JSONArray(listOf(T1))))
        arr.put(org.json.JSONObject().put("id", KEEP_ID).put("name", KEEP).put("createdAt", 1L)
            .put("trackIds", org.json.JSONArray(listOf(T2, T3))))
        assertTrue("could not write the damaged list", prefs.edit().putString("playlists_json", arr.toString()).commit())
        log("LIB_P_A saved liked=$liked playlists=${arr.length()} pid=${android.os.Process.myPid()}")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun b_newProcessKeepsOrderAndGoodPlaylists() {
        assumeTrue(phase == "b")
        val repo = OfflineMediaRepository(ctx)
        try {
            val pls = repo.playlists.value
            log("LIB_P_B pid=${android.os.Process.myPid()} playlists=${pls.map { it.name + ":" + it.trackIds.size }}")
            val liked = pls.first { it.id == "favorites_default" }.trackIds.filter { it in ids }
            assertEquals("LB-8 liked order after a process death", ids, liked)
            val dup = pls.firstOrNull { it.name == DUP }
            assertTrue("LB-8 playlist from a list with repeats is missing", dup != null)
            assertEquals("LB-8 repeats removed", listOf(T1, T2), dup!!.trackIds)
            assertTrue("LB-3 the good playlist next to a broken entry was lost", pls.any { it.id == KEEP_ID && it.trackIds == listOf(T2, T3) })
            assertTrue("LB-3 the broken entry was loaded", pls.none { it.name == "YuLB broken" })
            // One more save (the old bug made the next save permanent) must keep the user's lists.
            runBlocking { repo.renamePlaylist(KEEP_ID, "$KEEP 2") }
            Thread.sleep(1_000)
            val again = org.json.JSONArray(prefs.getString("playlists_json", "[]"))
            val names = (0 until again.length()).map { again.getJSONObject(it).optString("name") }
            log("LIB_P_B after save: $names")
            assertTrue("LB-3 a save after the load dropped the user's lists: $names", "$KEEP 2" in names && DUP in names)
            log("LIB_P_RESULT PASS")
        } finally {
            runBlocking {
                repo.playlists.value.filter { it.name == DUP || it.id == KEEP_ID }.forEach { repo.deletePlaylist(it.id) }
                ids.forEach { id -> if (repo.playlists.value.first { it.id == "favorites_default" }.trackIds.contains(id)) repo.toggleFavorite(id) }
            }
            Thread.sleep(1_000)
            log("LIB_P_B cleaned: ${repo.playlists.value.map { it.name + ":" + it.trackIds.size }}")
        }
    }

    private companion object {
        const val T1 = "yu_lb_t1"; const val T2 = "yu_lb_t2"; const val T3 = "yu_lb_t3"
        const val DUP = "YuLB repeats"; const val KEEP = "YuLB keep"; const val KEEP_ID = "playlist_yulb_keep"
    }
}
