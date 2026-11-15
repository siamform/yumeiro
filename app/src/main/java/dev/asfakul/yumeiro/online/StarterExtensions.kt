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
    suspend fun ensure(context: Context): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ensureOnIo(context) }

    // Called from a LaunchedEffect on the main thread; the first prefs read is disk I/O (StrictMode, run 43632313418).
    private suspend fun ensureOnIo(context: Context): List<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(KEY_DONE, false)) return emptyList()
        ExtensionManager.ensureLoaded(context)
        val tries = p.getInt(KEY_TRIES, 0)
        // A user who already has extensions needs no starters, unless an earlier starter run was only partly done.
        if (tries == 0 && ExtensionManager.state.value.installed.isNotEmpty()) { p.edit().putBoolean(KEY_DONE, true).apply(); return emptyList() }
        // No catalogue (offline): try again next launch.
        val catalog = try { ExtensionMarket.load(context) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { null } ?: return emptyList()
        val picks = pick(catalog.extensions)
        val have = ExtensionManager.state.value.installed.map { it.internalName }.toSet()
        val names = picks.filter { it.internalName !in have }.mapNotNull { r ->
            try { ExtensionManager.install(context, r, ExtensionReview.Mode.AUTO); r.name }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Throwable) { null }
        }
        val now = ExtensionManager.state.value.installed.map { it.internalName }.toSet()
        val complete = picks.all { it.internalName in now }
        // Done when every pick is in, the catalogue has nothing suitable, or after MAX_TRIES launches
        // (a starter that keeps failing must not cost a download on every start). Before, one success
        // ended it and the failed picks were never tried again.
        val done = complete || (catalog.extensions.isNotEmpty() && picks.isEmpty()) || tries + 1 >= MAX_TRIES
        p.edit().putInt(KEY_TRIES, tries + 1).putBoolean(KEY_DONE, done).apply()
        return names
    }

    internal const val MAX_TRIES = 3
    private const val KEY_TRIES = "tries_v1"
}
