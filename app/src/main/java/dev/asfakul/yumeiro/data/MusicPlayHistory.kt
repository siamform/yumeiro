/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.data

import android.content.Context
import java.util.concurrent.atomic.AtomicInteger

/**
 * G5-PC1: music "Recently played" and play counts.
 *
 * These were recorded only by the screen's MusicPlayerManager listener. When the app is swiped
 * away from recents while music plays, PlaybackService keeps playing the queue, but the activity's
 * ViewModel is cleared and its listener removed. Songs after that were never counted. The shared
 * player's process-wide watch now records song changes while no screen listener is attached.
 * There is still only one writer at a time: the screen's repository while a screen listens,
 * this object otherwise. A new screen's repository reads the same preferences when it starts.
 */
internal object MusicPlayHistory {
    const val PREFS = "mt_player_music_prefs"
    const val KEY_RECENT = "recently_played_ids"
    const val KEY_PLAY_COUNTS = "play_counts_json"
    const val MAX_RECENT = 50

    /** Screen-scoped MusicPlayerManager listeners attached to the shared player (main thread). */
    val screenRecorders = AtomicInteger(0)

    /** Track [id] pushed to the front of [recent] (max [MAX_RECENT]) and its count increased. */
    fun next(recent: List<String>, counts: Map<String, Int>, id: String): Pair<List<String>, Map<String, Int>> {
        val list = (listOf(id) + recent.filter { it != id }).take(MAX_RECENT)
        return list to (counts + (id to (counts[id] ?: 0) + 1))
    }

    /** Records one play straight into the preferences. Only when no screen is listening. */
    fun recordWithoutScreen(context: Context, id: String) {
        if (id.isBlank() || screenRecorders.get() > 0) return
        if (dev.asfakul.yumeiro.security.Privacy.incognito.value) return
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val recent = prefs.getString(KEY_RECENT, null).orEmpty().split('\n').filter { it.isNotBlank() }
            val counts = runCatching {
                val obj = org.json.JSONObject(prefs.getString(KEY_PLAY_COUNTS, null) ?: "{}")
                obj.keys().asSequence().associateWith { obj.optInt(it, 0) }
            }.getOrElse { emptyMap() }
            val (r, c) = next(recent, counts, id)
            prefs.edit()
                .putString(KEY_RECENT, r.joinToString("\n"))
                .putString(KEY_PLAY_COUNTS, org.json.JSONObject(c as Map<*, *>).toString())
                .apply()
        }
    }
}
