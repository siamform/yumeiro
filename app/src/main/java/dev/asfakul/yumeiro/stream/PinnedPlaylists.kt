/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.stream

import android.content.Context
import android.content.SharedPreferences
import dev.asfakul.yumeiro.online.PinnedExtensions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NBUG-15 (ADR-028): pinned playlists for the Live TV filter. Pinned playlists sit first in "Show videos
 * from", come first in the merged feed and get a one-tap chip under the header. Kept by playlist id (two
 * playlists can share a name), in pin order, at most [MAX]. Own prefs file, so a revert leaves nothing behind.
 */
object PinnedPlaylists {
    const val MAX = PinnedExtensions.MAX
    private const val KEY = "pinned"
    private var prefs: SharedPreferences? = null
    private val _pins = MutableStateFlow<List<String>>(emptyList())
    val pins: StateFlow<List<String>> = _pins.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(dev.asfakul.yumeiro.profiles.Profiles.prefsName(context, "pinned_playlists"), Context.MODE_PRIVATE).also { prefs = it }
        _pins.value = PinnedExtensions.decode(p.getString(KEY, null))
    }

    /** Pins or unpins [id]. Returns false when pinning would go over [MAX]. */
    fun toggle(context: Context, id: String): Boolean {
        init(context)
        val next = PinnedExtensions.toggled(_pins.value, id) ?: return false
        _pins.value = next
        prefs?.edit()?.putString(KEY, PinnedExtensions.encode(next))?.apply()
        return true
    }

    /** Pure: playlist ids with the pinned ones first (pin order), then the rest in their own order. */
    fun pinnedFirst(ids: List<String>, pins: List<String>): List<String> = PinnedExtensions.pinnedFirst(ids, pins)

    internal fun resetForTests() { prefs = null; _pins.value = emptyList() }
}
