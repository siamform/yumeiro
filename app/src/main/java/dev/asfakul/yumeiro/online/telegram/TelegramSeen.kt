/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.telegram

import android.content.Context

/**
 * F-02 (ADR-042): the newest post a saved channel showed last time, so the channel screen can mark
 * what is new since. One number per channel, kept in the saved-sites file ("yu_sites"), so it goes
 * with the saved channel in backups, restores and resets. No background checks: it only changes
 * while the channel screen is open.
 */
object TelegramSeen {
    private const val PREFS = "yu_sites"
    private fun key(channel: String) = "tg_seen_" + channel.lowercase()

    /** Pure: the posts newer than [lastSeen]; nothing is "new" on the first visit (lastSeen null). */
    fun newIds(lastSeen: Int?, ids: Collection<Int>): Set<Int> = if (lastSeen == null) emptySet() else ids.filter { it > lastSeen }.toSet()

    fun get(context: Context, channel: String): Int? =
        context.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE).getInt(key(channel), -1).takeIf { it >= 0 }

    /** Remembers [newest] (never moves back, e.g. after a search shows only older posts). */
    fun mark(context: Context, channel: String, newest: Int) {
        val prefs = context.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE)
        if (newest > prefs.getInt(key(channel), -1)) prefs.edit().putInt(key(channel), newest).apply()
    }

    fun forget(context: Context, channel: String) {
        context.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, PREFS), Context.MODE_PRIVATE).edit().remove(key(channel)).apply()
    }
}
