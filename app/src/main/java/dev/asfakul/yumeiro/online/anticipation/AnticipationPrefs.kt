/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online.anticipation

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * B23: the two switches in Settings › Video quality & playback.
 * - "Prepare upcoming episodes" (on): the Anticipation Engine runs.
 * - "Ask before playing from another source" (on): an episode prepared only from another source shows the offer;
 *   off (also set by "Never show again"): it plays at once with a one-line note naming the source (ADR-B23-5).
 */
object AnticipationPrefs {
    private const val PREFS = "yu_anticipation"
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _ask = MutableStateFlow(true)
    val askBeforeAlternative: StateFlow<Boolean> = _ask.asStateFlow()
    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = p.getBoolean("enabled", true)
        _ask.value = p.getBoolean("ask_alternative", true)
    }

    fun setEnabled(context: Context, on: Boolean) {
        _enabled.value = on
        put(context, "enabled", on)
        if (!on) Anticipator.clear()
    }

    fun setAskBeforeAlternative(context: Context, on: Boolean) { _ask.value = on; put(context, "ask_alternative", on) }

    private fun put(context: Context, key: String, v: Boolean) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, v).apply()
}
