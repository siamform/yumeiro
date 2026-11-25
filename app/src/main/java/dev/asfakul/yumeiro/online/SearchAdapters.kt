/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.online

import com.lagradost.cloudstream3.MainAPI

/**
 * SEARCH-CAP-1/2 (A0): how each source is searched, in words the search screen shows next to its status.
 * Pure (reads only the provider object) → unit-tested.
 */
object SearchAdapters {
    enum class Way { NATIVE, TITLE_LOOKUP, LOCAL_CATALOG }

    fun wayOf(api: MainAPI): Way = when {
        api.sourcePlugin?.startsWith(dev.asfakul.yumeiro.sources.lookup.StreamLookup.PLUGIN_PREFIX) == true -> Way.TITLE_LOOKUP
        (api as? dev.asfakul.yumeiro.sources.stremio.StremioProvider)?.searchesLocally == true -> Way.LOCAL_CATALOG
        else -> Way.NATIVE
    }

    /** One line under the results when a single source is picked; null for an ordinary source. */
    fun note(way: Way, name: String): String? = when (way) {
        Way.NATIVE -> null
        Way.TITLE_LOOKUP -> "$name has no search of its own, so Yumeiro looked the title up. Its streams appear when you play one."
        Way.LOCAL_CATALOG -> "$name has no search of its own, so Yumeiro searched its catalog."
    }

    /** The empty-state reason for one source that answered with nothing. */
    fun emptyReason(way: Way, name: String, query: String): String = when (way) {
        Way.NATIVE -> "$name found nothing for “$query”. Try another spelling or a shorter title."
        Way.TITLE_LOOKUP -> "No title matched “$query”. Try the original title or add the year."
        Way.LOCAL_CATALOG -> "“$query” isn't in $name's catalog pages. Its search covers only what the add-on lists."
    }

    /** Summary line while / after searching: "8 answered · 3 found nothing · 1 failed". Pure → unit-tested. */
    fun summary(searching: Int, withResults: Int, empty: Int, failed: Int): String? {
        if (searching + withResults + empty + failed == 0) return null
        val parts = buildList {
            if (searching > 0) add("searching $searching")
            if (withResults > 0) add("$withResults with results")
            if (empty > 0) add("$empty found nothing")
            if (failed > 0) add("$failed failed")
        }
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }
}
