/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.sources.stremio

import dev.asfakul.yumeiro.online.SearchRanker
import dev.asfakul.yumeiro.sources.stremio.StremioAddons.str
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * SEARCH-CAP-2 (A0): a local search index for one Stremio catalog add-on that has no search catalog.
 * The catalog pages are read on the first search, kept for [TTL_MS] and matched with [SearchRanker]
 * (the same ranking the "All" search sorts by). [clock] and the page reader are passed in so tests
 * run on saved answers.
 */
class StremioIndex(private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Mutex()
    @Volatile private var items: List<JsonObject> = emptyList()
    @Volatile private var builtAt = 0L

    suspend fun search(query: String, pages: suspend () -> List<JsonObject>): List<JsonObject> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val all = lock.withLock {
            if (items.isEmpty() || clock() - builtAt > TTL_MS) {
                val fresh = runCatching { pages() }.getOrDefault(emptyList()).distinctBy { it.str("id") }
                // A failed read keeps the old index rather than emptying it.
                if (fresh.isNotEmpty() || items.isEmpty()) { items = fresh; builtAt = clock() }
            }
            items
        }
        return match(q, all)
    }

    companion object {
        const val TTL_MS = 30 * 60_000L
        const val PAGES = 3
        const val MAX_CATALOGS = 8
        const val MIN_SCORE = 60
        const val MAX_RESULTS = 40

        /** Items whose name ranks at least [MIN_SCORE] for [q], best first. Pure → unit-tested. */
        fun match(q: String, all: List<JsonObject>): List<JsonObject> =
            all.mapNotNull { o -> o.str("name")?.let { n -> o to SearchRanker.score(q, n) } }
                .filter { it.second >= MIN_SCORE }
                .sortedByDescending { it.second }
                .take(MAX_RESULTS)
                .map { it.first }

        /** The IMDb id of a Stremio item: its id when that is one (`tt123`, not an episode id), else `imdb_id`. */
        fun imdbOf(id: String, imdbField: String?): String? {
            val base = id.substringBefore(':')
            if (base.startsWith("tt") && base.length > 2 && base.drop(2).all(Char::isDigit)) return base
            return imdbField?.trim()?.takeIf { it.startsWith("tt") && it.length > 2 }
        }
    }
}
