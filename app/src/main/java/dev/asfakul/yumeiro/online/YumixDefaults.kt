/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * H-5 (owner item 5): the extensions Yumix's mixed feed uses each time Yumeiro opens. Empty = every extension
 * (the old behaviour). Set in "Show videos from" → Default extensions: at least one, as many as wanted.
 * Picking one extension in the sheet still works for any extension; the defaults only shape "All".
 * Kept per profile, by extension name (what the feed and the filter use).
 */
object YumixDefaults {
    private const val KEY = "defaults"
    private var prefs: SharedPreferences? = null
    private val _names = MutableStateFlow<Set<String>>(emptySet())
    val names: StateFlow<Set<String>> = _names.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, "yumix_defaults"), Context.MODE_PRIVATE).also { prefs = it }
        _names.value = decode(p.getString(KEY, null))
    }

    /** Saves [picked] (empty = every extension again). */
    fun set(context: Context, picked: Set<String>) {
        init(context)
        _names.value = picked
        prefs?.edit()?.putString(KEY, encode(picked))?.apply()
    }

    /**
     * Pure: the names the mixed feed uses. With no defaults, or none of them installed any more, it is every
     * extension, so the feed is never empty because a default was uninstalled.
     */
    fun inMix(all: List<String>, defaults: Set<String>): List<String> {
        if (defaults.isEmpty()) return all
        val picked = all.filter { it in defaults }
        return picked.ifEmpty { all }
    }

    internal fun encode(names: Set<String>) = names.sorted().joinToString("\n")
    internal fun decode(s: String?): Set<String> = s.orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    /** Tests only. */
    internal fun resetForTest() { prefs = null; _names.value = emptySet() }
}
