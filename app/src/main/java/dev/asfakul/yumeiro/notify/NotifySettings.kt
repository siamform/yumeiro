/*
 * Yumeiro — Copyright (C) 2026 dev-asfakul
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the user can control about notifications, reminders and background work
 * (Settings → Notifications and Settings → Background activity). All on by default except
 * "Wi-Fi only".
 */
object NotifySettings {
    private const val PREFS = "notify_settings"

    enum class Key(val pref: String, val default: Boolean) {
        /** Master switch for system notifications (the in-app inbox still fills). */
        ENABLED("enabled", true),
        EPISODES("episodes", true),
        EXTENSIONS("extensions", true),
        PLAYLISTS("playlists", true),
        /** New features, releases and announcements from the Yumeiro feed. */
        NEWS("news", true),
        /** Occasional "Feeling bored? Watch a movie" ideas. */
        NUDGES("nudges", true),
        /** Yumeiro's own chime instead of silence. */
        SOUND("sound", true),
        /** In-app reminders: follow, star on GitHub, support. */
        REMINDERS("reminders", true),
        /** Periodic background checks (new episodes, feed). */
        BACKGROUND("background", true),
        /** Background checks only on Wi-Fi / unmetered networks. */
        WIFI_ONLY("wifi_only", false),
    }

    private val flows = Key.entries.associateWith { MutableStateFlow(it.default) }
    @Volatile private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun init(c: Context) {
        if (loaded) return
        val p = prefs(c)
        Key.entries.forEach { k -> flows.getValue(k).value = p.getBoolean(k.pref, k.default) }
        loaded = true
    }

    fun flow(k: Key): StateFlow<Boolean> = flows.getValue(k).asStateFlow()
    fun get(c: Context, k: Key): Boolean { init(c); return flows.getValue(k).value }

    fun set(c: Context, k: Key, v: Boolean) {
        init(c)
        flows.getValue(k).value = v
        prefs(c).edit().putBoolean(k.pref, v).apply()
        if (k == Key.BACKGROUND || k == Key.WIFI_ONLY) BackgroundWork.apply(c)
    }

    // Kept for existing callers.
    val extensions: StateFlow<Boolean> get() = flow(Key.EXTENSIONS)
    val playlists: StateFlow<Boolean> get() = flow(Key.PLAYLISTS)
    val nudges: StateFlow<Boolean> get() = flow(Key.NUDGES)
    fun setExtensions(c: Context, v: Boolean) = set(c, Key.EXTENSIONS, v)
    fun setPlaylists(c: Context, v: Boolean) = set(c, Key.PLAYLISTS, v)
    fun setNudges(c: Context, v: Boolean) = set(c, Key.NUDGES, v)
}
