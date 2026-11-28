/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.hub

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Group W (B2): keeps the genres learned from sources ([GenreCatalog.learn]) across restarts. Read once (off the
 * main thread, by the first caller), written once every [SAVE_EVERY] lists seen.
 */
object GenreMemory {
    private const val PREFS = "genre_memory"
    private const val KEY = "learned"
    private const val SAVE_EVERY = 8
    private val loaded = AtomicBoolean(false)
    @Volatile private var pending = 0

    /** Reads the saved names once. Call off the main thread. */
    fun load(context: Context) {
        if (!loaded.compareAndSet(false, true)) return
        GenreCatalog.importLearned(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    /** Cleans [raw] for display, counts its unknown names and saves now and then. Call off the main thread. */
    fun seen(context: Context, raw: List<String>): List<String> {
        load(context)
        val clean = GenreCatalog.clean(raw)
        GenreCatalog.learn(clean)
        if (++pending >= SAVE_EVERY) {
            pending = 0
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, GenreCatalog.exportLearned()).apply()
        }
        return clean
    }
}
