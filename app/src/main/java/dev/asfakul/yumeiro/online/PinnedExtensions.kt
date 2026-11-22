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
 * R15 Phase 5: favourite (pinned) extensions. Pinned extensions sit first in "Show videos from"
 * and get a one-tap chip beside the filter in Yumix. Kept in the order they were pinned, by name
 * (the name is what the feed and the filter use), at most [MAX].
 */
object PinnedExtensions {
    const val MAX = 8
    private const val KEY = "pinned"
    private var prefs: SharedPreferences? = null
    private val _pins = MutableStateFlow<List<String>>(emptyList())
    val pins: StateFlow<List<String>> = _pins.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, "pinned_extensions"), Context.MODE_PRIVATE).also { prefs = it }
        _pins.value = decode(p.getString(KEY, null))
    }

    fun isPinned(name: String) = name in _pins.value

    /** Pins or unpins [name]. Returns false when pinning would go over [MAX]. */
    fun toggle(context: Context, name: String): Boolean {
        init(context)
        val next = toggled(_pins.value, name) ?: return false
        _pins.value = next
        prefs?.edit()?.putString(KEY, encode(next))?.apply()
        return true
    }

    /** Pure: the pin list after toggling [name]; null if it is full. */
    internal fun toggled(pins: List<String>, name: String): List<String>? = when {
        name in pins -> pins - name
        pins.size >= MAX -> null
        else -> pins + name
    }

    /** Pure: [names] with the pinned ones first (in pin order), the rest in their own order. */
    fun pinnedFirst(names: List<String>, pins: List<String>): List<String> {
        val on = names.toHashSet()
        val first = pins.filter { it in on }
        val set = first.toHashSet()
        return first + names.filter { it !in set }
    }

    internal fun encode(pins: List<String>) = pins.joinToString("\n")
    internal fun decode(s: String?): List<String> = s.orEmpty().split('\n').filter { it.isNotBlank() }.distinct().take(MAX)

    internal fun resetForTests() { prefs = null; _pins.value = emptyList() }
}
