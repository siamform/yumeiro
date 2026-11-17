/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.asfakul.yumeiro.backup.Backup
import dev.asfakul.yumeiro.backup.PendingRestore
import dev.asfakul.yumeiro.backup.ResetCenter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G9 S24 (SE-1) on a device: "Reset settings" and "Clear history" through the same path the app
 * takes (staged plan, applied by PendingRestore at the next launch), on the phone's real
 * shared_prefs folder. Unit tests (SettingsResetTest) cover the rules on Robolectric; this checks
 * that on Android the files are found and the library survives. Every preference file the test
 * touches is put back afterwards. Log tag YuResetTest, RESET_CASE / RESET_RESULT.
 */
@RunWith(AndroidJUnit4::class)
class SettingsResetDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val names = listOf("mt_player", "mt_player_music_prefs", "mt_player_video_prefs", "yu_sites", "yumeiro_listening", "yu_last_played", "scrobble", "online", "tachiyomi_bridge")
    private var saved: Map<String, Map<String, *>> = emptyMap()
    private val historyFile by lazy { java.io.File(ctx.filesDir, "online_history.json") }
    private var historyCopy: ByteArray? = null
    private fun p(n: String) = ctx.getSharedPreferences(n, Context.MODE_PRIVATE)

    @Before fun setUp() {
        PendingRestore.discard(ctx)
        historyCopy = historyFile.takeIf { it.isFile }?.readBytes()
        // Snapshot every included file: the reset goes through all of them.
        val all = Backup.prefsDir(ctx).listFiles()?.filter { it.extension == "xml" }?.map { it.nameWithoutExtension }.orEmpty() + names
        saved = all.distinct().associateWith { HashMap(p(it).all) }
        p("mt_player").edit().putInt("speed", 150).commit()
        p("mt_player_music_prefs").edit().putString("playlists_json", "[{\"id\":\"p1\",\"name\":\"Mine\"}]").putStringSet("favorite_track_ids", setOf("t1"))
            .putString("recently_played_ids", "t1\nt2").putString("play_counts_json", "{\"t1\":3}").commit()
        p("mt_player_video_prefs").edit().putStringSet("favorite_video_ids", setOf("v1")).putLong("pos_v1", 61_000L)
            .putBoolean("watched_v2", true).putStringSet("hidden_video_folders", setOf("/x")).commit()
        p("yumeiro_listening").edit().putLong("total_ms", 3_600_000L).commit()
        p("scrobble").edit().putString("queue", "[{\"a\":\"x\"}]").commit()
        p("online").edit().putString("online_search_history", "naruto").commit()
        p("tachiyomi_bridge").edit().putString("repos", "r").commit()
    }

    @After fun tearDown() {
        PendingRestore.discard(ctx)
        saved.forEach { (name, values) -> restore(p(name), values) }
        historyCopy?.let { historyFile.writeBytes(it) }
    }

    private fun restore(sp: SharedPreferences, values: Map<String, *>) {
        val ed = sp.edit().clear()
        values.forEach { (k, v) ->
            @Suppress("UNCHECKED_CAST")
            when (v) {
                is Int -> ed.putInt(k, v); is Long -> ed.putLong(k, v); is Float -> ed.putFloat(k, v)
                is Boolean -> ed.putBoolean(k, v); is String -> ed.putString(k, v); is Set<*> -> ed.putStringSet(k, v as Set<String>)
            }
        }
        ed.commit()
    }

    @Test fun a_settingsResetKeepsLibrary() {
        assertTrue("prefs folder found: ${Backup.prefsDir(ctx)}", Backup.prefsDir(ctx).isDirectory)
        ResetCenter.stage(ctx, setOf(ResetCenter.Scope.SETTINGS))
        assertTrue("plan applied", PendingRestore.applyIfAny(ctx))
        val speed = p("mt_player").getInt("speed", 0)
        val playlists = p("mt_player_music_prefs").getString("playlists_json", null)
        val pos = p("mt_player_video_prefs").getLong("pos_v1", 0L)
        Log.i(TAG, "RESET_CASE a speed=$speed hidden=${p("mt_player_video_prefs").getStringSet("hidden_video_folders", null)} playlists=$playlists pos=$pos")
        assertEquals("switch back to default", 0, speed)
        assertFalse("hidden folders are a setting", p("mt_player_video_prefs").contains("hidden_video_folders"))
        assertEquals("[{\"id\":\"p1\",\"name\":\"Mine\"}]", playlists)
        assertEquals(setOf("t1"), p("mt_player_music_prefs").getStringSet("favorite_track_ids", null))
        assertEquals(setOf("v1"), p("mt_player_video_prefs").getStringSet("favorite_video_ids", null))
        assertEquals(61_000L, pos)
        assertEquals(3_600_000L, p("yumeiro_listening").getLong("total_ms", 0L))
        assertEquals("[{\"a\":\"x\"}]", p("scrobble").getString("queue", null))
        assertEquals("r", p("tachiyomi_bridge").getString("repos", null))
        Log.i(TAG, "RESET_RESULT PASS a")
    }

    @Test fun b_historyResetKeepsTheRest() {
        ResetCenter.stage(ctx, setOf(ResetCenter.Scope.HISTORY))
        assertTrue("plan applied", PendingRestore.applyIfAny(ctx))
        Log.i(TAG, "RESET_CASE b pos=${p("mt_player_video_prefs").contains("pos_v1")} search=${p("online").contains("online_search_history")} playlists=${p("mt_player_music_prefs").contains("playlists_json")}")
        assertFalse(p("mt_player_video_prefs").contains("pos_v1"))
        assertFalse(p("mt_player_video_prefs").contains("watched_v2"))
        assertFalse(p("mt_player_music_prefs").contains("recently_played_ids"))
        assertFalse(p("online").contains("online_search_history"))
        assertTrue(p("mt_player_music_prefs").contains("playlists_json"))
        assertEquals(setOf("v1"), p("mt_player_video_prefs").getStringSet("favorite_video_ids", null))
        assertEquals(150, p("mt_player").getInt("speed", 0))
        Log.i(TAG, "RESET_RESULT PASS b")
    }

    private companion object { const val TAG = "YuResetTest" }
}
