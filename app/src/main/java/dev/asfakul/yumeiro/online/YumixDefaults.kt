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

    /**
     * B18: the extension picked in "Show videos from" (null = All). It is saved per profile on every change and
     * Yumix opens on it next time, on that extension's own page; picking another one makes that the new default.
     */
    private const val PICK_KEY = "picked"
    private val _picked = MutableStateFlow<String?>(null)
    val picked: StateFlow<String?> = _picked.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, "yumix_defaults"), Context.MODE_PRIVATE).also { prefs = it }
        _names.value = decode(p.getString(KEY, null))
        _picked.value = p.getString(PICK_KEY, null)?.takeIf { it.isNotBlank() }
    }

    /** B18: saves the single pick (null = All). Same value twice writes nothing. */
    fun setPicked(context: Context, name: String?) {
        init(context)
        if (_picked.value == name) return
        _picked.value = name
        prefs?.edit()?.apply { if (name == null) remove(PICK_KEY) else putString(PICK_KEY, name) }?.apply()
    }

    /**
     * Pure (B18): what Yumix opens on. The saved pick while that extension is installed; when the engine has
     * not finished loading yet ([ready] false) the saved pick is kept, so a slow start never drops it. With no
     * pick and exactly one default extension, that one opens on its own page.
     */
    fun openOn(saved: String?, installed: Collection<String>, ready: Boolean, defaults: Set<String>): String? {
        if (saved != null && (!ready || saved in installed)) return saved
        if (saved == null) defaults.singleOrNull()?.let { one -> if (!ready || one in installed) return one }
        return null
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
    internal fun resetForTest() { prefs = null; _names.value = emptySet(); _picked.value = null }
}
