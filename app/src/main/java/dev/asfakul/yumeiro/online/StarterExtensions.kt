/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import android.content.Context
import dev.asfakul.yumeiro.ui.screens.online.topPicks

/**
 * First run: installs the market's top-rated Movies, Anime and Sports (Live) extension so online
 * video works out of the box. Runs once per install and only when nothing is installed yet; the
 * user can remove them any time in Extensions.
 */
object StarterExtensions {
    private const val PREFS = "starter_extensions"
    private const val KEY_DONE = "done_v1"
    private val SPORTS = Regex("sport|football|cricket|soccer|nba|nfl|f1|ufc|tennis", RegexOption.IGNORE_CASE)

    /** The three starter picks from a market catalogue (pure; unit-tested). */
    fun pick(all: List<RemoteExtension>): List<RemoteExtension> {
        val out = LinkedHashMap<String, RemoteExtension>()
        topPicks(all, "Movies", 1).firstOrNull()?.let { out[it.internalName] = it }
        topPicks(all, "Anime", 3).firstOrNull { it.internalName !in out }?.let { out[it.internalName] = it }
        val live = topPicks(all, "Live", 50).filter { it.internalName !in out }
        (live.firstOrNull { SPORTS.containsMatchIn(it.name + " " + it.description.orEmpty()) } ?: live.firstOrNull())
            ?.let { out[it.internalName] = it }
        return out.values.toList()
    }

    /** Installs the starters if this is the first run; returns the names installed. */
    suspend fun ensure(context: Context): List<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(KEY_DONE, false)) return emptyList()
        ExtensionManager.ensureLoaded(context)
        if (ExtensionManager.state.value.installed.isNotEmpty()) { p.edit().putBoolean(KEY_DONE, true).apply(); return emptyList() }
        // No catalogue (offline): try again next launch.
        val catalog = runCatching { ExtensionMarket.load(context) }.getOrNull() ?: return emptyList()
        val picks = pick(catalog.extensions)
        val names = picks.mapNotNull { r -> runCatching { ExtensionManager.install(context, r, ExtensionReview.Mode.AUTO); r.name }.getOrNull() }
        // Done once something installed, or the catalogue simply has nothing suitable. Failed downloads retry next launch.
        if (names.isNotEmpty() || (catalog.extensions.isNotEmpty() && picks.isEmpty())) p.edit().putBoolean(KEY_DONE, true).apply()
        return names
    }
}
